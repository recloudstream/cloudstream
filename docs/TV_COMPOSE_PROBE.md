# Compose for TV — Phase 7 Real Search → same Details as Home

Architecture:

```
TvSearchScreen → TvSearchViewModel (StateContainer)
  → TvSearchRepository → APIRepository.search(query, page)
  → TvSearchMapper → immutable TvSearchResult (+ TvContentRef)
  → cards → existing TvDetailsScreen / TvDetailsRepository
  → existing playback / episodes (Phase 5–6)
```

## Domain search APIs (inspected, not invented)

From `APIRepository` / `SearchViewModel` / `MainAPI`:

| API | Behavior |
|-----|----------|
| `APIRepository.search(query, page)` | `Resource<SearchResponseList>`; empty query → Success(empty); timeout `searchTimeoutMs` |
| `APIRepository.quickSearch(query)` | providers with `hasQuickSearch` only |
| `SearchResponse` | retains `apiName` + `url` (+ name, poster, type, score, …) |
| Multi-provider | `APIHolder.apis` → `APIRepository`; parallel `amap`; cancel via job + generation |
| Partial failure | failed providers skipped; successes kept (`SearchViewModel`) |
| Mobile/TV legacy | `SearchFragment` submits on IME Done (`onQueryTextSubmit`); suggestions debounce 300ms only |
| History | `SEARCH_HISTORY_KEY` writes — **forbidden** in Phase 7 TV Compose |

Phase 7 uses **full `search(query, 1)` on explicit submit**, providers from read-only `DataStoreHelper.searchPreferenceProviders` (fallback: all APIs). No quickSearch, no history writes.

## Details convergence

`TvSearchResult.contentRef` is the same `TvContentRef(url, apiName, title)` Home builds via `TvContentRef.fromMediaItem`. Shell opens the **same** `TvDetailsScreen` / `TvDetailsRepository` — no search-only details path. Mock never appears in search results; items missing url/apiName are dropped by the mapper.

## UX

- TV-native large search field + Search / Clear buttons; IME Done submits.
- Explicit submit (not per-keystroke) — matches production SearchFragment.
- Cancel superseded searches; generation guard against stale overwrite.
- Empty / Error with Retry + Clear — never silent demo.
- Query + result focus preserved across Details round-trip (ViewModel + hoisted `TvSearchFocusState`).
- Lazy grid + Coil 3.3.0 via existing `TvMediaCard`.

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

Prefer `app/.../tv/**` only. Do **not** modify library / plugins / extractors / CS3IPlayer / GeneratorPlayer / ResultFragmentTv / TV XML / Watchlist.
