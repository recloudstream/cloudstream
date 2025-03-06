plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "CloudStream"

include(":app")
include(":library")
include(":docs")