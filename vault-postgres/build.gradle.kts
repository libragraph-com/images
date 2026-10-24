// C-IMAGES §2 — vault-postgres: a public Dockerfile image on its own counter line (C-BUILD-LOGIC §3b, BFR-004)

import com.libragraph.build_tools.publish.ContainerImagePublish.Channel
import com.libragraph.build_tools.publish.DockerfileImageBuildTask

plugins {
    java
    id("com.libragraph.build-tools")
    id("com.libragraph.shared-mustr")
}

libragraphBuild {
    releasable()
    publishesContainerImage(
        imageName = "vault-postgres",
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

// TASK-IMAGES-035GTH — the boot check (src/checks, BootChecks): the house lanes run it in `gate` over the image this module just built, its
// local reference handed to the fork as `image-boot.image` (IMAGE_BOOT_IMAGE), and the run's counts are the verb's roll-up (C-SHARED-MUSTR §6).
dependencies {
    checksImplementation("com.libragraph.commons:commons-service-container")
}

val image = tasks.named<DockerfileImageBuildTask>("buildDockerfileImage")
tasks.withType<com.libragraph.mustr.gradle.MustrRun>().configureEach {
    doFirst { environment("IMAGE_BOOT_IMAGE", image.get().localReference) }
}
tasks.named("mustrGate") { dependsOn(image) }
