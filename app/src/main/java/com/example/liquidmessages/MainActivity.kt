package com.example.liquidmessages

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

private val Black = Color(0xFF000000)
private val Background = Color(0xFF0E0F14)
private val Divider = Color(0xFF23242B)
private val Purple = Color(0xFF5B567C)
private val ProfileName = "Moi"

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
    var draft by remember(selected) { mutableStateOf("") }
    var refresh by remember(selected) { mutableIntStateOf(0) }
    val hasReadPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val hasSendPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    val conversations by produceState(emptyList(), hasReadPermission, permissionVersion) {
        value = if (hasReadPermission) loadConversations(context) else emptyList()
    }
    val filtered = conversations.filter { it.name.contains(query, true) || it.preview.contains(query, true) }

    LaunchedEffect(Unit) {
        if (!hasReadPermission || !hasSendPermission) permissionLauncher.launch(permissions)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                context.startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        }
    }

    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = rememberLayerBackdrop(graphicsLayer) {
        drawRect(Background)
        drawContent()
    }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Background)) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                when {
                    selected != null -> ConversationBody(selected!!, refresh)
                    searchMode -> SearchBody(filtered, { selected = it })
                    else -> MessagesBody(conversations, { selected = it })
                }
            }
            when {
                selected != null -> ConversationGlass(selected!!, backdrop, draft, { draft = it },
                    onSend = {
                        if (draft.isNotBlank()) { sendSms(context, selected!!.address, draft); draft = ""; refresh++ }
                    },
                    onBack = { selected = null })
                searchMode -> SearchGlass(backdrop, query, { query = it }, onBack = { searchMode = false; query = "" })
                else -> MessagesGlass(backdrop,
                    onSearch = { searchMode = true },
                    onSort = { filterOpen = !filterOpen },
                    onNewDiscussion = { searchMode = true }
                )
            }
            if (filterOpen && selected == null) {
                GlassMenu(Modifier.align(Alignment.TopEnd).padding(top = 84.dp, end = 20.dp), backdrop, null,
                    listOf(Icons.Default.FilterList to "Tous les messages"), "Les SMS sont stockés localement", { filterOpen = false })
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
private fun MessagesBody(conversations: List<Conversation>, onOpen: (Conversation) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 140.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar("M", 44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(ProfileName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("En ligne", color = Color.Gray, fontSize = 14.sp)
            }
        }
        if (conversations.isEmpty()) Text("Aucun SMS disponible\nAccorde les permissions pour commencer.", color = Color.Gray, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 100.dp)) { items(conversations) { ConversationRow(it) { onOpen(it) } } }
    }
}

@Composable
private fun MessagesGlass(backdrop: Backdrop, onSearch: () -> Unit, onSort: () -> Unit, onNewDiscussion: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            GlassCircleButton(backdrop, 56.dp, {}, Icons.Default.Edit, Modifier.align(Alignment.CenterStart))
            Text("Messages", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            GlassCircleButton(backdrop, 56.dp, onSort, Icons.Default.FilterList, Modifier.align(Alignment.CenterEnd))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCircleButton(backdrop, 72.dp, onSort, Icons.Default.FilterList)
            GlassSurface(backdrop, Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(30.dp)).clickable(onClick = onSearch), RoundedCornerShape(30.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Rechercher", color = Color.White.copy(alpha = .55f), fontSize = 16.sp)
                }
            }
            GlassCircleButton(backdrop, 72.dp, onNewDiscussion, Icons.Default.Edit)
        }
    }
}

@Composable
private fun SearchBody(conversations: List<Conversation>, onOpen: (Conversation) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 96.dp)) {
        if (conversations.isEmpty()) Text("Aucun résultat", color = Color.Gray, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) { items(conversations) { ConversationRow(it) { onOpen(it) } } }
    }
}

@Composable
private fun SearchGlass(backdrop: Backdrop, query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCircleButton(backdrop, 64.dp, onBack, Icons.Default.ArrowBack)
            GlassSurface(backdrop, Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(32.dp)), RoundedCornerShape(32.dp)) {
                TextField(query, onQueryChange, singleLine = true, placeholder = { Text("Rechercher", color = Color.Gray, fontSize = 16.sp) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            }
        }
    }
}

@Composable
private fun ConversationBody(conversation: Conversation, refresh: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val messages by produceState(emptyList(), conversation.address, refresh) { value = loadMessages(context, conversation.address) }
    LazyColumn(Modifier.fillMaxSize().padding(top = 96.dp, bottom = 96.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(messages) { message ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT) Arrangement.End else Arrangement.Start) {
                Surface(color = if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT) Color(0xFF514A78) else Color(0xFF26262E), shape = RoundedCornerShape(20.dp)) { Text(message.body, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(14.dp)) }
            }
        }
    }
}

@Composable
private fun ConversationGlass(conversation: Conversation, backdrop: Backdrop, text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(backdrop, 56.dp, onBack, Icons.Default.ArrowBack)
            Spacer(Modifier.width(16.dp))
            Avatar(conversation.initial, 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(conversation.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(12.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassSurface(backdrop, Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(32.dp)), RoundedCornerShape(32.dp)) {
                TextField(text, onTextChange, placeholder = { Text("SMS", color = Color.Gray, fontSize = 16.sp) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            }
            GlassCircleButton(backdrop, 64.dp, onSend, Icons.Default.Send)
        }
    }
}

private fun sendSms(context: Context, address: String, body: String) {
    android.telephony.SmsManager.getDefault().sendTextMessage(address, null, body, null, null)
    context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, ContentValues().apply { put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) })
}

@Composable private fun ConversationRow(c: Conversation, onClick: () -> Unit) { Column { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(c.initial, 46.dp); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(c.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(c.date, color = Color.Gray, fontSize = 14.sp) }; Text(c.preview, color = Color.LightGray, fontSize = 15.sp, maxLines = 2) }; Text("›", color = Color.Gray, fontSize = 32.sp) }; Box(Modifier.fillMaxWidth().padding(start = 82.dp, end = 20.dp).height(1.dp).background(Divider)) } }

@Composable private fun GlassMenu(modifier: Modifier, backdrop: Backdrop, title: String?, entries: List<Pair<ImageVector, String>>, footer: String?, onDismiss: () -> Unit) { GlassSurface(backdrop, modifier.width(340.dp).clip(RoundedCornerShape(34.dp)), RoundedCornerShape(34.dp)) { Column(Modifier.padding(24.dp)) { title?.let { Text(it, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }; entries.forEach { (icon, label) -> Row(Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(20.dp)); Text(label, color = Color.White, fontSize = 18.sp) } }; footer?.let { Text(it, color = Color.Gray, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp)) } } } }

@Composable
private fun GlassSurface(backdrop: Backdrop, modifier: Modifier, shape: Shape, content: @Composable () -> Unit) {
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(4f.dp.toPx())
                lens(12f.dp.toPx(), 20f.dp.toPx())
            },
            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.30f)) }
        ),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable private fun GlassCircleButton(backdrop: Backdrop, size: Dp, onClick: () -> Unit, icon: ImageVector, modifier: Modifier = Modifier) { GlassSurface(backdrop, modifier.size(size).clip(CircleShape).clickable(onClick = onClick), CircleShape) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.45f)) } }

@Composable private fun Avatar(initial: String, size: Dp = 46.dp) { Box(Modifier.size(size).clip(CircleShape).background(Purple), contentAlignment = Alignment.Center) { if (initial.isBlank()) Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(size * 0.6f)) else Text(initial, color = Color.White, fontSize = (size.value / 2.6f).sp, fontWeight = FontWeight.Bold) } }
