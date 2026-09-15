# Compose for TV — Phase 4 Details (load bridge, no player)

Home → compact content identity → `APIRepository.load` → `TvDetailsRepository` → immutable `TvDetailsUiState` → `TvDetailsScreen` → `onWatchNow` stub.

## Architecture

```
SearchResponse / TvMediaItem → TvContentRef (url + apiName)
  → TvDetailsRepository (APIHolder + SyncRedirector + APIRepository.load)
  → TvDetailsMapper → TvDetailsContent
  → TvDetailsViewModel (StateContainer) → TvDetailsUiState
  → TvDetailsScreen
```

`TvComposeProbeActivity` → `TvTheme` → `TvNavigationShell` (Details overlay via saveable url/apiName strings) → Home / Details

## Packages

```
tv/
  details/TvDetailsScreen.kt, TvDetailsViewModel.kt
  data/TvDetailsRepository.kt, TvDetailsMapper.kt  (+ Phase 3 Home bridge)
  model/TvDetailsModels.kt (TvContentRef, TvDetailsContent, UiState)
  home/… (cards + hero Details → openDetails)
  navigation/TvNavigationShell.kt
```

## Load flow (documented, not invented)

Mirrors `ResultViewModel2.load` without DataStore / trailer / player side effects:

1. Identity: `url` + `apiName` (same as ResultFragment bundles from SearchResponse)
2. Resolve API: `getApiFromNameNull` ?: `getApiFromUrlNull`
3. `SyncRedirector.redirect(url, api)`
4. `APIRepository(api).load(validUrl)` → `Resource<LoadResponse>`
5. Map Movie / TvSeries / Anime / LiveStream / Torrent / Other

Mock / demo Home items never call load (no fake IDs).

## States

`Loading` → `Content` | `Error` with D-pad **Retry** and **Back**. Back restores Home focus via `TvHomeFocusState`.

Watch Now = stub only — no GeneratorPlayer / CS3IPlayer / Media3.

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
