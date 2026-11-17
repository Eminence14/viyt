package com.viyt.app.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Parsing tests for YouTube Music's API (music.youtube.com), using the shapes of its real responses. */
class MusicParsingTest {

    private val songRow = """
        {"musicResponsiveListItemRenderer":{
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://lh3.googleusercontent.com/song=w60-h60-l90-rj","width":60},
            {"url":"https://lh3.googleusercontent.com/song=w120-h120-l90-rj","width":120}]}}},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Last Last"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
              {"text":"Song"},{"text":" • "},
              {"text":"Burna Boy","navigationEndpoint":{"browseEndpoint":{"browseId":"UCburna000000000000000000",
                "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},
              {"text":" • "},{"text":"3:52"}]}}}],
          "overlay":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{"playNavigationEndpoint":{"watchEndpoint":{
            "videoId":"421w1j87fEM","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}}}}}},
          "playlistItemData":{"videoId":"421w1j87fEM"}}}
    """.trimIndent()

    private val albumCard = """
        {"musicTwoRowItemRenderer":{
          "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://lh3.googleusercontent.com/album=w226-h226-l90-rj","width":226}]}}},
          "title":{"runs":[{"text":"Love, Damini"}]},
          "subtitle":{"runs":[{"text":"Album"},{"text":" • "},{"text":"Burna Boy"}]},
          "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_album0000",
            "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}}}
    """.trimIndent()

    @Test fun searchAndHomeShelves() {
        val json = JSONObject("""
            {"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
              {"musicShelfRenderer":{"title":{"runs":[{"text":"Songs"}]},"contents":[$songRow]}},
              {"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Albums for you"}]}}},
                "contents":[$albumCard]}}
            ]}}}}]}}}
        """)
        val shelves = Music.shelves(json)
        assertEquals(listOf("Songs", "Albums for you"), shelves.map { it.title })

        val song = shelves[0].items.single()
        assertEquals(MusicItem.Kind.SONG, song.kind)
        assertEquals("421w1j87fEM", song.id)
        assertEquals("Last Last", song.title)
        assertEquals("3:52", song.duration)
        assertEquals("Song • Burna Boy", song.subtitle)
        assertEquals("Burna Boy", song.artist)
        assertEquals("UCburna000000000000000000", song.artistId)
        assertEquals("Bigger cover art is requested", "https://lh3.googleusercontent.com/song=w544-h544-l90-rj", song.thumb)

        val album = shelves[1].items.single()
        assertEquals(MusicItem.Kind.ALBUM, album.kind)
        assertEquals("MPREb_album0000", album.id)
        assertEquals("Burna Boy", album.artist)
        assertEquals(false, shelves[1].list)
    }

    @Test fun radioQueue() {
        val json = JSONObject("""
            {"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{"watchNextTabbedResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"musicQueueRenderer":{"content":{"playlistPanelRenderer":{
              "contents":[
                {"playlistPanelVideoRenderer":{"videoId":"421w1j87fEM","title":{"runs":[{"text":"Last Last"}]},
                  "longBylineText":{"runs":[{"text":"Burna Boy"},{"text":" • "},{"text":"Love, Damini"},{"text":" • "},{"text":"2022"}]},
                  "lengthText":{"runs":[{"text":"2:53"}]},
                  "thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/421w1j87fEM/sddefault.jpg","width":640}]}}},
                {"playlistPanelVideoRenderer":{"videoId":"Essence0001","title":{"runs":[{"text":"Essence"}]},
                  "longBylineText":{"runs":[{"text":"Wizkid"},{"text":" & "},{"text":"Tems"},{"text":" • "},{"text":"Made in Lagos"}]},
                  "lengthText":{"runs":[{"text":"4:08"}]}}},
                {"playlistPanelVideoRenderer":{"videoId":"421w1j87fEM","title":{"runs":[{"text":"Last Last (again)"}]}}}
              ],
              "continuations":[{"nextRadioContinuationData":{"continuation":"RADIO_PAGE_2"}}]}}}}}}]}}}}}
        """)
        val tracks = Music.panel(json)
        assertEquals("Each song appears once", listOf("421w1j87fEM", "Essence0001"), tracks.map { it.videoId })
        assertEquals("Burna Boy", tracks[0].artist)
        assertEquals("Wizkid & Tems", tracks[1].artist)
        assertEquals("2:53", tracks[0].duration)
        assertEquals("RADIO_PAGE_2", Music.radioToken(json))
    }

    @Test fun noRadioTokenAtTheEnd() {
        assertNull(Music.radioToken(JSONObject("""{"contents":{}}""")))
    }

    @Test fun biggerArt() {
        assertEquals("https://lh3.googleusercontent.com/x=w544-h544-l90-rj", Music.big("https://lh3.googleusercontent.com/x=w120-h120-l90-rj"))
        assertEquals("https://yt3.ggpht.com/a=s544", Music.big("https://yt3.ggpht.com/a=s88-c-k-c0x00ffffff-no-rj"))
        assertEquals("ytimg links are left alone", "https://i.ytimg.com/vi/x/hqdefault.jpg", Music.big("https://i.ytimg.com/vi/x/hqdefault.jpg"))
        assertNull(Music.big(null))
    }

    @Test fun artistFromSubtitle() {
        fun item(sub: String?) = MusicItem(MusicItem.Kind.SONG, "id", "t", sub, null)
        assertEquals("Burna Boy", item("Song • Burna Boy • 3M plays").artist)
        assertEquals("Asake", item("Asake • Mr. Money").artist)
        assertNull(item(null).artist)
    }
}
