# Compose for TV — Phase 14 Caption-style audit (no UI)

Architecture (unchanged playback pipeline):

```
Home / Search / Watchlist / CW / Hero / Details
  → TvContentRef / TvPlaybackRequest
  → TvPlaybackBridge
  → GeneratorPlayer / RepoLinkGenerator / CS3IPlayer (existing)
```

Settings (unchanged from Phase 13):

```
TvSettingsScreen / TvSettingsAdapter
  → AppSettings (PreferenceData)                  [Phase 11]
  → AndroidPreferenceStore / PreferenceManager    [filter_sub_lang_key, subtitles_encoding_key]
  → CloudStreamApp.setKey / getKey                [subs_auto_select, subs_auto_download]
  SAME values the phone SubtitlesFragment / DownloadManager already use.
```

## Architectural Q — Every exposed caption setting preserves exact persisted representation/semantics/downstream behavior?

**None exposed.** Audit found **zero** caption-style fields that pass the strict 1:1 gate.
Caption styling intentionally remains unavailable on TV Compose Settings.
Phone SubtitlesFragment / SubtitlesScreen continue to own the `subtitle_settings` blob.

## Storage model (inspected)

| Key | Type | R/W API | Consumer |
|-----|------|---------|----------|
| `subtitle_settings` (`SUBTITLE_KEY`) | JSON blob `SaveCaptionStyle` | `Context.saveStyle` / `getCurrentSavedStyle` / `getKey`+`setKey` | `setSubtitleViewStyle` → Media3 `CaptionStyleCompat` + `SubtitleView`; `Cue.Builder.applyStyle`; `CustomSubtitleDecoderFactory` (removeBloat/upperCase) |
| `chome_subtitle_settings` | JSON blob `SaveChromeCaptionStyle` | Chromecast save/get | Chromecast cast path only (not in-app player) |
| System `CaptioningManager` | N/A | **Not used** by app | App always applies own `SaveCaptionStyle`; `setUserDefaultTextSize` is commented out |

Live apply path: `applyStyleEvent` → `PlayerView` / `PlayerSubtitleHelper.setSubStyle`.
No separate PreferenceData / DataStore keys per style field. No style presets beyond long-press reset-to-default on phone.

## Candidate audit (1:1 gate)

Gate (ALL required): existing persisted value · clear meaning · established R/W · app consumes · TV representable without meaning change · same behavior as phone · no new translation/sync layer.

| Candidate | Field / key | Storage | Established R/W | Used by player? | Media3? | System override? | Recreate? | 1:1? | Verdict |
|-----------|-------------|---------|-----------------|-----------------|---------|------------------|-----------|------|---------|
| Size | `fixedTextSize` Float? (sp) | blob field | whole-blob only | yes (`setFixedTextSize`) | via SubtitleView | no (app ignores system size) | no | **no** | Reject — null↔25 UI coercion; Fragment discrete null+6..60 vs Compose slider 5..60; blob RMW |
| Color (fg) | `foregroundColor` Int ARGB | blob | whole-blob | yes → CaptionStyleCompat | yes | no | no | **no** | Reject — free-form color picker forbidden; alpha baked into ARGB |
| Alpha | (no separate key) | part of color Ints | n/a | via colors | yes | no | no | **no** | Reject — not a standalone persisted value |
| Background | `backgroundColor` Int | blob | whole-blob | yes | yes | no | no | **no** | Reject — free-form color; interacts with `backgroundRadius` span path |
| Outline / edge color | `edgeColor` Int | blob | whole-blob | yes | yes | no | no | **no** | Reject — free-form color |
| Shadow / edge type | `edgeType` `@CaptionStyleCompat.EdgeType` Int | blob | whole-blob | yes | yes | no | no | **no** | Reject — blob field RMW = reconstruct fragmented blob; no per-field PreferenceData |
| Edge size | `edgeSize` Float? (px) | blob | whole-blob | yes (`OutlineSpan` in applyStyle) | custom span, not CaptionStyleCompat | no | no | **no** | Reject — null↔0 coercion; Fragment null+1..60 vs Compose 0..60; blob RMW |
| Position / elevation | `elevation` Int (dp) | blob | whole-blob | yes (SubtitleView padding) | no | no | no | **no** | Reject — Fragment discrete 0,10..400 vs Compose slider 0..400 steps=39; blob RMW |
| Alignment / position | `alignment` Int? (SSA) | blob | whole-blob | yes (`setSubtitleAlignment`) | Cue anchors | no | no | **no** | Reject — blob RMW; SSA ints tied to CustomDecoder constants |
| Font | `font` SubtitleFont? + `typefaceFilePath` | blob | whole-blob + file picker | yes → Typeface | yes | no | no | **no** | Reject — font bundling / custom file path; Custom enum triggers picker |
| Bold | `bold` Boolean | blob | whole-blob | yes (StyleSpan in applyStyle) | custom span | no | no | **no** | Reject — no established per-field R/W; would invent blob field RMW + applyStyleEvent wiring |
| Italic | `italic` Boolean | blob | whole-blob | yes | custom span | no | no | **no** | Reject — same as bold |
| Uppercase | `upperCase` Boolean | blob | whole-blob | yes (decoder + preview) | no | no | no | **no** | Reject — blob RMW; decoder-path side effect |
| Remove captions | `removeCaptions` Boolean | blob | whole-blob | yes (applyStyle regex) | no | no | no | **no** | Reject — blob RMW |
| Remove bloat | `removeBloat` Boolean | blob | whole-blob | yes (CustomSubtitleDecoderFactory) | no | no | no | **no** | Reject — blob RMW; decoder-specific |
| Window | `windowColor` Int | blob | whole-blob | yes | yes | no | no | **no** | Reject — free-form color |
| Background radius | `backgroundRadius` Float? | blob | whole-blob | yes (RoundedBackgroundColorSpan) | custom | no | no | **no** | Reject — Fragment null+5..50×5 vs Compose 0..50 steps=9; blob RMW |
| Presets | (none) | n/a | long-press reset only | n/a | n/a | n/a | n/a | **no** | Reject — no persisted preset catalog |
| System caption prefs | CaptioningManager | unused | n/a | app overrides | Media3 has API unused here | would be system | n/a | **no** | Reject — app does not read/write system caption prefs |
| Chromecast style | `chome_subtitle_settings` | separate blob | Chromecast APIs | Chromecast only | n/a | n/a | no | **no** | Reject — not in-app player; Phase DO NOT |

### Why none qualify (summary)

1. **Single fragmented blob** — All style fields live under one `subtitle_settings` JSON object. Phone mutates via in-screen `StatePreferenceStore` then `saveStyle(whole)`. TV would need a new per-field read-modify-write layer over that blob → violates “no new translation/sync layer” and “DO NOT reconstruct fragmented blobs”.
2. **Phone UI already disagrees with itself** on numeric null/default/range/step (Fragment dialogs vs Compose sliders) → cannot claim “same behavior as phone” with one TV control.
3. **Colors** require free-form pickers (forbidden).
4. **Fonts** require bundling / file import (forbidden).
5. **No system-caption override path** exists in-app to mirror.
6. Exposing an empty “Caption Style” category is forbidden; with zero safe fields, **Settings UI is left unchanged**.

## Controls implemented

**None.** No caption-style models, rows, or category additions.

## Runtime / invalid / persistence

- Runtime effect of TV caption UI: N/A (none).
- Invalid-value behavior: N/A.
- Persistence: unchanged; still only phone writes `subtitle_settings`.
- Player code changed: **no**.
- New persistence / DataStore keys: **no**.

## Phase 12–13 prefs still present (unchanged)

| Pref | Key | TV |
|------|-----|----|
| Auto-select language | `subs_auto_select` | Subtitles enum |
| Filter sub lang | `filter_sub_lang_key` | Subtitles toggle |
| Encoding | `subtitles_encoding_key` | Subtitles enum |
| Download languages | `subs_auto_download` | Downloads multi-select |

## Validate

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:assembleStableDebug
```

## Phase 15 recommendation

Do **not** force caption-style TV UI. Prefer adjacent non-style Settings polish, playback/seek prefs already gated, or CW/Hero/Details gaps — still over existing PreferenceData / setKey only. Revisit caption style only if upstream splits `SaveCaptionStyle` into first-class PreferenceData keys with stable ranges (unlikely / out of TV scope).

Prefer `app/.../tv/**` only. STOP after Phase 14.
