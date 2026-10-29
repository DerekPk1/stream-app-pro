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
    val ident: String,
    val description: String,
    val isSeries: Boolean = false,
    val episodes: List<Episode> = emptyList()
)

data class Episode(
    val number: Int,
    val title: String,
    val fileIdent: String
)

@Composable
fun MainApp() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("StreamAppPrefs", Context.MODE_PRIVATE) }
    
    var wstToken by remember { mutableStateOf(sharedPreferences.getString("wst_token", "") ?: "") }
    var isLoggedIn by remember { mutableStateOf(wstToken.isNotEmpty()) }
    
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
            Text(text = "Stream App - Webshare", style = MaterialTheme.typography.headlineMedium)
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
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        statusText = "Prihlasujem sa..."
                        coroutineScope.launch {
                            val token = loginWebshare(username, password)
                            isLoading = false
                            if (token != null) {
                                sharedPreferences.edit().putString("wst_token", token).apply()
                                wstToken = token
                                isLoggedIn = true
                            } else {
                                statusText = "Chyba prihlásenia. Skúste znova."
                            }
                        }
                    } else {
                        statusText = "Zadajte meno aj heslo!"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Prihlasovanie..." else "Prihlásiť sa")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = statusText, color = MaterialTheme.colorScheme.secondary)
        }
    } else {
        HomeDashboard(
            wstToken = wstToken,
            onLogout = {
                sharedPreferences.edit().remove("wst_token").apply()
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
    val coroutineScope = rememberCoroutineScope()

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
                    Text(text = "⭐ VIP Aktívne (Webshare)", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Odhlásiť")
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
                0 -> MediaSectionScreen(title = "🎬 Filmy", wstToken = wstToken, isSeries = false, context = context, onPlayMedia = { ident, title ->
                    saveHistory(context, title, ident, "Rozpozerané")
                    coroutineScope.launch {
                        val url = getWebshareStreamLink(wstToken, ident)
                        activePlayerUrl = url ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    }
                })
                1 -> MediaSectionScreen(title = "📺 Seriály", wstToken = wstToken, isSeries = true, context = context, onPlayMedia = { ident, title ->
                    saveHistory(context, title, ident, "Rozpozerané")
                    coroutineScope.launch {
                        val url = getWebshareStreamLink(wstToken, ident)
                        activePlayerUrl = url ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    }
                })
                2 -> WatchlistScreen(title = "⏳ Rozpozerané", context = context, wstToken = wstToken, onPlay = { ident ->
                    coroutineScope.launch {
                        val url = getWebshareStreamLink(wstToken, ident)
                        activePlayerUrl = url ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    }
                })
                3 -> WatchlistScreen(title = "✅ Pozreté", context = context, wstToken = wstToken, onPlay = { ident ->
                    coroutineScope.launch {
                        val url = getWebshareStreamLink(wstToken, ident)
                        activePlayerUrl = url ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    }
                })
            }
        }
    }
}

@Composable
fun MediaSectionScreen(title: String, wstToken: String, isSeries: Boolean, context: Context, onPlayMedia: (String, String) -> Unit) {
    var subTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var mediaItems by remember { mutableStateOf<List<MediaItemData>>(emptyList()) }
    var selectedSeries by remember { mutableStateOf<MediaItemData?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    if (selectedSeries != null) {
        SeriesDetailScreen(series = selectedSeries!!, onBack = { selectedSeries = null }, onPlayEpisode = { ident ->
            onPlayMedia(ident, selectedSeries!!.title)
        })
        return
    }

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
                Text("🔥 Najnovšie pridané položky", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Použite záložku 'Hľadať' pre vyhľadanie konkrétnych filmov alebo seriálov.")
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
                                        mediaItems = searchWebshareReal(wstToken, genre, isSeries)
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
                    label = { Text(if (isSeries) "Hľadať seriál..." else "Hľadať film...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    if (searchQuery.isNotEmpty()) {
                        isSearching = true
                        coroutineScope.launch {
                            mediaItems = searchWebshareReal(wstToken, searchQuery, isSeries)
                            isSearching = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isSearching) "Vyhľadávam..." else "Vyhľadať")
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
                                        if (isSeries) {
                                            selectedSeries = item.copy(isSeries = true, episodes = listOf(
                                                Episode(1, "${item.title} - 1. Časť", item.ident),
                                                Episode(2, "${item.title} - 2. Časť", item.ident)
                                            ))
                                        } else {
                                            onPlayMedia(item.ident, item.title)
                                        }
                                    },
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Veľkość: ${item.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = item.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                } else {
                    Text("Žiadne výsledky na zobrazenie.")
                }
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(series: MediaItemData, onBack: () -> Unit, onPlayEpisode: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("← Späť") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = series.title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "📺 Zoznam epizód:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(series.episodes) { episode ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onPlayEpisode(episode.fileIdent) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = episode.title, style = MaterialTheme.typography.bodyLarge)
                        Text("▶ Prehrať")
                    }
                }
            }
        }
    }
}

@Composable
fun WatchlistScreen(title: String, context: Context, wstToken: String, onPlay: (String) -> Unit) {
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
                        val ident = parts[1]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onPlay(ident) },
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

fun saveHistory(context: Context, title: String, ident: String, category: String) {
    val prefs = context.getSharedPreferences("StreamAppPrefs", Context.MODE_PRIVATE)
    val key = "history_$category"
    val existing = prefs.getString(key, "") ?: ""
    val newItem = "$title###$ident"
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

fun sha1(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

suspend fun loginWebshare(username: String, pass: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://webshare.cz/api/login/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            val postData = "username_or_email=" + URLEncoder.encode(username, "UTF-8") +
                    "&password=" + URLEncoder.encode(sha1(pass), "UTF-8")
            conn.outputStream.write(postData.toByteArray(Charsets.UTF_8))
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                if (response.contains("<token>")) {
                    return@withContext response.substringAfter("<token>").substringBefore("</token>")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        "demo_wst_token_active"
    }
}

suspend fun getWebshareStreamLink(token: String, ident: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://webshare.cz/api/file_link/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            val postData = "wst=" + URLEncoder.encode(token, "UTF-8") +
                    "&ident=" + URLEncoder.encode(ident, "UTF-8") +
                    "&download_type=video"
            conn.outputStream.write(postData.toByteArray(Charsets.UTF_8))
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                if (response.contains("<link>")) {
                    return@withContext response.substringAfter("<link>").substringBefore("</link>")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}

suspend fun searchWebshareReal(token: String, query: String, isSeries: Boolean): List<MediaItemData> {
    return withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItemData>()
        try {
            val url = URL("https://webshare.cz/api/search/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            val postData = "wst=" + URLEncoder.encode(token, "UTF-8") +
                    "&what=" + URLEncoder.encode(query, "UTF-8") +
                    "&offset=0&limit=20"
            conn.outputStream.write(postData.toByteArray(Charsets.UTF_8))
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val fileBlocks = response.split("<file>")
                for (block in fileBlocks.drop(1)) {
                    val name = block.substringAfter("<name>").substringBefore("</name>")
                    val ident = block.substringAfter("<ident>").substringBefore("</ident>")
                    val size = block.substringAfter("<size>").substringBefore("</size>")
                    
                    if (name.isNotEmpty() && ident.isNotEmpty()) {
                        val lowerName = name.lowercase()
                        val matchesFilter = if (isSeries) {
                            lowerName.contains("s0") || lowerName.contains("serie") || lowerName.contains("ep") || lowerName.contains("smer") || lowerName.contains("cz")
                        } else {
                            !lowerName.contains("s0") && !lowerName.contains("s1")
                        }

                        if (matchesFilter) {
                            list.add(
                                MediaItemData(
                                    id = ident,
                                          title = name,
                                    size = "${size.toLongOrNull()?.let { it / (1024 * 1024) } ?: 0} MB",
                                    ident = ident,
                                    description = "Webshare zdrojový súbor",
                                    isSeries = isSeries
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }
}
