// C-IMAGES §2 — vault-minio: MinIO and mc built from pinned source into a public Dockerfile image on its own counter line (C-BUILD-LOGIC §3b, BFR-004)

import com.libragraph.build_tools.publish.ContainerImagePublish.Channel
import com.libragraph.build_tools.publish.DockerfileImageBuildTask
import java.time.Duration

plugins {
    id("com.libragraph.build-tools")
}

libragraphBuild {
    releasable()
    publishesContainerImage(
        imageName = "vault-minio",
        containerChannels = setOf(Channel.LOCAL, Channel.GLOBAL),
        dockerfile = file("Dockerfile"),
        platforms = listOf("linux/amd64", "linux/arm64"),
    )
    emitProbe(
        builtBy = tasks.named("buildDockerfileImage"),
        command = listOf(
            "sh", "-c",
            "docker image inspect --format '{{index .Config.Labels \"org.opencontainers.image.version\"}}' " +
                "\$(cat build/libragraph/dockerfile-image.digest)",
        ),
    )
}

// BFR-004 — every git source the Dockerfile ADDs is named by a 40-hex commit sha and carries an equal --checksum, which
// BuildKit verifies against the clone's head. `${NAME}` references resolve through the Dockerfile's own ARG defaults.
fun unpinnedSources(dockerfile: String): List<String> {
    val args = mutableMapOf<String, String>()
    val problems = mutableListOf<String>()
    fun resolve(text: String) = Regex("""\$\{(\w+)}|\$(\w+)""").replace(text) { m -> args[m.groupValues[1].ifEmpty { m.groupValues[2] }] ?: m.value }
    dockerfile.lines().forEachIndexed { index, line ->
        val words = line.trim().split(Regex("\\s+"))
        when (words.firstOrNull()?.uppercase()) {
            "ARG" -> words.drop(1).filter { '=' in it }.forEach { args[it.substringBefore('=')] = it.substringAfter('=').trim('"') }
            "ADD" -> {
                val resolved = words.drop(1).map { resolve(it) }
                val source = resolved.firstOrNull { !it.startsWith("--") } ?: return@forEachIndexed
                if (!source.contains(".git")) return@forEachIndexed
                val ref = source.substringAfter('#', "")
                val checksum = resolved.firstOrNull { it.startsWith("--checksum=") }?.substringAfter('=')
                when {
                    !Regex("[0-9a-f]{40}").matches(ref) -> problems += "line ${index + 1}: ADD $source names no 40-hex commit sha after '#' (got '$ref')"
                    checksum != ref -> problems += "line ${index + 1}: ADD $source carries --checksum=$checksum, not the pinned sha $ref"
                }
            }
        }
    }
    return problems
}

val assertSourcePinned = tasks.register("assertSourcePinned") {
    group = "verification"
    description = "Fail naming the line of any ADD git source without a 40-hex sha or with a --checksum that differs from it (TASK-IMAGES-022VXK AC1)."
    val dockerfile = layout.projectDirectory.file("Dockerfile")
    inputs.file(dockerfile)
    doLast {
        val text = dockerfile.asFile.readText()
        val real = unpinnedSources(text)
        check(real.isEmpty()) { "vault-minio/Dockerfile has an unpinned source:\n" + real.joinToString("\n") }
        check(Regex("""(?m)^ADD .*\.git#""").findAll(text).count() == 2) { "vault-minio/Dockerfile must ADD exactly the two git sources, minio and mc" }
        check(Regex("(?im)apt-get|\\bapt\\b|:latest").find(text) == null) { "vault-minio/Dockerfile installs nothing and names no floating reference" }
        val byTag = unpinnedSources(text.replace(".git#\${MINIO_COMMIT}", ".git#\${MINIO_TAG}"))
        check(byTag.isNotEmpty()) { "negative control: an ADD naming a tag was accepted" }
        val byChecksum = unpinnedSources(text.replace("--checksum=\${MC_COMMIT}", "--checksum=\${MINIO_COMMIT}"))
        check(byChecksum.isNotEmpty()) { "negative control: an ADD whose --checksum differs from its sha was accepted" }
        logger.lifecycle("assertSourcePinned: the Dockerfile's sources are pinned; negative controls refused: ${byTag.first()} | ${byChecksum.first()}")
    }
}

// TASK-IMAGES-022VXK AC3, AC4, AC6 — the built image run as the dev vault's driver runs it, under a wall-clock cap (NFR-203).
val assertObjectStoreStarts = tasks.register<Exec>("assertObjectStoreStarts") {
    group = "verification"
    description = "Start the built vault-minio image as the cmdb driver does, read /minio/health/live, run the first-run reset's mc commands and compare the licence files with the pinned commits."
    val build = tasks.named<DockerfileImageBuildTask>("buildDockerfileImage")
    dependsOn(build)
    val script = layout.projectDirectory.file("check-start.sh")
    val dockerfile = layout.projectDirectory.file("Dockerfile")
    inputs.files(script, dockerfile)
    outputs.upToDateWhen { false }
    timeout.set(Duration.ofSeconds(90))
    doFirst {
        val tag = Regex("ARG MINIO_TAG=(\\S+)").find(dockerfile.asFile.readText())!!.groupValues[1]
        val minioCommit = Regex("ARG MINIO_COMMIT=(\\S+)").find(dockerfile.asFile.readText())!!.groupValues[1]
        val mcCommit = Regex("ARG MC_COMMIT=(\\S+)").find(dockerfile.asFile.readText())!!.groupValues[1]
        commandLine(
            script.asFile.absolutePath, build.get().localReference, tag, minioCommit, mcCommit,
            "https://github.com/minio/minio.git", "https://github.com/minio/mc.git",
        )
    }
}

libragraphBuild {
    gateCheck(assertSourcePinned)
    gateCheck(assertObjectStoreStarts)
}
