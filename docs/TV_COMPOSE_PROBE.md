# Compose for TV — Phase 8 Read-only Continue Watching + Watchlist/Library

Architecture:

```
Home Continue Watching
  → TvContinueWatchingRepository (read-only DataStore + header cache)
  → TvContinueWatchingItem → TvMediaItem / TvContentRef
  → TvDetailsScreen (Details first, not direct play)

Watchlist destination
  → TvWatchlistRepository (Local list sources only)
  → sections (Watching / Completed / On-Hold / Dropped / Plan to Watch / Favorites)
  → TvWatchlistItem → same TvDetailsScreen
```

## Architectural Q — pure read-only adapters?

**Yes** for both CW and Watchlist, with a custom CW path.

### Continue Watching — exact read-only APIs

| API | Role |
|-----|------|
| `DataStoreHelper.getAllResumeStateIds()` | list parent ids |
| `DataStoreHelper.getLastWatched(id)` | resume meta |
| `getKey(DOWNLOAD_HEADER_CACHE, parentId)` | name/url/apiName/poster/type |
| `getKey(DOWNLOAD_HEADER_CACHE_BACKUP, parentId)` | **read only** fallback if primary missing |
| `DataStoreHelper.getViewPos(episodeId)` | real progress only |

**Blocked write path (not used):** `HomeViewModel.getResumeWatching()` can `setKey(DOWNLOAD_HEADER_CACHE, …)` when restoring from backup. Phase 8 does **not** call it and does **not** modify persistence to “fix” that.

Empty CW → omit rail on real Home (never mix demo CW into live catalog). Full mock fallback still shows explicit demo CW.

### Watchlist / Library — exact read-only APIs

Same sources as `LocalList.library()`:

| API | Role |
|-----|------|
| `getAllWatchStateIds()` + `getResultWatchState(id)` | WatchType buckets |
| `getBookmarkedData(id)` | bookmark card fields |
| `getAllFavorites()` | Favorites section |
| `getCurrentAccount()` / `currentAccount` | profile label only |

Does **not** use SyncRepo / MAL / AniList / Simkl / Kitsu (auth + network), does **not** write `LAST_SYNC_API_KEY` or `librarySortingMode`. No remove/edit. No auth UI — remote sync simply omitted.

## UX

- CW + Watchlist cards open shared Details; stale url/apiName → Details error OK.
- Load on enter / Activity resume; no polling; no DataStore reads on recomposition.
- Lazy rows/columns; focus memory hoisted like Search/Home.

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

Prefer `app/.../tv/**` only. No new persistence / DataStore keys / DB / sync.
