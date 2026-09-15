# Compose for TV — Phase 6 Series/Anime episode + season selection

Architecture:

```
LoadResponse → TvDetailsMapper → immutable TvSeason/TvEpisode
  → TvDetailsUiState (seasons, selectedSeason, selectedEpisode)
  → TvEpisodeSelector → TvPlaybackRequest → TvPlaybackBridge
  → RepoLinkGenerator → GeneratorPlayer → CS3IPlayer
```

## Domain episode model (inspected, not invented)

From `library/.../MainAPI.kt`:

| Type | Episodes |
|------|----------|
| `Episode` | `data`, `name?`, `season?`, `episode?`, `posterUrl?`, `score?`, `description?`, `date?`, `runTime?` (season/episode are **Int?** only) |
| `SeasonData` | `season: Int`, `name?`, `displaySeason?` |
| `TvSeriesLoadResponse` | `episodes: List<Episode>`, `seasonNames: List<SeasonData>?` |
| `AnimeLoadResponse` | `episodes: MutableMap<DubStatus, List<Episode>>`, `seasonNames` |
| `DubStatus` | None(-1), Subbed(0), Dubbed(1) |

Specials / missing season: `Episode.season == null` or `0` → TV seasonIndex **0**, label **"No Season"** (mirrors ResultViewModel2 / `R.string.no_season`).

Non-int episodes: **do not exist** in LoadResponse; null `episode` → `(listIndex + 1)`.

## Default episode rule (no resume / DataStore writes)

1. Dub: Subbed if non-empty, else Dubbed, else None, else first group with episodes.
2. Season: lowest `seasonIndex != 0` with episodes; else season 0.
3. Episode: first playable (`data` non-blank) in that season; else first episode.

## Playback

- **Movies**: Watch Now (Phase 5 path unchanged).
- **Series/Anime**: Play Episode → `TvPlaybackRequest.fromEpisode` → bridge builds `ResultEpisode` via `buildResultEpisode` → `RepoLinkGenerator(listOf(ep), page)` → `GeneratorPlayer` in `tv_player_container`.
- **Live / Torrent**: explicit unsupported toast.
- **Mock**: never plays.

## Playback return

GeneratorPlayer is on the Fragment back stack. Back / `exitPlayer` pops it; Compose Details ViewModel keeps dub/season/episode selection. Focus returns to Play Episode CTA (composition FocusRequester).

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

Prefer `app/.../tv/**` only. Do **not** modify library / plugins / extractors / CS3IPlayer / GeneratorPlayer / ResultFragmentTv / TV XML.
