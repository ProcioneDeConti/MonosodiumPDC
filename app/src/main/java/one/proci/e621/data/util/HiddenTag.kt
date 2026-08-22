package one.proci.e621.data.util

/**
 * A single, hardcoded tag excluded everywhere posts are fetched - unlike the user's own
 * blacklist (see [one.proci.e621.data.settings.UserSettings.isBlacklisted]), this is not stored
 * in DataStore, never shown in the blacklist editor, and not affected by the "temporarily disable
 * blacklist" toggle. It's applied server-side (as a `-tag` search exclusion) so matching posts
 * never even arrive in the app, rather than being fetched and then hidden client-side.
 * See [one.proci.e621.data.repository.PostRepository.fetchPosts] and
 * [one.proci.e621.data.repository.AvatarRepository.fetchAvatarUrl].
 */
const val PERMANENTLY_HIDDEN_TAG = "notkastar"
