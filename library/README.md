## CloudStream extension library

This is the official API surface for all CloudStream plugins.

To ensure that all plugins work on both the stable release and pre-release we must have
binary compatibility on all changes. All new changes must be marked with `@Prerelease` to
prevent accidental usage among extension developers.

We use Kotlin binary compatibility validation using:

``./gradlew checkKotlinAbi``

If you for some reason must update the binary compatibility then manually edit `api/jvm/library.api` or use:

``./gradlew updateKotlinAbi``
