package com.eyeconlite.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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

private fun missingDestPermissions(context: Context): Array<String> {
    return ContactsSync.SAVE_DEST_PERMISSIONS.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()
}

private fun destIconFor(kind: String, accountType: String?): ImageVector = when {
    kind == "sim" -> Icons.Filled.SimCard
    kind == "phone" -> Icons.Filled.Smartphone
    (accountType ?: "").contains("google", ignoreCase = true) -> Icons.Filled.Email
    kind == "account" -> Icons.Filled.Email
    else -> Icons.Filled.Contacts
}

/** Icon + text shown in the permission-explainer popup before requesting. */
private data class DestPermInfo(
    val icon: ImageVector,
    val title: String,
    val text: String
)

private fun destPermInfos(): List<DestPermInfo> = listOf(
    DestPermInfo(
        Icons.Filled.Contacts,
        "Contacts",
        "Read your existing Google contacts and save the new number."
    ),
    DestPermInfo(
        Icons.Filled.SimCard,
        "Phone state (SIM)",
        "Show SIM 1 and SIM 2 separately so you can pick one."
    ),
    DestPermInfo(
        Icons.Filled.Smartphone,
        "Phone numbers",
        "Show each SIM's carrier name and number."
    ),
    DestPermInfo(
        Icons.Filled.Email,
        "Accounts (Gmail)",
        "List every logged-in Gmail account as a save location."
    )
)

@Composable
private fun PermissionExplainerDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10294D),
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFD4AF37), Color(0xFF8A6D1B))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Contacts,
                    contentDescription = null,
                    tint = Color(0xFF10294D),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                "Allow access to save anywhere",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Eyecon Lite needs these permissions to show every save location. " +
                        "Nothing is uploaded — everything stays on your phone.",
                    color = Color(0xFF9DB9D6),
                    fontSize = 13.sp
                )
                destPermInfos().forEach { info ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                info.icon,
                                contentDescription = null,
                                tint = Color(0xFFF5D67B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                info.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                info.text,
                                color = Color(0xFF9DB9D6),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Allow", color = Color(0xFF3A2E05), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Not now", color = Color.Gray)
            }
        }
    )
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
    var showPermExplainer by remember { mutableStateOf(false) }
    var pendingSaveAfterPerms by remember { mutableStateOf(false) }
    val bmp = remember(info) { EyeconApi.decodePhoto(info.photoBytes) }

    fun refreshDestinations() {
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
    ) { grants ->
        refreshDestinations()
        if (hasContactPermissions(context)) {
            if (pendingSaveAfterPerms) {
                pendingSaveAfterPerms = false
                doSave()
            }
        } else {
            pendingSaveAfterPerms = false
            Toast.makeText(context, "Contacts permission needed", Toast.LENGTH_LONG).show()
        }
    }

    val destPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshDestinations()
    }

    LaunchedEffect(Unit) {
        refreshDestinations()
        // First open: explain, then ask — so SIM 2 + Gmail can appear.
        if (missingDestPermissions(context).isNotEmpty()) {
            showPermExplainer = true
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            destIconFor(dest.kind, dest.accountType),
                                            contentDescription = null,
                                            tint = Color(0xFFF5D67B),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(dest.label)
                                            Text(
                                                dest.detail,
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selected = dest
                                    expanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Add Google account")
                                        Text(
                                            "Open system settings",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            },
                            onClick = {
                                expanded = false
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                                            putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
                if (destinations.none { it.kind == "account" }) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Email,
                            contentDescription = null,
                            tint = Color(0xFFF5D67B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "No Gmail found — tap \"Add Google account\" in the list, " +
                                "then reopen this dialog.",
                            color = Color(0xFF9DB9D6),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { refreshDestinations() }) {
                            Text("Refresh", color = Color(0xFFF5D67B), fontWeight = FontWeight.Bold)
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
                        pendingSaveAfterPerms = true
                        showPermExplainer = true
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
    if (showPermExplainer) {
        PermissionExplainerDialog(
            onAllow = {
                showPermExplainer = false
                val missing = missingDestPermissions(context)
                if (missing.isEmpty()) {
                    refreshDestinations()
                    if (pendingSaveAfterPerms) {
                        pendingSaveAfterPerms = false
                        if (hasContactPermissions(context)) doSave()
                    }
                } else {
                    val saveOnly = missing.filter {
                        it == Manifest.permission.READ_CONTACTS ||
                            it == Manifest.permission.WRITE_CONTACTS
                    }.toTypedArray()
                    if (pendingSaveAfterPerms && saveOnly.isNotEmpty() && saveOnly.size == missing.size) {
                        permLauncher.launch(saveOnly)
                    } else {
                        destPermLauncher.launch(missing)
                    }
                }
            },
            onDismiss = {
                showPermExplainer = false
                pendingSaveAfterPerms = false
            }
        )
    }
}
