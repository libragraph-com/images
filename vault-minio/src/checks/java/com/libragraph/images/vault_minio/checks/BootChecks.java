package com.libragraph.images.vault_minio.checks;

import static com.libragraph.mustr.plan.Category.RELIABILITY;
import static com.libragraph.mustr.plan.Level.SYSTEM;
import static com.libragraph.mustr.plan.Speed.FAST;

import com.libragraph.commons.container.ContainerDefinition;
import com.libragraph.commons.container.ContainerRuntime;
import com.libragraph.commons.service.DependsOn;
import com.libragraph.commons.service.EdgeKind;
import com.libragraph.commons.service.container.ContainerRuntimeOperations;
import com.libragraph.commons.service.container.ContainerRuntimeService;
import com.libragraph.mustr.annotation.Category;
import com.libragraph.mustr.annotation.Check;
import com.libragraph.mustr.annotation.CheckBudget;
import com.libragraph.mustr.annotation.Level;
import com.libragraph.mustr.annotation.Speed;
import com.libragraph.mustr.annotation.UsesConfig;
import com.libragraph.mustr.resource.TestContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TASK-IMAGES-035GTH·AC1·AC3 — the built vault-minio image boots: a container of it started as {@code server /data} with root
 * credentials answers its readiness probe, {@code GET /minio/health/live} on 9000 inside the container returning 200, within the boot's
 * deadline. The control replaces the image's entrypoint with one that exits 1 and holds the same boot red. Each boot's container is
 * removed on every exit, and each cell is capped by its budget (NFR-205, NFR-203).
 */
// @comp C-IMAGES
@UsesConfig(ImageBootConfig.class)
@DependsOn(value = ContainerRuntimeService.class, slot = BootResources.DOCKER, kind = EdgeKind.HARD)
@Category(RELIABILITY)
@Level(SYSTEM)
@Speed(FAST)
@CheckBudget("60s")
public class BootChecks {

    private static final Duration DEADLINE = Duration.ofSeconds(30);
    // The image installs nothing, so the probe is bash's own /dev/tcp: one HTTP/1.0 request, ready when the status line says 200.
    private static final List<String> PROBE = List.of("bash", "-c",
            "exec 3<>/dev/tcp/127.0.0.1/9000 || exit 1; printf 'GET /minio/health/live HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3;"
                    + " read -r status <&3; echo \"$status\"; case \"$status\" in *' 200 '*) exit 0 ;; esac; exit 1");

    @Check
    void bootsAndAnswersHealthLive(TestContext ctx) {
        ImageBoot.Result boot = boot(runtime(ctx), ctx.get(ImageBootConfig.class).image());
        if (!boot.ready()) {
            throw new AssertionError("vault-minio did not boot: " + boot.detail());
        }
    }

    @Check
    void aBrokenEntrypointFailsTheBoot(TestContext ctx) {
        ContainerRuntime runtime = runtime(ctx);
        ImageBoot.Result boot = ImageBoot.withBrokenEntrypoint(runtime, ctx.get(ImageBootConfig.class).image(), tag -> boot(runtime, tag));
        if (boot.ready()) {
            throw new AssertionError("negative control: an image whose entrypoint exits 1 passed the boot: " + boot.detail());
        }
        if (!boot.detail().startsWith(ImageBoot.STOPPED)) {
            throw new AssertionError("negative control: the boot failed, but not because the container stopped: " + boot.detail());
        }
    }

    private static ImageBoot.Result boot(ContainerRuntime runtime, String image) {
        ContainerDefinition definition = ContainerDefinition.of(image)
                .withEnv(Map.of("MINIO_ROOT_USER", "boot-check", "MINIO_ROOT_PASSWORD", UUID.randomUUID().toString()))
                .withCommand("server", "/data");
        return ImageBoot.boot(runtime, definition, PROBE, DEADLINE);
    }

    private static ContainerRuntime runtime(TestContext ctx) {
        return ctx.service(BootResources.DOCKER, ContainerRuntimeOperations.class);
    }
}
