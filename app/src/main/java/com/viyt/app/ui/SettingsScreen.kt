package com.viyt.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viyt.app.core.DownloadFormat
import com.viyt.app.core.Engine
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = Engine.prefs
    var signedIn by remember { mutableStateOf(Engine.cookies.isSignedIn) }
    var height by remember { mutableIntStateOf(prefs.streamHeight) }
    var dl by remember { mutableStateOf(prefs.defaultDownload) }
    var nightly by remember { mutableStateOf(prefs.nightly) }
    var updating by remember { mutableStateOf(false) }
    val version by Engine.version.collectAsState()

    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        signedIn = Engine.cookies.isSignedIn; if (signedIn) Engine.onAccountChanged()
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val ok = Engine.cookies.importFrom(uri)
            signedIn = Engine.cookies.isSignedIn; if (signedIn) Engine.onAccountChanged()
            Toast.makeText(context, if (ok) "Imported" else "No YouTube cookies in that file", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Section("YouTube account")
        Text(
            if (signedIn) "Signed in. Your feeds and library load from your account."
            else "Not signed in.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (signedIn) {
                OutlinedButton(onClick = { Engine.cookies.signOut(); Engine.onAccountChanged(); signedIn = false }) { Text("Sign out") }
                OutlinedButton(onClick = { login.launch(Intent(context, LoginActivity::class.java)) }) { Text("Sign in again") }
            } else {
                Button(onClick = { login.launch(Intent(context, LoginActivity::class.java)) }) { Text("Sign in") }
                OutlinedButton(onClick = { importer.launch(arrayOf("text/plain", "*/*")) }) { Text("Import cookies.txt") }
            }
        }

        Divider()
        Section("Open app on")
        var start by remember { mutableStateOf(prefs.startTab) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("HOME" to "Home", "SUBS" to "Subscriptions", "YOU" to "You").forEach { (key, label) ->
                FilterChip(selected = start == key, onClick = { start = key; prefs.startTab = key }, label = { Text(label) })
            }
        }
        Text("The page you land on every time you open viyt.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        Divider()
        val hideShorts by prefs.hideShortsState
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Hide Shorts")
                Text("Keeps Shorts out of Subscriptions, search and Up next, and hides the Shorts tab. Home never shows Shorts.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = hideShorts, onCheckedChange = { prefs.saveHideShorts(it) })
        }

        Divider()
        Section("Streaming quality")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(360, 480, 720, 1080).forEach { h ->
                FilterChip(selected = height == h, onClick = { height = h; prefs.streamHeight = h }, label = { Text("${h}p") })
            }
        }
        Text("Lower uses less data. 360p is the best choice on mobile data.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))

        Divider()
        Section("Default download (used by the player's download button)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadFormat.entries.forEach { f ->
                FilterChip(selected = dl == f, onClick = { dl = f; prefs.defaultDownload = f }, label = { Text(f.label) })
            }
        }
        Text("Files are saved to Download/viyt.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        Divider()
        Section("Engine")
        Text("yt-dlp ${version ?: "not loaded yet"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (Engine.jsRuntime != null) "JavaScript engine: QuickJS bundled (full YouTube support)"
            else "JavaScript engine: missing (some YouTube formats may fail)",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Nightly yt-dlp")
                Text("Gets YouTube fixes days earlier", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = nightly, onCheckedChange = { nightly = it; prefs.nightly = it })
        }
        Spacer(Modifier.height(8.dp))
        Button(enabled = !updating, onClick = {
            updating = true
            scope.launch {
                val msg = runCatching { Engine.update() }.getOrElse { Engine.friendly(it) }
                updating = false
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }) { Text(if (updating) "Updating" else "Update yt-dlp now") }
        Text("Browsing and streaming use YouTube's API directly; yt-dlp is the backup and handles MP3. It updates itself every 12 hours.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))

        Divider()
        Section("About")
        Text("viyt: no ads, no trackers. Streams and downloads run on your phone with yt-dlp.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.outline)
}
