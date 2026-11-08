// BFR-002 Cross-repo resolution — verbatim from build-tools resource settings-bootstrap.txt (v7), with one selection line per
// further first-party plugin by the root election (the MUSTr engine and LibRAGraph's lane rows, which the checks apply)
pluginManagement {
    // Two channels (BFR-002): a dev floor resolves from fleet, beta, rc and stable from github (a LAN box's per-account init points it at
    // fleet's proxy), an exact version from github if held there, else fleet; each first-party plugin is requested of its floor's channel
    // only, the core by its own election and every other plugin by the root default. No box setting names a channel: where each lives is
    // a location (libragraph.fleet.url, libragraph.github.url).
    // A first-party plugin's version: the core the loaded core handed over (libragraph.internal.<name>), else the election in
    // gradle/elections.properties (most specific line; no file or no line: dev) among the versions the records clone names on that floor's
    // channel (read from disk, no channel metadata query), a floor above beta narrowed to those the pm clone's 12-releases record grades
    // at least that, an exact version itself, else the newest on the channel (BFR-002). The records clone is
    // -Plibragraph.recordsClone, else the main worktree's sibling records, a linked worktree included (C-RECORD, build-tools#215);
    // the pm clone is -Plibragraph.pmClone, else the records clone's sibling pm.
    val worktree = settingsDir.resolve(".git").takeIf { it.isFile }?.readText()?.substringAfter("gitdir:")?.trim()?.let { settingsDir.resolve(it) }
    val records = providers.gradleProperty("libragraph.recordsClone").orNull?.takeIf { it.isNotBlank() }?.let { settingsDir.resolve(it.trim()) } ?: (worktree?.resolve("commondir")?.takeIf { it.isFile }?.let { worktree.resolve(it.readText().trim()).canonicalFile.parentFile } ?: settingsDir).resolveSibling("records")
    val pm = providers.gradleProperty("libragraph.pmClone").orNull?.takeIf { it.isNotBlank() }?.let { settingsDir.resolve(it.trim()) } ?: records.resolveSibling("pm")
    val elected = settingsDir.resolve("gradle/elections.properties").takeIf { it.isFile }?.readLines().orEmpty().map { it.trim() }.filter { "=" in it && !it.startsWith("#") && !it.startsWith("!") }.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    val graded = { coordinate: String, floor: String -> pm.resolve("12-releases/${coordinate.substringAfter(':')}.yaml").takeIf { it.isFile }?.readLines().orEmpty().filter { listOf("beta", "rc", "stable").indexOf(it.substringAfter(':').trim()) >= listOf("beta", "rc", "stable").indexOf(floor) }.map { it.substringBefore(':').trim() } }
    val floor = { coordinate: String -> elected[coordinate] ?: elected[coordinate.substringBefore(':') + ":*"] ?: elected["*"] ?: "dev" }
    val recorded = { id: String, coordinate: String -> floor(coordinate).let { floor -> if (floor.first().isDigit()) floor else records.resolve("records/maven/$id/$id.gradle.plugin").listFiles().orEmpty().filter { it.resolve(if (floor == "dev") "fleet.json" else "github.json").isFile }.map { it.name }.filter { floor !in listOf("rc", "stable") || it in graded(coordinate, floor) }.maxByOrNull { v -> v.split('.').fold(0L) { a, p -> a * 100_000 + (p.toLongOrNull() ?: 0L) } } } }
    val channels = { floor: String -> if (floor == "dev") listOf("fleet") else if (floor.first().isDigit()) listOf("github", "fleet") else listOf("github") }
    val core = channels(floor("com.libragraph:build-tools"))
    val rest = channels(elected["*"] ?: "dev")
    // A channel's credential is its own pair, both set, or none: an empty credential is never sent, and a first-party plugin whose every
    // remote channel lacks its pair is refused before any request, naming the properties (BFR-018).
    val pair = { user: String, key: String -> providers.gradleProperty(user).orNull?.takeIf { it.isNotEmpty() }?.let { u -> providers.gradleProperty(key).orNull?.takeIf { it.isNotEmpty() }?.let { u to it } } }
    val keys = mapOf("github" to ("gpr.user" to "gpr.key"), "fleet" to ("fleet.user" to "fleet.key"))
    val channel = { id: String, url: String, user: String, key: String -> repositories.maven(url) { name = id; isAllowInsecureProtocol = uri(url).host == "127.0.0.1"; pair(user, key)?.let { (u, k) -> credentials { username = u; password = k } }; content { if (id !in core) { excludeModule("com.libragraph", "build-tools"); excludeGroup("com.libragraph.build-tools") }; if (id !in rest) { excludeModuleByRegex("com\\.libragraph", "(?!build-tools$).*"); excludeGroupByRegex("com\\.libragraph\\.(?!build-tools$).+") } } } }
    repositories {
        exclusiveContent {
            forRepositories(
                channel("github", providers.gradleProperty("libragraph.github.url").getOrElse("https://maven.pkg.github.com/libragraph-com/*"), "gpr.user", "gpr.key"),
                channel("fleet", providers.gradleProperty("libragraph.fleet.url").getOrElse("https://fleet.libragraph.com/releases"), "fleet.user", "fleet.key"),
            )
            filter { includeGroupByRegex("com\\.libragraph(\\..+)?") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy.eachPlugin {
        val via = if (requested.id.id == "com.libragraph.build-tools") core else if (requested.id.id.startsWith("com.libragraph.")) rest else emptyList()
        if (via.isNotEmpty() && via.none { (repositories.getByName(it) as MavenArtifactRepository).url.scheme !in listOf("http", "https") || keys.getValue(it).let { (u, k) -> pair(u, k) != null } }) throw GradleException("settings bootstrap: plugin '${requested.id.id}' resolves from ${via.joinToString(" or ")}, and no credential is set for it: set ${via.joinToString(" or ") { keys.getValue(it).let { (u, k) -> "$u and $k" } }} as Gradle properties (BFR-018). An empty credential is never sent.")
        if (requested.id.id == "com.libragraph.build-tools") useVersion(providers.gradleProperty("libragraph.internal.core").orNull ?: recorded("com.libragraph.build-tools", "com.libragraph:build-tools") ?: "+")
    }
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "com.libragraph.mustr") useVersion(recorded("com.libragraph.mustr", "com.libragraph.mustr:com.libragraph.mustr.gradle.plugin") ?: "+")
        if (requested.id.id == "com.libragraph.shared-mustr") useVersion(recorded("com.libragraph.shared-mustr", "com.libragraph.shared-mustr:com.libragraph.shared-mustr.gradle.plugin") ?: "+")
    }
    buildscript.configurations.configureEach { resolutionStrategy.cacheDynamicVersionsFor(0, "seconds") }
    gradle.beforeProject { buildscript.configurations.configureEach { resolutionStrategy.cacheDynamicVersionsFor(0, "seconds") } }
}
plugins { id("com.libragraph.build-tools") }

rootProject.name = "images"

include(":vault-postgres")
include(":vault-minio")
