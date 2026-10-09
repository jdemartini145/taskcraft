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
include(":baselineprofile")
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
    ":feature:log",
    ":feature:diagnosis",
    ":feature:alerts",
    ":feature:crops",
    ":feature:pests",
    ":feature:shopping",
    ":feature:settings",
    ":feature:sensors",
)
