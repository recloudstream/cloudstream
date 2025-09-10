plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.buildkonfig.gradle.plugin) apply false // Universal build config
    alias(libs.plugins.dokka.gradle.plugin) apply false
    alias(libs.plugins.gradle.versions.plugin) // do not "apply false" as it's used only in the root project
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

// Task to list dependencies updates // https://github.com/ben-manes/gradle-versions-plugin
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}

fun isNonStable(version: String): Boolean {
    var containsUnstableKeyword = listOf("alpha", "beta", "rc", "cr", "m", "preview", "snapshot").any {
        version.contains(it, ignoreCase = true)
    }
    val containsStableKeyword = listOf("release", "final", "ga").any {
        version.contains(it, ignoreCase = true)
    }
    val matchesGitCommitRegex = Regex("""^([0-9a-f]{40}|[0-9a-f]{6,8})$""").matches(version)
    val matchesStableRegex = Regex("""^[0-9,.v-]+(-r)?$""").matches(version)
    val isStable = containsStableKeyword || matchesStableRegex || matchesGitCommitRegex
    return containsUnstableKeyword || !isStable
}