# Extension Lab implementation

The Android launcher is `ExtensionLabActivity`. Its navigation graph exposes only
Home (manual API tester), Extensions (the existing Compose repository manager),
and an installed-extension management subpage. There are exactly two bottom tabs.

## Manual API calls

Select a downloaded provider, method and inputs, then press Run. Calls are not run
automatically. Supported methods: getMainPage (including provider presets), search,
paginated search, quickSearch, load and loadLinks. Pass the exact opaque movie or
episode `data` returned by load into loadLinks, not automatically the content URL.
The result is the provider model as formatted JSON, plus loadLinks callback data,
not a raw HTTP capture. Null results and unsupported-method errors are explicit.
Cancellation and a 120-second coroutine timeout are provided; cancellation of a
non-cooperative third-party blocking call remains dependent on that extension.

## Source cleanup

The former MainActivity, account, media home/search/library/result/player pages,
media downloads, services/receivers, cast/subtitle UI, onboarding and automatic
provider-test screens are physically deleted, along with unused XML layouts,
drawables, menus, strings, styles, desktop sources, APK updater UI, obsolete tests
and upstream release/localization workflows. Their app-level dependencies and
unused version-catalog entries are removed too.

Shared helpers were replaced by narrowly scoped extension runtime code:

- PluginManager loads/unloads provider and extractor classes and preserves the
  existing extension metadata keys and filename mapping, so downloaded extensions
  survive an in-place app update.
- ExtensionTransfer performs cancellable, integrity-checked plugin downloads;
  it does not install APKs or create media download queues.
- InstalledPluginsFragment lists/removes downloaded/local extensions and imports
  local files through the system document picker.
- Extension search and type labels no longer depend on old settings screens.

The `library` module deliberately remains intact: providers/extractors and their
network/parser/crypto utilities are invoked by extensions via runtime linkage,
not necessarily by a static host import. Gson/fuzzywuzzy are likewise retained as
provider compatibility dependencies. Removing these solely because an IDE calls
them unused would break downloaded plugins. Plugins that require deleted media
UI/video-click action APIs are outside this provider-only host's supported scope.

## Checks

`python3 scripts/check-lab-scope.py` guards against restoring legacy packages,
media dependencies, extra navigation tabs or media manifest components. CI runs
this check, focused Kotlin unit tests, signed release compilation and certificate
fingerprint verification. The signature and application ID are unchanged from the
previous Extension Lab release. See `signing/README.md` for public-key limitations.
