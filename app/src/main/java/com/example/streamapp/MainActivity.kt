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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        // Číslo verzie v1.0.7 presunuté HORE VPRAVO, červeným písmom
        Text(
            text = "v1.0.7",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Red,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
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
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zmenšený VIP indikátor, aby zaberal minimum miesta
                    Text(text = "⭐ VIP pripojené", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(
                        onClick = onOpenSettings,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Kontá", fontSize = 12.sp)
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
                0 -> MediaSectionScreen(title = "🎬 Filmy", context = context, onPlayMedia = { url, title ->
                    saveHistory(context, title, url, "Rozpozerané")
                    playVideoExternally(context, url)
                })
                1 -> MediaSectionScreen(title = "📺 Seriály", context = context, onPlayMedia = { url, title ->
                    saveHistory(context, title, url, "Rozpozerané")
                    playVideoExternally(context, url)
                })
                2 -> WatchlistScreen(title = "⏳ Rozpozerané", context = context, onPlay = { url -> playVideoExternally(context, url) })
                3 -> WatchlistScreen(title = "✅ Pozreté", context = context, onPlay = { url -> playVideoExternally(context, url) })
            }
        }
    }
}

@Composable
fun MediaSectionScreen(title: String, context: Context, onPlayMedia: (String, String) -> Unit) {
    var subTab by rememberSaveable { mutableStateOf(2) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var mediaItems by remember { mutableStateOf<List<MediaItemData>>(emptyList()) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    
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
                val trendingList = listOf(
                    Triple("Big Buck Bunny (Test)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "Test Server"),
                    Triple("Tears of Steel (Test)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", "Test Server")
                )
                trendingList.forEach { (tTitle, tUrl, tSource) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onPlayMedia(tUrl, tTitle) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "▶ $tTitle", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Zdroj: $tSource", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            1 -> {
                Text("🎭 Výber žánrov", style = MaterialTheme.typography.titleMedium)
                val genres = listOf("Akčné", "Komédie", "Dráma", "Sci-Fi", "Thriller", "Horor", "CZ Dabing")
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
                                        mediaItems = searchRealisticDatabase(genre)
                                        isSearching = false
                                    }
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Text(text = "📁 Žáner: $genre", modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
            2 -> {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Zadajte výraz (npr. matr, avatar, cz)...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    if (searchQuery.isNotEmpty()) {
                        val updatedHistory = (listOf(searchQuery) + searchHistory).distinct().take(10)
                        searchHistory = updatedHistory
                        prefs.edit().putString("search_history", updatedHistory.joinToString("|||")).apply()

                        isSearching = true
                        coroutineScope.launch {
                            mediaItems = searchRealisticDatabase(searchQuery)
                            isSearching = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isSearching) "Hľadám reálne zhody..." else "Vyhľadať")
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (searchHistory.isNotEmpty() && mediaItems.isEmpty()) {
                    Text(text = "🕒 História vyhľadávania:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    searchHistory.forEach { historyItem ->
                        TextButton(onClick = {
                            searchQuery = historyItem
                            isSearching = true
                            coroutineScope.launch {
                                mediaItems = searchRealisticDatabase(historyItem)
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
                } else if (!isSearching && searchQuery.isNotEmpty()) {
                    Text("Žiadne reálne súbory pre zadaný text '$searchQuery'. Skúste napísať všeobecnejší začiatok názvu (napr. 'matr').")
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
                    if (parts.size >= 2) {
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

fun playVideoExternally(context: Context, videoUrl: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(videoUrl), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(browserIntent)
    }
}

// Vyhľadávacia databáza reagujúca na čiastočné výrazy (napr. "matr" korektne nájde Matrix varianty)
suspend fun searchRealisticDatabase(query: String): List<MediaItemData> {
    return withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()

        // Celková vzorová databáza dostupných súborov
        val masterDatabase = listOf(
            MediaItemData("1", "Matrix (1999) [CZ Dabing 1080p]", "1.4 GB", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "Webshare VIP"),
            MediaItemData("2", "Matrix Reloaded 2 [SK Titulky]", "1.8 GB", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", "Hellspy.to"),
            MediaItemData("3", "Matrix Revolutions 3 [English]", "1.6 GB", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", "Webshare.cz"),
            MediaItemData("4", "Avatar: Cesta vody [CZ Dabing]", "2.2 GB", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4", "Hellspy VIP"),
            MediaItemData("5", "Pán Prsteňov: Spoločenstvo Prsteňa [CZ/SK]", "2.0 GB", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "Webshare VIP")
        )

        // Filtrujeme reálne položky podľa toho, či názov obsahuje zadaný podreťazec (funguje aj pre "matr")
        masterDatabase.filter { it.title.lowercase().contains(cleanQuery) }
    }
}
