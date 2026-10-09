package com.kudo.android

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.account.*
import com.kudo.android.ui.theme.KudoTheme
import kotlinx.coroutines.cancel
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class MemberAccountUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val writes = AtomicInteger()
    private lateinit var http: OkHttpClient
    private lateinit var repository: CommunityRepository
    private var vm: AccountViewModel? = null
    @Before fun fixture() {
        http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.method != "GET") writes.incrementAndGet()
            val body = when (request.url.encodedPath) {
                "/session/current.json" -> """{"current_user":{"id":42,"username":"fixture","name":"资料测试","trust_level":3}}"""
                "/u/fixture.json" -> """{"user":{"id":42,"username":"fixture","name":"资料测试","trust_level":3}}"""
                "/u/fixture/summary.json" -> """{"user_summary":{"days_visited":825,"posts_read_count":357012,"likes_received":4000,"post_count":3102}}"""
                "/notifications" -> if (request.url.queryParameter("offset") == "30")
                    """{"notifications":[{"id":31,"notification_type":25,"data":{"title":"后页回应","display_username":"fixture"}}],"total_rows_notifications":31}"""
                    else """{"notifications":[${(1..30).joinToString(",") { """{"id":$it,"notification_type":2,"data":{"title":"回复夹具$it","display_username":"fixture"}}""" }}],"total_rows_notifications":31,"load_more_notifications":"/notifications?offset=30&limit=30"}"""
                else -> error("Unexpected fixture endpoint ${request.url.encodedPath}")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        repository = CommunityRepository(DiscourseClient(object : SessionCookies {
            override fun get(url: String) = ""
            override fun set(url: String, cookie: String) {}
        }, "https://member.test/".toHttpUrl(), http = http))
    }
    @After fun release() { vm?.viewModelScope?.cancel(); http.dispatcher.executorService.shutdown() }

    @Test fun memberUsesRealStatisticsAndCompactWorkingEntrancesWithoutDuplicateHeaderActions() {
        val destinations = mutableListOf<AccountDestination>()
        var profiles = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                ProfileScreen(Member(42, "fixture", "资料测试", "", trustLevel = 3), {}, {}, {}, { profiles++ },
                    repository = repository, destination = destinations::add)
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("825").fetchSemanticsNodes().isNotEmpty() }
        for (text in listOf("访问天数", "已读帖子", "获赞", "发帖数", "35.7万", "4000", "3102")) compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithText("我的").assertDoesNotExist()
        compose.onNodeWithContentDescription("设置").assertDoesNotExist()
        compose.onNodeWithText("查看个人主页  →").assertDoesNotExist()
        capture("member-profile-light.png", "member-profile")
        compose.onNodeWithTag("member-avatar").performClick()
        compose.onNodeWithText("Lv.3").performClick()
        compose.onNodeWithText("我的草稿").performClick()
        compose.onNodeWithText("邀请链接").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, profiles); assertEquals(listOf(AccountDestination.TRUST, AccountDestination.DRAFTS), destinations); assertEquals(0, writes.get()) }
    }
    @Test fun likesGroupFindsReactionOnFollowingPageWithoutMarkingRealOrFixtureNotificationsRead() {
        vm = AccountViewModel(repository, {}, {})
        val account = vm!!
        compose.setContent { KudoTheme(DisplaySettings()) { NotificationsScreen(account, {}, { _, _ -> }) } }
        compose.waitUntil(5000) { account.user != null && account.noticesLoaded }
        compose.onNodeWithText("赞", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { account.notices.any { it.id == 31 } }
        compose.onNodeWithText("后页回应").assertIsDisplayed()
        compose.onNodeWithText("回复夹具1").assertIsNotDisplayed()
        capture("member-notifications-likes.png", "member-notifications")
        compose.runOnIdle { assertEquals(0, writes.get()) }
    }
    @Test fun darkLargeTextKeepsAllFourQuickEntrancesReachable() {
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            Box(Modifier.fillMaxSize()) { Box(Modifier.width(280.dp).height(480.dp)) {
                ProfileScreen(Member(42, "fixture", "较长的昵称用于验证文字布局", "", trustLevel = 2), {}, {}, {}, {})
            } }
        } }
        for (label in listOf("我的话题", "我的收藏", "我的草稿", "浏览历史", "界面与显示", "关于酷DO")) {
            compose.onNodeWithTag("member-profile").performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNodeWithText("邀请链接").assertDoesNotExist()
    }

    private fun capture(name: String, tag: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
