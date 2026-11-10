# Extension Lab

Android host for installing/managing CloudStream-compatible provider extensions
and manually calling their APIs. There is no media browser or player.

## Two tabs

- **Home:** select an installed provider and a method, enter the required values,
  then run it and inspect the returned JSON or error. Supports `getMainPage`,
  `search`, paginated search, `quickSearch`, `load`, and `loadLinks`. Stream URLs,
  headers and subtitles are displayed as data, never played.
- **Extensions:** add repositories; download, update and remove extensions; inspect
  installed extensions or import a local `.cs3`/`.zip` provider extension.

## Build

GitHub Actions workflow **Build Android APK** builds a signed `stableRelease` APK,
executes focused unit tests and checks its fixed signing certificate. Download the
`extension-lab-release-*` artifact from a successful workflow run.

Local build (JDK 17 and Android SDK platform 37.0 required):

```sh
./gradlew :app:testStableDebugUnitTest :app:assembleStableRelease
```

For an update over a CI-installed APK, set `BUILD_VERSION_CODE` to a number above
its installed version code. See [signing/README.md](signing/README.md).

**The committed signing key is deliberately public, for personal testing only.**
It is not a secure production signing identity. Never regenerate it per build.

## Structure

- `app`: extension-only Android host, repository manager and manual tester.
- `library`: provider/extractor API, parsers, networking helpers and shared models.
  This is retained because downloaded extensions call these APIs dynamically.
- `shared`: Compose extension UI support, theme, settings store and state handling.

Legacy playback, accounts, browsing, media downloads, casting, app-update UI,
onboarding, automatic provider-test UI, desktop app and upstream publishing
workflows have been removed. Provider/extractor plugins are supported; plugins
requiring the removed media-app UI or video-click action APIs are not.

Only install extensions you trust: they execute code in the app. Responses may
contain private URLs/tokens; take care when copying or sharing them.

Based on CloudStream. Original contributor credits and GPL-3.0 licensing are
preserved in the source history and [LICENSE](LICENSE).

[Details and validation](docs/EXTENSION-LAB.md)
