# Compose for TV — Phase 9 Continue Watching true Resume

Architecture:

```
Continue Watching (read-only Phase 8 fields)
  → classify A / B / C
  A Movie: TvPlaybackRequest(variantLabel=Movie) → TvPlaybackBridge → GeneratorPlayer
  B Series/Anime + exact S/E or episodeId:
      TvDetailsRepository.load → match TvEpisode → fromEpisode → same bridge
      else → shared TvDetailsScreen (restore season/episode if possible)
  C: clear unavailable — never play
Progress UI is display-only (PosDur). Player remains position owner.
```

## Architectural Q — direct resume without second persistence?

**Movies (A): Yes.** Exact path:

`TvContinueWatchingItem { url, apiName, title, typeLabel=Movie }`
→ `TvPlaybackRequest(url, apiName, title, variantLabel="Movie")`
→ `TvPlaybackBridge.launch` → `APIRepository.load` → `MovieLoadResponse` → `RepoLinkGenerator` → `GeneratorPlayer`
(existing player seeks via PosDur / episode id — Compose does not seek)

**Series/Anime: Not from CW fields alone.** Precise missing data: **`Episode.data`** (playable payload) plus full episode metadata required by `TvPlaybackRequest.fromEpisode` / bridge. CW only has `season?`, `episode?`, `episodeId?`, `parentId?` + header identity.

Exact path when S/E or episodeId present (still no new persistence):

`TvContentRef` → **same** `TvDetailsRepository.load` as Details → match episode by `episodeId` else season+number → `TvPlaybackRequest.fromEpisode` → existing bridge.

Do **not** fix the gap via DataStore writes, PosDur writers, or a second resume store.

## Phase 8 resume fields (audit)

| Field | Source |
|-------|--------|
| title, url, apiName, posterUrl, typeLabel | `DOWNLOAD_HEADER_CACHE` (+ backup read-only) |
| episode, season, parentId, episodeId, updateTime | `ResumeWatching` / `getLastWatched` |
| progressFraction | `getViewPos(episodeId)` when duration > 0 |

## Classification

| Class | Criteria |
|-------|----------|
| **A Direct** | Non-blank url+apiName+title and `typeLabel == "Movie"` |
| **B After Details** | Series/Anime/Cartoon/AsianDrama/OVA (or other non-blocked types) with identity; restore/resolve when S/E or episodeId present |
| **C Unsafe** | Mock; Live; Torrent; missing identity |

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

Prefer `app/.../tv/**` only. No new persistence / DataStore keys / player / library changes.
