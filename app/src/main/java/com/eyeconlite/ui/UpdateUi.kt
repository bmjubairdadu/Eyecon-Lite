package com.eyeconlite.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eyeconlite.data.AppUpdater
import com.eyeconlite.data.UpdateInfo
import com.eyeconlite.data.UpdateCheckException
import com.eyeconlite.data.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** Shared update state so the header bell and the update card stay in sync. */
object UpdateStore {
    var state: UpdateState by mutableStateOf(UpdateState.Idle)
    var showDialog: Boolean by mutableStateOf(false)
    var pendingInfo: UpdateInfo? by mutableStateOf(null)
        private set
    private var autoStarted = false

    /** Silent automatic check on startup; shows dialog only when a new version exists. */
    fun autoCheck(context: android.content.Context, scope: CoroutineScope) {
        if (autoStarted) return
        autoStarted = true
        // Don't override an in-progress manual check.
        if (state !is UpdateState.Idle) return
        scope.launch {
            try {
                val latest = AppUpdater.checkLatest()
                val current = AppUpdater.currentVersionName(context)
                // Only touch UI when still idle so a manual check is never overwritten.
                if (state !is UpdateState.Idle) return@launch
                if (AppUpdater.isNewer(latest.tag, current)) {
                    pendingInfo = latest
                    state = UpdateState.Available(latest)
                    showDialog = true
                }
                // Else: stay silent on Idle so a fresh launch never pops "already updated".
            } catch (_: Exception) {
                // Stay silent on startup failure (offline/limit): keep idle UI, no error text.
            }
        }
    }

    fun check(context: android.content.Context, scope: CoroutineScope, autoDownload: Boolean = false) {
        val s = state
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        state = UpdateState.Checking
        scope.launch {
            try {
                val latest = AppUpdater.checkLatest()
                val current = AppUpdater.currentVersionName(context)
                if (AppUpdater.isNewer(latest.tag, current)) {
                    pendingInfo = latest
                    if (autoDownload) {
                        download(context, scope, latest)
                    } else {
                        state = UpdateState.Available(latest)
                        showDialog = true
                    }
                } else {
                    pendingInfo = null
                    state = UpdateState.UpToDate("App already updated")
                }
            } catch (_: Exception) {
                // Generic message only; never expose backend or limit details.
                state = UpdateState.Error("Couldn't check for updates. Please try again.")
            }
        }
    }

    fun acknowledge() {
        // "Done" just returns the card to its idle state.
        if (state is UpdateState.UpToDate) state = UpdateState.Idle
        showDialog = false
    }

    fun download(context: android.content.Context, scope: CoroutineScope, info: UpdateInfo) {
        if (state is UpdateState.Downloading) return
        pendingInfo = info
        state = UpdateState.Downloading(0f)
        scope.launch {
            try {
                val file = AppUpdater.downloadApk(context, info) { p ->
                    state = UpdateState.Downloading(p)
                }
                state = UpdateState.Ready(file, info)
                showDialog = true // auto-open install prompt once downloaded
            } catch (e: Exception) {
                state = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun install(context: android.content.Context, file: File) {
        if (!AppUpdater.canInstall(context)) {
            Toast.makeText(
                context,
                "Allow \"Install unknown apps\" for Eyecon Lite, then tap Install again",
                Toast.LENGTH_LONG
            ).show()
            AppUpdater.openUnknownSourcesSettings(context)
            return
        }
        try {
            AppUpdater.installApk(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun UpdateBell() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = UpdateStore.state

    LaunchedEffect(Unit) {
        UpdateStore.autoCheck(context, scope)
    }

    val hasUpdate = state is UpdateState.Available ||
        state is UpdateState.Downloading ||
        state is UpdateState.Ready

    Box(contentAlignment = Alignment.TopEnd) {
        IconButton(onClick = {
            val s = UpdateStore.state
            when (s) {
                is UpdateState.Available,
                is UpdateState.Downloading,
                is UpdateState.Ready -> UpdateStore.showDialog = true
                else -> UpdateStore.check(context, scope)
            }
        }) {
            if (state is UpdateState.Checking || state is UpdateState.Downloading) {
                CircularProgressIndicator(
                    color = Color(0xFFF5D67B),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = "App updates",
                    tint = if (hasUpdate) Color(0xFFF5D67B) else Color(0xFF4CAF50)
                )
            }
        }
        if (hasUpdate) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF5D67B))
            )
        }
    }

    if (UpdateStore.showDialog) {
        UpdateDialog()
    }
}

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = UpdateStore.state
    val current = remember { AppUpdater.currentVersionName(context) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        when (state) {
            is UpdateState.Idle,
            is UpdateState.UpToDate -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(Icons.Filled.SystemUpdate, null, tint = Color(0xFF4CAF50))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Eyecon Lite v$current",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            if (state is UpdateState.UpToDate) state.message
                            else "App updated",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = {
                        if (state is UpdateState.UpToDate) UpdateStore.acknowledge()
                        else UpdateStore.check(context, scope)
                    }) {
                        val doneLabel = if (state is UpdateState.UpToDate) "Done" else "Check"
                        Text(doneLabel, color = Color(0xFFF5D67B), fontWeight = FontWeight.Bold)
                    }
                }
            }
            is UpdateState.Checking -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFF5D67B),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Checking for updates...", color = Color.White, fontSize = 14.sp)
                }
            }
            is UpdateState.Available -> {
                UpdateAvailableRow(
                    info = state.info,
                    onUpdate = { UpdateStore.download(context, scope, state.info) }
                )
            }
            is UpdateState.Downloading -> {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Downloading update... ${(state.progress * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        color = Color(0xFFF5D67B),
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
            is UpdateState.Ready -> {
                UpdateAvailableRow(
                    info = state.info,
                    buttonText = "Install Now",
                    onUpdate = { UpdateStore.install(context, state.file) }
                )
            }
            is UpdateState.Error -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Update check failed", color = Color(0xFFFF8A80), fontSize = 14.sp)
                        Text(state.message, color = Color.Gray, fontSize = 12.sp)
                    }
                    TextButton(onClick = { UpdateStore.check(context, scope) }) {
                        Text("Retry", color = Color(0xFFF5D67B), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun InstallStatsCard() {
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ready = true }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(Icons.Filled.SystemUpdate, null, tint = Color(0xFF7FB8EC))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "App reach",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    if (ready) "Installed on this device" else "Checking status.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun UpdateAvailableRow(
    info: UpdateInfo,
    buttonText: String = "Download & Install",
    onUpdate: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(14.dp)
    ) {
        Icon(Icons.Filled.SystemUpdate, null, tint = Color(0xFFF5D67B))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "New version ${info.versionName} available",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                if (info.apkSize > 0) "Size: ${(info.apkSize / 1048576)} MB" else "Tap to update",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Button(
            onClick = onUpdate,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(buttonText, color = Color(0xFF3A2E05), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = UpdateStore.state

    val info: UpdateInfo? = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Ready -> state.info
        else -> UpdateStore.pendingInfo
    }
    val file: File? = (state as? UpdateState.Ready)?.file
    val progress: Float? = (state as? UpdateState.Downloading)?.progress
    val downloading = progress != null

    AlertDialog(
        onDismissRequest = { if (!downloading) UpdateStore.showDialog = false },
        containerColor = Color(0xFF10294D),
        shape = RoundedCornerShape(24.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFD4AF37), Color(0xFF8A6D1B))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (downloading) {
                    CircularProgressIndicator(
                        color = Color(0xFF10294D),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Icon(
                        Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = Color(0xFF10294D),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (info != null) "Update to v${info.versionName}" else "App Update",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                if (info != null && info.apkSize > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Size: ${info.apkSize / 1048576} MB • Free update",
                        color = Color(0xFFF5D67B),
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "A new version of Eyecon Lite is available. " +
                        "Download it and install to upgrade automatically — no need to uninstall.",
                    color = Color(0xFF9DB9D6),
                    fontSize = 13.sp
                )
                if (!info?.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.07f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            info!!.notes.take(400),
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                if (progress != null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = Color(0xFFF5D67B),
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        "Downloading... ${(progress * 100).toInt()}%",
                        color = Color(0xFFF5D67B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    file != null && info != null -> {
                        Button(
                            onClick = { UpdateStore.install(context, file) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Filled.Download, null, tint = Color(0xFF3A2E05))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Install Now",
                                color = Color(0xFF3A2E05),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        TextButton(
                            onClick = { UpdateStore.showDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Later", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                    info != null -> {
                        Button(
                            onClick = { UpdateStore.download(context, scope, info) },
                            enabled = !downloading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Filled.Download, null, tint = Color(0xFF3A2E05))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (downloading) "Downloading…" else "Download Update",
                                color = Color(0xFF3A2E05),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { UpdateStore.showDialog = false },
                            enabled = !downloading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text("Later", color = Color(0xFFF5D67B), fontSize = 14.sp)
                        }
                    }
                    else -> {
                        Button(
                            onClick = { UpdateStore.check(context, scope) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, null, tint = Color(0xFF3A2E05))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Check Again",
                                color = Color(0xFF3A2E05),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    )
}




