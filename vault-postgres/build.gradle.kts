// C-IMAGES §2 — vault-postgres: a public Dockerfile image on its own counter line (C-BUILD-LOGIC §3b, BFR-004)

import com.libragraph.build_tools.publish.ContainerImagePublish.Channel

plugins {
    id("com.libragraph.build-tools")
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
