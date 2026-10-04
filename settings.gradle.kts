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
    }
}

rootProject.name = "Maffinet"
include(":app")
// Isolated P01 experiment. Never linked into the shipped application.
if (providers.gradleProperty("maffinet.transportLab").orNull == "true") {
    include(":transport-lab", ":traffic-helper")
    project(":transport-lab").projectDir = file("lab/transport")
    project(":traffic-helper").projectDir = file("lab/helper")
}
