package com.kudo.android

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kudo.android.ui.IncomingLinuxDoLink
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeepLinkUiTest {
    @get:Rule val compose = createComposeRule()
    private var incoming by mutableStateOf<String?>(null)
    private var sequence by mutableStateOf(0L)
    private var selected = 4
    private var consumed = 0
    private lateinit var nav: NavHostController

    private fun show() {
        compose.setContent {
            nav = rememberNavController()
            IncomingLinuxDoLink(nav, incoming, sequence, { selected = 0 }) { incoming = null; consumed++ }
            NavHost(nav, "root") {
                composable("root") { Text("首页") }
                composable("user/{username}") { Text("用户：${it.arguments!!.getString("username")}") }
                composable("category/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                    Text("分类：${it.arguments!!.getInt("id")}")
                }
                composable("topic/{id}?post={post}", arguments = listOf(navArgument("id") { type = NavType.IntType },
                    navArgument("post") { type = NavType.IntType; defaultValue = 0 })) {
                    Text("帖子：${it.arguments!!.getInt("id")} 楼层：${it.arguments!!.getInt("post")}")
                }
            }
        }
    }

    private fun open(url: String) { compose.runOnIdle { sequence++; incoming = url } }

    @Test fun initialAndRepeatedLinkAfterBackAreConsumedSeparately() {
        val url = "https://linux.do/t/topic/3001000/8"
        incoming = url
        show()
        compose.onNodeWithText("帖子：3001000 楼层：8").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, consumed); assertNull(incoming); nav.popBackStack() }
        compose.onNodeWithText("首页").assertIsDisplayed()
        open(url)
        compose.onNodeWithText("帖子：3001000 楼层：8").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, consumed); nav.popBackStack() }
        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test fun profileSubpathAndOtherSiteLinkReturnToHomeTab() {
        show()
        open("https://linux.do/u/stingysheep/activity/replies/")
        compose.onNodeWithText("用户：stingysheep").assertIsDisplayed()
        open("https://linux.do/guidelines")
        compose.onNodeWithText("首页").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, selected) }
        open("https://linux.do/t/topic/3001000")
        compose.onNodeWithText("帖子：3001000 楼层：0").assertIsDisplayed()
        open("https://linux.do/c/4")
        compose.onNodeWithText("分类：4").assertIsDisplayed()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("帖子：3001000 楼层：0").assertIsDisplayed()
    }
}
