package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kudo.android.data.DisplaySettings
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.RichContent
import org.junit.*
import org.junit.Assert.*

class ArticleImageSizingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun smallAnimatedAttachmentUsesItsHtmlDisplayWidthInsteadOfFillingThePhone() {
        var density = 1f
        var opened = ""
        compose.setContent { KudoTheme(DisplaySettings()) {
            density = LocalDensity.current.density
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                RichContent("""<p>小图<img class="animated" src="https://article.test/small.avif" width="153" height="153"></p>""", { opened = it }, {})
            }
        } }
        val node = compose.onNodeWithTag("article-image-article-0")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertEquals("小图不能铺满屏幕", 153f, bounds.width / density, 1f)
        assertEquals(153f, bounds.height / density, 1f)
        node.performClick()
        compose.runOnIdle { assertEquals("https://article.test/small.avif", opened) }
    }
}
