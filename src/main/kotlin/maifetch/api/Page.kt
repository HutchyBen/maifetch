package maifetch.api

data class Page<T>(
    internal val client: MaiTeaClient,
    val data: List<T>,
    val first: String,
    val last: String,
    val previous: String?,
    val next: String?,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)
