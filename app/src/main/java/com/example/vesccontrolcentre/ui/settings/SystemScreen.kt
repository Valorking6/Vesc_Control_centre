package com.example.vesccontrolcentre.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.vesccontrolcentre.settings.ThemeMode
import com.example.vesccontrolcentre.settings.UserSettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { UserSettingsManager(context) }
    
    val geminiKey by settingsManager.geminiApiKey.collectAsState()
    val elevenLabsKey by settingsManager.elevenLabsApiKey.collectAsState()
    val voiceId by settingsManager.elevenLabsVoiceId.collectAsState()
    val usePremiumVoice by settingsManager.usePremiumVoice.collectAsState()
    val appTheme by settingsManager.appTheme.collectAsState()

    var showGeminiKey by remember { mutableStateOf(false) }
    var showElevenLabsKey by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Settings") },
                navigationIcon = {
                    Button(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme Selection
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = (appTheme == mode),
                                    onClick = { settingsManager.updateAppTheme(mode) }
                                )
                                Text(mode.name, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }

            // AI Intelligence Section
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI Intelligence (Gemini)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Powers Friday's intent parsing and proactive updates.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { settingsManager.updateGeminiApiKey(it) },
                        label = { Text("Gemini API Key") },
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Button(onClick = { showGeminiKey = !showGeminiKey }) {
                                Text(if (showGeminiKey) "Hide" else "Show")
                            }
                        }
                    )
                }
            }

            // Voice Engine Section
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Voice Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Configure how Friday speaks to you.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable Premium Neural Voice (ElevenLabs)")
                        Switch(
                            checked = usePremiumVoice,
                            onCheckedChange = { settingsManager.updateUsePremiumVoice(it) }
                        )
                    }
                    
                    AnimatedVisibility(visible = usePremiumVoice) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            OutlinedTextField(
                                value = elevenLabsKey,
                                onValueChange = { settingsManager.updateElevenLabsApiKey(it) },
                                label = { Text("ElevenLabs API Key") },
                                visualTransformation = if (showElevenLabsKey) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    Button(onClick = { showElevenLabsKey = !showElevenLabsKey }) {
                                        Text(if (showElevenLabsKey) "Hide" else "Show")
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = voiceId,
                                onValueChange = { settingsManager.updateElevenLabsVoiceId(it) },
                                label = { Text("Voice ID") },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    Button(onClick = { settingsManager.updateElevenLabsVoiceId("QAmlwgbPtjxpk7u98Qs9") }) {
                                        Text("Reset")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Documentation Card
            var docExpanded by remember { mutableStateOf(false) }
            Card(onClick = { docExpanded = !docExpanded }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("In-App Setup Guide & Documentation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (docExpanded) "▼" else "▶")
                    }
                    
                    AnimatedVisibility(visible = docExpanded) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text("1. Gemini API Key", fontWeight = FontWeight.Bold)
                            Text("Obtain a free Gemini API key from Google AI Studio (aistudio.google.com). This is required for intent parsing and context-aware responses.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text("2. ElevenLabs Premium Voice", fontWeight = FontWeight.Bold)
                            Text("Create an account at elevenlabs.io. Locate stock Voice IDs (e.g., Rachel, Jodi) or create custom clones. Retrieve your xi-api-key from your profile settings.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text("3. Free Usage", fontWeight = FontWeight.Bold)
                            Text("Keeping 'Premium Voice' disabled lets you run the app 100% free using Android's native Text-to-Speech (TTS) engine.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
