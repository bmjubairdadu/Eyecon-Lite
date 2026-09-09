package com.eyeconlite

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eyeconlite.data.CardImageMaker
import com.eyeconlite.data.CallerInfo
import com.eyeconlite.data.EyeconApi
import com.eyeconlite.ui.theme.EyeconLiteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EyeconLiteTheme(dynamicColor = false, darkTheme = true) {
                EyeconLiteApp()
            }
        }
    }
}

@Composable
fun EyeconLiteApp() {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2200)
        showSplash = false
    }
    if (showSplash) {
        SplashScreen()
    } else {
        SearchScreen()
    }
}

@Composable
fun SplashScreen() {
    var start by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { start = true }
    val scale by animateFloatAsState(
        targetValue = if (start) 1f else 0.6f,
        animationSpec = tween(900, easing = FastOutSlowInEasing), label = "logo"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1628), Color(0xFF10305E), Color(0xFF0A1628))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Eyecon Lite logo",
                modifier = Modifier
                    .size(150.dp)
                    .scale(scale)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "EYECON LITE",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Text(
                "Finding Anyone",
                color = Color(0xFF7FB8EC),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(color = Color(0xFF2196F3), strokeWidth = 3.dp)
        }
    }
}

@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CallerInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(listOf<String>()) }

    fun doSearch(number: String) {
        val q = number.trim()
        if (q.filter { it.isDigit() }.length < 7) {
            error = "Valid mobile number likhun (min 7 digit)"
            return
        }
        scope.launch {
            loading = true
            error = null
            result = null
            try {
                val info = EyeconApi.lookup(q)
                result = info
                history = (listOf(info.phone) + history).distinct().take(6)
                if (info.name == "Unknown" && !info.hasPhoto) {
                    error = "No record found for this number"
                }
            } catch (e: Exception) {
                error = e.message ?: "Search failed. Try again."
            } finally {
                loading = false
            }
        }
    }

    fun doDownload(info: CallerInfo) {
        scope.launch {
            saving = true
            try {
                val photo = withContext(Dispatchers.IO) {
                    EyeconApi.decodePhoto(info.photoBytes)
                }
                val card = withContext(Dispatchers.Default) {
                    CardImageMaker.buildCard(context, info, photo)
                }
                val path = withContext(Dispatchers.IO) {
                    CardImageMaker.saveToDownloads(context, card, info.phone)
                }
                Toast.makeText(context, "Saved: $path", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                saving = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0A1628), Color(0xFF0E2340)))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Eyecon Lite",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Finding Anyone",
                        color = Color(0xFF7FB8EC),
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50)
                )
            }

            Spacer(Modifier.height(22.dp))

            // Hero text
            Text(
                "Who's calling you?",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                "Search any number — name & photo with Eyecon data",
                color = Color(0xFF9DB9D6),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(18.dp))

            // Search box
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("e.g. 8801XXXXXXXXX", color = Color.Gray) },
                        leadingIcon = {
                            Icon(Icons.Filled.Call, null, tint = Color(0xFF2196F3))
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Text("✕", color = Color.Gray, fontSize = 18.sp)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        keyboardActions = KeyboardActions(onSearch = { doSearch(query) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF2196F3),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            cursorColor = Color(0xFF2196F3)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { doSearch(query) },
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Searching…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Search Number", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // History chips
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.History, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Text("Recent:", color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    history.forEach { h ->
                        Button(
                            onClick = { query = h; doSearch(h) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.07f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(h, color = Color(0xFFBFE0FF), fontSize = 13.sp)
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            result?.let { info ->
                Spacer(Modifier.height(16.dp))
                ResultCard(info = info, saving = saving, onDownload = { doDownload(info) })
            }

            Spacer(Modifier.height(30.dp))
            Text(
                "Data source: Eyecon • Portrait PNG saves to Downloads",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ResultCard(info: CallerInfo, saving: Boolean, onDownload: () -> Unit) {
    val bmp = remember(info) { EyeconApi.decodePhoto(info.photoBytes) }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(22.dp)
        ) {
            // Photo
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2196F3), Color(0xFF0D47A1))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        info.name.trim().firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontSize = 60.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, null, tint = Color(0xFF2196F3))
                Spacer(Modifier.width(6.dp))
                Text(
                    info.name.ifBlank { "Unknown" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0A1628),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                info.phone,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2196F3))
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            )
            if (info.tag.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(info.tag, fontSize = 13.sp, color = Color.Gray)
            }

            Spacer(Modifier.height(14.dp))
            // info rows
            DetailRow("Number", info.phone)
            DetailRow("Name", info.name.ifBlank { "Unknown" })
            DetailRow("Source", "Eyecon")

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDownload,
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B7B3E))
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Saving PNG…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download PNG (Portrait)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Text(
                "1080×1920 stylish card → Download folder",
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF1F6FC))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF5B7A99))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 15.sp, color = Color(0xFF0A1628), fontWeight = FontWeight.Medium)
    }
}
