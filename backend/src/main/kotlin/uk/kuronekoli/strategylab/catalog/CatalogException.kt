package uk.kuronekoli.strategylab.catalog

class CatalogException(val code: String, message: String, val status: Int) : RuntimeException(message) {
    companion object {
        fun input(message: String) = CatalogException("INVALID_CATALOG_QUERY", message, 400)
        fun schema(source: String) = CatalogException("CATALOG_SCHEMA_INVALID", "${source}格式或識別欄位不正確，名錄查詢已停止。", 502)
        fun upstream(source: String) = CatalogException("CATALOG_UPSTREAM_UNAVAILABLE", "${source}暫時無法取得，請稍後再試。", 502)
    }
}
