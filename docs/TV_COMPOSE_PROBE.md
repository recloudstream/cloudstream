# Compose for TV — Phase 10 CW polish, stale handling, Hero Watch Now

Architecture (unchanged single pipeline):

```
Home / Search / Watchlist / CW / Hero
  → TvContentRef / TvPlaybackRequest
  → TvPlaybackBridge
  → GeneratorPlayer / RepoLinkGenerator / CS3IPlayer (existing)
```

## Architectural Q — single pipeline? New persistence?

**Yes, preserve one pipeline.** No new player path.

**Persistence: NO new keys / DataStore / DB / sync.**  
CW remove only calls existing `DataStoreHelper.removeLastWatched(parentId)`.

## CW mutation API audit

| API | Class | Notes |
|-----|-------|-------|
| `DataStoreHelper.removeLastWatched(parentId)` | **A reusable** | Per-item CW remove (mobile Home uses same) |
| `DataStoreHelper.deleteAllResumeStateIds()` | A (bulk) | Clear-all; not used for per-item TV UI |
| `setLastWatched` | write | Player/history write — not a hide API |
| `deleteBookmarkedData` / favorites / watch state | **B side-effecty** | Library mutations + refresh; not CW remove |
| Soft-hide / dismiss flag | **C none** | Would invent persistence — not implemented |

**Remove UI:** yes (A + TV confirm dialog). **Hide:** no.

## Phase 10 surfaces

1. **CW rail** — valid resume: poster/title/ep/progress/Resume; stale: availability badge, no fake play
2. **Remove** — long-press CW → confirm (Back cancels, focus Cancel) → `removeLastWatched` → refresh CW → neighbor focus
3. **Stale labels** — Available / Unavailable / Provider Missing / Load Failed / Playback Unavailable across Home/Search/Watchlist/Details/CW
4. **Hero Watch Now** — Movie → `TvPlaybackRequest` → bridge; Series/Anime → Phase 9 deterministic resolve only if exact episode hint, else Details; mock never plays
5. **Home state** — real / loading / empty / error / demo explicit; CW refresh never silent-fail→demo

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

Prefer `app/.../tv/**` only. STOP after Phase 10.
