package one.proci.e621.data.util

/**
 * True if [remote] (e.g. "2.10.0", from a GitHub release tag) is a newer version than [local]
 * (e.g. "2.9.0", from BuildConfig.VERSION_NAME) - compared numerically component-by-component,
 * since as plain strings "2.10.0" sorts before "2.9.0".
 */
fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = remote.removePrefix("v").split(".")
    val localParts = local.removePrefix("v").split(".")
    val length = maxOf(remoteParts.size, localParts.size)
    for (i in 0 until length) {
        val r = remoteParts.getOrNull(i)?.toIntOrNull() ?: 0
        val l = localParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (r != l) return r > l
    }
    return false
}
