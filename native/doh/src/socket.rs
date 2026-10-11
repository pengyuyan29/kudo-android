use crate::dns::{self, Resolver};
use base64::{engine::general_purpose::STANDARD, Engine};
use rustls::{client::EchStatus, pki_types::ServerName, ClientConnection, StreamOwned};
use serde_json::{json, Value};
use std::{
    io::{self, Read, Write},
    net::{IpAddr, Shutdown, SocketAddr, TcpStream},
    sync::{
        atomic::{AtomicBool, AtomicU64, Ordering},
        Arc, Mutex,
    },
    time::{Duration, Instant},
};

struct SharedTcp {
    tcp: Arc<TcpStream>,
    closed: Arc<AtomicBool>,
    read_timeout: Arc<AtomicU64>,
}
impl Read for SharedTcp {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let millis = self.read_timeout.load(Ordering::SeqCst);
        self.tcp
            .set_read_timeout(Some(Duration::from_millis(if millis == 0 {
                200
            } else {
                millis.min(200)
            })))?;
        let start = Instant::now();
        loop {
            if self.closed.load(Ordering::SeqCst) {
                return Err(other("socket_closed"));
            }
            match (&*self.tcp).read(buf) {
                Err(e)
                    if matches!(
                        e.kind(),
                        io::ErrorKind::TimedOut | io::ErrorKind::WouldBlock
                    ) =>
                {
                    if millis > 0 && start.elapsed() >= Duration::from_millis(millis) {
                        return Err(e);
                    }
                }
                result => return result,
            }
        }
    }
}
impl Write for SharedTcp {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        let start = Instant::now();
        loop {
            if self.closed.load(Ordering::SeqCst) {
                return Err(other("socket_closed"));
            }
            match (&*self.tcp).write(buf) {
                Err(e)
                    if matches!(
                        e.kind(),
                        io::ErrorKind::TimedOut | io::ErrorKind::WouldBlock
                    ) && start.elapsed() < Duration::from_secs(30) => {}
                result => return result,
            }
        }
    }
    fn flush(&mut self) -> io::Result<()> {
        (&*self.tcp).flush()
    }
}
enum State {
    Empty,
    Plain(SharedTcp),
    Tls(Box<StreamOwned<ClientConnection, SharedTcp>>),
}
pub struct Socket {
    resolver: Arc<Resolver>,
    state: Mutex<State>,
    // Shutdown must never wait for the mutex held by a blocking read/write.
    shutdown: Mutex<Option<Arc<TcpStream>>>,
    closed: Arc<AtomicBool>,
    read_timeout: Arc<AtomicU64>,
}

fn other(code: &'static str) -> io::Error {
    crate::code_error(code)
}
impl Socket {
    pub fn new(resolver: Arc<Resolver>) -> Self {
        Self {
            resolver,
            state: Mutex::new(State::Empty),
            shutdown: Mutex::new(None),
            closed: Arc::new(AtomicBool::new(false)),
            read_timeout: Arc::new(AtomicU64::new(0)),
        }
    }
    pub fn connect(&self, ip: &str, port: u16, millis: u64) -> io::Result<()> {
        if self.closed.load(Ordering::SeqCst) {
            return Err(other("socket_closed"));
        }
        let ip = ip.parse::<IpAddr>().map_err(|_| other("invalid_ip"))?;
        let tcp = TcpStream::connect_timeout(
            &SocketAddr::new(ip, port),
            Duration::from_millis(millis.max(1)),
        )?;
        tcp.set_nodelay(true)?;
        tcp.set_write_timeout(Some(Duration::from_millis(200)))?;
        let mut shutdown = self.shutdown.lock().map_err(|_| other("socket_lock"))?;
        if self.closed.load(Ordering::SeqCst) {
            let _ = tcp.shutdown(Shutdown::Both);
            return Err(other("socket_closed"));
        }
        let tcp = Arc::new(tcp);
        *shutdown = Some(tcp.clone());
        *self.state.lock().map_err(|_| other("socket_lock"))? = State::Plain(SharedTcp {
            tcp,
            closed: self.closed.clone(),
            read_timeout: self.read_timeout.clone(),
        });
        Ok(())
    }
    pub fn handshake(&self, host: &str) -> io::Result<Value> {
        let answer = crate::runtime()
            .block_on(self.resolver.resolve(host))
            .map_err(other)?;
        let use_ech = !answer.ech.is_empty();
        if dns::requires_ech(host) && !use_ech {
            return Err(other("ech_config_missing"));
        }
        let mut config =
            dns::tls_config(if use_ech { Some(answer.ech) } else { None }).map_err(other)?;
        // OkHttp's Android socket adapters do not recognize this custom Socket for ALPN.
        // Advertise only the protocol that the HTTP layer actually uses.
        config.alpn_protocols = vec![b"http/1.1".to_vec()];
        let name = ServerName::try_from(host.to_owned()).map_err(|_| other("invalid_hostname"))?;
        let mut state = self.state.lock().map_err(|_| other("socket_lock"))?;
        let tcp = match std::mem::replace(&mut *state, State::Empty) {
            State::Plain(tcp) => tcp,
            other_state => {
                *state = other_state;
                return Err(other("socket_not_plain"));
            }
        };
        let conn = ClientConnection::new(Arc::new(config), name)
            .map_err(|_| other("tls_configuration_failed"))?;
        let mut stream = StreamOwned::new(conn, tcp);
        while stream.conn.is_handshaking() {
            if let Err(e) = stream.conn.complete_io(&mut stream.sock) {
                if stream.conn.ech_status() == EchStatus::Rejected {
                    self.resolver.invalidate(host);
                    return Err(other("ech_rejected"));
                }
                if matches!(
                    e.kind(),
                    io::ErrorKind::TimedOut | io::ErrorKind::WouldBlock
                ) {
                    return Err(e);
                }
                return Err(other("tls_handshake_or_certificate_failed"));
            }
        }
        if use_ech && stream.conn.ech_status() != EchStatus::Accepted {
            return Err(other("ech_rejected"));
        }
        let certs: Vec<_> = stream
            .conn
            .peer_certificates()
            .ok_or(other("tls_certificate_missing"))?
            .iter()
            .map(|c| STANDARD.encode(c.as_ref()))
            .collect();
        let protocol = match stream.conn.protocol_version() {
            Some(rustls::ProtocolVersion::TLSv1_3) => "TLSv1.3",
            Some(rustls::ProtocolVersion::TLSv1_2) => "TLSv1.2",
            _ => return Err(other("tls_version_invalid")),
        };
        let cipher = format!(
            "{:?}",
            stream
                .conn
                .negotiated_cipher_suite()
                .ok_or(other("tls_cipher_missing"))?
                .suite()
        )
        .replace("TLS13_", "TLS_");
        let metadata = json!({"protocol":protocol,"cipher":cipher,"certs":certs,"ech":if use_ech {"Accepted"} else {"NotOffered"}});
        *state = State::Tls(Box::new(stream));
        Ok(metadata)
    }
    pub fn read(&self, buf: &mut [u8]) -> io::Result<usize> {
        let mut state = self.state.lock().map_err(|_| other("socket_lock"))?;
        match &mut *state {
            State::Plain(s) => s.read(buf),
            State::Tls(s) => s.read(buf),
            State::Empty => Err(other("socket_not_connected")),
        }
    }
    pub fn write(&self, buf: &[u8]) -> io::Result<()> {
        let mut state = self.state.lock().map_err(|_| other("socket_lock"))?;
        match &mut *state {
            State::Plain(s) => {
                s.write_all(buf)?;
                s.flush()
            }
            State::Tls(s) => {
                s.write_all(buf)?;
                s.flush()
            }
            State::Empty => Err(other("socket_not_connected")),
        }
    }
    pub fn timeout(&self, millis: u64) -> io::Result<()> {
        self.read_timeout.store(millis, Ordering::SeqCst);
        Ok(())
    }
    pub fn close(&self) {
        self.closed.store(true, Ordering::SeqCst);
        if let Ok(mut tcp) = self.shutdown.lock() {
            if let Some(tcp) = tcp.take() {
                let _ = tcp.shutdown(Shutdown::Both);
            }
        }
    }
}
impl Drop for Socket {
    fn drop(&mut self) {
        self.close();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{net::TcpListener, thread, time::Instant};
    fn connected() -> (Arc<Socket>, TcpStream) {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let socket = Arc::new(Socket::new(Arc::new(
            Resolver::new(r#"{"url":"https://dns.test/query"}"#).unwrap(),
        )));
        socket
            .connect("127.0.0.1", listener.local_addr().unwrap().port(), 1000)
            .unwrap();
        let peer = listener.accept().unwrap().0;
        (socket, peer)
    }
    #[test]
    fn close_interrupts_a_blocking_read_without_waiting_for_state_lock() {
        let (socket, _peer) = connected();
        let reader = socket.clone();
        let (sender, result) = std::sync::mpsc::channel();
        thread::spawn(move || {
            let _ = sender.send(reader.read(&mut [0; 8]));
        });
        thread::sleep(Duration::from_millis(30));
        let start = Instant::now();
        socket.close();
        assert!(result
            .recv_timeout(Duration::from_secs(1))
            .expect("close must interrupt read")
            .map(|n| n == 0)
            .unwrap_or(true));
        assert!(start.elapsed() < Duration::from_secs(1));
    }
    #[test]
    fn streams_bytes_and_read_timeout_is_not_a_reconnect() {
        let (socket, mut peer) = connected();
        socket.write(b"one request").unwrap();
        let mut bytes = [0; 11];
        peer.read_exact(&mut bytes).unwrap();
        assert_eq!(&bytes, b"one request");
        socket.timeout(30).unwrap();
        assert!(matches!(
            socket.read(&mut [0; 1]).unwrap_err().kind(),
            io::ErrorKind::TimedOut | io::ErrorKind::WouldBlock
        ));
        peer.write_all(b"ok").unwrap();
        assert_eq!(socket.read(&mut bytes).unwrap(), 2);
    }
    #[test]
    fn closed_socket_cannot_start_a_connection() {
        let socket = Socket::new(Arc::new(
            Resolver::new(r#"{"url":"https://dns.test/query"}"#).unwrap(),
        ));
        socket.close();
        assert!(socket.connect("127.0.0.1", 9, 100).is_err());
    }
}
