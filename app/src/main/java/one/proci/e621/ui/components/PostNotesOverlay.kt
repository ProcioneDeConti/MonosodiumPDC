package one.proci.e621.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.proci.e621.data.model.Post
import one.proci.e621.data.model.PostNote
import kotlin.math.min

private val NoteYellow = Color(0xFFFFE082)

/**
 * View-only translation-note boxes over the image. Placed inside the media viewer's zoom/pan
 * transform so the boxes track the image; note coordinates are against the post's *original*
 * pixel dimensions, so they're scaled to the letterboxed rendered image rect (ContentScale.Fit)
 * here rather than by re-deriving the zoom transform.
 */
@Composable
fun PostNotesOverlay(
    post: Post,
    notes: List<PostNote>,
    selectedNoteId: Long?,
    onSelectNote: (Long?) -> Unit,
) {
    val originalW = post.file.width
    val originalH = post.file.height
    if (notes.isEmpty() || originalW <= 0 || originalH <= 0) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        // ContentScale.Fit: the image is uniformly scaled to fit, centered, with letterbox bars.
        val fit = min(boxW / originalW, boxH / originalH)
        val padX = (boxW - originalW * fit) / 2f
        val padY = (boxH - originalH * fit) / 2f

        notes.forEachIndexed { index, note ->
            val selected = note.id == selectedNoteId
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (padX + note.x * fit).toDp() },
                        y = with(density) { (padY + note.y * fit).toDp() },
                    )
                    .size(
                        width = with(density) { (note.width * fit).toDp() },
                        height = with(density) { (note.height * fit).toDp() },
                    )
                    .background(NoteYellow.copy(alpha = if (selected) 0.28f else 0.14f))
                    .border(if (selected) 2.dp else 1.dp, NoteYellow, RoundedCornerShape(2.dp))
                    .clickable { onSelectNote(if (selected) null else note.id) },
            ) {
                Text(
                    (index + 1).toString(),
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(NoteYellow, RoundedCornerShape(bottomEnd = 3.dp))
                        .padding(horizontal = 3.dp),
                )
            }
        }
    }
}

/**
 * The tapped note's translated body, as a caption panel. Rendered by the caller *outside* the
 * zoom transform so it stays legible at any zoom level.
 */
@Composable
fun NoteBodyBubble(note: PostNote, index: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.88f))
            .border(1.dp, NoteYellow, RoundedCornerShape(7.dp))
            .clickable(onClick = onDismiss)
            .padding(12.dp),
    ) {
        Column {
            Text("Note ${index + 1}", color = NoteYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                DTextView(
                    text = note.body,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
