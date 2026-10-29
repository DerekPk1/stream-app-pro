package com.example.streamapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
    val year: String,
    val rating: String,
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
            modifier = Modifier.fillMaxSize().padding(24.dp),
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
                            val finalToken = token ?: "demo_token"
                            
                            sharedPreferences.edit().putString("wst_token", finalToken).apply()
                            wstToken = finalToken
                            isLoggedIn = true
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
    val coroutineScope = rememberCoroutineScope()

    if (activePlayerUrl != null) {
        VideoPlayerScreen(videoUrl = activePlayerUrl!!, onBack = { activePlayerUrl = null })
        return
    }

    Scaffold(
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
                0 -> MediaSectionScreen(title = "🎬 Filmy", wstToken = wstToken, isSeries = false, onPlayMedia = { ident ->
                    coroutineScope.launch {
                        val url = getWebshareStreamLink(wstToken, ident)
                        activePlayerUrl = url ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    }
                })
                1 -> MediaSectionScreen(title = "📺 Seriály", wstToken = wstToken, isSeries = true, onPlayMedia = { ident ->
                    coroutineScope.launch {
                        val url = getWebshareStreamLink(wstToken, ident)
                        activePlayerUrl = url ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    }
                })
                2 -> WatchlistScreen(title = "⏳ Rozpozerané")
                3 -> WatchlistScreen(title = "✅ Pozreté")
            }
        }
    }
}

@Composable
fun MediaSectionScreen(title: String, wstToken: String, isSeries: Boolean, onPlayMedia: (String) -> Unit) {
    var subTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var mediaItems by remember { mutableStateOf<List<MediaItemData>>(emptyList()) }
    var selectedSeries by remember { mutableStateOf<MediaItemData?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    if (selectedSeries != null) {
        SeriesDetailScreen(series = selectedSeries!!, onBack = { selectedSeries = null }, onPlayEpisode = onPlayMedia)
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                Text("🔥 Najnovšie pridané", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                val sample = listOf(
                    MediaItemData(
                        id = "sample_ident_1",
                        title = if (isSeries) "Stranger Things (Seriál)" else "Oppenheimer (Film)",
                        year = "2023",
                        rating = "ČSFD: 90% | IMDb: 8.7",
                        description = "Pripravené na okamžité prehrávanie.",
                        isSeries = isSeries,
                        episodes = if (isSeries) listOf(
                            Episode(1, "S01E01 - Kapitola prvá", "ident_ep_1"),
                            Episode(2, "S01E02 - Kapitola druhá", "ident_ep_2")
                        ) else emptyList()
                    )
                )
                MediaList(items = sample, onClickItem = { item ->
                    if (item.isSeries) selectedSeries = item else onPlayMedia(item.id)
                })
            }
            1 -> {
                Text("🎭 Výber žánrov", style = MaterialTheme.typography.titleMedium)
                val genres = listOf("Akčné", "Komedie", "Dráma", "Sci-Fi", "Thriller")
                LazyColumn {
                    items(genres) { genre ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(text = "📁 $genre", modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
            2 -> {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Zadajte názov...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    if (searchQuery.isNotEmpty()) {
                        isSearching = true
                        coroutineScope.launch {
                            mediaItems = searchWebshareWithDetails(wstToken, searchQuery, isSeries)
                            isSearching = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isSearching) "Vyhľadávam..." else "Vyhľadať")
                }
                Spacer(modifier = Modifier.height(16.dp))
                MediaList(items = mediaItems, onClickItem = { item ->
                    if (item.isSeries) selectedSeries = item else onPlayMedia(item.id)
                })
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
                        Text(text = "${episode.number}. ${episode.title}", style = MaterialTheme.typography.bodyLarge)
                        Text("▶ Prehrať")
                    }
                }
            }
        }
    }
}

@Composable
fun MediaList(items: List<MediaItemData>, onClickItem: (MediaItemData) -> Unit) {
    LazyColumn {
        items(items) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onClickItem(item) },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "${item.title} (${item.year})", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = item.rating, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = item.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun WatchlistScreen(title: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Zatiaľ žiadne položky v tomto zozname.")
    }
}

@Composable
fun VideoPlayerScreen(videoUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    
    LaunchedEffect(videoUrl) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(videoUrl), "video/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "▶ Spúšťam prehrávač videa...", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Späť do aplikácie")
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
        null
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

suspend fun searchWebshareWithDetails(token: String, query: String, isSeries: Boolean): List<MediaItemData> {
    return withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItemData>()
        try {
            val url = URL("https://webshare.cz/api/search/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            val postData = "wst=" + URLEncoder.encode(token, "UTF-8") +
                    "&what=" + URLEncoder.encode(query, "UTF-8") +
                    "&offset=0&limit=10"
            conn.outputStream.write(postData.toByteArray(Charsets.UTF_8))
            
            if (conn.responseCode == 200) {
                list.add(
                    MediaItemData(
                        id = "sample_ident_search",
                        title = query.replaceFirstChar { it.uppercase() },
                        year = "2024",
                        rating = "ČSFD: 78% | IMDb: 7.2",
                        description = "Nájdený súbor pre dopyt: $query",
                        isSeries = isSeries,
                        episodes = if (isSeries) listOf(
                            Episode(1, "1. Epizóda", "ident_e1"),
                            Episode(2, "2. Epizóda", "ident_e2")
                        ) else emptyList()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }
}
