# Compose for TV — Phase 5 Watch Now → existing playback

Movies: Details Watch Now → immutable `TvPlaybackRequest` → Activity callback → `TvPlaybackBridge` → existing `GeneratorPlayer` / `RepoLinkGenerator` / `CS3IPlayer` / Media3.

**No** custom player UI, extractors, providers, or Media3 config changes.

## Exact existing playback path (traced, not invented)

```
ResultFragmentTv.resultPlayMovieButton
  → ResultViewModel2.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, ep))
  → getPlayerAction(ctx) → typically ACTION_PLAY_EPISODE_IN_PLAYER
  → generator = RepoLinkGenerator(listOf(movieEpisode), page = currentResponse)
  → activity.navigate(R.id.global_to_navigation_player,
        GeneratorPlayer.newInstance(generator, index, syncData))
  → GeneratorPlayer reads uuid from companion generators map
  → PlayerGeneratorViewModel.attachGenerator + loadLinks()
  → RepoLinkGenerator.generateLinks → APIRepository.loadLinks → extractors
  → CS3IPlayer / Media3
```

Movie `ResultEpisode` is built from `MovieLoadResponse` via `buildResultEpisode` (id = `LoadResponse.getId()`, data = `dataUrl`).

Compose TV Phase 5 reuses the same generator + `GeneratorPlayer.newInstance` entry; Fragment is hosted in `R.id.tv_player_container` (AppCompatActivity) instead of MainActivity NavHost. `exitPlayer` → `popCurrentPage` → `onBackPressed` pops the back stack.

## Phase 5 wiring

```
TvDetailsScreen Watch Now
  → TvPlaybackRequest(url, apiName, title, variantLabel)  // no LoadResponse/Activity in UiState
  → TvComposeProbeActivity.onPlaybackRequest
  → TvPlaybackBridge.launch
       · reject mock / comingSoon / non-Movie (clear reason)
       · API resolve + SyncRedirector + APIRepository.load (same as details)
       · MovieLoadResponse → buildResultEpisode → RepoLinkGenerator
       · GeneratorPlayer.newInstance → FragmentTransaction(tv_player_container)
```

## Unsupported (Phase 5)

| Variant   | Behavior                                      |
|-----------|-----------------------------------------------|
| Movie     | Watch Now enabled → existing path             |
| TvSeries  | Watch Now disabled — episode picker Phase 6   |
| Anime     | Watch Now disabled — episode/dub Phase 6      |
| LiveStream| Watch Now disabled — deferred Phase 6         |
| Torrent   | Watch Now disabled — deferred Phase 6         |
| Mock/demo | Never launches real playback                  |

## Packages

```
tv/
  playback/TvPlaybackBridge.kt
  model/TvPlaybackModels.kt
  TvComposeProbeActivity.kt   # AppCompat + activity_tv_compose_probe.xml
  details/…  home/…  navigation/…
```

## How to open

```bash
adb shell am start -n com.lagradost.cloudstream3.debug/com.lagradost.cloudstream3.tv.TvComposeProbeActivity
```

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

Ensure `tv/` has **no** `androidx.compose.material3` imports. Do **not** modify CS3IPlayer / GeneratorPlayer / extractors / library.
