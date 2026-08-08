package one.proci.e621.data.util

import android.content.Context
import one.proci.e621.R

/** One greeting per non-blank line of res/raw/hello.txt, in whatever language/script it's written in. */
fun loadGreetings(context: Context): List<String> =
    context.resources.openRawResource(R.raw.hello).bufferedReader().readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
