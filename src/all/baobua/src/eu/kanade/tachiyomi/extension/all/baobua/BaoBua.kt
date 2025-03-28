package eu.kanade.tachiyomi.extension.all.baobua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BaoBua() : SimpleParsedHttpSource() {

    override val baseUrl = "https://www.baobua.net"
    override val lang = "all"
    override val name = "BaoBua"
    override val supportsLatest = false

    override fun simpleMangaSelector() = "article.post"

    override fun simpleMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a.popunder")!!.absUrl("href"))
        title = element.selectFirst("div.read-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun simpleNextPageSelector(): String = "nav.pagination a.next"

    // region popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl?page=$page", headers)
    // endregion

    // region latest
    override fun latestUpdatesRequest(page: Int) = throw Exception("Stub!")
    // endregion

    // region Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filter = filters.findInstance<SourceCategorySelector>()!!
        return filter.selectedCategory?.let {
            GET(it.url, headers)
        } ?: run {
            baseUrl.toHttpUrl().newBuilder()
                .addEncodedQueryParameter("q", query)
                .addEncodedQueryParameter("page", page.toString())
                .build()
                .let { GET(it, headers) }
        }
    }

    // region Details
    override fun mangaDetailsParse(document: Document): SManga {
        val trailItemsEl = document.selectFirst("div.breadcrumb-trail > ul.trail-items")!!
        return SManga.create().apply {
            title = trailItemsEl.selectFirst("li.trail-end")!!.text().trim()
            genre = trailItemsEl.select("li:not(.trail-end):not(.trail-begin)").joinToString { it.text() }
        }
    }

    override fun chapterListSelector() = "html"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        chapter_number = 0F
        setUrlWithoutDomain(element.selectFirst("div.breadcrumb-trail li.trail-end > a")!!.absUrl("href"))
        date_upload = POST_DATE_FORMAT.tryParse(element.selectFirst("span.item-metadata.posts-date")?.text()?.trim())
        name = DISPLAY_DATE_FORMAT.format(Date(date_upload))
    }
    // endregion

    // region Pages
    override fun pageListParse(document: Document): List<Page> {
        val basePageUrl = document.selectFirst("div.breadcrumb-trail li.trail-end > a")!!.absUrl("href")

        val maxPage: Int = document.selectFirst("div.nav-links > a.next.page-numbers")?.text()?.toInt() ?: 1

        var pageIndex = 0
        return (1..maxPage).flatMap { pageNum ->
            val doc = if (pageNum == 1) {
                document
            } else {
                client.newCall(GET("$basePageUrl?p=$pageNum", headers)).execute().asJsoup()
            }

            doc.select("div.entry-content.read-details img.wp-image")
                .map { Page(pageIndex++, imageUrl = it.absUrl("src")) }
        }
    }
    // endregion

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Unable to further search in the category!"),
        Filter.Separator(),
        SourceCategorySelector.create(baseUrl),
    )

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    companion object {

        private val POST_DATE_FORMAT = SimpleDateFormat("EEE MMM dd yyyy", Locale.US)

        private val DISPLAY_DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    }
}
