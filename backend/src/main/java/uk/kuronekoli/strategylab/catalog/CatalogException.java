package uk.kuronekoli.strategylab.catalog;

/** Public catalog failures never include a provider row or personal-data field. */
public final class CatalogException extends RuntimeException {
  private final String code;
  private final int status;
  public CatalogException(String code, String message, int status) { super(message); this.code = code; this.status = status; }
  public String code() { return code; }
  public int status() { return status; }
  static CatalogException input(String message) { return new CatalogException("INVALID_CATALOG_QUERY", message, 400); }
  static CatalogException schema(String source) { return new CatalogException("CATALOG_SCHEMA_INVALID", source + "格式或識別欄位不正確，名錄查詢已停止。", 502); }
  static CatalogException upstream(String source) { return new CatalogException("CATALOG_UPSTREAM_UNAVAILABLE", source + "暫時無法取得，請稍後再試。", 502); }
}
