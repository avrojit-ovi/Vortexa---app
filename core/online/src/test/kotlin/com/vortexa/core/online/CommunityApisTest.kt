package com.vortexa.core.online.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityApisTest {

    @Test
    fun `sponsor segments are parsed, sorted and filtered`() {
        val json = """
            [
              {"category":"intro","segment":[0.0,5.5]},
              {"category":"sponsor","segment":[120.0,180.25]},
              {"category":"filler","segment":[10.0,20.0]},
              {"category":"sponsor","segment":[300.0,200.0]},
              {"category":"outro","segment":[900.0,960.0]}
            ]
        """.trimIndent()

        val segments = SponsorBlockParser.parse(json)

        assertEquals(3, segments.size)
        assertEquals("intro", segments[0].category)
        assertEquals(5.5, segments[0].endSeconds, 0.001)
        assertEquals("sponsor", segments[1].category)
        assertEquals(180.25, segments[1].endSeconds, 0.001)
        assertEquals("outro", segments[2].category)
    }

    @Test
    fun `broken sponsor payloads return nothing`() {
        assertTrue(SponsorBlockParser.parse("").isEmpty())
        assertTrue(SponsorBlockParser.parse("not json").isEmpty())
        assertTrue(SponsorBlockParser.parse("{}").isEmpty())
        assertTrue(SponsorBlockParser.parse("""[{"category":"sponsor"}]""").isEmpty())
    }

    @Test
    fun `dislike payloads parse, and errors return null`() {
        val stats = DislikeParser.parse("""{"id":"abc","likes":1200,"dislikes":30,"rating":4.8}""")
        assertEquals(1200L, stats?.likes)
        assertEquals(30L, stats?.dislikes)
        assertEquals(4.8, stats!!.rating, 0.001)

        assertNull(DislikeParser.parse("""{"error":"Video not found"}"""))
        assertNull(DislikeParser.parse("garbage"))
    }
}
