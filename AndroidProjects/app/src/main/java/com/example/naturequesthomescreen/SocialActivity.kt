package com.example.naturequesthomescreen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.natureobservation.feature.posts.PhotoFeedScreen
import com.example.naturequesthomescreen.ui.components.ProfileAvatar
import com.example.naturequesthomescreen.ui.components.rememberAuthorProfile
import com.example.naturequesthomescreen.ui.theme.hasCustomAppearanceBackground
import com.example.naturequesthomescreen.ui.theme.NatureQuestHomeScreenTheme
import com.example.useraccount.SocialRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class SocialActivity : ComponentActivity() {
    companion object {
        fun open(context: Context, page: String, peer: String = "") {
            context.startActivity(Intent(context, SocialActivity::class.java)
                .putExtra("page", page).putExtra("peer", peer))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        val repo = SocialRepository(me)
        val page = intent.getStringExtra("page").orEmpty()
        val peer = intent.getStringExtra("peer").orEmpty()
        setContent {
            NatureQuestHomeScreenTheme {
                ProfileLayout(
                    onBack = { finish() },
                    scrollContent = page != "Messages" && page != "chat" && page != "profile"
                ) {
                    when (page) {
                        "profile" -> PeerPage(repo, peer)
                        "chat" -> ChatScreen(repo, peer)
                        "Messages" -> MessageInbox(repo)
                        else -> PeopleList(repo, page)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileLayout(
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    backLabel: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
    scrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hasCustomBackground = hasCustomAppearanceBackground()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
        contentColor = colors.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (hasCustomBackground) colors.background else colors.primaryContainer,
                            colors.background
                        )
                    )
                )
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(onClick = onBack, enabled = backEnabled) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
                }
                Spacer(Modifier.weight(1f))
                actions()
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun ProfileHeading(title: String) {
    val colors = MaterialTheme.colorScheme
    val custom = hasCustomAppearanceBackground()
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        ),
        color = if (custom) colors.onBackground else colors.onPrimaryContainer
    )
}

@Composable
internal fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun ProfileLink(label: String, onClick: () -> Unit, leading: @Composable () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
fun OwnSocialSection(me: String, bio: String) {
    val context = LocalContext.current
    val repo = remember(me) { SocialRepository(me) }
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable(me) { mutableStateOf(false) }
    var draft by rememberSaveable(me) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ProfileCard {
        Text("Bio", style = MaterialTheme.typography.titleMedium)
        if (editing) {
            OutlinedTextField(draft, { if (it.length <= 300) draft = it },
                modifier = Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text("Bio (300 characters)") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            repo.people.document(me).set(mapOf("bio" to draft.trim()), SetOptions.merge()).await()
                            editing = false
                            error = null
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.localizedMessage }
                        finally { busy = false }
                    }
                }) { Text("Save bio") }
                TextButton(enabled = !busy, onClick = { editing = false; error = null }) { Text("Cancel") }
            }
        } else {
            Text(bio.ifBlank { "No bio yet." })
            OutlinedButton(onClick = { draft = bio; editing = true }) { Text("Edit bio") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    listOf("Followers", "Following", "Find people", "Blocked users").forEach { page ->
        ProfileLink(page, onClick = { SocialActivity.open(context, page) })
    }
}

private data class Result<T>(val value: T? = null, val error: String? = null)

@Composable
private fun documents(query: Query): Result<List<DocumentSnapshot>> {
    var result by remember(query) { mutableStateOf(Result<List<DocumentSnapshot>>()) }
    DisposableEffect(query) {
        val listener = query.addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
            if (e != null) result = Result(error = e.localizedMessage ?: "Couldn't load this list.")
            else if (snap != null && !snap.metadata.isFromCache) result = Result(snap.documents)
        }
        onDispose { listener.remove() }
    }
    return result
}

@Composable
private fun exists(ref: DocumentReference): Result<Boolean> {
    var result by remember(ref) { mutableStateOf(Result<Boolean>()) }
    DisposableEffect(ref) {
        val listener = ref.addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
            if (e != null) result = Result(error = e.localizedMessage ?: "Couldn't load this profile.")
            else if (snap != null && !snap.metadata.isFromCache) result = Result(snap.exists())
        }
        onDispose { listener.remove() }
    }
    return result
}

@Composable
private fun PeopleList(repo: SocialRepository, page: String) {
    ProfileHeading(page)

    val searchable = page == "Find people" || page == "Blocked users"
    var search by rememberSaveable(page) { mutableStateOf("") }
    val searchTerm = search.trim()

    if (searchable) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search by username") },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            trailingIcon = {
                if (search.isNotEmpty()) {
                    TextButton(onClick = { search = "" }) { Text("Clear") }
                }
            }
        )
    }

    val query = remember(repo, page) {
        when (page) {
            "Followers" -> repo.incoming("following")
            "Following" -> repo.edges(repo.me, "following")
            "Blocked users" -> repo.edges(repo.me, "blocks")
            else -> repo.people
        }
    }
    val result = documents(query)
    val profiles = result.value

    if (result.error != null) {
        ProfileCard { Text(result.error, color = MaterialTheme.colorScheme.error) }
        return
    }
    if (profiles == null) {
        ProfileCard { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        return
    }

    val matchingIds = mutableListOf<String>()
    var hasPeople = false
    for (document in profiles) {
        val id = if (page == "Followers") document.getString("source") else document.id
        if (id.isNullOrBlank() || id == repo.me) continue
        hasPeople = true

        val matches = when {
            !searchable || searchTerm.isEmpty() -> true
            page == "Find people" -> document.getString("username").orEmpty()
                .contains(searchTerm, ignoreCase = true)
            else -> key(id) {
                val profile by rememberAuthorProfile(id)
                profile.username.contains(searchTerm, ignoreCase = true)
            }
        }
        if (matches) matchingIds.add(id)
    }

    if (matchingIds.isEmpty()) {
        val message = when {
            hasPeople -> "No usernames match your search."
            page == "Followers" -> "No one follows you yet."
            page == "Following" -> "You aren't following anyone yet."
            page == "Blocked users" -> "You haven't blocked anyone."
            else -> "No other profiles yet."
        }
        ProfileCard { Text(message) }
    } else {
        matchingIds.forEach { id -> key(id) { PersonButton(id, "profile") } }
    }
}

@Composable
private fun PersonButton(id: String, page: String) {
    val context = LocalContext.current
    val profile by rememberAuthorProfile(id)
    ProfileLink(profile.username, onClick = { SocialActivity.open(context, page, id) }) {
        ProfileAvatar(profile.photoPath)
    }
}

@Composable
private fun PeerPage(repo: SocialRepository, peer: String) {
    val context = LocalContext.current
    val profile by rememberAuthorProfile(peer)
    if (peer == repo.me) {
        PhotoFeedScreen(
            ownPostsOnly = true,
            profileHeader = {
                ProfileHeading(profile.username)
                OwnSocialSection(peer, profile.bio)
            }
        )
        return
    }
    val mine = exists(repo.edge(repo.me, "blocks", peer))
    val theirs = exists(repo.edge(peer, "blocks", repo.me))
    val following = exists(repo.edge(repo.me, "following", peer))
    val ready = mine.value == false && theirs.value == false
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun change(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { action() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage }
            finally { busy = false }
        }
    }
    PhotoFeedScreen(
        authorId = peer,
        showPosts = ready,
        profileHeader = {
            ProfileHeading(profile.username)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), enabled = ready && following.value != null && !busy, onClick = {
                    change { repo.follow(peer, following.value != true) }
                }) { Text(if (following.value == true && ready) "Unfollow" else "Follow") }
                FilledTonalButton(modifier = Modifier.weight(1.5f), enabled = ready && !busy, onClick = {
                    SocialActivity.open(context, "chat", peer)
                }) { Text("Message") }
            }
            OutlinedButton(enabled = mine.value != null && !busy, onClick = {
                change { repo.block(peer, mine.value != true) }
            }) { Text(if (mine.value == true) "Unblock" else "Block") }
            if (mine.value == true) Text("You blocked this user. They can’t follow you, message you, or see your posts.")
            else if (theirs.value == true) Text("Following and direct messages are unavailable between these accounts.")
            (error ?: mine.error ?: theirs.error ?: following.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ProfileCard {
                ProfileAvatar(profile.photoPath, 80.dp)
                Text("Bio", style = MaterialTheme.typography.titleMedium)
                Text(profile.bio.ifBlank { "No bio yet." })
            }
        }
    )
}

@Composable
private fun messageHistory(repo: SocialRepository, peer: String? = null): Result<List<DocumentSnapshot>> {
    val queries = remember(repo, peer) {
        val sent = repo.messages.whereEqualTo("sender", repo.me)
        val received = repo.messages.whereEqualTo("recipient", repo.me)
        if (peer == null) sent to received
        else sent.whereEqualTo("recipient", peer) to received.whereEqualTo("sender", peer)
    }
    val sent = documents(queries.first)
    val received = documents(queries.second)
    val failure = sent.error ?: received.error
    val outgoing = sent.value
    val incoming = received.value
    if (failure != null) return Result(error = failure)
    if (outgoing == null || incoming == null) return Result()
    val history = remember(outgoing, incoming) {
        (outgoing + incoming)
            .sortedWith(compareByDescending<DocumentSnapshot> { it.getTimestamp("createdAt") }.thenBy { it.id })
    }
    return Result(history)
}

@Composable
private fun ColumnScope.MessageInbox(repo: SocialRepository) {
    val context = LocalContext.current
    val history = messageHistory(repo)
    val conversations = remember(history.value, repo.me) {
        val latestByPeer = linkedMapOf<String, DocumentSnapshot>()
        history.value.orEmpty().forEach { message ->
            val nameField = if (message.getString("sender") == repo.me) "recipient" else "sender"
            val peer = message.getString(nameField)
            if (!peer.isNullOrBlank() && peer != repo.me) latestByPeer.putIfAbsent(peer, message)
        }
        latestByPeer.toList()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) { ProfileHeading("Messages") }
        FilledTonalButton(onClick = { SocialActivity.open(context, "Find people") }) {
            Text("New message")
        }
    }
    when {
        history.error != null -> ProfileCard {
            Text("Couldn’t load messages.", color = MaterialTheme.colorScheme.error)
        }
        history.value == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
        conversations.isEmpty() -> ProfileCard { Text("No messages yet.") }
        else -> LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(conversations, key = { it.first }) { (peer, message) ->
                ConversationRow(peer, message, repo.me)
            }
        }
    }
}

@Composable
private fun ConversationRow(peer: String, latest: DocumentSnapshot, me: String) {
    val context = LocalContext.current
    val profile by rememberAuthorProfile(peer)
    val time = remember(latest) { messageTime(latest.getTimestamp("createdAt")?.toDate()) }
    val prefix = if (latest.getString("sender") == me) "You: " else ""
    Surface(
        onClick = { SocialActivity.open(context, "chat", peer) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(profile.photoPath, 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(profile.username, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (time.isNotEmpty()) Text(time, style = MaterialTheme.typography.labelSmall)
                }
                Text(prefix + latest.getString("text").orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ColumnScope.ChatScreen(repo: SocialRepository, peer: String) {
    val context = LocalContext.current
    val profile by rememberAuthorProfile(peer)
    val mine = exists(repo.edge(repo.me, "blocks", peer))
    val theirs = exists(repo.edge(peer, "blocks", repo.me))
    val canSend = mine.value == false && theirs.value == false
    val history = if (canSend) messageHistory(repo, peer) else Result<List<DocumentSnapshot>>()
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(peer) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf(false) }

    ProfileLink(profile.username, onClick = { SocialActivity.open(context, "profile", peer) }) {
        ProfileAvatar(profile.photoPath, 38.dp)
    }

    val messages = history.value.orEmpty()
    val listState = rememberLazyListState()
    val newest = messages.firstOrNull()
    LaunchedEffect(newest?.id) {
        if (newest != null && (listState.firstVisibleItemIndex <= 1 ||
                    newest.getString("sender") == repo.me)) {
            listState.scrollToItem(0)
        }
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            history.error != null -> ProfileCard {
                Text("Couldn’t load this conversation.", color = MaterialTheme.colorScheme.error)
            }
            canSend && history.value == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            canSend && messages.isEmpty() -> ProfileCard { Text("No messages yet.") }
            canSend -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message, message.getString("sender") == repo.me)
                }
            }
        }
    }

    if (canSend) {
        if (sendError) {
            Text("Couldn’t send your message. Try again.", color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 2000) draft = it },
                modifier = Modifier.weight(1f),
                enabled = !sending,
                placeholder = { Text("Message") },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            Button(
                modifier = Modifier.heightIn(min = 48.dp),
                enabled = draft.isNotBlank() && !sending,
                onClick = {
                    val text = draft.trim()
                    sending = true
                    sendError = false
                    scope.launch {
                        try {
                            repo.send(peer, text)
                            draft = ""
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            sendError = true
                        } finally {
                            sending = false
                        }
                    }
                }
            ) { Text(if (sending) "Sending…" else "Send") }
        }
    } else {
        ProfileCard {
            Text(when {
                mine.value == true -> "Unblock this person to send messages."
                theirs.value == true -> "Messaging is unavailable between these accounts."
                mine.error != null || theirs.error != null -> "Couldn’t check messaging permissions."
                else -> "Checking messaging permissions…"
            })
        }
    }
}

@Composable
private fun MessageBubble(message: DocumentSnapshot, outgoing: Boolean) {
    val colors = MaterialTheme.colorScheme
    val time = remember(message) { messageTime(message.getTimestamp("createdAt")?.toDate()) }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.86f)) {
            Surface(
                modifier = Modifier.align(if (outgoing) Alignment.CenterEnd else Alignment.CenterStart),
                shape = RoundedCornerShape(16.dp),
                color = if (outgoing) colors.primaryContainer else colors.surface,
                contentColor = if (outgoing) colors.onPrimaryContainer else colors.onSurface
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(message.getString("text").orEmpty())
                    if (time.isNotEmpty()) Text(time, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun messageTime(date: Date?): String {
    if (date == null) return ""
    val today = Calendar.getInstance()
    val sent = Calendar.getInstance().apply { time = date }
    val sameDay = today.get(Calendar.YEAR) == sent.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == sent.get(Calendar.DAY_OF_YEAR)
    val format = if (sameDay) DateFormat.getTimeInstance(DateFormat.SHORT)
    else DateFormat.getDateInstance(DateFormat.MEDIUM)
    return format.format(date)
}