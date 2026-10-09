package com.kudo.android

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.kudo.android.data.DisplaySettings
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.RichContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RichContentLinkUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inlineLinksUseTheCurrentAppCallbackInsteadOfTheSystemHandler() {
        val systemLinks = mutableListOf<String>()
        val firstLinks = mutableListOf<String>()
        val currentLinks = mutableListOf<String>()
        var updated by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { systemLinks += uri }
            }) {
                KudoTheme(DisplaySettings()) {
                    RichContent("""<p><a href="/t/topic/3001000/8">打开楼层</a></p><p><a href="https://example.org/help">打开外站</a></p>""",
                        {}, if (updated) ({ currentLinks += it }) else ({ firstLinks += it }))
                }
            }
        }
        clickText("打开楼层")
        compose.runOnIdle {
            assertEquals(listOf("https://linux.do/t/topic/3001000/8"), firstLinks)
            assertTrue(systemLinks.isEmpty())
            updated = true
        }
        clickText("打开楼层")
        clickText("打开外站")
        compose.runOnIdle {
            assertEquals(listOf("https://linux.do/t/topic/3001000/8", "https://example.org/help"), currentLinks)
            assertEquals(1, firstLinks.size)
            assertTrue(systemLinks.isEmpty())
        }
    }

    private fun clickText(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText(label)
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        node.performTouchInput { click(layouts.single().getBoundingBox(1).center) }
    }
}
