# Compose for TV — Phase 3 Home catalog bridge

Read-only Home catalog via thin bridge. No Search / Watchlist / History / Details / Player.

## Architecture

```
APIRepository → TvHomeRepository → immutable TV models → TvHomeViewModel (StateContainer)
  → TvHomeUiState → TvHomeScreen
```

`TvComposeProbeActivity` → `TvTheme` → `TvNavigationShell` → `TvHomeScreen`

## Packages

```
tv/
  TvComposeProbeActivity.kt
  TvTheme.kt
  TvProbeScreen.kt
  navigation/TvNavigationShell.kt
  home/TvHomeScreen.kt, TvHeroSection.kt, TvContentRail.kt, TvHomeViewModel.kt
  components/TvMediaCard.kt, TvFocusScale.kt
  model/TvHomeModels.kt, TvMockCatalog.kt
  data/TvMediaMapper.kt, TvHomeRepository.kt
```

## Rails honesty

| Rail | Source |
|------|--------|
| Continue Watching | **Mock** — resume history needs DataStore / download-header cache (can write); Phase 3 forbids persistence touches |
| Trending / Movies / Anime | **Real** via `APIRepository.getMainPage(1)` when a homepage provider exists; else mock slot or explicit demo fallback |
| Hero | First suitable real item (poster as backdrop); else explicit mock hero |

## States

`Loading` → `Content` | `Empty` | `Error` with D-pad **Retry** and explicit **Load demo catalog** (never silent fake success).

## How to open

```bash
adb shell am start -n com.lagradost.cloudstream3.debug/com.lagradost.cloudstream3.tv.TvComposeProbeActivity
```

Or **Settings → Updates → Actions → Compose TV (debug)** (`BuildConfig.DEBUG` only).

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

Ensure `app/src/main/java/com/lagradost/cloudstream3/tv/` has **no** `androidx.compose.material3` imports.
