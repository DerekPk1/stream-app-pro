package com.example.streamapp

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainApp()
                }
            }
        }
    }
}

data class MediaItemData(
    val id: String,
    val title: String,
    val size: String,
    val streamUrl: String,
    val backupUrl: String,
    val description: String
)

@Composable
fun MainApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("StreamAppPrefs", Context.MODE_PRIVATE) }

    var webshareUser by rememberSaveable { mutableStateOf(prefs.getString("ws_user", "") ?: "") }
    var websharePass by rememberSaveable { mutableStateOf(prefs.getString("ws_pass", "") ?: "") }
    var hellspyUser by rememberSaveable { mutableStateOf(prefs.getString("hs_user", "") ?: "") }
    var hellspyPass by rememberSaveable { mutableStateOf(prefs.getString("hs_pass", "") ?: "") }

    var isConfigured by rememberSaveable { 
        mutableStateOf(prefs.getBoolean("is_configured", false)) 
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isConfigured) {
            AccountSetupScreen(
                initialWsUser = webshareUser,
                initialWsPass = websharePass,
                initialHsUser = hellspyUser,
                initialHsPass = hellspyPass,
                onSave = { wsU, wsP, hsU, hsP ->
                    prefs.edit()
                        .putString("ws_user", wsU)
                        .putString("ws_pass", wsP)
                        .putString("hs_user", hsU)
                        .putString("hs_pass", hsP)
                        .putBoolean("is_configured", true)
                        .apply()
                    webshareUser = wsU
                    websharePass = wsP
                    hellspyUser = hsU
                    hellspyPass = hsP
                    isConfigured = true
                }
            )
        } else {
            HomeDashboard(
                onOpenSettings = { isConfigured = false }
            )
        }

        // Číslo verzie pevne zafixované v pravom dolnom rohu
        Text(
            text = "v1.0.1",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
fun AccountSetupScreen(
    initialWsUser: String,
    initialWsPass: String,
    initialHsUser: String,
    initialHsPass: String,
    onSave: (String, String, String, String) -> Unit
) {
    var wsUser by rememberSaveable { mutableStateOf(initialWsUser) }
    var wsPass by rememberSaveable { mutableStateOf(initialWsPass) }
    var hsUser by rememberSaveable { mutableStateOf(initialHsUser) }
    var hsPass by rememberSaveable { mutableStateOf(initialHsPass) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "⚙️ Správa kont k zdrojom", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Webshare.cz konto", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = wsUser,
            onValueChange = { wsUser = it },
            label = { Text("Webshare Meno / Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = wsPass,
            onValueChange = { wsPass = it },
            label = { Text("Webshare Heslo") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Hellspy.to konto", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = hsUser,
            onValueChange = { hsUser = it },
            label = { Text("Hellspy Meno / Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = hsPass,
            onValueChange = { hsPass = it },
            label = { Text("Hellspy Heslo") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onSave(wsUser, wsPass, hsUser, hsPass) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Uložiť konta a vstúpiť do aplikácie")
        }
    }
}

@Composable
fun HomeDashboard(onOpenSettings: () -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(0) } 
    var activePlayerUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var activeBackupUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    if (activePlayerUrl != null) {
        VideoPlayerScreen(
            videoUrl = activePlayerUrl!!, 
            backupUrl = activeBackupUrl,
            onBack = { 
                activePlayerUrl = null 
                activeBackupUrl = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⭐ VIP pripojené (Webshare & Hellspy)", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenSettings) {
                        Text("Kontá")
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Text("🎬") },
                    label = { Text("Filmy") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Text("📺") },
                    label = { Text("Seriály") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Text("⏳") },
                    label = { Text("Rozpozerané") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Text("✅") },
                    label = { Text("Pozreté") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> MediaSectionScreen(title = "🎬 Filmy", context = context, onPlayMedia = { url, backup, title ->
                    saveHistory(context, title, url, backup, "Rozpozerané")
                    activePlayerUrl = url
                    activeBackupUrl = backup
                })
                1 -> MediaSectionScreen(title = "📺 Seriály", context = context, onPlayMedia = { url, backup, title ->
                    saveHistory(context, title, url, backup, "Rozpozerané")
                    activePlayerUrl = url
                    activeBackupUrl = backup
                })
                2 -> WatchlistScreen(title = "⏳ Rozpozerané", context = context, onPlay = { url, backup -> 
                    activePlayerUrl = url 
                    activeBackupUrl = backup
                })
                3 -> WatchlistScreen(title = "✅ Pozreté", context = context, onPlay = { url, backup -> 
                    activePlayerUrl = url 
                    activeBackupUrl = backup
                })
            }
        }
    }
}

@Composable
fun MediaSectionScreen(title: String, context: Context, onPlayMedia: (String, String, String) -> Unit) {
    var subTab by rememberSaveable { mutableStateOf(2) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var mediaItems by remember { mutableStateOf<List<MediaItemData>>(emptyList()) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    
    // Načítanie histórie vyhľadávania z SharedPreferences
    val prefs = context.getSharedPreferences("StreamAppPrefs", Context.MODE_PRIVATE)
    var searchHistory by remember { 
        mutableStateOf(prefs.getString("search_history", "")?.split("|||")?.filter { it.isNotEmpty() } ?: emptyList()) 
    }
    
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding()
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { subTab = 0 }) { Text("Novinky") }
            Button(onClick = { subTab = 1 }) { Text("Žánre") }
            Button(onClick = { subTab = 2 }) { Text("Hľadať") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (subTab) {
            0 -> {
                Text("🔥 Odporúčané streamy", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPlayMedia(
                                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                "Tears of Steel (Test Stream)"
                            )
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "▶ Tears of Steel (Testovacie video)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Kliknite pre otestovanie prepínania záložného streamu.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            1 -> {
                Text("🎭 Výber žánrov", style = MaterialTheme.typography.titleMedium)
                val genres = listOf("Akčné", "Komédie", "Dráma", "Sci-Fi", "Thriller", "Horor")
                Column {
                    genres.forEach { genre ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    searchQuery = genre
                                    subTab = 2
                                    isSearching = true
                                    coroutineScope.launch {
                                        mediaItems = searchCombinedSources(genre)
                                        isSearching = false
                                    }
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Text(text = "📁 $genre", modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
            2 -> {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Zadajte názov (napr. Avatar, Matrix)...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    if (searchQuery.isNotEmpty()) {
                        // Uloženie do histórie vyhľadávania
                        val updatedHistory = (listOf(searchQuery) + searchHistory).distinct().take(10)
                        searchHistory = updatedHistory
                        prefs.edit().putString("search_history", updatedHistory.joinToString("|||")).apply()

                        isSearching = true
                        coroutineScope.launch {
                            mediaItems = searchCombinedSources(searchQuery)
                            isSearching = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isSearching) "Vyhľadávam v kontách..." else "Vyhľadať")
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Zobrazenie histórie vyhľadávania, ak existuje a práve sa nevyhľadáva
                if (searchHistory.isNotEmpty() && mediaItems.isEmpty()) {
                    Text(text = "🕒 História vyhľadávania:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    searchHistory.forEach { historyItem ->
                        TextButton(onClick = {
                            searchQuery = historyItem
                            isSearching = true
                            coroutineScope.launch {
                                mediaItems = searchCombinedSources(historyItem)
                                isSearching = false
                            }
                        }) {
                            Text("🔍 $historyItem")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (mediaItems.isNotEmpty()) {
                    Column {
                        mediaItems.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        onPlayMedia(item.streamUrl, item.backupUrl, item.title)
                                    },
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Zdroj: ${item.description} | Veľkosť: ${item.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else if (!isSearching && searchQuery.isNotEmpty()) {
                    Text("Žiadne výsledky.")
                }
            }
        }
    }
}

@Composable
fun WatchlistScreen(title: String, context: Context, onPlay: (String, String) -> Unit) {
    val prefs = context.getSharedPreferences("StreamAppPrefs", Context.MODE_PRIVATE)
    val historyJson = prefs.getString("history_$title", "") ?: ""
    val items = if (historyJson.isNotEmpty()) historyJson.split("|||") else emptyList()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (items.isEmpty()) {
            Text("Zatiaľ žiadne položky v tomto zozname.")
        } else {
            LazyColumn {
                items(items) { entry ->
                    val parts = entry.split("###")
                    if (parts.size >= 3) {
                        val mediaTitle = parts[0]
                        val url = parts[1]
                        val backup = parts[2]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onPlay(url, backup) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = mediaTitle, style = MaterialTheme.typography.bodyLarge)
                                Text("▶ Spustiť")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun saveHistory(context: Context, title: String, url: String, backup: String, category: String) {
    val prefs = context.getSharedPreferences("StreamAppPrefs", Context.MODE_PRIVATE)
    val key = "history_$category"
    val existing = prefs.getString(key, "") ?: ""
    val newItem = "$title###$url###$backup"
    if (!existing.contains(newItem)) {
        val updated = if (existing.isEmpty()) newItem else "$existing|||$newItem"
        prefs.edit().putString(key, updated).apply()
    }
}

// Inteligentný VideoPrehrávač s automatickým záložným zdrojom (Fallback)
@Composable
fun VideoPlayerScreen(videoUrl: String, backupUrl: String?, onBack: () -> Unit) {
    var currentUrl by rememberSaveable { mutableStateOf(videoUrl) }
    var hasSwitchedToBackup by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse(currentUrl))
                    val mediaController = MediaController(ctx)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)
                    
                    setOnErrorListener { _, _, _ ->
                        if (!hasSwitchedToBackup && backupUrl != null && backupUrl != currentUrl) {
                            hasSwitchedToBackup = true
                            currentUrl = backupUrl
                            setVideoURI(Uri.parse(currentUrl))
                            start()
                            true
                        } else {
                            false
                        }
                    }
                    start()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = onBack,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        ) {
            Text("← Späť do aplikácie")
        }
    }
}

suspend fun searchCombinedSources(query: String): List<MediaItemData> {
    return withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItemData>()
        try {
            list.add(
                MediaItemData(
                    id = "ws_1",
                    title = "$query (Webshare VIP - CZ Dabing)",
                    size = "1.5 GB",
                    streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    backupUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    description = "Webshare.cz"
                )
            )
            list.add(
                MediaItemData(
                    id = "hs_1",
                    title = "$query (Hellspy Konto - HD)",
                    size = "1.1 GB",
                    streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    backupUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    description = "Hellspy.to"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }
}
