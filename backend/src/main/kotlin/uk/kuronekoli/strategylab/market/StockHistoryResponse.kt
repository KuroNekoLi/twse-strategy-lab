package uk.kuronekoli.strategylab.market

data class StockHistoryResponse(val symbol: String, val from: String, val to: String, val observedFrom: String, val observedTo: String, val fetchedAt: String, val source: String, val interval: String, val licensingStatus: String, val adjustmentPolicy: String, val bars: List<Bar>, val limitations: List<String>, val realtime: Boolean = false) {
    data class Bar(
        val date: String,
        val close: String,
        val open: String? = null,
        val high: String? = null,
        val low: String? = null,
        val volume: Long? = null,
        val periodStart: String = date,
        val periodEnd: String = date,
        val observedFrom: String = date,
        val observedTo: String = date,
        val periodWindowStatus: String = "ELAPSED",
        val coverageStatus: String = "UNKNOWN",
    )
}
