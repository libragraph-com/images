// BFR-002 Cross-repo resolution — verbatim from build-tools ConventionCoreFixture.BOOTSTRAP, with one selection line per
// further first-party plugin in the core's own shape (the MUSTr engine and LibRAGraph's lane rows, which the boot checks apply)
pluginManagement {
    // The org channel: github, or the fleet repository on a box electing -Plibragraph.orgChannel=fleet; the orgChannelUrl override wins (BFR-002).
    val fleet = providers.gradleProperty("libragraph.orgChannel").orNull?.trim() == "fleet"
    val org = if (fleet) "fleet" else "github"
    val channel = providers.gradleProperty("libragraph.orgChannelUrl").orNull ?: if (fleet) providers.gradleProperty("libragraph.fleet.url").getOrElse("https://fleet.libragraph.com/releases") else "https://maven.pkg.github.com/libragraph-com/*"
    // A first-party plugin's version: -Plibragraph.<id>, else the newest the records clone names on the org channel (read from disk,
    // no channel metadata query), else the newest on the channel (BFR-002, build-tools#205). The clone is
    // -Plibragraph.recordsClone, else the main worktree's sibling records, a linked worktree included (C-RECORD, build-tools#215).
    val worktree = settingsDir.resolve(".git").takeIf { it.isFile }?.readText()?.substringAfter("gitdir:")?.trim()?.let { settingsDir.resolve(it) }
    val records = providers.gradleProperty("libragraph.recordsClone").orNull?.takeIf { it.isNotBlank() }?.let { settingsDir.resolve(it.trim()) } ?: (worktree?.resolve("commondir")?.takeIf { it.isFile }?.let { worktree.resolve(it.readText().trim()).canonicalFile.parentFile } ?: settingsDir).resolveSibling("records")
    val recorded = { id: String -> records.resolve("records/maven/$id/$id.gradle.plugin").listFiles().orEmpty().filter { it.resolve("$org.json").isFile }.map { it.name }.maxByOrNull { v -> v.split('.').fold(0L) { a, p -> a * 100_000 + (p.toLongOrNull() ?: 0L) } } }
    repositories {
        exclusiveContent {
            forRepositories(
                maven(channel) {
                    isAllowInsecureProtocol = uri(channel).host == "127.0.0.1"
                    credentials {
                        username = providers.gradleProperty(if (fleet) "fleet.user" else "gpr.user").getOrElse("")
                        password = providers.gradleProperty(if (fleet) "fleet.key" else "gpr.key").getOrElse("")
                    }
                },
            )
            filter { includeGroupByRegex("com\\.libragraph(\\..+)?") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "com.libragraph.build-tools") useVersion(providers.gradleProperty("libragraph.core").orNull ?: recorded("com.libragraph.build-tools") ?: "+")
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
