# 台股策略回測實驗室

Angular 前端、Spring Boot API 的台股策略回測作品。前端部署在 GitHub Pages；Spring Boot API 需另外部署到支援 Java 的主機。

## 專案結構

- `frontend/`：Angular 22 單頁應用程式與 Pages 靜態建置。
- `backend/`：Spring Boot 4.1 API、TWSE 歷史行情客戶端與回測引擎。
- `docs/feature-roadmap.md`：後續功能規劃。
- `docs/spring-boot-migration.md`：API 契約與計算假設。

## 本機啟動

需求：Node.js 22、Java 17 以上。專案附 Maven Wrapper（Maven 3.9.11），不必安裝系統 Maven。

啟動 API：

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`local` profile 使用 H2 記憶體資料庫；啟動時由 Hibernate 建立本機資料表。資料庫連線狀態可用 `GET /api/health/database` 檢查。正式環境需透過 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 提供連線設定，並以 `SPRING_JPA_HIBERNATE_DDL_AUTO=update` 管理 schema；程式不提供預設正式資料庫網址或憑證。Zeabur 上可使用 MySQL 私有網路位址。既有 CORS 設定仍由 `CORS_ALLOWED_ORIGIN_PATTERNS` 控制。

目前持久化合成回放 session／決策，以及上市公司、上市基金與上櫃公司名錄白名單。名錄首次搜尋時會完整讀取各官方來源並寫入資料庫，後續搜尋只查資料庫；每天排程更新，只有全部來源成功驗證才整批替換。上櫃標的可搜尋，但 TWSE 回測行情尚未支援。schema 選擇理由與欄位見 [MySQL schema proposal](docs/database/MYSQL_SCHEMA_PROPOSAL.md)。服務依賴儲存介面與 Spring Data JPA adapter；切換其他資料庫時可替換 adapter／連線設定，但仍須驗證 driver、Hibernate dialect、鎖定及 schema 更新行為。TWSE 歷史行情快取與回測輸出因授權尚未確認而不寫入資料庫。

啟動 Angular：

```sh
cd frontend
npm ci
npm start
```

開啟 `http://localhost:4200`。Angular 開發環境預設呼叫 `http://localhost:8080`。如需更換，編輯 `frontend/public/config.js`。

## GitHub Pages 部署

`.github/workflows/deploy-pages.yml` 會在推送到 `main` 時建置並部署前端。GitHub 專案設定需將 **Settings → Pages → Build and deployment → Source** 設為 **GitHub Actions**。

在 **Settings → Secrets and variables → Actions → Variables** 新增：

- `BACKEND_API_URL`：部署後端的 HTTPS 根網址，不要加 `/api/v1/backtests`。例如 `https://api.example.com`。

若此變數留白，網站可開啟，但回測會提示尚未設定後端 API。後端主機還要設定 `CORS_ALLOWED_ORIGIN_PATTERNS`，允許正式 Pages 網址，以及本機開發用的 `http://localhost:4200`。

### Zeabur 後端

將 GitHub 儲存庫連到 Zeabur，新增服務並選擇 `backend` 作為服務根目錄；Zeabur 會使用 `backend/Dockerfile` 建置 Spring Boot。部署後在 Zeabur 產生服務網域，確認 `https://<服務網域>/api/health` 回傳 `{"status":"ok"}`，再把該 HTTPS 根網址設為 GitHub Actions Variable `BACKEND_API_URL`。後端環境變數 `CORS_ALLOWED_ORIGIN_PATTERNS` 設為 `https://kuronekoli.github.io,http://localhost:4200`；切換自訂網域後再加上 `https://finance.kuronekoli.uk`。

自訂網域 `finance.kuronekoli.uk` 目前仍指向原 Sites 網站，所以此遷移不會先綁定 Pages CNAME 或更改 Cloudflare DNS。等後端 API 已部署、前端 Actions Variable 已設定且 GitHub Pages 網站可用後，再到 Pages 設定自訂網域並切換 Cloudflare CNAME 至 `KuroNekoLi.github.io`。

## 回測限制

目前資料來源為 TWSE `STOCK_DAY` 原始日收盤價（2010 年起、每次最長 17 年）；最多比較 3 檔。使用下一筆觀察資料的收盤代理計價，不代表真實可成交價格。結果不含股利、公司行動調整、滑價與券商最低手續費；未取得可靠交易日曆時，不保證已識別停牌或缺漏。歷史模擬僅供研究，不構成投資建議。

## M0 基礎與驗證

本次範圍與驗收見 [M0_SCOPE](docs/product/M0_SCOPE.md)。引擎／API 規格、行情來源與授權盤點分別位於 `docs/specifications/`；固定行情情境及交付證據位於 `docs/quality/`。市場資料的授權、公司行動與交易日曆關卡尚未清除，工程測試通過不代表可以公開提供完整含息績效。

```sh
cd backend
./mvnw verify
```

```sh
cd frontend
npm ci
npm run typecheck
npm run build
```

固定測試使用合成行情與獨立手算預期，無需連線 TWSE。後端 CI 執行 `verify`，包含測試與打包；瀏覽器的合成上游證據和官方活行情來源檢查分開記錄。行情 hash 用來辨識本次資料；未封存活行情快照時，不能保證將來重取結果完全相同。
