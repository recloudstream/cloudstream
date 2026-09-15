# Compose for TV — Phase 12 Subtitle preferences in TvSettingsScreen

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
  → AppSettings (PreferenceData)                  [Phase 11]
  → AndroidPreferenceStore / PreferenceManager    [filter_sub_lang_key, subtitles_encoding_key]
  → CloudStreamApp.setKey / getKey                [subs_auto_select]
  SAME values the phone SubtitlesFragment / GeneratorPlayer already use.
```

## Architectural Q — same subtitle preference values/behavior?

**Yes.** TV Subtitles rows write the existing keys through the existing APIs.  
**No** TV-specific subtitle config path, **no** second prefs system, **no** new DataStore keys.

## Exposed vs skipped

| Pref | Key | TV Phase 12 |
|------|-----|-------------|
| Auto-select language | `subs_auto_select` (setKey JSON IETF / `""` none) | Exposed |
| Encoding | `subtitles_encoding_key` (PreferenceManager string) | Exposed |
| Filter by preferred media language | `filter_sub_lang_key` (PreferenceManager bool) | Exposed |
| Download languages | `subs_auto_download` (List/set IETF, multi-select) | Skipped — no TV multi-select |
| Caption style blob | `subtitle_settings` (SaveCaptionStyle JSON) | Skipped — fragmented style mapping |
| Chromecast style | `chome_subtitle_settings` | Skipped — Chromecast / not in-app player |

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

Prefer `app/.../tv/**` only. STOP after Phase 12.
