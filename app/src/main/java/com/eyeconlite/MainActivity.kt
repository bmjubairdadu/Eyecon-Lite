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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.eyeconlite.data.AppUpdater
import com.eyeconlite.data.CardImageMaker
import com.eyeconlite.data.CallerInfo
import com.eyeconlite.data.EyeconApi
import com.eyeconlite.data.PhoneUtils
import com.eyeconlite.ui.ContactsSyncCard
import com.eyeconlite.ui.InstallStatsCard
import com.eyeconlite.ui.SaveContactDialog
import com.eyeconlite.ui.UpdateBell
import com.eyeconlite.ui.UpdateCard
import com.eyeconlite.ui.theme.EyeconLiteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var showFullPhoto by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    fun doSearch(number: String) {
        val q = number.trim()
        try {
            PhoneUtils.normalize(q)
        } catch (e: IllegalArgumentException) {
            error = e.message ?: "Valid mobile number likhun"
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

    val centerWhenIdle = result == null && !loading
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
                .padding(20.dp)
                .padding(top = 28.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header (always on top)
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
                UpdateBell()
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (centerWhenIdle) Arrangement.Center else Arrangement.Top
            ) {
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
                        label = { Text("Enter your number", color = Color.Gray) },
                        placeholder = { Text("Enter your number", color = Color.Gray.copy(alpha = 0.6f)) },
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
                    val normPreview = remember(query) {
                        try {
                            if (query.trim().isEmpty()) null else PhoneUtils.normalize(query)
                        } catch (_: Exception) { null }
                    }
                    if (normPreview != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${normPreview.countryFlag} ${normPreview.prettyInternational} • ${normPreview.countryName}",
                            color = Color(0xFF8FDE8F),
                            fontSize = 12.sp
                        )
                        Text(
                            "${normPreview.operator} • ${normPreview.numberType} • National: ${normPreview.nationalFormat}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    } else if (query.trim().isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Enter a valid phone number",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
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
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { history = emptyList() }) {
                        Text("Clear all", color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    history.forEach { h ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { query = h; doSearch(h) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.07f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(h, color = Color(0xFFBFE0FF), fontSize = 13.sp)
                            }
                            IconButton(onClick = { history = history.filter { it != h } }) {
                                Icon(Icons.Filled.Close, contentDescription = "Delete", tint = Color.Gray)
                            }
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(14.dp))
            ContactsSyncCard()

            result?.let { info ->
                Spacer(Modifier.height(16.dp))
                ResultCard(
                    info = info,
                    saving = saving,
                    onDownload = { doDownload(info) },
                    onPhotoClick = { if (info.hasPhoto) showFullPhoto = true },
                    onSaveClick = { showSaveDialog = true }
                )
            }

            Spacer(Modifier.height(14.dp))
            InstallStatsCard()
            UpdateCard()

            if (showFullPhoto && result != null) {
                FullPhotoViewer(
                    info = result!!,
                    onDismiss = { showFullPhoto = false },
                    onDownload = { doDownload(result!!) }
                )
            }

            if (showSaveDialog && result != null) {
                SaveContactDialog(
                    info = result!!,
                    onDismiss = { showSaveDialog = false }
                )
            }

            Spacer(Modifier.height(30.dp))
            Text(
                "Developed by JUBAIR HOSEN",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Portrait PNG saves to Downloads",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
            } // middle content column
        }
    }
}

@Composable
fun ResultCard(
    info: CallerInfo,
    saving: Boolean,
    onDownload: () -> Unit,
    onPhotoClick: () -> Unit = {},
    onSaveClick: () -> Unit = {}
) {
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
            // Photo (tap for fullscreen)
            if (bmp != null) {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.clickable { onPhotoClick() }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Tap to view full photo",
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF0A1628).copy(alpha = 0.75f))
                            .padding(7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Fullscreen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    "Tap photo for full view",
                    fontSize = 11.sp,
                    color = Color(0xFF2196F3),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { onPhotoClick() }
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
            // info rows (national number only, no international row)
            val n = info.normalized
            DetailRow("Name", info.name.ifBlank { "Unknown" }, Icons.Filled.Person)
            DetailRow("Number", n?.nationalFormat ?: info.phone, Icons.Filled.Call)
            DetailRow(
                "Country",
                if (n != null) "${n.countryFlag} ${n.countryName} (+${n.countryCode})" else "Unknown",
                Icons.Filled.LocationOn
            )
            DetailRow("Operator", n?.operator ?: "Unknown", Icons.Filled.SimCard)
            DetailRow("Type", n?.numberType ?: "Unknown", Icons.Filled.Info)
            DetailRow("Tag", info.tag.ifBlank { "No tag" }, Icons.Filled.Badge)
            DetailRow("Photo", info.photoStatus, Icons.Filled.Image)
            DetailRow("Source", "Eyecon", Icons.Filled.Public)
            DetailRow(
                "Checked",
                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(info.checkedAt)),
                Icons.Filled.History
            )

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
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37))
            ) {
                Icon(Icons.Filled.PersonAdd, null, tint = Color(0xFF3A2E05))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Save to Contacts",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF3A2E05)
                )
            }
            Text(
                "Name, number & photo pre-filled — edit before saving",
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun FullPhotoViewer(info: CallerInfo, onDismiss: () -> Unit, onDownload: () -> Unit) {
    val bmp = remember(info) { EyeconApi.decodePhoto(info.photoBytes) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val zoomState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 5f)
        pan = if (zoom > 1f) pan + panChange else Offset.Zero
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            info.name.ifBlank { "Unknown" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(info.phone, color = Color.Gray, fontSize = 14.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Full photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom,
                                    translationX = pan.x,
                                    translationY = pan.y
                                )
                                .transformable(state = zoomState)
                        )
                    } else {
                        Text("No photo", color = Color.Gray)
                    }
                }
                Text(
                    if (zoom > 1f) "Pinch to zoom out • Drag to move" else "Pinch to zoom • Tap ✕ to close",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B7B3E))
                ) {
                    Icon(Icons.Filled.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download PNG Card", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF1F6FC))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color(0xFF5B7A99),
            modifier = Modifier.width(100.dp)
        )
        Text(
            value,
            fontSize = 15.sp,
            color = Color(0xFF0A1628),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
