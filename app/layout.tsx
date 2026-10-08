import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "台股策略回測實驗室｜策略實驗室",
  description: "使用臺灣證券交易所歷史日行情，比較自訂均線策略與定期定額的回測結果。",
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
