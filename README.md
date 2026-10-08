# 台股策略回測實驗室

Angular 前端、Spring Boot API 的台股策略回測作品。前端部署在 GitHub Pages；Spring Boot API 需另外部署到支援 Java 的主機。

## 專案結構

- `frontend/`：Angular 22 單頁應用程式與 Pages 靜態建置。
- `backend/`：Spring Boot 4.1 API、TWSE 歷史行情客戶端與回測引擎。
- `docs/feature-roadmap.md`：後續功能規劃。
- `docs/spring-boot-migration.md`：API 契約與計算假設。

## 本機啟動

需求：Node.js 22、Java 17 以上、Maven 3.6.3 以上。

啟動 API：

```sh
cd backend
mvn spring-boot:run
```

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

目前資料來源為 TWSE `STOCK_DAY` 日收盤價（2010 年起、每次最長 17 年）；最多比較 3 檔。結果不含配息、滑價與券商最低手續費。歷史模擬僅供研究，不構成投資建議。
