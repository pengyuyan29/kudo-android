package com.kudo.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow

@Composable
internal fun CommunityButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(LogoYellow.copy(alpha = if (enabled) 1f else 0.18f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = if (compact) 36.dp else 48.dp).padding(horizontal = 18.dp, vertical = if (compact) 6.dp else 11.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) Ink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun CommunityTextField(value: String, change: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "",
    enabled: Boolean = true, singleLine: Boolean = false, minLines: Int = 1, maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default) {
    BasicTextField(value, change, modifier, enabled = enabled, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)),
        cursorBrush = SolidColor(LogoYellow), keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        decorationBox = { input -> Box(Modifier.fillMaxWidth()) {
            if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), style = textStyle)
            input()
        } })
}

@Composable
internal fun CommunityRadio(selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val neutral = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.28f else 0.12f)
    Canvas(modifier.size(24.dp)) {
        drawCircle(if (selected) LogoYellow else neutral, radius = 10.dp.toPx(), style = Stroke(1.3.dp.toPx()))
        if (selected) drawCircle(LogoYellow, radius = 5.5.dp.toPx())
    }
}

@Composable
internal fun CommunityCheckbox(checked: Boolean, change: (Boolean) -> Unit, enabled: Boolean = true) {
    val neutral = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    Box(Modifier.size(48.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = change), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            drawRoundRect(if (checked) LogoYellow else neutral, cornerRadius = CornerRadius(3.dp.toPx()), style = if (checked) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.3.dp.toPx()))
            if (checked) drawPath(Path().apply { moveTo(size.width * .22f, size.height * .5f); lineTo(size.width * .43f, size.height * .71f); lineTo(size.width * .8f, size.height * .29f) }, Ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun CommunitySwitch(checked: Boolean, change: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val x by animateDpAsState(if (checked) 23.dp else 3.dp, label = "switch-thumb")
    val neutral = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    Box(modifier.size(54.dp, 48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change), contentAlignment = Alignment.Center) {
        Box(Modifier.size(50.dp, 30.dp).clip(RoundedCornerShape(50)).background(if (checked) LogoYellow.copy(alpha = if (enabled) 1f else .4f) else neutral)) {
            Box(Modifier.offset(x = x, y = 3.dp).size(24.dp).background(Color.White, RoundedCornerShape(50)))
        }
    }
}
