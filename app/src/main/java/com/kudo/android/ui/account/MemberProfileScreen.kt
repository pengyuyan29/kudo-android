package com.kudo.android.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.BuildConfig
import com.kudo.android.data.*
import com.kudo.android.ui.components.Avatar
import com.kudo.android.ui.components.AvatarPresentation
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

@Composable
internal fun MemberProfileScreen(user: Member, repository: CommunityRepository?, settings: () -> Unit,
    about: () -> Unit, profile: () -> Unit, destination: (AccountDestination) -> Unit,
    statusBarStyle: (Boolean?) -> Unit) {
    var member by remember(user.id, user.username) { mutableStateOf(user) }
    var summary by remember(user.id, user.username) { mutableStateOf<UserSummary?>(null) }
    var summaryError by remember(user.id, user.username) { mutableStateOf(false) }
    var refresh by remember(user.id) { mutableIntStateOf(0) }
    var loading by remember(user.id) { mutableStateOf(repository != null) }
    val scroll = rememberLazyListState()
    var backgroundLoaded by remember(user.id, member.profileBackground) { mutableStateOf(false) }
    LaunchedEffect(user.id, user.username, repository, refresh) {
        if (repository == null) return@LaunchedEffect
        loading = true; summaryError = false
        supervisorScope {
            launch {
                try {
                    withContext(Dispatchers.IO) { repository.user(user.username) }
                        .takeIf { it.id == user.id && it.username.equals(user.username, true) }?.let { member = it }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
            launch {
                try { summary = withContext(Dispatchers.IO) { repository.userSummary(user.username) } }
                catch (e: CancellationException) { throw e } catch (_: Exception) { summaryError = true }
                finally { loading = false }
            }
        }
    }
    val headerVisible by remember { derivedStateOf { scroll.firstVisibleItemIndex == 0 } }
    SideEffect { statusBarStyle(if (headerVisible) !backgroundLoaded else null) }
    DisposableEffect(Unit) { onDispose { statusBarStyle(null) } }
    LazyColumn(Modifier.fillMaxSize().testTag("member-profile"), state = scroll,
        contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header") {
            UserBackground(member.profileBackground, LogoYellow, Modifier.fillMaxWidth().testTag("member-header")) { loaded ->
                SideEffect { backgroundLoaded = loaded }
                val ink = if (loaded) Color.White else Ink
                Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 25.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button,
                            onClickLabel = "打开个人主页", onClick = profile).testTag("member-avatar")) {
                            Avatar(member, 64.dp, presentation = AvatarPresentation.ACCOUNT)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(member.displayName, color = ink, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                            Text("@${member.username}", color = ink.copy(alpha = .78f), fontSize = 12.sp, lineHeight = 18.sp)
                            Row(Modifier.clip(RoundedCornerShape(5.dp)).background(ink.copy(alpha = .12f))
                                .clickable(role = Role.Button) { destination(AccountDestination.TRUST) }
                                .padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Lv.${member.trustLevel}", color = ink, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(13.dp), tint = ink)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("访问天数" to summary?.daysVisited, "已读帖子" to summary?.postsRead,
                            "获赞" to summary?.likesReceived, "发帖数" to summary?.posts).forEach { (label, value) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(value?.let(::compactNumber) ?: "—", color = ink, fontSize = 19.sp,
                                    lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                                Text(label, color = ink.copy(alpha = .78f), fontSize = 11.sp, lineHeight = 17.sp)
                            }
                        }
                    }
                    if (loading || summaryError) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(if (loading) "正在读取统计…" else "统计暂不可用 · 点击重试",
                            Modifier.then(if (summaryError) Modifier.clickable { refresh++ } else Modifier),
                            color = ink.copy(alpha = .8f), fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
        item(key = "shortcuts") {
            BoxWithConstraints(Modifier.padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp, vertical = 12.dp)) {
                val columns = if (maxWidth < 300.dp) 2 else 4
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(AccountDestination.TOPICS, AccountDestination.BOOKMARKS, AccountDestination.DRAFTS, AccountDestination.HISTORY)
                        .chunked(columns).forEach { entries ->
                            Row(Modifier.fillMaxWidth()) { entries.forEach { entry ->
                                val (icon, color) = destinationIcon(entry)
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { destination(entry) }
                                    .padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Icon(icon, null, Modifier.size(25.dp), tint = color)
                                    Text(entry.title, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                                }
                            } }
                        }
                }
            }
        }
        item(key = "community") {
            Column(Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)) {
                val entries = listOf(AccountDestination.MESSAGES, AccountDestination.CHAT, AccountDestination.PENDING,
                    AccountDestination.BADGES, AccountDestination.TRUST) +
                    (if (member.trustLevel >= 3) listOf(AccountDestination.INVITES) else emptyList())
                entries.forEach { entry ->
                    val (icon, color) = destinationIcon(entry)
                    CompactAccountRow(icon, color, entry.title, { destination(entry) })
                }
            }
        }
        item(key = "settings") {
            Column(Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)) {
                CompactAccountRow(Icons.Rounded.Palette, Color(0xFF78828E), "界面与显示", settings)
                CompactAccountRow(Icons.Rounded.Info, Color(0xFF78828E), "关于酷DO", about, BuildConfig.VERSION_NAME)
            }
        }
        item { Text("真诚 · 友善 · 团结 · 专业", Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center,
            fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun CompactAccountRow(icon: ImageVector, color: Color, title: String, click: () -> Unit, value: String? = null) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = click).heightIn(min = 50.dp)
        .padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(21.dp), tint = color)
        Text(title, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 21.sp)
        if (value != null) Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

private fun destinationIcon(entry: AccountDestination): Pair<ImageVector, Color> = when (entry) {
    AccountDestination.TOPICS -> Icons.Rounded.Article to Color(0xFF4D97DE)
    AccountDestination.BOOKMARKS -> Icons.Rounded.BookmarkBorder to Color(0xFFEC9B35)
    AccountDestination.DRAFTS -> Icons.Rounded.EditNote to Color(0xFF42A894)
    AccountDestination.HISTORY -> Icons.Rounded.History to Color(0xFF9479CB)
    AccountDestination.MESSAGES -> Icons.Rounded.MailOutline to Color(0xFF6C83CA)
    AccountDestination.CHAT -> Icons.Rounded.Forum to Color(0xFF42A894)
    AccountDestination.PENDING -> Icons.Rounded.PendingActions to Color(0xFFD7A12D)
    AccountDestination.BADGES -> Icons.Rounded.WorkspacePremium to Color(0xFFDC9C32)
    AccountDestination.TRUST -> Icons.Rounded.VerifiedUser to Color(0xFF57A16D)
    AccountDestination.INVITES -> Icons.Rounded.Link to Color(0xFF419FB8)
}
