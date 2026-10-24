package com.libragraph.images.vault_postgres.checks;

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
 * TASK-IMAGES-035GTH·AC2·AC3 — the built vault-postgres image boots: a container of it starts with only the password the upstream
 * entrypoint requires, and {@code pg_isready} over TCP answers inside it within the boot's deadline — the server the entrypoint's
 * initdb leaves, not its socket-only first start. The control replaces the image's entrypoint with one that exits 1 and holds the same
 * boot red. Each boot's container is removed on every exit, and each cell is capped by its budget (NFR-205, NFR-203).
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
    private static final List<String> PROBE = List.of("pg_isready", "-h", "127.0.0.1", "-p", "5432");

    @Check
    void bootsAndAnswersPgIsready(TestContext ctx) {
        ImageBoot.Result boot = boot(runtime(ctx), ctx.get(ImageBootConfig.class).image());
        if (!boot.ready()) {
            throw new AssertionError("vault-postgres did not boot: " + boot.detail());
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
                .withEnv(Map.of("POSTGRES_PASSWORD", UUID.randomUUID().toString()))
                .withTmpfs(Map.of("/var/lib/postgresql/data", "rw"));
        return ImageBoot.boot(runtime, definition, PROBE, DEADLINE);
    }

    private static ContainerRuntime runtime(TestContext ctx) {
        return ctx.service(BootResources.DOCKER, ContainerRuntimeOperations.class);
    }
}
