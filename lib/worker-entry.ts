import { handleBacktest } from "./backtest-service";
import type { BacktestInput } from "./backtest-engine";
import { pageHtml, appJavaScript, appCss } from "../dist/server/site-assets";

const headers = {
  html: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-cache" },
  js: { "Content-Type": "text/javascript; charset=utf-8", "Cache-Control": "public, max-age=3600" },
  css: { "Content-Type": "text/css; charset=utf-8", "Cache-Control": "public, max-age=3600" },
};

export default {
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/") return new Response(pageHtml, { headers: headers.html });
    if (request.method === "GET" && url.pathname === "/app.js") return new Response(appJavaScript, { headers: headers.js });
    if (request.method === "GET" && url.pathname === "/style.css") return new Response(appCss, { headers: headers.css });
    if (url.pathname === "/api/v1/backtests" && request.method === "POST") {
      let input;
      try { input = await request.json(); }
      catch { return Response.json({ error: "請求內容不是有效的 JSON。" }, { status: 400 }); }
      const result = await handleBacktest(input as BacktestInput);
      return Response.json(result.body, { status: result.status, headers: { "Cache-Control": "no-store" } });
    }
    if (url.pathname === "/api/v1/backtests") return Response.json({ error: "此路徑僅接受 POST 請求。" }, { status: 405, headers: { Allow: "POST" } });
    return new Response("Not found", { status: 404, headers: { "Content-Type": "text/plain; charset=utf-8" } });
  },
};
