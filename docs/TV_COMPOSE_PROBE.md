# Compose for TV — Phase 13 Download subtitle-language multi-select

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
  → CloudStreamApp.setKey / getKey                [subs_auto_select, subs_auto_download]
  SAME values the phone SubtitlesFragment / DownloadManager already use.
```

## Architectural Q — TV multi-select writes EXACT same preference representation as phone?

**Yes.** Apply commits `CloudStreamApp.setKey(SUBTITLE_DOWNLOAD_KEY, List<String>)` —
JSON IETF tags under `subs_auto_download`, same as `SubtitlesFragment` /
`getDownloadSubsLanguageTagIETF()`. **No** sync layer, **no** second representation,
**no** new DataStore keys.

## Phase 13 download languages

| Pref | Key | API | TV |
|------|-----|-----|----|
| Download languages | `subs_auto_download` | `getDownloadSubsLanguageTagIETF` / `setKey` JSON `List<String>` IETF | Multi-select under **Downloads** |

- Languages from `SubtitleHelper.languages` (display via `nameNextToFlagEmoji()`).
- Temp selection + Apply commit; Back/Cancel discards (no partial write).
- Empty selection = download no subtitles (real DownloadManager semantics); confirm before save.
- Unknown/legacy tags preserved as Current until the user replaces them.
- No write on dialog open.

## Still skipped

| Pref | Reason |
|------|--------|
| Caption style blob `subtitle_settings` | Fragmented SaveCaptionStyle mapping |
| Chromecast style | Not in-app player |

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

Prefer `app/.../tv/**` only. STOP after Phase 13.
