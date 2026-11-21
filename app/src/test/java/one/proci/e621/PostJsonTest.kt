package one.proci.e621

import kotlinx.serialization.json.Json
import one.proci.e621.data.model.PostsResponse
import one.proci.e621.data.model.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same config as E621Client. */
private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class PostJsonTest {

    private val v2 = """
        [{"id":1,"created_at":"2026-11-18T14:47:53.110-05:00","change_seq":5,
          "files":{"meta":{"md5":"abc","ext":"jpg","size":475322,"duration":null,"has_sample":false},
                   "original":{"width":1191,"height":842,"url":"https://static1.e621.net/data/x.jpg"},
                   "preview":{"width":362,"height":256,"jpg":"https://static1.e621.net/data/preview/x.jpg","webp":"https://static1.e621.net/data/preview/x.webp"},
                   "sample":{"width":1191,"height":842,"jpg":"https://static1.e621.net/data/x.jpg","webp":"https://static1.e621.net/data/x.jpg"}},
          "uploader_id":7,"stats":{"score":{"up":3,"down":-1,"total":2},"fav_count":9,"is_favorited":true,"vote":1,"comment_count":4},
          "flags":{"pending":true,"deleted":false},
          "has":{"parent":true,"children":true,"active_children":false,"notes":true,"sample":false},
          "relationships":{"parent_id":42,"children":[5,6]},
          "pools":[3],"rating":"e","locked_tags":["x"],"sources":["https://a"],"description":"d",
          "tags":{"general":["male"],"artist":["someone"],"contributor":["helper"]}},
         {"id":2,"files":{"meta":{"ext":"webm","size":1,"duration":5.9,"has_sample":true},
                   "original":{"width":1024,"height":1024,"url":"https://s/v.webm"},
                   "preview":{"width":1,"height":1,"jpg":null,"webp":"https://s/p.webp"},
                   "sample":{"width":850,"height":850,"jpg":"https://s/sample.jpg","webp":"https://s/sample.webp"}},
          "stats":{},"has":{},"relationships":{},"rating":"s","tags":["flat","list"]}]
    """.trimIndent()

    @Test
    fun decodesV2BareArray() {
        val posts = json.decodeFromString(PostsResponse.serializer(), v2).posts
        assertEquals(2, posts.size)
        val p = posts[0]
        assertEquals(1L, p.id)
        assertEquals("https://static1.e621.net/data/x.jpg", p.file.url)
        assertEquals(1191, p.file.width)
        assertEquals("jpg", p.file.ext)
        assertEquals(475322L, p.file.size)
        assertEquals("abc", p.file.md5)
        assertEquals("https://static1.e621.net/data/preview/x.jpg", p.preview.url)
        // No sample: v2 puts the original in sample.jpg, legacy semantics want null.
        assertEquals(false, p.sample.has)
        assertNull(p.sample.url)
        assertEquals(2, p.score.total)
        assertEquals(9, p.favCount)
        assertEquals(4, p.commentCount)
        assertTrue(p.isFavorited)
        assertEquals(1, p.voteBy)
        assertTrue(p.hasNotes)
        assertEquals(42L, p.relationships.parentId)
        assertTrue(p.relationships.hasChildren)
        assertEquals(listOf(5L, 6L), p.relationships.children)
        assertTrue(p.flags.pending)
        assertEquals(listOf(3L), p.pools)
        assertEquals(7L, p.uploaderId)
        assertEquals(listOf("x"), p.lockedTags)
        assertEquals(TagCategory.CONTRIBUTOR, p.categorizedTags.first { it.name == "helper" }.category)
        assertEquals(TagCategory.ARTIST, p.categorizedTags.first { it.name == "someone" }.category)

        val v = posts[1]
        assertTrue(v.isVideo)
        assertEquals(5.9, v.duration!!, 0.0)
        assertEquals("https://s/sample.jpg", v.sample.url)
        assertEquals("https://s/p.webp", v.preview.url)
        assertEquals(listOf("flat", "list"), v.allTags)
    }

    @Test
    fun decodesEmptyV2() {
        assertEquals(0, json.decodeFromString(PostsResponse.serializer(), "[]").posts.size)
    }

    @Test
    fun decodesLegacyEnvelope() {
        val legacy = """
            {"posts":[{"id":3,"file":{"width":10,"height":20,"ext":"png","size":5,"md5":"m","url":"https://s/f.png"},
              "preview":{"width":1,"height":2,"url":"https://s/p.jpg"},
              "sample":{"has":false,"width":10,"height":20,"url":null},
              "score":{"up":1,"down":0,"total":1},"tags":{"general":["a"],"artist":["b"]},
              "fav_count":2,"vote":-1,"relationships":{"parent_id":null,"has_children":true,"children":[]}}]}
        """.trimIndent()
        val p = json.decodeFromString(PostsResponse.serializer(), legacy).posts.single()
        assertEquals("https://s/f.png", p.file.url)
        assertEquals("https://s/p.jpg", p.preview.url)
        assertNull(p.sample.url)
        assertEquals(1, p.score.total)
        assertEquals(2, p.favCount)
        assertEquals(-1, p.voteBy)
        assertTrue(p.relationships.hasChildren)
        assertEquals(listOf("b", "a"), p.allTags)
    }
}
