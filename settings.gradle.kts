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
        maven { url = uri(rootDir.resolve(".gradle-local-repo")) }
        google()
        mavenCentral()
    }
}

rootProject.name = "Refocus"
include(":app")
