pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// This checkout lives under a OneDrive-synced folder on Windows. OneDrive/Windows Search/
// Defender real-time scanning grab transient file handles on files the moment Gradle creates
// them, which intermittently makes Gradle's own "delete stale output dir before rebuilding"
// step throw AccessDeniedException/IOException mid-build. Routing build output to a local,
// non-synced temp directory avoids that contention entirely. Harmless outside this scenario.
gradle.beforeProject {
    val externalBuildRoot = File(System.getProperty("java.io.tmpdir"), "phone_storage_gradle_build")
    layout.buildDirectory.set(File(externalBuildRoot, if (path == ":") "root" else path.removePrefix(":")))
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PhotoSync"
include(":app")
