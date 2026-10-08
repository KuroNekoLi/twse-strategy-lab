# MySQL schema proposal

The first persisted workflow is the synthetic paper-replay exercise. Its sessions and user decisions are useful across service restarts and contain no real market data. Replay state is reconstructed from the fixed, code-owned synthetic fixture and ordered decisions, so derived portfolio values and future observations are not stored.

| Table | Columns | Purpose |
| --- | --- | --- |
| `replay_session` | `id` (UUID string, primary key), `challenge_id`, `created_at`, `last_touched_at`, `expires_at`, `version` | Bounded, expiring synthetic exercise session. An index on expiry supports cleanup. |
| `replay_decision` | `id` (generated primary key), `session_id` (foreign key), `sequence_no`, `action`, `shares` (nullable for WAIT), `recorded_at` | Ordered user decisions. Unique `(session_id, sequence_no)` prevents duplicate steps; deleting a session deletes its decisions. |
| `catalog_source` | `id`, `title`, `provider`, `dataset_url`, `resource_url`, `license`, `license_url`, `update_frequency`, `fetched_at`, `as_of_from`, `as_of_to`, `cache_ttl_seconds` | Metadata and provenance for each fully refreshed directory source. |
| `catalog_instrument` | `id` (source plus code), `code`, `name`, `kind`, `market`, `as_of`, `backtest_supported`, `source_id` | Searchable whitelist of ticker/name fields, with code/name indexes. One code may exist in more than one market/source without overwriting provenance. |

Sessions retain the existing 30-minute idle expiry and 64-session bound. The API response shape remains unchanged. Sessions are not user-authenticated; the UUID remains the session capability as in the existing API, so this is suitable for the current anonymous demo, not private account data.

Deferred: saved strategy configurations currently live only in browser local storage and have no backend ownership/authentication contract. Backtest outputs and TWSE price caches are not persisted because the repository licensing matrix marks historical market-data storage rights as unresolved. Revisit these after licensing and user-ownership decisions.

The instrument catalog is refreshed from official TWSE listed-company/fund resources and the TPEx OTC-company resource. On an empty database, the first catalog query performs the initial refresh; afterward searches read only the persisted rows. A daily refresh replaces the complete snapshot transactionally after all sources validate, so source failure keeps the previous catalog. The current backtest price endpoint supports TWSE only; OTC entries are searchable but marked unavailable to backtest until a TPEx price-data adapter is implemented and verified.
