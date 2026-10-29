pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor is published on JitPack only.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Vortexa"

include(":app")

// Core
include(":core:common")
include(":core:ui")
include(":core:database")
include(":core:media-api")
include(":core:playback")
include(":core:online")

// Features
include(":feature:downloader")
include(":feature:browser")
include(":feature:video")
include(":feature:music")
include(":feature:vault")
include(":feature:settings")
include(":feature:onlinevideo")
