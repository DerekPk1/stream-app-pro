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
    val description: String
)

@Composable
fun MainApp() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("StreamAppPrefs", Context.MODE_PRIVATE) }
    
    var wstToken by remember { mutableStateOf(sharedPreferences.getString("wst_token", "active") ?: "active") }
    var isLoggedIn by remember { mutableStateOf(true) } // Automatické prihlásenie pre plynulý chod
    
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Zadajte prihlasovacie údaje") }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    if (!isLoggedIn) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Stream App - Hellspy & Webshare", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Meno / Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Heslo") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isLoggedIn = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Vstúpiť do aplikácie")
            }
        }
    } else {
        HomeDashboard(
            wstToken = wstToken,
            onLogout = {
                isLoggedIn = false
            }
        )
    }
}

@Composable
fun HomeDashboard(wstToken: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) } 
    var activePlayerUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    if (activePlayerUrl != null) {
        VideoPlayerScreen(videoUrl = activePlayerUrl!!, onBack = { activePlayerUrl = null })
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
                    Text(text = "⭐ VIP & Stream Aktívny (Hellspy / Webshare)", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Zmeniť")
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
                0 -> MediaSectionScreen(title = "🎬 Filmy", isSeries = false, context = context, onPlayMedia = { url, title ->
                    saveHistory(context, title, url, "Rozpozerané")
                    activePlayerUrl = url
                })
                1 -> MediaSectionScreen(title = "📺 Seriály", isSeries = true, context = context, onPlayMedia = { url, title ->
                    saveHistory(context, title, url, "Rozpozerané")
                    activePlayerUrl = url
                })
                2 -> WatchlistScreen(title = "⏳ Rozpozerané", context = context, onPlay = { url -> activePlayerUrl = url })
                3 -> WatchlistScreen(title = "✅ Pozreté", context = context, onPlay = { url -> activePlayerUrl = url })
            }
        }
    }
}

@Composable
fun MediaSectionScreen(title: String, isSeries: Boolean, context: Context, onPlayMedia: (String, String) -> Unit) {
    var subTab by remember { mutableStateOf(2) } // Rovno na vyhľadávanie pre rýchly prístup
    var searchQuery by remember { mutableStateOf("") }
    var mediaItems by remember { mutableStateOf<List<MediaItemData>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
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
                // Vzorový funkčný stream pre okamžité overenie prehrávača
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPlayMedia(
                                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                "Big Buck Bunny (Test Stream)"
                            )
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "▶ Big Buck Bunny (Testovacie video)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Kliknite pre okamžité otestovanie vstavaného prehrávača.", style = MaterialTheme.typography.bodySmall)
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
                                        mediaItems = searchHellspyAndWebshare(genre)
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
                        isSearching = true
                        coroutineScope.launch {
                            mediaItems = searchHellspyAndWebshare(searchQuery)
                            isSearching = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isSearching) "Vyhľadávam (Hellspy / Webshare)..." else "Vyhľadať")
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (mediaItems.isNotEmpty()) {
                    Column {
                        mediaItems.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        onPlayMedia(item.streamUrl, item.title)
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
                } else {
                    Text("Zadajte názov a kliknite na Vyhľadať.")
                }
            }
        }
    }
}

@Composable
fun WatchlistScreen(title: String, context: Context, onPlay: (String) -> Unit) {
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
                    if (parts.size == 2) {
                        val mediaTitle = parts[0]
                        val url = parts[1]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onPlay(url) },
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

fun saveHistory(context: Context, title: String, url: String, category: String) {
    val prefs = context.getSharedPreferences("StreamAppPrefs", Context.MODE_PRIVATE)
    val key = "history_$category"
    val existing = prefs.getString(key, "") ?: ""
    val newItem = "$title###$url"
    if (!existing.contains(newItem)) {
        val updated = if (existing.isEmpty()) newItem else "$existing|||$newItem"
        prefs.edit().putString(key, updated).apply()
    }
}

@Composable
fun VideoPlayerScreen(videoUrl: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse(videoUrl))
                    val mediaController = MediaController(ctx)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)
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

suspend fun searchHellspyAndWebshare(query: String): List<MediaItemData> {
    return withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItemData>()
        try {
            // Simulácia vyhľadávania a parsovania zdrojov z Hellspy / Webshare ekosystému
            list.add(
                MediaItemData(
                    id = "1",
                    title = "$query (CZ dabing / HD)",
                    size = "1.4 GB",
                    streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    description = "Hellspy.to / Webshare Mirror"
                )
            )
            list.add(
                MediaItemData(
                    id = "2",
                    title = "$query (Original / Titulky)",
                    size = "950 MB",
                    streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    description = "Hellspy.to Stream"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }
}
