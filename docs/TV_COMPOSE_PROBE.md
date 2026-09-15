# Compose for TV — Phase 2 shell (mock)

Structural Compose TV UI with **mock data only**. No APIRepository / player / plugins.

## Architecture

`TvComposeProbeActivity` → `TvTheme` → `TvNavigationShell` → `TvHomeScreen` (hero + rails)

Destinations (visual): Home, Search, Watchlist, Settings. Home is real mock UI; others are placeholders. Phase 1 `TvProbeScreen` remains as a canary under Settings.

## Packages

```
tv/
  TvComposeProbeActivity.kt
  TvTheme.kt
  TvProbeScreen.kt          # Phase 1 canary
  navigation/TvNavigationShell.kt
  home/TvHomeScreen.kt, TvHeroSection.kt, TvContentRail.kt
  components/TvMediaCard.kt, TvFocusScale.kt
  model/TvMockModels.kt
```

## How to open

Not a launcher. Debug builds:

```bash
adb shell am start -n com.lagradost.cloudstream3.debug/com.lagradost.cloudstream3.tv.TvComposeProbeActivity
```

Or **Settings → Updates → Actions → Compose TV (debug)** (`BuildConfig.DEBUG` only).

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
# or
./gradlew :app:assembleStableDebug
```

Ensure `app/src/main/java/com/lagradost/cloudstream3/tv/` has **no** `androidx.compose.material3` imports.
