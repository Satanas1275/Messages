package com.example.liquidmessages

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Black = Color(0xFF000000)
private val Glass = Color(0xFF202020).copy(alpha = .84f)
private val Divider = Color(0xFF292929)
private val Purple = Color(0xFF5B567C)

data class Conversation(val address: String, val name: String, val preview: String, val date: String, val initial: String = "")
data class SmsMessage(val id: Long, val address: String, val body: String, val date: Long, val type: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiquidMessagesApp() }
    }
}

@Composable
fun LiquidMessagesApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionVersion by remember { mutableIntStateOf(0) }
    val permissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionVersion++ }
    var selected by remember { mutableStateOf<Conversation?>(null) }
    var searchMode by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filterOpen by remember { mutableStateOf(false) }
    val backdrop = Unit
    val hasReadPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val hasSendPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    val conversations by produceState(emptyList(), hasReadPermission, permissionVersion) {
        value = if (hasReadPermission) loadConversations(context) else emptyList()
    }

    LaunchedEffect(Unit) {
        if (!hasReadPermission || !hasSendPermission) permissionLauncher.launch(permissions)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                context.startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Black) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF292442), Black), radius = 1000f))) {
                when {
                    selected != null -> ConversationScreen(selected!!, backdrop, onBack = { selected = null })
                    searchMode -> SearchScreen(query, { query = it }, { searchMode = false; query = "" }, backdrop)
                    else -> MessagesScreen(
                        conversations.filter { it.name.contains(query, true) || it.preview.contains(query, true) },
                        { searchMode = true }, { filterOpen = !filterOpen }, { selected = it }, backdrop
                    )
                }
                if (filterOpen && selected == null) {
                    GlassMenu(Modifier.align(Alignment.TopEnd).padding(top = 84.dp, end = 20.dp), null,
                        listOf(Icons.Default.FilterList to "Tous les messages"), "Les SMS sont stockés localement", { filterOpen = false }, backdrop)
                }
            }
        }
    }
}

private fun loadConversations(context: Context): List<Conversation> {
    val result = mutableListOf<Conversation>()
    val cursor = context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE), null, null, "${Telephony.Sms.DATE} DESC")
    cursor?.use {
        val address = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val body = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val date = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val seen = mutableSetOf<String>()
        while (it.moveToNext() && result.size < 100) {
            val number = it.getString(address) ?: continue
            if (seen.add(number)) result += Conversation(number, number, it.getString(body) ?: "", formatDate(it.getLong(date)), number.takeLast(1))
        }
    }
    return result
}

private fun loadMessages(context: Context, address: String): List<SmsMessage> {
    val result = mutableListOf<SmsMessage>()
    val cursor = context.contentResolver.query(Telephony.Sms.CONTENT_URI, null, "${Telephony.Sms.ADDRESS} = ?", arrayOf(address), "${Telephony.Sms.DATE} ASC")
    cursor?.use {
        val id = it.getColumnIndexOrThrow(Telephony.Sms._ID)
        val body = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val date = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val type = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
        while (it.moveToNext()) result += SmsMessage(it.getLong(id), address, it.getString(body) ?: "", it.getLong(date), it.getInt(type))
    }
    return result
}

private fun formatDate(time: Long): String = android.text.format.DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), android.text.format.DateUtils.DAY_IN_MILLIS).toString()

@Composable
private fun MessagesScreen(conversations: List<Conversation>, onSearch: () -> Unit, onFilter: () -> Unit, onConversation: (Conversation) -> Unit, backdrop: Unit) {
    Column(Modifier.fillMaxSize().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            GlassButton("Modifier", {}, backdrop)
            Text("Messages", Color.White, 26.sp, FontWeight.Bold)
            GlassIconButton(Icons.Default.FilterList, onFilter, backdrop)
        }
        if (conversations.isEmpty()) Text("Aucun SMS disponible\nAccorde les permissions pour commencer.", Color.Gray, 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) { items(conversations) { ConversationRow(it) { onConversation(it) } } }
        SearchBar(onSearch = onSearch, backdrop = backdrop)
    }
}

@Composable
private fun ConversationScreen(conversation: Conversation, backdrop: Unit, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    val messages by produceState(emptyList(), conversation.address, refresh) { value = loadMessages(context, conversation.address) }
    Column(Modifier.fillMaxSize().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Avatar(conversation.initial)
            Spacer(Modifier.width(12.dp)); Text(conversation.name, Color.White, 21.sp, FontWeight.Bold)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT) Color(0xFF514A78) else Glass, shape = RoundedCornerShape(20.dp)) { Text(message.body, Color.White, 17.sp, Modifier.padding(14.dp)) }
                }
            }
        }
        Row(Modifier.navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassSurface(Modifier.weight(1f).height(58.dp), backdrop, RoundedCornerShape(30.dp)) {
                TextField(text, { text = it }, placeholder = { Text("SMS", Color.Gray) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            }
            IconButton(onClick = { if (text.isNotBlank()) { sendSms(context, conversation.address, text); text = ""; refresh++ } }) { Icon(Icons.Default.Send, null, tint = Color.White) }
        }
    }
}

private fun sendSms(context: Context, address: String, body: String) {
    android.telephony.SmsManager.getDefault().sendTextMessage(address, null, body, null, null)
    context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, ContentValues().apply { put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) })
}

@Composable private fun ConversationRow(c: Conversation, onClick: () -> Unit) { Column { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(20.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(c.initial); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(c.name, Color.White, 18.sp, FontWeight.Bold); Text(c.date, Color.Gray, 15.sp) }; Text(c.preview, Color.LightGray, 16.sp, maxLines = 2) }; Text("›", Color.Gray, 34.sp) }; Box(Modifier.fillMaxWidth().padding(start = 88.dp, end = 20.dp).height(1.dp).background(Divider)) } }

@Composable private fun SearchScreen(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit, backdrop: Unit) { Column(Modifier.fillMaxSize()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Text("Recherche", Color.White, 26.sp, FontWeight.Bold) }; Spacer(Modifier.weight(1f)); SearchBar(query, onQueryChange, onClose = onClose, backdrop = backdrop) } }
@Composable private fun SearchBar(query: String = "", onQueryChange: ((String) -> Unit)? = null, onSearch: (() -> Unit)? = null, onClose: (() -> Unit)? = null, backdrop: Unit) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) { GlassSurface(Modifier.weight(1f).height(62.dp), backdrop, RoundedCornerShape(34.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { onSearch?.invoke() }) { Icon(Icons.Default.Search, null, tint = Color.White) }; if (onQueryChange != null) TextField(query, onQueryChange, singleLine = true, placeholder = { Text("Recherche", Color.Gray) }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)) else Text("Recherche", Color.Gray, modifier = Modifier.weight(1f)); Icon(Icons.Default.Mic, null, tint = Color.White) } }; Spacer(Modifier.width(10.dp)); GlassIconButton(if (onClose == null) Icons.Default.Search else Icons.Default.Close, { onClose?.invoke() }, backdrop) } }
@Composable private fun GlassMenu(modifier: Modifier, title: String?, entries: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, String>>, footer: String?, onDismiss: () -> Unit, backdrop: Unit) { GlassSurface(modifier.width(340.dp), backdrop, RoundedCornerShape(34.dp)) { Column(Modifier.padding(24.dp)) { title?.let { Text(it, Color.White, 20.sp, FontWeight.Bold) }; entries.forEach { (icon, label) -> Row(Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(20.dp)); Text(label, Color.White, 18.sp) } }; footer?.let { Text(it, Color.Gray, 15.sp, Modifier.padding(top = 12.dp)) } } } }
@Composable private fun GlassSurface(modifier: Modifier, backdrop: Unit, shape: Shape, content: @Composable () -> Unit) { Box(modifier.clip(shape).background(Glass), contentAlignment = Alignment.Center) { content() } }
@Composable private fun GlassButton(text: String, onClick: () -> Unit, backdrop: Unit) { GlassSurface(Modifier.clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp), backdrop, RoundedCornerShape(28.dp)) { Text(text, Color.White, 19.sp, FontWeight.Bold) } }
@Composable private fun GlassIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, backdrop: Unit) { GlassSurface(Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onClick), backdrop, CircleShape) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) } }
@Composable private fun Avatar(initial: String) { Box(Modifier.size(62.dp).clip(CircleShape).background(Purple), contentAlignment = Alignment.Center) { if (initial.isBlank()) Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(38.dp)) else Text(initial, Color.White, 30.sp, FontWeight.Bold) } }
