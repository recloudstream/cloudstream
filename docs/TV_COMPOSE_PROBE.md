# Compose for TV — Phase 1 probe

Isolated smoke test for `androidx.tv:tv-material` focus / theme / D-pad behavior.

## Scope

- Activity: `com.lagradost.cloudstream3.tv.TvComposeProbeActivity`
- Theme: `TvTheme` (`androidx.tv.material3.MaterialTheme` only)
- Screen: title + focusable cards/buttons (no Home, catalog, player, or API bridge)

Phone UI and legacy XML TV remain the default launch path (`AccountSelectActivity` MAIN + LEANBACK_LAUNCHER).

## How to open

Build a debug APK, install, then:

```bash
# stableDebug (applicationId com.lagradost.cloudstream3.debug)
adb shell am start -n com.lagradost.cloudstream3.debug/com.lagradost.cloudstream3.tv.TvComposeProbeActivity
```

Release / no debug suffix:

```bash
adb shell am start -n com.lagradost.cloudstream3/.tv.TvComposeProbeActivity
```

The activity is not exported as a launcher; default startup is unchanged.

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
# or
./gradlew :app:assembleStableDebug
```

Ensure `app/src/main/java/com/lagradost/cloudstream3/tv/` has **no** `androidx.compose.material3` imports.
