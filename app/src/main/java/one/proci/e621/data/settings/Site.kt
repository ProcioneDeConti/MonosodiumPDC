package one.proci.e621.data.settings

/**
 * e621 and its sister site e6AI (e6ai.net, AI-generated content) run the same e621ng software
 * fork and expose the same relative JSON API paths - only the host and each site's separate
 * account/login differ. See [UserSettings.useE6Ai].
 */
enum class Site(val displayName: String, val apiHost: String, val webBaseUrl: String) {
    E621("e621", "e621.net", "https://e621.net"),
    E6AI("e6AI", "e6ai.net", "https://e6ai.net"),
}
