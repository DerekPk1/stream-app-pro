package com.example.streamapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    var isLoggedIn by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Zadajte prihlasovacie údaje") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isLoggedIn) {
            Text(text = "Stream App - Webshare", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Meno / Email") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Heslo") },
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    isLoggedIn = true
                    statusText = "Prihlásený ako $username"
                } else {
                    statusText = "Zadajte meno aj heslo!"
                }
            }) {
                Text("Prihlásiť sa")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = statusText, color = MaterialTheme.colorScheme.secondary)
        } else {
            Text(text = "Vyhľadávanie filmov", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Názov filmu alebo seriálu...") },
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                statusText = "Vyhľadávam: $searchQuery..."
            }) {
                Text("Vyhľadať")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = statusText)
            
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(onClick = { isLoggedIn = false }) {
                Text("Odhlásiť sa")
            }
        }
    }
}
