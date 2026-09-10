package com.eyeconlite.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.eyeconlite.data.CallerInfo
import com.eyeconlite.data.ContactPhotoSyncWorker
import com.eyeconlite.data.ContactsSync
import com.eyeconlite.data.EyeconApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun hasContactPermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
}

fun hasExtraDestPermissions(context: Context): Boolean {
    val phoneOk = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED
    val acctOk = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.GET_ACCOUNTS
    ) == PackageManager.PERMISSION_GRANTED
    return phoneOk && acctOk
}

@Composable
fun ContactsSyncCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPerms by remember { mutableStateOf(hasContactPermissions(context)) }
    var running by remember { mutableStateOf(false) }
    var doneCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var updatedCount by remember { mutableStateOf(ContactsSync.getUpdatedCount(context)) }
    var finished by remember { mutableStateOf(ContactsSync.isDone(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    var currentNumber by remember { mutableStateOf("") }
    var currentName by remember { mutableStateOf("") }
    var currentStatus by remember { mutableStateOf("") }
    var events by remember { mutableStateOf(emptyList<String>()) }

    fun refresh() {
        updatedCount = ContactsSync.getUpdatedCount(context)
        finished = ContactsSync.isDone(context)
    }

    fun startSync(force: Boolean) {
        if (running) return
        running = true
        status = null
        scope.launch {
            try {
                val wm = WorkManager.getInstance(context)
                val req = OneTimeWorkRequestBuilder<ContactPhotoSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(workDataOf("force" to force))
                    .build()
                wm.enqueueUniqueWork(
                    ContactsSync.WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    req
                )
                while (true) {
                    delay(1500)
                    val infos = withContext(Dispatchers.IO) {
                        wm.getWorkInfosForUniqueWork(ContactsSync.WORK_NAME).get()
                    }
                    val wi = infos.firstOrNull()
                    if (wi == null) {
                        running = false
                        refresh()
                        break
                    }
                    val p = wi.progress
                    totalCount = p.getInt("total", totalCount)
                    doneCount = p.getInt("done", doneCount)
                    updatedCount = p.getInt("updated", updatedCount)
                    currentNumber = p.getString("currentNumber").orEmpty()
                    currentName = p.getString("currentName").orEmpty()
                    currentStatus = p.getString("currentStatus").orEmpty()
                    events = p.getString("events")
                        .orEmpty()
                        .split('\n')
                        .filter { it.isNotBlank() }
                    when (wi.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            running = false
                            refresh()
                            status = "Sync complete"
                            break
                        }
                        WorkInfo.State.FAILED -> {
                            running = false
                            status = "Sync failed — try again"
                            break
                        }
                        WorkInfo.State.CANCELLED -> {
                            running = false
                            break
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                running = false
                status = e.message ?: "Sync error"
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPerms = hasContactPermissions(context)
        if (hasPerms) {
            startSync(force = false)
        } else {
            status = "Contacts permission denied"
        }
    }

    LaunchedEffect(hasPerms) {
        if (hasPerms && !ContactsSync.isDone(context) && !running) {
            startSync(force = false)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Contacts, null, tint = Color(0xFFF5D67B))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Contact Photos",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Auto-fill missing profile photos",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                if (finished && !running) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50))
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                !hasPerms -> {
                    Text(
                        "Allow contacts access — every number is scanned once " +
                            "and found photos are saved permanently on your contacts. " +
                            "Existing photos and names are never changed.",
                        color = Color(0xFF9DB9D6),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            permLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.WRITE_CONTACTS
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Contacts, null, tint = Color(0xFF3A2E05))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Allow Contacts & Sync",
                            color = Color(0xFF3A2E05),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                running -> {
                    val frac = if (totalCount > 0) {
                        (doneCount.toFloat() / totalCount).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Text(
                        if (totalCount > 0) "Scanning $doneCount / $totalCount… ($updatedCount photos added)"
                        else "Preparing scan… (runs in background too)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { frac },
                        color = Color(0xFFF5D67B),
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    if (currentNumber.isNotBlank()) {
                        Text(
                            "$currentStatus: ${currentName.ifBlank { "Unknown contact" }} — $currentNumber",
                            color = Color(0xFFBFE0FF),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (events.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            events.forEach { event ->
                                Text(event, color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                    Text(
                        "Keep internet on — continues even if the app is closed",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                else -> {
                    Text(
                        if (finished) "$updatedCount photos added • Up to date — won't rescan"
                        else "$updatedCount photos added",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { startSync(force = true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.10f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, null, tint = Color(0xFFF5D67B))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Check new contacts",
                            color = Color(0xFFF5D67B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            status?.let {
                Text(
                    it,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveContactDialog(info: CallerInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember {
        mutableStateOf(if (info.name == "Unknown") "" else info.name)
    }
    var phone by remember {
        mutableStateOf(info.normalized?.nationalFormat ?: info.phone)
    }
    var nameError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var destinations by remember {
        mutableStateOf(ContactsSync.listSaveDestinations(context))
    }
    var selected by remember { mutableStateOf(destinations.first()) }
    val bmp = remember(info) { EyeconApi.decodePhoto(info.photoBytes) }

    fun doSave() {
        if (name.isBlank()) {
            nameError = true
            return
        }
        saving = true
        val dest = selected
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ContactsSync.saveNewContact(
                    context,
                    name.trim(),
                    phone.trim(),
                    info.photoBytes,
                    destination = dest
                )
            }
            saving = false
            Toast.makeText(
                context,
                if (ok) "Saved to ${dest.label}" +
                    if (dest.isSim) " • name + number only" else "" else "Save failed",
                Toast.LENGTH_LONG
            ).show()
            if (ok) onDismiss()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasContactPermissions(context)) {
            doSave()
        } else {
            Toast.makeText(context, "Contacts permission needed", Toast.LENGTH_LONG).show()
        }
    }

    val destPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                ContactsSync.listSaveDestinations(context)
            }
            destinations = fresh
            if (fresh.none { it.key() == selected.key() }) {
                selected = fresh.first()
            }
        }
    }

    LaunchedEffect(Unit) {
        destinations = withContext(Dispatchers.IO) {
            ContactsSync.listSaveDestinations(context)
        }
        if (destinations.none { it.key() == selected.key() }) {
            selected = destinations.first()
        }
        // Ask for SIM/account visibility once; reload so SIM 2 + Gmail appear.
        if (!hasExtraDestPermissions(context)) {
            destPermLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_PHONE_NUMBERS,
                    Manifest.permission.GET_ACCOUNTS
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10294D),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PersonAdd, null, tint = Color(0xFFF5D67B))
                Spacer(Modifier.width(8.dp))
                Text("Save to Contacts", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF2196F3), Color(0xFF0D47A1))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name.trim().firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Photo will be saved with the contact",
                        color = Color(0xFF9DB9D6),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Name") },
                    isError = nameError,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF5D67B),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        cursorColor = Color(0xFFF5D67B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError) {
                    Text("Name required", color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Number") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF5D67B),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        cursorColor = Color(0xFFF5D67B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selected.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Save to") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF5D67B),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            cursorColor = Color(0xFFF5D67B)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        destinations.forEach { dest ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(dest.label)
                                        Text(
                                            dest.detail,
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                },
                                onClick = {
                                    selected = dest
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                if (selected.isSim) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "SIM stores name + number only — photo is skipped.",
                        color = Color(0xFF9DB9D6),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (hasContactPermissions(context)) {
                        doSave()
                    } else {
                        permLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.WRITE_CONTACTS
                            )
                        )
                    }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        color = Color(0xFF3A2E05),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Saving…", color = Color(0xFF3A2E05), fontWeight = FontWeight.Bold)
                } else {
                    Text("Save", color = Color(0xFF3A2E05), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
