pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // miuix (GitHub Packages) — sama seperti InstallerX-Revived. GitHub Packages
        // butuh autentikasi: set GITHUB_ACTOR + GITHUB_TOKEN (atau gpr.user + gpr.key).
        val gprUser = providers.gradleProperty("gpr.user")
            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
        val gprKey = providers.gradleProperty("gpr.key")
            .orElse(providers.environmentVariable("GITHUB_TOKEN"))
        maven {
            name = "GitHubPackagesMiuix"
            url = uri("https://maven.pkg.github.com/compose-miuix-ui/miuix")
            if (gprUser.isPresent && gprKey.isPresent) {
                credentials {
                    username = gprUser.get()
                    password = gprKey.get()
                }
            }
        }
    }
}
rootProject.name = "AnimeX"
include(":app")
