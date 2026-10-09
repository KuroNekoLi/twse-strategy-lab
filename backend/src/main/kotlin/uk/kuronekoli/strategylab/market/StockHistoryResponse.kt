package uk.kuronekoli.strategylab.market

data class StockHistoryResponse(val symbol: String, val from: String, val to: String, val observedFrom: String, val observedTo: String, val fetchedAt: String, val source: String, val interval: String, val licensingStatus: String, val adjustmentPolicy: String, val bars: List<Bar>, val limitations: List<String>) {
    data class Bar(val date: String, val close: String)
}
