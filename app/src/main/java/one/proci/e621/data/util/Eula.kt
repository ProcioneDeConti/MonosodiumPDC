package one.proci.e621.data.util

import android.content.Context
import one.proci.e621.R

fun loadEulaText(context: Context): String =
    context.resources.openRawResource(R.raw.eula).bufferedReader().use { it.readText() }

/**
 * A simple content fingerprint (not cryptographic) - just enough that editing `res/raw/eula.txt`
 * changes the stored hash, so a previously-accepted version no longer matches and the user is
 * prompted to agree again.
 */
fun eulaHash(text: String): String = text.hashCode().toString()
