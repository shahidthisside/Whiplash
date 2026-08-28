pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack: required for NewPipeExtractor (YouTube/YouTube Music extraction provider).
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Whiplash"
include(":app")
