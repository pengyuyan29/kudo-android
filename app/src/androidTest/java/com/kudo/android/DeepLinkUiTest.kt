package com.kudo.android

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepLinkUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun open(url: String) {
        compose.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url), compose.activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        compose.waitForIdle()
    }

    @Test fun repeatedLinkAfterBackAndRecreationDoesNotReplayConsumedLink() {
        val url = "https://linux.do/t/topic/3001000/8"
        open(url)
        compose.onNodeWithTag("topic-top-bar").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("topic-top-bar").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("topic-top-bar").assertDoesNotExist()
        open(url)
        compose.onNodeWithTag("topic-top-bar").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("topic-top-bar").assertDoesNotExist()
    }

    @Test fun profileSubpathAndOtherSiteLinkReturnToHome() {
        open("https://linux.do/u/stingysheep/activity/replies/")
        compose.onNodeWithText("个人主页").assertIsDisplayed()
        open("https://linux.do/guidelines")
        compose.onNodeWithText("个人主页").assertDoesNotExist()
        compose.onNodeWithText("真诚 · 友善 · 团结 · 专业").assertIsDisplayed()
        open("https://linux.do/t/topic/3001000")
        compose.onNodeWithTag("topic-top-bar").assertIsDisplayed()
        open("https://linux.do/c/4")
        compose.onNodeWithTag("topic-top-bar").assertDoesNotExist()
    }
}
