import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  metadataBase: new URL("https://twse-strategy-lab.alice-margatroid-lov.chatgpt.site"),
  title: "台股策略回測工具｜均線、RSI 與定期定額比較",
  description: "用臺灣證券交易所上市股票歷史日行情，回測雙均線、RSI、布林通道、區間突破與自訂規則，並和定期定額比較。",
  alternates: { canonical: "/" },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true, "max-image-preview": "large" },
  },
  openGraph: {
    type: "website",
    locale: "zh_TW",
    url: "/",
    siteName: "策略實驗室",
    title: "台股策略回測工具｜均線、RSI 與定期定額比較",
    description: "用臺灣證券交易所上市股票歷史日行情，回測多種策略並與定期定額比較。",
  },
  twitter: {
    card: "summary",
    title: "台股策略回測工具｜策略實驗室",
    description: "使用台股歷史日行情，自訂標的、期間與策略，查看回測結果。",
  },
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-TW">
      <body className="antialiased">{children}</body>
    </html>
  );
}
