# Fixed personal-testing signing identity

`extension-lab.p12` is intentionally committed at the repository owner's request.
It is a **public private key**, not a secret or a production signing identity.
Anyone with repository access can sign an APK as this app. Do not use this key for
trusted public distribution or for an app that holds sensitive data.

- Format: PKCS12
- Alias: `extensionlab`
- Store/key password: `extension-lab-public-key`
- Application ID: `com.lagradost.cloudstream3.extensionlab`

Keep this exact file for all future updates. Never regenerate it for each run.
Back it up before deleting this repository. CI sets versionCode to
`100000 + github.run_number`, increasing on each new workflow run. Re-running an
old run uses its original versionCode; do not use old artifacts as upgrades.
Local release builds should set BUILD_VERSION_CODE above the installed version.
The separate application ID avoids impersonating/updating official CloudStream.
The previous `.debug` app is a different application and cannot be upgraded by
this release; install Extension Lab once, then update it with future lab releases.

For secure distribution, migrate to a privately backed-up keystore supplied via
Actions Secrets before publishing. Changing keys later normally requires a fresh
installation, so decide on that before distributing broadly.
