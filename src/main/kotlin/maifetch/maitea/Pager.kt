package maifetch.maitea

import kotlinx.serialization.KSerializer

class PageDoesNotExist : RuntimeException("Page does not exist")

class Pager<T>(
    private val apiClient: ApiClient,
    private var page: PagerPage<T>,
    private val serializer: KSerializer<T>,
) {
    fun currentPage(): T = page.data

    fun next(): T {
        val next = page.links.next ?: throw PageDoesNotExist()
        page = apiClient.getPage(next, serializer)
        return page.data
    }

    fun prev(): T {
        val prev = page.links.prev ?: throw PageDoesNotExist()
        page = apiClient.getPage(prev, serializer)
        return page.data
    }

    fun first(): T {
        page = apiClient.getPage(page.links.first, serializer)
        return page.data
    }

    fun last(): T {
        page = apiClient.getPage(page.links.last, serializer)
        return page.data
    }
}
