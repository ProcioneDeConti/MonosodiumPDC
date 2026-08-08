package one.proci.e621.ui.screens.grid

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.proci.e621.R
import one.proci.e621.data.model.TagCategory
import one.proci.e621.data.model.TagSuggestion
import one.proci.e621.data.repository.TagSuggestionRepository
import one.proci.e621.data.util.loadGreetings
import one.proci.e621.ui.components.PostGridBody
import one.proci.e621.ui.components.RainbowStripeColors
import one.proci.e621.ui.theme.RatingExplicit
import one.proci.e621.ui.theme.RatingSafe
import one.proci.e621.ui.theme.TagArtist
import one.proci.e621.ui.theme.TagCharacter
import one.proci.e621.ui.theme.TagCopyright
import one.proci.e621.ui.theme.TagGeneral
import one.proci.e621.ui.theme.TagSpecies
import one.proci.e621.ui.theme.ViewerBackground

/** Shared height for the search field and its icon row, for a unified look. */
private val SearchBarHeight = 48.dp

/** Caps how tall the suggestions dropdown can grow, leaving a comfortable margin above the bottom of the screen. */
private val SuggestionsMaxHeight = 260.dp

// Prefixed onto the field's value whenever chips exist, purely so a backspace at the start of an
// otherwise-empty input always reaches onValueChange (soft keyboards often swallow backspace on a
// truly empty field instead of delivering it as a KeyEvent).
private const val BackspaceAnchor = "\u200B"

/** Easter egg trigger word - deliberately not a real e621 tag. */
private const val EasterEggWord = "cooter"

private data class SearchTag(val name: String, val negative: Boolean, val category: TagCategory? = null) {
    val token: String get() = if (negative) "-$name" else name
    val display: String get() = token.replace('_', ' ')
}

private fun parseToken(raw: String): SearchTag {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("-") && trimmed.length > 1) {
        SearchTag(trimmed.removePrefix("-"), negative = true)
    } else {
        SearchTag(trimmed.removePrefix("-"), negative = false)
    }
}

private fun parseQuery(query: String): List<SearchTag> =
    query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map(::parseToken)

private fun List<SearchTag>.joinToQuery(): String = joinToString(" ") { it.token }

private fun formatPostCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fk".format(count / 1_000.0)
    else -> count.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostGridScreen(
    state: PostGridUiState,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDismissError: () -> Unit,
    onPostClick: (index: Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenForum: () -> Unit,
    onOpenSavedSearches: (currentQuery: String) -> Unit,
    onOpenProfile: () -> Unit,
    onSetBlacklistDisabled: (Boolean) -> Unit,
    unreadMessageCount: Int,
    forumUnread: Boolean,
    tagSuggestionRepository: TagSuggestionRepository,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Scaffold(
            topBar = {
                SearchTopBar(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    onSearchSubmit = onSearchSubmit,
                    onOpenMenu = { menuExpanded = true },
                    blacklistDisabled = state.blacklistDisabled,
                    onSetBlacklistDisabled = onSetBlacklistDisabled,
                    unreadMessageCount = unreadMessageCount,
                    forumUnread = forumUnread,
                    tagSuggestionRepository = tagSuggestionRepository,
                    snackbarHostState = snackbarHostState,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            PostGridBody(
                posts = state.posts,
                isRefreshing = state.isRefreshing,
                isLoadingMore = state.isLoadingMore,
                error = state.error,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onDismissError = onDismissError,
                onPostClick = onPostClick,
                blacklistDisabled = state.blacklistDisabled,
                onEnableBlacklist = { onSetBlacklistDisabled(false) },
                modifier = Modifier.padding(padding),
            )
        }

        NavDrawerOverlay(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            username = state.username,
            unreadMessageCount = unreadMessageCount,
            forumUnread = forumUnread,
            onOpenMessages = onOpenMessages,
            onOpenForum = onOpenForum,
            onOpenFavorites = onOpenFavorites,
            onOpenSavedSearches = { onOpenSavedSearches(state.activeQuery) },
            onOpenProfile = onOpenProfile,
            onOpenSettings = onOpenSettings,
        )
    }
}

/**
 * Slides in from the right edge (rather than Material3's built-in `ModalNavigationDrawer`, which
 * only opens from the layout-start edge - left, in this app's LTR-only UI).
 */
@Composable
private fun NavDrawerOverlay(
    expanded: Boolean,
    onDismiss: () -> Unit,
    username: String,
    unreadMessageCount: Int,
    forumUnread: Boolean,
    onOpenMessages: () -> Unit,
    onOpenForum: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSavedSearches: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    BackHandler(enabled = expanded, onBack = onDismiss)

    fun dismissAnd(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }

    val context = LocalContext.current
    val greetings = remember { loadGreetings(context) }
    // Re-picked (not just re-shown) each time the drawer opens - keyed on `expanded` so the
    // `remember` block reruns synchronously in the same composition pass the drawer appears in,
    // rather than a frame later via LaunchedEffect, which would flash the previous/blank greeting.
    val greeting = remember(expanded) { if (expanded) greetings.randomOrNull() else null }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.78f)
                    .widthIn(max = 300.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(vertical = 8.dp),
                ) {
                    if (greeting != null) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            // Left-aligned unconditionally (rather than the usual start/end, which
                            // would flip for RTL scripts like Arabic/Hebrew) so the greeting always
                            // hugs the same edge no matter which language it happens to land on.
                            Text(
                                text = "$greeting,",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Left,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (username.isNotBlank()) {
                                Text(
                                    text = username,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Left,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = dismissAnd(onOpenProfile)),
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    }
                    DrawerItem(
                        icon = Icons.Filled.Email,
                        label = stringResource(R.string.messages_title),
                        tint = MaterialTheme.colorScheme.primary,
                        badgeCount = unreadMessageCount,
                        onClick = dismissAnd(onOpenMessages),
                    )
                    DrawerItem(
                        icon = Icons.Filled.Forum,
                        label = stringResource(R.string.forum_title),
                        tint = MaterialTheme.colorScheme.primary,
                        badgeDot = forumUnread,
                        onClick = dismissAnd(onOpenForum),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DrawerItem(
                        icon = Icons.Filled.Favorite,
                        label = stringResource(R.string.favorites),
                        onClick = dismissAnd(onOpenFavorites),
                    )
                    DrawerItem(
                        icon = Icons.Filled.Bookmark,
                        label = stringResource(R.string.saved_searches_title),
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = dismissAnd(onOpenSavedSearches),
                    )
                    DrawerItem(
                        icon = Icons.Filled.Settings,
                        label = stringResource(R.string.action_settings),
                        onClick = dismissAnd(onOpenSettings),
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = LocalContentColor.current,
    badgeCount: Int = 0,
    badgeDot: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (badgeCount > 0) {
            Badge { Text(if (badgeCount > 99) "99+" else badgeCount.toString()) }
        } else if (badgeDot) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onOpenMenu: () -> Unit,
    blacklistDisabled: Boolean,
    onSetBlacklistDisabled: (Boolean) -> Unit,
    unreadMessageCount: Int,
    forumUnread: Boolean,
    tagSuggestionRepository: TagSuggestionRepository,
    snackbarHostState: SnackbarHostState,
) {
    // Each search results screen is its own back-stack entry seeded from its own route's query,
    // so `query` never changes out from under an already-composed instance of this bar - it only
    // needs to be read once. Chip/in-progress-text state otherwise lives here so keystrokes don't
    // need to round-trip the ViewModel.
    var tags by remember { mutableStateOf(parseQuery(query)) }
    // The field's real text is prefixed with a zero-width anchor char whenever there are chips,
    // purely so backspacing at the very start of the (otherwise empty) input always produces an
    // onValueChange callback - soft keyboards frequently swallow backspace on a truly empty field
    // instead of delivering a KeyEvent, which made deleting a chip unreliable. Every programmatic
    // change below sets the cursor explicitly (rather than leaving Compose to infer it), since
    // repeatedly rewriting the same anchor-only text on consecutive backspaces otherwise left the
    // cursor in front of the anchor - the first chip deleted fine, but a second one wouldn't.
    var fieldValue by remember { mutableStateOf(TextFieldValue(if (tags.isNotEmpty()) BackspaceAnchor else "")) }
    fun inputText() = fieldValue.text.removePrefix(BackspaceAnchor)
    var isFocused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<TagSuggestion>>(emptyList()) }
    var almostThereHint by remember { mutableStateOf(false) }
    var showEggDialog by remember { mutableStateOf(false) }
    var barBottomY by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val eggCoroutineScope = rememberCoroutineScope()
    val eggMessage = stringResource(R.string.easter_egg_snackbar)

    fun triggerEasterEgg() {
        showEggDialog = true
        eggCoroutineScope.launch { snackbarHostState.showSnackbar(eggMessage) }
    }

    fun setField(newTags: List<SearchTag>, text: String) {
        tags = newTags
        onQueryChange(newTags.joinToQuery())
        val full = (if (newTags.isNotEmpty()) BackspaceAnchor else "") + text
        fieldValue = TextFieldValue(full, selection = TextRange(full.length))
    }

    fun processTyped(baseTags: List<SearchTag>, text: String) {
        if (text.contains(' ')) {
            val endsWithSpace = text.endsWith(' ')
            val parts = text.split(' ')
            val toFinalize = (if (endsWithSpace) parts else parts.dropLast(1)).filter { it.isNotBlank() }
            val remainder = if (endsWithSpace) "" else parts.last()
            // "cooter" isn't a real e621 tag - it only ever triggers the easter egg, never becomes
            // an actual search chip, whether typed live or finalized with a trailing space.
            val isEgg = { token: String -> token.trim().removePrefix("-").lowercase() == EasterEggWord }
            if (toFinalize.any(isEgg)) triggerEasterEgg()
            val realTokens = toFinalize.filterNot(isEgg)
            val newTags = if (realTokens.isNotEmpty()) baseTags + realTokens.map(::parseToken) else baseTags
            setField(newTags, remainder)
        } else {
            setField(baseTags, text)
        }
    }

    fun handleFieldChange(new: TextFieldValue) {
        val newText = new.text
        if (tags.isNotEmpty() && !newText.startsWith(BackspaceAnchor)) {
            // The anchor itself got deleted - i.e. backspace with nothing else typed - so drop
            // the last chip; anything left over (only possible via a selection that spanned the
            // anchor) is treated as freshly typed text.
            processTyped(tags.dropLast(1), newText.removePrefix(BackspaceAnchor))
        } else {
            processTyped(tags, newText.removePrefix(BackspaceAnchor))
        }
    }

    fun submit() {
        val pending = inputText().trim()
        val finalTags = if (pending.isNotEmpty()) tags + parseToken(pending) else tags
        suggestions = emptyList()
        setField(finalTags, "")
        onSearchSubmit(finalTags.joinToQuery())
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    fun clearAll() {
        suggestions = emptyList()
        setField(emptyList(), "")
    }

    fun collapseSearch() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    BackHandler(enabled = isFocused) { collapseSearch() }

    fun selectSuggestion(suggestion: TagSuggestion) {
        val negative = inputText().trim().startsWith("-")
        suggestions = emptyList()
        setField(tags + SearchTag(suggestion.name, negative, suggestion.tagCategory), "")
    }

    // Live typing straight into "cooter" (no space needed) triggers the egg immediately and
    // clears the field, so the word itself never lingers as a fake search chip.
    LaunchedEffect(inputText()) {
        if (inputText().removePrefix("-").trim().lowercase() == EasterEggWord) {
            triggerEasterEgg()
            setField(tags, "")
        }
    }

    // Debounced live suggestions for whatever's currently being typed - cancels/restarts
    // automatically whenever inputText changes, so a fast typist never triggers a request per
    // keystroke, and any in-flight request for stale text is dropped.
    LaunchedEffect(inputText()) {
        val prefix = inputText().removePrefix("-").trim()
        val lowerPrefix = prefix.lowercase()
        // Close to (but not yet) the magic word: tease it instead of showing real suggestions,
        // without ever revealing/offering the word itself.
        if (lowerPrefix.isNotEmpty() &&
            EasterEggWord.startsWith(lowerPrefix) &&
            lowerPrefix.length >= EasterEggWord.length - 2 &&
            lowerPrefix != EasterEggWord
        ) {
            almostThereHint = true
            suggestions = emptyList()
            return@LaunchedEffect
        }
        almostThereHint = false
        if (prefix.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = tagSuggestionRepository.suggest(prefix)
    }

    // Chips added by picking a suggestion already know their category; chips finalized by typing
    // a tag out and pressing space don't, so look those up in the background and patch them in
    // once resolved (matched by name, since indices can shift as chips are added/removed).
    val unresolvedNames = tags.filter { it.category == null }.map { it.name }
    LaunchedEffect(unresolvedNames) {
        unresolvedNames.forEach { name ->
            val resolved = tagSuggestionRepository.suggest(name).firstOrNull { it.name == name }?.tagCategory
            if (resolved != null) {
                tags = tags.map { if (it.name == name && it.category == null) it.copy(category = resolved) else it }
            }
        }
    }

    Surface(shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .onGloballyPositioned { barBottomY = it.positionInWindow().y.toInt() + it.size.height },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Always full width (minus whatever trailing icons are showing) - never collapsed to
            // an icon. Focus is only ever requested from a real tap (this Box's own clickable, or
            // the leading icon's), never programmatically from an effect: that was what broke chip
            // backspacing - requesting focus asynchronously, before the field had settled, left the
            // cursor showing but the IME connection not fully wired up.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SearchBarHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        focusRequester.requestFocus()
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                    if (isFocused) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { collapseSearch() },
                        )
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(22.dp))
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .horizontalScroll(scrollState),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEachIndexed { index, tag ->
                            SearchTagChip(
                                tag = tag,
                                onRemove = { setField(tags.filterIndexed { i, _ -> i != index }, inputText()) },
                                onToggleNegative = {
                                    setField(
                                        tags.mapIndexed { i, t -> if (i == index) t.copy(negative = !t.negative) else t },
                                        inputText(),
                                    )
                                },
                            )
                        }
                        Box {
                            if (inputText().isEmpty() && tags.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            BasicTextField(
                                value = fieldValue,
                                onValueChange = ::handleFieldChange,
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { submit() }),
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isFocused = it.isFocused },
                            )
                        }
                    }

                    // Hidden until the field is actually focused, so it can't be tapped by
                    // accident and wipe out a whole search - only shows once you've
                    // deliberately re-entered it.
                    if (isFocused && (tags.isNotEmpty() || inputText().isNotEmpty())) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { clearAll() },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !isFocused,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSetBlacklistDisabled(!blacklistDisabled) }) {
                        Icon(
                            if (blacklistDisabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = stringResource(
                                if (blacklistDisabled) R.string.blacklist_enable else R.string.blacklist_disable,
                            ),
                        )
                    }
                    IconButton(onClick = onOpenMenu) {
                        BadgedBox(badge = { if (unreadMessageCount > 0 || forumUnread) Badge() }) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.action_menu))
                        }
                    }
                }
            }
        }

        if (isFocused && almostThereHint) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, barBottomY),
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.easter_egg_almost_there),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (isFocused && suggestions.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, barBottomY),
                properties = PopupProperties(focusable = false),
            ) {
                val negatedInput = inputText().trim().startsWith("-")
                // Wrapping chips (rather than one-per-line rows) pack far more suggestions into
                // the same vertical space, so more of them are visible without scrolling.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .heightIn(max = SuggestionsMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(suggestion = suggestion, isNegative = negatedInput, onClick = { selectSuggestion(suggestion) })
                    }
                }
            }
        }
    }

    if (showEggDialog) {
        EasterEggDialog(onDismiss = { showEggDialog = false })
    }
}

/** Same fill/outline language as [SearchTagChip], plus the (fuzzified) post count folded right into the chip. */
@Composable
private fun SuggestionChip(suggestion: TagSuggestion, isNegative: Boolean, onClick: () -> Unit) {
    val background = categoryColor(suggestion.tagCategory)
    val content = categoryContentColor(suggestion.tagCategory)
    val borderColor = if (isNegative) RatingExplicit else RatingSafe
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(2.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(suggestion.name.replace('_', ' '), color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(formatPostCount(suggestion.postCount), color = content.copy(alpha = 0.75f), fontSize = 11.sp)
        }
        if (suggestion.antecedentName != null) {
            Text(
                "via ${suggestion.antecedentName.replace('_', ' ')}",
                color = content.copy(alpha = 0.75f),
                fontSize = 10.sp,
            )
        }
    }
}

private fun categoryColor(category: TagCategory?): Color = when (category) {
    TagCategory.ARTIST -> TagArtist
    TagCategory.COPYRIGHT -> TagCopyright
    TagCategory.CHARACTER -> TagCharacter
    TagCategory.SPECIES -> TagSpecies
    TagCategory.GENERAL, TagCategory.LORE, TagCategory.META, null -> TagGeneral
}

/** Some category colors (artist/character) are light enough that white text loses contrast. */
private fun categoryContentColor(category: TagCategory?): Color = when (category) {
    TagCategory.ARTIST, TagCategory.CHARACTER -> Color.Black
    else -> Color.White
}

/**
 * A finalized search tag rendered as a squircle chip: filled with its e621 tag category color,
 * outlined green/red for included/excluded. Tap toggles negation; long-press removes it (removal
 * is destructive, so it shouldn't be one accidental tap away).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchTagChip(
    tag: SearchTag,
    onRemove: () -> Unit,
    onToggleNegative: () -> Unit,
) {
    val borderColor = if (tag.negative) RatingExplicit else RatingSafe
    val catColor = categoryColor(tag.category)
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(catColor)
            .border(2.dp, borderColor, shape)
            .combinedClickable(onClick = onToggleNegative, onLongClick = onRemove)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = tag.display, color = categoryContentColor(tag.category), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun EasterEggDialog(onDismiss: () -> Unit) {
    // res/raw is a plain byte stream, not a drawable, so it has to be decoded by hand rather than
    // via painterResource - and Coil's AsyncImage silently failed to load it too, since this app's
    // custom ImageLoader only registers a network fetcher, with nothing that resolves
    // android.resource:// URIs. A real drawable resource (res/drawable/egg.png, referenced as
    // painterResource(R.drawable.egg)) is the conventional/correct place for a static image like
    // this going forward - res/raw is meant for arbitrary non-image files.
    val context = LocalContext.current
    val eggBitmap = remember {
        context.resources.openRawResource(R.raw.egg).use(BitmapFactory::decodeStream).asImageBitmap()
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(ViewerBackground)
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ScrollingRainbowBackground(modifier = Modifier.fillMaxSize())
                Image(
                    bitmap = eggBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                )
            }
        }
    }
}

/**
 * An infinitely, seamlessly scrolling rainbow flag - equal-width solid stripes drawn directly as
 * rectangles (rather than a multi-stop gradient Brush, which turned out unreliable here - some
 * stops weren't rendering and the tile seam showed a gap), so there's no shader-stop ambiguity:
 * what you see is exactly the [RainbowStripeColors] list, tiled edge-to-edge with a 1px overlap
 * between rectangles to paper over any antialiasing seam, including where the last stripe wraps
 * back around to the first.
 */
@Composable
private fun ScrollingRainbowBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rainbow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(6000, easing = LinearEasing)),
        label = "rainbowProgress",
    )
    Canvas(modifier = modifier.clipToBounds()) {
        // Oversized (2x) and rotated 45deg, so the diagonal sweep still fully covers every corner
        // of the visible area once rotated - a same-size square would leave gaps at the corners.
        val overW = size.width * 2f
        val overH = size.height * 2f
        val shiftPx = progress * overW
        val n = RainbowStripeColors.size
        val stripeWidth = overW / n
        val left = center.x - overW / 2f
        val top = center.y - overH / 2f

        rotate(degrees = 45f, pivot = center) {
            for (tile in 0..1) {
                val tileLeft = left - shiftPx + tile * overW
                for (i in 0 until n) {
                    drawRect(
                        color = RainbowStripeColors[i],
                        topLeft = Offset(tileLeft + i * stripeWidth, top),
                        size = Size(stripeWidth + 1f, overH),
                    )
                }
            }
        }
    }
}
