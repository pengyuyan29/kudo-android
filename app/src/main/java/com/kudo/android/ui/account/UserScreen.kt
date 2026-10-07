package com.kudo.android.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UserScreen(username: String, repository: CommunityRepository, back: () -> Unit, login: () -> Unit, openLink: (String) -> Unit) {
    var user by remember(username) { mutableStateOf<Member?>(null) }
    var error by remember(username) { mutableStateOf<Throwable?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(username, attempt) {
        error = null
        try { user = withContext(Dispatchers.IO) { repository.user(username) } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
    }
    Column(Modifier.fillMaxSize()) {
        PageTopBar("个人主页", back)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
            if (user == null && error == null) LoadingRow()
            error?.let { ErrorState(it, { attempt++ }, login) }
            user?.let {
                Avatar(it, 84.dp)
                Text(it.displayName, style = MaterialTheme.typography.headlineSmall)
                Text("@${it.username}  ·  Lv.${it.trustLevel}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (it.bio.isNotBlank()) Text(it.bio, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(18.dp), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { openLink("https://linux.do/u/${android.net.Uri.encode(username)}") }) { Text("在社区查看完整资料") }
            }
        }
    }
}
