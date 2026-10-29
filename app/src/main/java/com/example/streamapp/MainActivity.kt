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
    var vipStatus by remember { mutableStateOf("Overujem VIP...") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(wstToken) {
        coroutineScope.launch {
            vipStatus = checkVipStatus(wstToken)
        }
    }

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
                    Text(text = "⭐ $vipStatus", style = MaterialTheme.typography.bodyMedium)
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
        SeriesDetailScreen(series = selectedSeries!!, onBack = { selectedSeries = null }, onPlayEpisode = on
