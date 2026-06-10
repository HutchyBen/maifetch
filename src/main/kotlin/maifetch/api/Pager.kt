package maifetch.api

class Pager<T>(
    private var currentPage: Page<T>,
    private val mapper: Models.Mapper<T>,
) {
    fun currentPage(): List<T> = currentPage.data

    fun next(): List<T> {
        val link = currentPage.next
        if (link.isNullOrBlank()) {
            throw PageDoesNotExist()
        }
        currentPage = currentPage.client.getPage(link, mapper)
        return currentPage.data
    }

    fun previous(): List<T> {
        val link = currentPage.previous
        if (link.isNullOrBlank()) {
            throw PageDoesNotExist()
        }
        currentPage = currentPage.client.getPage(link, mapper)
        return currentPage.data
    }

    fun first(): List<T> {
        currentPage = currentPage.client.getPage(currentPage.first, mapper)
        return currentPage.data
    }

    fun last(): List<T> {
        currentPage = currentPage.client.getPage(currentPage.last, mapper)
        return currentPage.data
    }

    fun pageInfo(): Page<T> = currentPage

    class PageDoesNotExist : java.io.IOException("Page does not exist")
}
