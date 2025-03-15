package eu.kanade.tachiyomi.extension.zh.misskon

import android.util.Log
import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MissKon() : SimpleParsedHttpSource() {

    val TAG: String = "MissKon.Log"

    override val baseUrl = "https://misskon.com"
    override val lang = "zh"
    override val name = "MissKon"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 10, 1, TimeUnit.SECONDS)
        .setRandomUserAgent(UserAgentType.MOBILE)
        .build()

    override fun simpleMangaSelector() = "article.item-list"
    override fun simpleMangaFromElement(element: Element): SManga {
        val titleEL = element.select(".post-box-title")

        val manga = SManga.create()
        manga.title = titleEL.text()
        manga.thumbnail_url = element.select(".post-thumbnail img").attr("data-src")
        manga.setUrlWithoutDomain(titleEL.select("a").attr("href").substring(baseUrl.length))
        return manga
    }

    override fun simpleNextPageSelector(): String? = null

    // region popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top7/")
    // endregion

    // region latest
    override fun latestUpdatesRequest(page: Int): Request {
        val url: String = if (page <= 1) {
            baseUrl
        } else {
            "$baseUrl/page/$page"
        }
        return GET(url)
    }

    override fun latestUpdatesNextPageSelector() = ".current + a.page"
    // endregion

    // region Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filter = filters.findInstance<TagFilter>()!!
        return if (filter.isSelected()) {
            GET(filter.selected.url)
        } else {
            GET("$baseUrl/page/$page/?s=$query")
        }
    }

    override fun searchMangaNextPageSelector() = "div.content > div.pagination > span.current + a"
    override fun searchMangaSelector() = "article.item-list"
    // endregion

    // region Details
    override fun mangaDetailsParse(document: Document): SManga {
        val postInnerEl = document.select("article > .post-inner")

        val manga = SManga.create()
        manga.title = postInnerEl.select(".post-title").text()
        manga.description = ""
        manga.genre = postInnerEl.select(".post-tag > a").joinToString(", ") { it.text() }
        return manga
    }

    override fun chapterListSelector() = "html"

    override fun chapterFromElement(element: Element): SChapter {
        val dataSrc = element.selectFirst(".entry img")!!.attr("data-src")
        val dataRegex = Regex("^.+(\\d{4}/\\d{2}/\\d{2}).+$")
        val dateStr = dataSrc.replace(dataRegex, "$1")

        val chapter = SChapter.create()
        chapter.chapter_number = 0F
        chapter.setUrlWithoutDomain(element.selectFirst("link[rel=\"canonical\"]")!!.attr("href"))
        chapter.name = dateStr
        chapter.date_upload = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).parse(dateStr)!!.time
        return chapter
    }
    // endregion

    // region Pages
    override fun pageListParse(document: Document): List<Page> {
        val basePageUrl = document.selectFirst("link[rel=\"canonical\"]")!!.attr("href")
        Log.d(TAG, "pageListParse: basePageUrl: $basePageUrl")

        val pages = mutableListOf<Page>()
        document.select("div.post-inner div.page-link:nth-child(1) .post-page-numbers")
            .forEachIndexed { index, pageEl ->
                Log.d(TAG, "pageListParse: index: $index")
                val doc = when (index) {
                    0 -> document
                    else -> {
                        val url = "$basePageUrl${pageEl.text()}/"
                        client.newCall(GET(url)).execute().asJsoup()
                    }
                }
                doc.select("div.post-inner > div.entry > p > img")
                    .map { it.attr("data-src") }
                    .forEach { pages.add(Page(pages.size, "", it)) }
            }
        pages.forEach {
            Log.d(TAG, "pageListParse: page: ${it.imageUrl}")
        }
        return pages
    }
    // endregion

    // region Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        initTagFilter(),
    )

    class Tag(private val name: String, val url: String) {
        override fun toString() = this.name
    }

    class TagFilter(name: String, tags: List<Tag>) : Filter.Select<Tag>(name, tags.toTypedArray()) {
        val selected: Tag
            get() = values[state]

        fun isSelected(): Boolean {
            return state > 0
        }
    }

    private fun initTagFilter(): TagFilter {
        val options = mutableListOf(Tag("未选择", ""))
        // top
        options.addAll(
            listOf(
                Tag("Top 3 days", "https://misskon.com/top3/"),
                Tag("Top 7 days", "https://misskon.com/top7/"),
                Tag("Top 30 days", "https://misskon.com/top30/"),
                Tag("Top 60 days", "https://misskon.com/top60/"),
            ),
        )
        // 中国
        options.addAll(
            listOf(
                Tag("中国:[MTCos] 喵糖映画", "https://misskon.com/tag/mtcos/"),
                Tag("中国:BoLoli", "https://misskon.com/tag/bololi/"),
                Tag("中国:CANDY", "https://misskon.com/tag/candy/"),
                Tag("中国:FEILIN", "https://misskon.com/tag/feilin/"),
                Tag("中国:FToow", "https://misskon.com/tag/ftoow/"),
                Tag("中国:GIRLT", "https://misskon.com/tag/girlt/"),
                Tag("中国:HuaYan", "https://misskon.com/tag/huayan/"),
                Tag("中国:HuaYang", "https://misskon.com/tag/huayang/"),
                Tag("中国:IMISS", "https://misskon.com/tag/imiss/"),
                Tag("中国:ISHOW", "https://misskon.com/tag/ishow/"),
                Tag("中国:JVID", "https://misskon.com/tag/jvid/"),
                Tag("中国:KelaGirls", "https://misskon.com/tag/kelagirls/"),
                Tag("中国:Kimoe", "https://misskon.com/tag/kimoe/"),
                Tag("中国:LegBaby", "https://misskon.com/tag/legbaby/"),
                Tag("中国:MF", "https://misskon.com/tag/mf/"),
                Tag("中国:MFStar", "https://misskon.com/tag/mfstar/"),
                Tag("中国:MiiTao", "https://misskon.com/tag/miitao/"),
                Tag("中国:MintYe", "https://misskon.com/tag/mintye/"),
                Tag("中国:MISSLEG", "https://misskon.com/tag/missleg/"),
                Tag("中国:MiStar", "https://misskon.com/tag/mistar/"),
                Tag("中国:MTMeng", "https://misskon.com/tag/mtmeng/"),
                Tag("中国:MyGirl", "https://misskon.com/tag/mygirl/"),
                Tag("中国:PartyCat", "https://misskon.com/tag/partycat/"),
                Tag("中国:QingDouKe", "https://misskon.com/tag/qingdouke/"),
                Tag("中国:RuiSG", "https://misskon.com/tag/ruisg/"),
                Tag("中国:SLADY", "https://misskon.com/tag/slady/"),
                Tag("中国:TASTE", "https://misskon.com/tag/taste/"),
                Tag("中国:TGOD", "https://misskon.com/tag/tgod/"),
                Tag("中国:TouTiao", "https://misskon.com/tag/toutiao/"),
                Tag("中国:TuiGirl", "https://misskon.com/tag/tuigirl/"),
                Tag("中国:Tukmo", "https://misskon.com/tag/tukmo/"),
                Tag("中国:UGIRLS", "https://misskon.com/tag/ugirls/"),
                Tag("中国:UGIRLS - Ai You Wu App", "https://misskon.com/tag/ugirls-ai-you-wu-app/"),
                Tag("中国:UXING", "https://misskon.com/tag/uxing/"),
                Tag("中国:WingS", "https://misskon.com/tag/wings/"),
                Tag("中国:XiaoYu", "https://misskon.com/tag/xiaoyu/"),
                Tag("中国:XingYan", "https://misskon.com/tag/xingyan/"),
                Tag("中国:XIUREN", "https://misskon.com/tag/xiuren/"),
                Tag("中国:XR Uncensored", "https://misskon.com/tag/xr-uncensored/"),
                Tag("中国:YouMei", "https://misskon.com/tag/youmei/"),
                Tag("中国:YouMi", "https://misskon.com/tag/youmi/"),
                Tag("中国:YouMi尤蜜", "https://misskon.com/tag/youmiapp/"),
                Tag("中国:YouWu", "https://misskon.com/tag/youwu/"),
            ),
        )
        // 韩国
        options.addAll(
            listOf(
                Tag("韩国:AG", "https://misskon.com/tag/ag/"),
                Tag("韩国:Bimilstory", "https://misskon.com/tag/bimilstory/"),
                Tag("韩国:BLUECAKE", "https://misskon.com/tag/bluecake/"),
                Tag("韩国:CreamSoda", "https://misskon.com/tag/creamsoda/"),
                Tag("韩国:DJAWA", "https://misskon.com/tag/djawa/"),
                Tag("韩国:Espacia Korea", "https://misskon.com/tag/espacia-korea/"),
                Tag("韩国:Fantasy Factory", "https://misskon.com/tag/fantasy-factory/"),
                Tag("韩国:Fantasy Story", "https://misskon.com/tag/fantasy-story/"),
                Tag("韩国:Glamarchive", "https://misskon.com/tag/glamarchive/"),
                Tag("韩国:HIGH FANTASY", "https://misskon.com/tag/high-fantasy/"),
                Tag("韩国:KIMLEMON", "https://misskon.com/tag/kimlemon/"),
                Tag("韩国:KIREI", "https://misskon.com/tag/kirei/"),
                Tag("韩国:KiSiA", "https://misskon.com/tag/kisia/"),
                Tag("韩国:Korean Realgraphic", "https://misskon.com/tag/korean-realgraphic/"),
                Tag("韩国:Lilynah", "https://misskon.com/tag/lilynah/"),
                Tag("韩国:Lookas", "https://misskon.com/tag/lookas/"),
                Tag("韩国:Loozy", "https://misskon.com/tag/loozy/"),
                Tag("韩国:Moon Night Snap", "https://misskon.com/tag/moon-night-snap/"),
                Tag("韩国:Paranhosu", "https://misskon.com/tag/paranhosu/"),
                Tag("韩国:PhotoChips", "https://misskon.com/tag/photochips/"),
                Tag("韩国:Pure Media", "https://misskon.com/tag/pure-media/"),
                Tag("韩国:PUSSYLET", "https://misskon.com/tag/pussylet/"),
                Tag("韩国:SAINT Photolife", "https://misskon.com/tag/saint-photolife/"),
                Tag("韩国:SWEETBOX", "https://misskon.com/tag/sweetbox/"),
                Tag("韩国:UHHUNG MAGAZINE", "https://misskon.com/tag/uhhung-magazine/"),
                Tag("韩国:UMIZINE", "https://misskon.com/tag/umizine/"),
                Tag("韩国:WXY ENT", "https://misskon.com/tag/wxy-ent/"),
                Tag("韩国:Yo-U", "https://misskon.com/tag/yo-u/"),
            ),
        )
        // 其他
        options.addAll(
            listOf(
                Tag("其他:AI Generated", "https://misskon.com/tag/ai-generated/"),
                Tag("其他:Cosplay", "https://misskon.com/tag/cosplay/"),
                Tag("其他:JP", "https://misskon.com/tag/jp/"),
                Tag("其他:JVID", "https://misskon.com/tag/jvid/"),
                Tag("其他:Patreon", "https://misskon.com/tag/patreon/"),
            ),
        )

        return TagFilter("标签", options)
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
    // endregion
}
