# Extension Lab

The launcher is now `ExtensionLabActivity`, not the media app/account selector.
Only two bottom tabs are exposed:

- **Home:** choose an installed provider, select a method, enter inputs, press Run.
  Supports getMainPage (including provider presets), search, paginated search,
  quickSearch, load, and loadLinks. `loadLinks.data` is the exact opaque value from
  load's movie/episode response, not automatically the page URL. Streams and
  subtitles are displayed as data only. Null responses, exceptions and unsupported
  methods are shown explicitly. Calls have a 120-second timeout and Cancel button.
- **Extensions:** the existing Compose repository/extension manager, including
  installation, removal, updates and the installed-plugin list. Refresh providers
  on Home after adding/removing an extension. The old automatic-test shortcut now
  routes to the manual tester.

The app has no registered media-player activities, account selector, media deep
links, casting metadata or video download services. Its dedicated navigation graph
contains only the lab and extension destinations. Legacy source/resources shared
with extension APIs are **retained for compatibility**; this is not a wholesale
source-code deletion or a claim that the APK contains no media-related classes.
A future extraction to a standalone extension-host module would remove those
transitive implementation dependencies safely.

The JSON shown is the provider method's returned model (and loadLinks callbacks),
not a raw HTTP capture. No provider requests are run automatically on this page.
Extensions execute third-party code: only install sources you trust. Responses
may contain signed URLs/tokens; take care before copying/sharing them.

## Builds and updates

See `signing/README.md` for the deliberately public testing key and its limitations.
CI builds `stableRelease`, runs focused runner tests, verifies APK signatures and
checks the fixed certificate fingerprint before uploading the APK + signing report.
The extension-lab application ID is independent of official CloudStream/debug apps.
