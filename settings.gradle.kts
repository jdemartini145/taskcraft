pluginManagement {
    includeBuild("build-logic")
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
    }
}

rootProject.name = "APhid"

include(":app")
include(
    ":core:model",
    ":core:formula-engine",
    ":core:domain",
    ":core:database",
    ":core:data",
    ":core:designsystem",
    ":core:notifications",
    ":core:ai",
)
include(
    ":feature:systems",
    ":feature:formula",
)
