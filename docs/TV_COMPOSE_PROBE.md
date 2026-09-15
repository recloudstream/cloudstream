# Compose for TV — Phase 11 Settings over existing preferences

Architecture (unchanged playback pipeline):

```
Home / Search / Watchlist / CW / Hero / Details
  → TvContentRef / TvPlaybackRequest
  → TvPlaybackBridge
  → GeneratorPlayer / RepoLinkGenerator / CS3IPlayer (existing)
```

Settings:

```
TvSettingsScreen / TvSettingsAdapter
  → AppSettings (PreferenceData)
  → AndroidPreferenceStore
  → PreferenceManager.getDefaultSharedPreferences  (SAME as phone)
```

## Architectural Q — same preference system?

**Yes.** TV Settings uses `AppSettings` / `PreferenceData.set|get` only.  
**No** second prefs store, **no** new DataStore keys, **no** parallel SharedPreferences file.

## Audit (safe-for-TV vs excluded)

| Class | Examples | TV Phase 11 |
|-------|----------|-------------|
| Safe-for-TV | autoplay, skip OP, episode sync, TV seek, show clock, DNS, downloads counts | Exposed |
| Restart-required | app locale | Exposed + confirm → `activity.recreate()` |
| Account | local profile name, skip account select | Read-only name / existing bool only |
| Mobile-only | gestures, PiP, rotate, brightness, battery opt, biometric | Excluded |
| Plugin / OAuth / backup file pickers / debug logcat | providers, MAL login, backup path | Excluded |
| Phase 2 DEBUG | focus probe | Kept behind `BuildConfig.DEBUG` action |

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

Prefer `app/.../tv/**` only. STOP after Phase 11.
