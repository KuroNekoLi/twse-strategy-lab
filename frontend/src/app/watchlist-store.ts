export type WatchlistItem = {
  code: string;
  addedAt: string;
};

type StoredWatchlist = {
  schemaVersion: 1;
  items: WatchlistItem[];
};

export type WatchlistReadResult =
  | { ok: true; items: WatchlistItem[] }
  | { ok: false; reason: 'unavailable' | 'malformed'; message: string };

export type WatchlistWriteResult =
  | { ok: true; items: WatchlistItem[]; changed: boolean }
  | { ok: false; reason: 'unavailable' | 'malformed' | 'invalid' | 'duplicate' | 'limit'; message: string; items: WatchlistItem[] };

const STORAGE_KEY = 'twse-strategy-lab:watchlist';
const MAX_ITEMS = 10;
const CODE_PATTERN = /^\d{4,6}$/;

export class WatchlistStore {
  read(): WatchlistReadResult {
    let raw: string | null;
    try {
      raw = window.localStorage.getItem(STORAGE_KEY);
    } catch {
      return {
        ok: false,
        reason: 'unavailable',
        message: '瀏覽器無法讀取本機儲存空間。請確認瀏覽器設定後重試。',
      };
    }
    if (raw === null) return { ok: true, items: [] };
    let parsed: unknown;
    try {
      parsed = JSON.parse(raw);
    } catch {
      return {
        ok: false,
        reason: 'malformed',
        message: '觀察清單資料格式損毀。為避免覆寫原資料，已停用新增與移除。',
      };
    }
    if (!this.isStoredWatchlist(parsed)) {
      return {
        ok: false,
        reason: 'malformed',
        message: '觀察清單資料格式不符或版本不支援。為避免覆寫原資料，已停用新增與移除。',
      };
    }
    return { ok: true, items: parsed.items.map(({ code, addedAt }) => ({ code, addedAt })) };
  }

  add(rawCode: string): WatchlistWriteResult {
    const current = this.read();
    if (!current.ok) return this.readFailure(current);

    const code = rawCode.trim();
    if (!CODE_PATTERN.test(code)) {
      return { ok: false, reason: 'invalid', message: '請輸入 4 至 6 位數字代碼。', items: current.items };
    }
    if (current.items.some((item) => item.code === code)) {
      return { ok: false, reason: 'duplicate', message: code + ' 已在觀察清單中。', items: current.items };
    }
    if (current.items.length >= MAX_ITEMS) {
      return { ok: false, reason: 'limit', message: '觀察清單最多保存 10 檔，請先移除一檔。', items: current.items };
    }

    const items = [...current.items, { code, addedAt: new Date().toISOString() }];
    return this.write({ schemaVersion: 1, items }, items, current.items);
  }

  remove(code: string): WatchlistWriteResult {
    const current = this.read();
    if (!current.ok) return this.readFailure(current);
    if (!current.items.some((item) => item.code === code)) {
      return { ok: true, items: current.items, changed: false };
    }
    const items = current.items.filter((item) => item.code !== code);
    return this.write({ schemaVersion: 1, items }, items, current.items);
  }

  private write(value: StoredWatchlist, items: WatchlistItem[], previousItems: WatchlistItem[]): WatchlistWriteResult {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
      return { ok: true, items, changed: true };
    } catch {
      return {
        ok: false,
        reason: 'unavailable',
        message: '無法保存觀察清單。請確認瀏覽器儲存空間可用後重試。',
        items: previousItems,
      };
    }
  }

  private readFailure(result: Extract<WatchlistReadResult, { ok: false }>): WatchlistWriteResult {
    return { ok: false, reason: result.reason, message: result.message, items: [] };
  }

  private isStoredWatchlist(value: unknown): value is StoredWatchlist {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
    const record = value as Record<string, unknown>;
    if (record['schemaVersion'] !== 1 || !Array.isArray(record['items']) || record['items'].length > MAX_ITEMS) return false;
    if (Object.keys(record).some((key) => key !== 'schemaVersion' && key !== 'items')) return false;
    const seen = new Set<string>();
    return record['items'].every((item: unknown) => {
      if (!item || typeof item !== 'object' || Array.isArray(item)) return false;
      const entry = item as Record<string, unknown>;
      if (Object.keys(entry).some((key) => key !== 'code' && key !== 'addedAt')
        || Object.keys(entry).length !== 2
        || typeof entry['code'] !== 'string'
        || !CODE_PATTERN.test(entry['code'])
        || typeof entry['addedAt'] !== 'string'
        || Number.isNaN(Date.parse(entry['addedAt']))
        || seen.has(entry['code'])) return false;
      seen.add(entry['code']);
      return true;
    });
  }
}

export const WATCHLIST_MAX_ITEMS = MAX_ITEMS;
