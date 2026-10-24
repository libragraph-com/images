// BFR-002 Cross-repo resolution — verbatim from build-tools ConventionCoreFixture.BOOTSTRAP, with one selection line per
// further first-party plugin in the core's own shape (the MUSTr engine and LibRAGraph's lane rows, which the boot checks apply)
pluginManagement {
    val channel = providers.gradleProperty("libragraph.orgChannelUrl").getOrElse("https://maven.pkg.github.com/libragraph-com/*")
    repositories {
        exclusiveContent {
            forRepositories(
                mavenLocal(),
                maven(channel) {
                    isAllowInsecureProtocol = uri(channel).host == "127.0.0.1"
                    credentials {
                        username = providers.gradleProperty("gpr.user").getOrElse("")
                        password = providers.gradleProperty("gpr.key").getOrElse("")
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
        if (requested.id.id == "com.libragraph.mustr") useVersion(providers.gradleProperty("libragraph.mustr").getOrElse("+"))
        if (requested.id.id == "com.libragraph.shared-mustr") useVersion(providers.gradleProperty("libragraph.shared-mustr").getOrElse("+"))
    }
    buildscript.configurations.configureEach { resolutionStrategy.cacheDynamicVersionsFor(0, "seconds") }
    gradle.beforeProject { buildscript.configurations.configureEach { resolutionStrategy.cacheDynamicVersionsFor(0, "seconds") } }
}
plugins { id("com.libragraph.build-tools") }

rootProject.name = "images"

include(":vault-postgres")
include(":vault-minio")
