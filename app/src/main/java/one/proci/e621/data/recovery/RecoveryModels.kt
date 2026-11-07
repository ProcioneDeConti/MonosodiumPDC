package one.proci.e621.data.recovery

import kotlinx.serialization.Serializable

/** How close a recovered file is to the original, worst first (the ordinal is part of the score). */
@Serializable
enum class Grade {
    /** Different shape from the original (cropped, another image of the set, a placeholder, or a still of a video post). */
    MISMATCH,

    /** Same picture at another resolution - or a video that isn't byte-identical (its resolution isn't checked). */
    RESCALED,

    /** Same pixel dimensions, but not the same bytes (re-encoded or metadata-stripped). */
    SAME_DIMENSIONS,

    /** Byte-identical to the original (md5 matches). */
    EXACT,
}

/** One way a recovered file differs from the original; rendered to text by the UI. */
@Serializable
data class Note(
    val kind: Kind,
    val got: String = "",
    val want: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val wantWidth: Int = 0,
    val wantHeight: Int = 0,
) {
    @Serializable
    enum class Kind { FORMAT, DIMENSIONS, SMALLER_FILE, LARGER_FILE, SHAPE_DIFFERS, STILL_OF_VIDEO, VIDEO_UNCHECKED }
}

/** One recovered file, kept in the post's cache folder (see [RecoveredStore]). */
@Serializable
data class RecoveredImage(
    /** File name inside the post's cache folder (`<md5>.<ext>`). */
    val file: String = "",
    val md5: String,
    /** The source URL (from the post's `sources`, or a mirror's post page) this was recovered through. */
    val sourceUrl: String,
    /** The URL the bytes actually came from (an og:image, a site lookup's result, an upgraded thumbnail...). */
    val imageUrl: String,
    /** The file's real type, from its leading bytes. */
    val mime: String,
    val isVideo: Boolean = false,
    val sizeBytes: Long,
    val viaArchive: Boolean,
    val grade: Grade,
    val md5Match: Boolean,
    /** Pixel dimensions (0 for videos - not parsed), and the original's (0 when e621 didn't report them). */
    val width: Int,
    val height: Int,
    val expectedWidth: Int,
    val expectedHeight: Int,
    val expectedSize: Long,
    val notes: List<Note> = emptyList(),
    /** Ranking key, best highest: grade, then format match, then byte-size closeness. */
    val score: Long = 0,
) {
    val ext: String get() = file.substringAfterLast('.', "bin")
}

/**
 * One place a search looked. [stage]: one of the post's sources, a source skipped as not a post
 * link, an md5 lookup on another booru, Wayback captures of a URL, or a phase running out of time.
 * [reason] is the raw message; the report turns the common ones into plain words.
 */
@Serializable
data class Attempt(
    val stage: Stage,
    /** The host or site name ("furaffinity.net", "Danbooru"). */
    val label: String,
    val url: String? = null,
    val reason: String,
    /** Set on report rows for a recovered file (built by the report, never stored). */
    val grade: Grade? = null,
) {
    @Serializable
    enum class Stage { SOURCE, SKIPPED, MIRROR, ARCHIVE, LIMIT }
}

@Serializable
data class SearchReport(val searchedAt: Long, val attempts: List<Attempt>)

/** A post's cache index: every file found (best first), the user's pick, and the latest search's report. */
@Serializable
data class RecoveredSet(
    /** File name of the candidate the user picked; the best match until they pick another. */
    val selected: String? = null,
    val items: List<RecoveredImage> = emptyList(),
    /** Unix seconds when a full search found nothing (and nothing was cached from before). */
    val notFoundAt: Long? = null,
    val report: SearchReport? = null,
) {
    val picked: RecoveredImage? get() = items.firstOrNull { it.file == selected } ?: items.firstOrNull()
}

/** Why a search came back empty-handed. */
class RecoveryFailed(val noSources: Boolean, val attempts: List<Attempt>) : Exception("No copy found.")
