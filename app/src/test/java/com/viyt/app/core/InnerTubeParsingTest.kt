package com.viyt.app.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing tests for YouTube's API. The samples copy the shapes of real responses captured from
 * youtube.com on 17 Nov 2026 (Home, a Home chip, Home page 2, search, search page 2, watch page),
 * trimmed to a few cards each.
 */
class InnerTubeParsingTest {

    // A Home/Up next card in YouTube's current format.
    private fun lockup(id: String, title: String, channel: String = "SVMM", channelId: String = "UCLfjwrn-9PxnsvqZj7RFTpQ") = """
        {"lockupViewModel":{
          "contentImage":{"thumbnailViewModel":{
            "image":{"sources":[{"url":"https://i.ytimg.com/vi/$id/hq720.jpg","width":720,"height":404}]},
            "overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":{"text":"5:31"}}]}}]}},
          "metadata":{"lockupMetadataViewModel":{
            "title":{"content":"$title"},
            "image":{"decoratedAvatarViewModel":{
              "avatar":{"avatarViewModel":{"image":{"sources":[{"url":"https://yt3.ggpht.com/a=s68","width":68,"height":68}]}}},
              "rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"browseEndpoint":{"browseId":"$channelId"}}}}}}},
            "metadata":{"contentMetadataViewModel":{"metadataRows":[
              {"metadataParts":[{"text":{"content":"$channel"}}]},
              {"metadataParts":[{"text":{"content":"3.3M views"}},{"text":{"content":"1 day ago"},"accessibilityLabel":"1 day ago"}]}
            ],"delimiter":" • "}}}},
          "contentId":"$id","contentType":"LOCKUP_CONTENT_TYPE_VIDEO"}}
    """.trimIndent()

    private fun cont(token: String) =
        """{"continuationItemRenderer":{"trigger":"CONTINUATION_TRIGGER_ON_ITEM_SHOWN","continuationEndpoint":{"continuationCommand":{"token":"$token","request":"CONTINUATION_REQUEST_TYPE_BROWSE"}}}}"""

    private val home = JSONObject("""
        {"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"selected":true,"content":{"richGridRenderer":{
          "contents":[
            {"richItemRenderer":{"content":${lockup("feRjEy72wvE", "46 Years Old Ronaldinho vs Forli 2026")}}},
            {"richItemRenderer":{"content":{"adSlotRenderer":{"fulfillmentContent":{"fulfilledLayout":{"inFeedAdLayoutRenderer":{"renderingContent":${lockup("AdVideo0001", "Buy this")}}}}}}}},
            ${cont("HOME_PAGE_2")}
          ],
          "header":{"feedFilterChipBarRenderer":{"contents":[
            {"chipCloudChipRenderer":{"text":{"runs":[{"text":"All"}]},"isSelected":true}},
            {"chipCloudChipRenderer":{"text":{"runs":[{"text":"Podcasts"}]},"navigationEndpoint":{"continuationCommand":{"token":"PODCASTS_TOKEN","request":"CONTINUATION_REQUEST_TYPE_BROWSE"}}}}
          ]}}}}}}]}},
         "onResponseReceivedActions":[{"adsControlFlowOpportunityReceivedCommand":{"opportunityType":"OPPORTUNITY_TYPE_ORGANIC_BROWSE_RESPONSE_RECEIVED"}}]}
    """)

    @Test fun homeCardsChipsAndNextPage() {
        val page = InnerTube.parsePage(home, "browse")
        assertEquals(listOf("feRjEy72wvE"), page.items.map { it.id })
        val v = page.items[0]
        assertEquals("46 Years Old Ronaldinho vs Forli 2026", v.title)
        assertEquals("SVMM", v.channel)
        assertEquals("3.3M views · 1 day ago", v.meta)
        assertEquals(331L, v.durationSec)
        assertEquals("https://www.youtube.com/channel/UCLfjwrn-9PxnsvqZj7RFTpQ", v.channelUrl)
        assertEquals("https://www.youtube.com/watch?v=feRjEy72wvE", v.url)
        assertFalse(v.short)
        assertEquals(Cont("browse", "HOME_PAGE_2"), page.next)

        val chips = InnerTube.parseChips(home)
        assertEquals(listOf("All", "Podcasts"), chips.map { it.label })
        assertNull("The selected chip is the feed already on screen", chips[0].token)
        assertEquals("PODCASTS_TOKEN", chips[1].token)
    }

    @Test fun homeNeverShowsAds() {
        val ids = InnerTube.parsePage(home, "browse").items.map { it.id }
        assertFalse(ids.contains("AdVideo0001"))
    }

    // Tapping a chip (Podcasts, Music, Gaming...): YouTube answers with a "reload" of the feed.
    // Before the fix, these videos were skipped and chips showed nothing.
    @Test fun chipResultsAreRead() {
        val json = JSONObject("""
            {"onResponseReceivedActions":[{"reloadContinuationItemsCommand":{"targetId":"browse-feedFEwhat_to_watch","continuationItems":[
              {"richItemRenderer":{"content":${lockup("Pod00000001", "A podcast episode")}}},
              {"richItemRenderer":{"content":${lockup("Pod00000002", "Another episode")}}},
              ${cont("PODCASTS_PAGE_2")}
            ]}}]}
        """)
        val page = InnerTube.parsePage(json, "browse")
        assertEquals(listOf("Pod00000001", "Pod00000002"), page.items.map { it.id })
        assertEquals(Cont("browse", "PODCASTS_PAGE_2"), page.next)
    }

    // Scrolling: the next page arrives as an "append". Before the fix, Home stopped at page one.
    @Test fun nextPagesAreRead() {
        val json = JSONObject("""
            {"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
              {"richItemRenderer":{"content":${lockup("Next0000001", "Page two video")}}},
              ${cont("HOME_PAGE_3")}
            ]}}]}
        """)
        val page = InnerTube.parsePage(json, "browse")
        assertEquals(listOf("Next0000001"), page.items.map { it.id })
        assertEquals(Cont("browse", "HOME_PAGE_3"), page.next)
    }

    @Test fun lastPageHasNoNext() {
        val json = JSONObject("""{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
            {"richItemRenderer":{"content":${lockup("Last0000001", "The end")}}}]}}]}""")
        assertNull(InnerTube.parsePage(json, "browse").next)
    }

    private val videoRenderer = """
        {"videoRenderer":{"videoId":"dQw4w9WgXcQ","title":{"runs":[{"text":"Afrobeats Mix 2026"}]},
          "ownerText":{"runs":[{"text":"DJ Example","navigationEndpoint":{"browseEndpoint":{"browseId":"UCdjexample00000000000000"}}}]},
          "lengthText":{"simpleText":"1:02:03"},"shortViewCountText":{"simpleText":"1.2M views"},
          "publishedTimeText":{"simpleText":"3 weeks ago"},
          "navigationEndpoint":{"watchEndpoint":{"videoId":"dQw4w9WgXcQ"}}}}
    """.trimIndent()

    private val shortsShelf = """
        {"gridShelfViewModel":{"contents":[
          {"shortsLockupViewModel":{"entityId":"s1","onTap":{"innertubeCommand":{"reelWatchEndpoint":{"videoId":"Short000001"}}},
            "overlayMetadata":{"primaryText":{"content":"Funny short"},"secondaryText":{"content":"2M views"}},
            "thumbnail":{"sources":[{"url":"https://i.ytimg.com/vi/Short000001/oar2.jpg","width":405}]}}}
        ]}}
    """.trimIndent()

    // Search: sponsored results (searchPyvRenderer) carry a normal card inside; they must not show.
    @Test fun searchSkipsSponsoredResults() {
        val json = JSONObject("""
            {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[
              {"itemSectionRenderer":{"contents":[
                {"searchPyvRenderer":{"ads":[{"adSlotRenderer":{}} , ${lockup("Sponsored01", "Sponsored")}]}},
                $videoRenderer,
                $shortsShelf
              ]}},
              ${cont("SEARCH_PAGE_2")}
            ]}}}}}
        """)
        val page = InnerTube.parsePage(json, "search")
        assertEquals(listOf("dQw4w9WgXcQ", "Short000001"), page.items.map { it.id })
        val v = page.items[0]
        assertEquals("Afrobeats Mix 2026", v.title)
        assertEquals("DJ Example", v.channel)
        assertEquals(3723L, v.durationSec)
        assertEquals("1.2M views · 3 weeks ago", v.meta)
        val s = page.items[1]
        assertTrue(s.short)
        assertEquals("https://www.youtube.com/shorts/Short000001", s.url)
        assertEquals("Funny short", s.title)
        assertEquals(Cont("search", "SEARCH_PAGE_2"), page.next)
    }

    @Test fun searchPageTwoSkipsAds() {
        val json = JSONObject("""
            {"onResponseReceivedCommands":[{"appendContinuationItemsAction":{"continuationItems":[
              {"itemSectionRenderer":{"contents":[
                {"adSlotRenderer":{"fulfillmentContent":${lockup("AdVideo0002", "Ad")}}},
                $videoRenderer
              ]}},
              ${cont("SEARCH_PAGE_3")}
            ]}}]}
        """)
        val page = InnerTube.parsePage(json, "search")
        assertEquals(listOf("dQw4w9WgXcQ"), page.items.map { it.id })
        assertEquals(Cont("search", "SEARCH_PAGE_3"), page.next)
    }

    @Test fun watchPage() {
        val json = JSONObject("""
            {"contents":{"twoColumnWatchNextResults":{
              "results":{"results":{"contents":[
                {"videoPrimaryInfoRenderer":{"title":{"runs":[{"text":"The video"}]},
                  "viewCount":{"videoViewCountRenderer":{"viewCount":{"simpleText":"1,234 views"},"shortViewCount":{"simpleText":"1.2K views"}}},
                  "relativeDateText":{"simpleText":"2 days ago"},
                  "videoActions":{"menuRenderer":{"topLevelButtons":[{"segmentedLikeDislikeButtonViewModel":{"likeButtonViewModel":{"likeButtonViewModel":{"toggleButtonViewModel":{"toggleButtonViewModel":{"defaultButtonViewModel":{"buttonViewModel":{"title":"5.4K"}}}},"likeStatusEntity":{"likeStatus":"LIKE"}}}}}]}}}},
                {"videoSecondaryInfoRenderer":{
                  "owner":{"videoOwnerRenderer":{"title":{"runs":[{"text":"Channel Name","navigationEndpoint":{"browseEndpoint":{"browseId":"UCchannel0000000000000000"}}}]},
                    "subscriberCountText":{"simpleText":"120K subscribers"},
                    "thumbnail":{"thumbnails":[{"url":"https://yt3.ggpht.com/x=s48","width":48}]}}},
                  "subscribeButton":{"subscribeButtonRenderer":{"subscribed":true,"channelId":"UCchannel0000000000000000"}},
                  "attributedDescription":{"content":"About this video"}}},
                {"itemSectionRenderer":{"sectionIdentifier":"comment-item-section","contents":[${cont("COMMENTS_TOKEN")}]}}
              ]}},
              "secondaryResults":{"secondaryResults":{"results":[
                ${lockup("Related0001", "Related one")},
                ${lockup("Mj8GzUEJkX8", "The same video again")}
              ]}}}}}
        """)
        val w = InnerTube.parseWatch(json, "Mj8GzUEJkX8")
        assertEquals("The video", w.title)
        assertEquals("1.2K views", w.views)
        assertEquals("2 days ago", w.date)
        assertEquals("Channel Name", w.channel)
        assertEquals("UCchannel0000000000000000", w.channelId)
        assertEquals("120K subscribers", w.subscribers)
        assertTrue(w.subscribed)
        assertEquals("5.4K", w.likes)
        assertTrue(w.liked)
        assertEquals("About this video", w.description)
        assertEquals("COMMENTS_TOKEN", w.commentsToken)
        assertEquals("Up next never lists the video you're watching", listOf("Related0001"), w.related.map { it.id })
    }

    @Test fun videoIdsFromLinks() {
        assertEquals("dQw4w9WgXcQ", InnerTube.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10"))
        assertEquals("dQw4w9WgXcQ", InnerTube.videoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", InnerTube.videoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", InnerTube.videoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM"))
        assertEquals("dQw4w9WgXcQ", InnerTube.videoId("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertNull(InnerTube.videoId("https://www.youtube.com/@channel"))
        assertNull(InnerTube.videoId("https://www.youtube.com/playlist?list=PL123"))
    }

    @Test fun refreshPutsUnseenVideosFirst() {
        fun v(id: String) = Item(id, id, "https://www.youtube.com/watch?v=$id", null, null, null, Item.Kind.VIDEO)
        val now = listOf(v("a"), v("b"), v("c"), v("d"))
        assertEquals(listOf("c", "d", "a", "b"), newFirst(now, setOf("a", "b")).map { it.id })
        assertEquals("First load keeps YouTube's order", now, newFirst(now, emptySet()))
    }

    @Test fun cachedFeedRoundTrip() {
        val items = InnerTube.parsePage(home, "browse").items
        val back = Item.listFromJson(Item.listToJson(items))
        assertEquals(items, back)
        assertNotNull(back.first().channelUrl)
    }
}
