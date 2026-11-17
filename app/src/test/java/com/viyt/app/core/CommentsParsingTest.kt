package com.viyt.app.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comments and replies, in the shape YouTube's "next" endpoint returned on 17 Nov 2026. */
class CommentsParsingTest {

    private fun payload(key: String, id: String, text: String, author: String, level: Int, replies: String, creator: Boolean = false) = """
        {"payload":{"commentEntityPayload":{"key":"$key",
          "properties":{"commentId":"$id","content":{"content":"$text"},"publishedTime":"6 years ago (edited)","replyLevel":$level},
          "author":{"channelId":"UCx","displayName":"$author","avatarThumbnailUrl":"https://yt3.ggpht.com/a=s88","isVerified":false,"isCreator":$creator},
          "toolbar":{"likeCountNotliked":"429K","replyCount":"$replies"}}}}
    """.trimIndent()

    private fun cont(token: String) =
        """{"continuationItemRenderer":{"trigger":"CONTINUATION_TRIGGER_ON_ITEM_SHOWN","continuationEndpoint":{"continuationCommand":{"token":"$token"}}}}"""

    @Test fun commentsWithRepliesLinks() {
        val json = JSONObject("""
            {"onResponseReceivedEndpoints":[
              {"reloadContinuationItemsCommand":{"targetId":"comments-section","continuationItems":[
                {"commentsHeaderRenderer":{"countText":{"runs":[{"text":"2.4M"}]},"sortMenu":{"sortFilterSubMenuRenderer":{"subMenuItems":[
                  {"title":"Top","serviceEndpoint":{"continuationCommand":{"token":"SORT_TOP"}}}]}}}}]}},
              {"reloadContinuationItemsCommand":{"targetId":"comments-section","continuationItems":[
                {"commentThreadRenderer":{
                  "replies":{"commentRepliesRenderer":{"contents":[${cont("REPLIES_OF_FIRST")}],
                    "viewReplies":{"buttonRenderer":{"text":{"runs":[{"text":"775 replies"}]}}}}},
                  "commentViewModel":{"commentViewModel":{"commentKey":"K1","commentId":"Ugy1"}}}},
                {"commentThreadRenderer":{
                  "commentViewModel":{"commentViewModel":{"commentKey":"K2","commentId":"Ugy2"}}}},
                ${cont("COMMENTS_PAGE_2")}
              ]}}],
             "frameworkUpdates":{"entityBatchUpdate":{"mutations":[
               ${payload("K2", "Ugy2", "Second comment", "@second", 0, "0", creator = true)},
               ${payload("K1", "Ugy1", "Rick rolling is the one meme that will never die", "@JetpackGamer456", 0, "775")}
             ]}}}
        """)
        val (list, next) = InnerTube.parseComments(json)
        assertEquals("Threads keep YouTube's order", listOf("Ugy1", "Ugy2"), list.map { it.id })
        val first = list[0]
        assertEquals("@JetpackGamer456", first.author)
        assertEquals("Rick rolling is the one meme that will never die", first.text)
        assertEquals("429K", first.likes)
        assertEquals("775", first.replies)
        assertEquals("6 years ago (edited)", first.time)
        assertEquals("REPLIES_OF_FIRST", first.repliesToken)
        assertFalse(first.byCreator)
        assertNull("No replies, no link", list[1].repliesToken)
        assertTrue(list[1].byCreator)
        assertEquals("COMMENTS_PAGE_2", next)
    }

    @Test fun repliesAndShowMoreReplies() {
        val json = JSONObject("""
            {"onResponseReceivedEndpoints":[{"appendContinuationItemsAction":{"continuationItems":[
              {"commentViewModel":{"commentKey":"R1","commentId":"Ugy1.r1"}},
              {"commentViewModel":{"commentKey":"R2","commentId":"Ugy1.r2"}},
              {"continuationItemRenderer":{"button":{"buttonRenderer":{"text":{"runs":[{"text":"Show more replies"}]},
                "command":{"continuationCommand":{"token":"MORE_REPLIES"}}}}}}
            ]}}],
             "frameworkUpdates":{"entityBatchUpdate":{"mutations":[
               ${payload("R1", "Ugy1.r1", "First reply", "@a", 1, "")},
               ${payload("R2", "Ugy1.r2", "Second reply", "@b", 1, "")}
             ]}}}
        """)
        val (list, next) = InnerTube.parseComments(json)
        assertEquals(listOf("First reply", "Second reply"), list.map { it.text })
        assertNull(list[0].repliesToken)
        assertEquals("Replies continue through the Show more replies button", "MORE_REPLIES", next)
    }

    @Test fun lastPageOfReplies() {
        val json = JSONObject("""
            {"onResponseReceivedEndpoints":[{"appendContinuationItemsAction":{"continuationItems":[
              {"commentViewModel":{"commentKey":"R9","commentId":"Ugy1.r9"}}]}}],
             "frameworkUpdates":{"entityBatchUpdate":{"mutations":[${payload("R9", "Ugy1.r9", "Last one", "@z", 1, "")}]}}}
        """)
        val (list, next) = InnerTube.parseComments(json)
        assertEquals(1, list.size)
        assertNull(next)
    }
}
