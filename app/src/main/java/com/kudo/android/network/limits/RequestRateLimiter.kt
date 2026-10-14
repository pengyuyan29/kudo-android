package com.kudo.android.network.limits

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 请求优先级。数值越小越先放行。
 */
enum class RequestPriority(val value: Int) {
    HIGH(0),   // 用户写操作（POST/PUT/DELETE/PATCH）
    NORMAL(1), // 普通 GET
    LOW(2),    // 静默请求（心跳、后台刷新、头像批量）
}

/**
 * 按 host 的滑动窗口限流拦截器。
 *
 * 同一 host 的所有 OkHttp 实例共享同一份状态，避免主站、connect、推送各自计数
 * 导致用户视角的真实速率 = 各实例之和，远超配置阈值而被服务端按 IP/cookie 维度 429。
 *
 * 超过并发或窗口上限时请求进入优先级队列等待；取消的排队请求会被跳过，
 * 不会阻塞后续请求。
 */
class RequestRateLimiter(
    private val settings: RateLimitSettings = RateLimitSettings(),
    private val priorityOf: (Interceptor.Chain) -> RequestPriority = { inferPriority(it) },
) : Interceptor {

    private val states = HashMap<String, HostState>()
    private val statesLock = ReentrantLock()

    private fun stateFor(host: String): HostState = statesLock.withLock {
        states.getOrPut(host.lowercase()) { HostState() }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val state = stateFor(host)
        val priority = priorityOf(chain)

        // 有空闲槽位且速率允许 → 直接放行
        if (state.tryAcquire(settings)) {
            return proceedAndRelease(chain, state, counted = true)
        }

        // 排队等待
        val entry = QueuedRequest(priority)
        state.enqueue(entry)
        state.drain(settings)
        entry.await(
            isCancelled = { chain.call().isCanceled() },
            onPoll = { state.drain(settings) },
        )
        if (entry.cancelled || chain.call().isCanceled()) {
            // 若已被 drain 授予配额，取消时归还，避免槽位泄漏。
            if (entry.granted) state.release(settings) else state.remove(entry)
            throw IOException("请求在限流队列中被取消")
        }
        // drain 已为该 entry 计入一次配额
        return proceedAndRelease(chain, state, counted = true)
    }

    private fun proceedAndRelease(chain: Interceptor.Chain, state: HostState, counted: Boolean): Response {
        try {
            return chain.proceed(chain.request())
        } finally {
            if (counted) state.release(settings)
        }
    }

    companion object {
        fun inferPriority(chain: Interceptor.Chain): RequestPriority = when (chain.request().method.uppercase()) {
            "POST", "PUT", "DELETE", "PATCH" -> RequestPriority.HIGH
            else -> RequestPriority.NORMAL
        }
    }
}

/**
 * 单个 host 的调度状态。
 */
private class HostState {
    private val lock = ReentrantLock()
    private val timestamps = ArrayDeque<Long>()
    private val queue = ArrayList<QueuedRequest>()
    private var running = 0

    /** 有空闲槽位且窗口未满时占用一个配额。 */
    fun tryAcquire(settings: RateLimitSettings): Boolean = lock.withLock {
        evict(settings)
        if (running >= settings.maxConcurrent || timestamps.size >= settings.maxPerWindow) return false
        running++
        timestamps.addLast(System.nanoTime())
        true
    }

    fun release(settings: RateLimitSettings) {
        lock.withLock {
            if (running > 0) running--
            drainLocked(settings)
        }
    }

    fun enqueue(entry: QueuedRequest) = lock.withLock { queue.add(entry) }

    fun remove(entry: QueuedRequest) = lock.withLock { queue.remove(entry) }

    fun drain(settings: RateLimitSettings) = lock.withLock { drainLocked(settings) }

    private fun drainLocked(settings: RateLimitSettings) {
        evict(settings)
        queue.sortWith(compareBy({ it.priority.value }, { it.sequence }))
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            if (running >= settings.maxConcurrent || timestamps.size >= settings.maxPerWindow) break
            val entry = iterator.next()
            if (entry.cancelled) { iterator.remove(); continue }
            iterator.remove()
            running++
            timestamps.addLast(System.nanoTime())
            entry.complete()
        }
    }

    private fun evict(settings: RateLimitSettings) {
        val cutoff = System.nanoTime() - settings.windowSeconds * 1_000_000_000L
        while (timestamps.isNotEmpty() && timestamps.first() < cutoff) timestamps.removeFirst()
    }
}

/**
 * 排队中的请求。等待被唤醒或取消。
 */
private class QueuedRequest(val priority: RequestPriority) {
    @Volatile var cancelled = false
        private set
    @Volatile var granted = false
        private set
    private var done = false
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    val sequence: Long = SEQUENCE.incrementAndGet()

    fun complete() = lock.withLock {
        granted = true
        done = true
        condition.signalAll()
    }

    /**
     * 等待被调度。定期醒来检查 [isCancelled] 并触发 [onPoll]，避免调用方取消后
     * 永久阻塞，也避免窗口到期后无人重新调度。
     */
    fun await(isCancelled: () -> Boolean, onPoll: () -> Unit) = lock.withLock {
        while (!done) {
            if (isCancelled()) {
                cancelled = true
                return
            }
            condition.await(250, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!done) onPoll()
        }
    }

    companion object {
        private val SEQUENCE = java.util.concurrent.atomic.AtomicLong()
    }
}
