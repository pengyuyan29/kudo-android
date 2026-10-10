package com.kudo.android.ui.account

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow

internal fun profileHeaderImage(member: Member, card: String): String = card.ifBlank {
    member.animatedAvatar.ifBlank { member.avatar.replace("{size}", "512") }
}

@Composable
internal fun PublicProfileHeader(profile: PublicProfile, summary: ProfileSummary?, card: String, viewerId: Int?,
    busy: Boolean, follow: () -> Unit, message: () -> Unit, follows: (Boolean) -> Unit,
    modifier: Modifier = Modifier, openLink: (String) -> Unit = {}) {
    val member = profile.member
    val avatar = member.animatedAvatar.ifBlank { member.avatar.replace("{size}", "512") }.let(::mediaUrl)
    val source = profileHeaderImage(member, card).let(::mediaUrl)
    var expanded by remember(member.id) { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth().background(Ink).testTag("public-profile-header")) {
        val pixels = with(LocalDensity.current) { maxWidth.roundToPx().coerceIn(128, 1200) }
        val compact = maxWidth < 340.dp
        AvatarImage(source, pixels, true, Modifier.matchParentSize().testTag("public-profile-background"), fallbackUrl = avatar, placeholder = {})
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .38f), Color.Black.copy(alpha = .72f)))))
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 66.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                Avatar(member, if (compact) 64.dp else 78.dp)
                Spacer(Modifier.weight(1f))
                if (member.id != viewerId) {
                    if (viewerId == null || member.canFollow || member.isFollowed) {
                        Box(Modifier.clip(RoundedCornerShape(50)).background(LogoYellow.copy(alpha = if (busy) .6f else 1f))
                            .clickable(enabled = !busy, role = Role.Button, onClick = follow).testTag("profile-follow")
                            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                            Text(if (busy) "处理中…" else if (member.isFollowed) "已关注" else "关注", color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    if (viewerId == null || profile.canMessage) Box(Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = .2f)).clickable(role = Role.Button, onClick = message), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MailOutline, "发送私信", Modifier.size(22.dp), tint = Color.White)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(member.displayName, color = Color.White, fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                    Text("Lv.${member.trustLevel}", Modifier.clip(RoundedCornerShape(50)).background(LogoYellow).padding(horizontal = 8.dp, vertical = 3.dp),
                        color = Ink, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("@${member.username}" + profile.title.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(),
                    color = Color.White.copy(alpha = .74f), fontSize = 12.sp, lineHeight = 19.sp)
            }
            if (member.bio.isNotBlank()) Text(member.bio, Modifier.fillMaxWidth().clickable { expanded = !expanded },
                color = Color.White.copy(alpha = .92f), fontSize = 14.sp, lineHeight = 22.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderStat(profile.following, "关注", Modifier.clickable(role = Role.Button) { follows(false) }.testTag("profile-following"))
                HeaderStat(profile.followers, "粉丝", Modifier.clickable(role = Role.Button) { follows(true) }.testTag("profile-followers"))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("likes_received" to "获赞", "days_visited" to "访问", "topic_count" to "话题", "post_count" to "回复").forEach { (key, label) ->
                    HeaderStat(summary?.counts?.get(key), label)
                }
            }
            if (profile.lastSeen.isNotBlank()) Text("${relativeTime(profile.lastSeen)}活跃", Modifier.clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White.copy(alpha = .82f), fontSize = 11.sp, lineHeight = 17.sp)
            if (profile.location.isNotBlank() || profile.website.isNotBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (profile.location.isNotBlank()) Text(profile.location, color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                if (profile.website.isNotBlank()) Text(profile.website, Modifier.clickable { openLink(profile.website) }, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HeaderStat(value: Int?, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value?.let(::compactNumber) ?: "—", color = Color.White, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Color.White.copy(alpha = .7f), fontSize = 12.sp, lineHeight = 20.sp)
    }
}

@Composable
internal fun ProfileToolbar(name: String, collapsed: Boolean, back: () -> Unit, search: () -> Unit, more: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (collapsed) MaterialTheme.colorScheme.onSurface else Color.White
    Row(modifier.fillMaxWidth().background(if (collapsed) MaterialTheme.colorScheme.surface else Color.Transparent)
        .statusBarsPadding().height(48.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = back), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(24.dp), tint = color)
        }
        Text(if (collapsed) name else "", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium,
            color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = search), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Search, "搜索该用户的帖子", Modifier.size(23.dp), tint = color)
        }
        Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = more), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreVert, "个人主页更多操作", Modifier.size(23.dp), tint = color)
        }
    }
}
