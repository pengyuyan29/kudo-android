package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.account.AccountViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    @Test fun lateSessionResponseCannotRestoreAccountAfterLogout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("""{"current_user":{"id":7,"username":"former-user"}}""").setBodyDelay(300, TimeUnit.MILLISECONDS))
        var cleared = false
        val vm = AccountViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), {}, { cleared = true })
        try {
            runCurrent()
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
            vm.logout()
            vm.viewModelScope.coroutineContext.job.children.toList().joinAll()
            assertTrue(cleared)
            assertNull("退出登录后，较早的会话检查不得重新写回用户", vm.user)
        } finally { vm.viewModelScope.cancel(); server.shutdown(); Dispatchers.resetMain() }
    }
}
