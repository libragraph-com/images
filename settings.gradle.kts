// BFR-002 Cross-repo resolution — verbatim from build-tools ConventionCoreFixture.BOOTSTRAP
pluginManagement {
    val channel = providers.gradleProperty("libragraph.orgChannelUrl").getOrElse("https://maven.pkg.github.com/libragraph-com/*")
    repositories {
        exclusiveContent {
            forRepositories(
                mavenLocal(),
                maven(channel) {
                    isAllowInsecureProtocol = uri(channel).host == "127.0.0.1"
                    credentials {
                        username = providers.environmentVariable("GITHUB_ACTOR").orElse(providers.gradleProperty("gpr.user")).getOrElse("")
                        password = providers.environmentVariable("GITHUB_TOKEN").orElse(providers.gradleProperty("gpr.key")).getOrElse("")
                    }
                },
            )
            filter { includeGroupByRegex("com\\.libragraph(\\..+)?") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "com.libragraph.build-tools") useVersion(providers.gradleProperty("libragraph.core").getOrElse("+"))
    }
    buildscript.configurations.configureEach { resolutionStrategy.cacheDynamicVersionsFor(0, "seconds") }
    gradle.beforeProject { buildscript.configurations.configureEach { resolutionStrategy.cacheDynamicVersionsFor(0, "seconds") } }
}
plugins { id("com.libragraph.build-tools") }

rootProject.name = "images"

include(":vault-postgres")
include(":vault-minio")
