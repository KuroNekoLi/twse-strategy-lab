import { NextResponse } from "next/server";
import { handleBacktest } from "@/lib/backtest-service";
import type { BacktestInput } from "@/lib/backtest-engine";

export const runtime = "edge";

export async function POST(request: Request) {
  let input: BacktestInput;
  try { input = await request.json() as BacktestInput; }
  catch { return NextResponse.json({ error: "請求內容不是有效的 JSON。" }, { status: 400 }); }
  const result = await handleBacktest(input);
  return NextResponse.json(result.body, { status: result.status });
}
