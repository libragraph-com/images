package com.libragraph.images.vault_minio.checks;

import com.libragraph.commons.container.Container;
import com.libragraph.commons.container.ContainerCommand;
import com.libragraph.commons.container.ContainerDefinition;
import com.libragraph.commons.container.ContainerRuntime;
import com.libragraph.commons.container.ExecResult;
import com.libragraph.commons.exec.Verdict;
import com.libragraph.commons.service.container.ContainerService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * One boot of an image on the {@code docker} slot's runtime: create a container, start it, and run its readiness probe inside it until
 * the probe exits 0, the container stops running, or the deadline passes. The container is removed on every exit (NFR-205); each one
 * carries the creator labels commons' container service sweeps by, and every boot first removes what a dead creator on this host left.
 */
// @comp C-IMAGES
final class ImageBoot {

    /** The label every container a boot check creates carries, so the sweep lists only these. */
    static final String LABEL = "com.libragraph.images.boot-check";

    /** How a boot's detail begins when the container stopped running before its probe answered. */
    static final String STOPPED = "the container stopped";

    private ImageBoot() {}

    /**
     * What one boot came to.
     *
     * @param ready  whether the probe answered within the deadline
     * @param detail what was seen
     */
    record Result(boolean ready, String detail) {}

    /**
     * Boot [definition] and read [probe] until it answers or [deadline] passes.
     *
     * @param runtime    the slot's runtime
     * @param definition the container to run; the boot adds its labels
     * @param probe      the readiness probe, argv run inside the container; exit 0 is ready
     * @param deadline   the wall-clock bound on the boot
     * @return whether the probe answered within the deadline, and what was seen
     */
    static Result boot(ContainerRuntime runtime, ContainerDefinition definition, List<String> probe, Duration deadline) {
        sweep(runtime);
        Map<String, String> labels = new HashMap<>(definition.labels());
        labels.put(LABEL, "true");
        labels.put(ContainerService.CREATOR_LABEL, ContainerService.creator(ProcessHandle.current()));
        ContainerService.creatorHost().ifPresent(host -> labels.put(ContainerService.CREATOR_HOST_LABEL, host));
        labels.put(ContainerService.MAY_TEARDOWN_LABEL, "true");
        long end = System.nanoTime() + deadline.toNanos();
        String id = null;
        try {
            id = run(runtime.create(definition.withLabels(labels)), 30).id();
            try {
                run(runtime.start(id), 30);
            } catch (RuntimeException e) {
                return new Result(false, STOPPED + " as it started: " + e.getMessage() + tail(runtime, id));
            }
            String last = "no probe ran";
            while (System.nanoTime() < end) {
                Optional<Container> now = runtime.inspect(id);
                if (now.isEmpty() || !now.get().running()) {
                    return new Result(false, STOPPED + " (" + now.map(c -> c.state().toString()).orElse("gone")
                            + ") before its probe answered" + tail(runtime, id));
                }
                try {
                    ExecResult answer = run(runtime.exec(id, probe), 10);
                    if (answer.exitCode() == 0) {
                        return new Result(true, String.join(" ", probe) + " exited 0: " + answer.stdout().strip());
                    }
                    last = String.join(" ", probe) + " exited " + answer.exitCode() + ": "
                            + (answer.stdout() + answer.stderr()).strip();
                } catch (RuntimeException e) {
                    last = "the probe could not run: " + e.getMessage();
                }
                Thread.sleep(250);
            }
            return new Result(false, "the probe did not answer within " + deadline.toSeconds() + " s; last: " + last
                    + tail(runtime, id));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "the boot was interrupted");
        } finally {
            if (id != null) {
                String held = id;
                uninterrupted(() -> run(runtime.remove(held), 30));
            }
        }
    }

    /**
     * Build, under a tag of its own, [image] with its entrypoint replaced by one that exits 1 — the negative control's image — and hand
     * its tag to [use]; the image and its build context are removed on every exit.
     *
     * @param runtime the slot's runtime
     * @param image   the image under check
     * @param use     what runs against the broken image's tag
     * @param <R>     what it returns
     * @return what [use] returned
     */
    static <R> R withBrokenEntrypoint(ContainerRuntime runtime, String image, Function<String, R> use) {
        String tag = "localhost/" + image.replaceAll("^.*/|:.*$", "") + "-boot-control:" + ProcessHandle.current().pid() + "-"
                + Long.toHexString(System.nanoTime());
        Path context;
        try {
            Path root = Files.createDirectories(Path.of("build", "image-boot"));
            context = Files.createTempDirectory(root, "broken-");
            Files.writeString(context.resolve("Dockerfile"), "FROM " + image + "\nENTRYPOINT [\"/bin/sh\", \"-c\", \"exit 1\"]\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        boolean built = false;
        try {
            run(runtime.build(context.toAbsolutePath(), tag, ""), 120);
            built = true;
            return use.apply(tag);
        } finally {
            if (built) {
                uninterrupted(() -> run(runtime.removeImage(tag), 60));
            }
            delete(context);
        }
    }

    /** Remove every boot-check container on this host whose creator is gone — what a killed run left. */
    private static void sweep(ContainerRuntime runtime) {
        Optional<String> host = ContainerService.creatorHost();
        if (host.isEmpty()) {
            return;
        }
        Map<String, String> by = new HashMap<>(ContainerService.removableOn(host.get()));
        by.put(LABEL, "true");
        for (Container left : runtime.list(by)) {
            String creator = left.labels().get(ContainerService.CREATOR_LABEL);
            if (creator != null && ContainerService.creatorGone(creator)) {
                run(runtime.remove(left.id()), 30);
            }
        }
    }

    private static String tail(ContainerRuntime runtime, String id) {
        try {
            List<String> lines = runtime.logLines(id);
            return "; log: " + String.join(" | ", lines.subList(Math.max(0, lines.size() - 5), lines.size()));
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void delete(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Teardown runs on a thread the run may have interrupted, so the interrupt is set aside while it runs and restored. */
    private static void uninterrupted(Runnable teardown) {
        boolean interrupted = Thread.interrupted();
        try {
            teardown.run();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Run [command] under a [seconds] cap and return its product; a failed command throws the runtime's own failure. */
    private static <R> R run(ContainerCommand<R> command, int seconds) {
        Verdict verdict;
        try {
            verdict = command.run().toCompletableFuture().get(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            command.cancel();
            Thread.currentThread().interrupt();
            throw new IllegalStateException(command.name() + " " + command.subject() + " was abandoned", e);
        } catch (TimeoutException e) {
            command.cancel();
            throw new IllegalStateException(command.name() + " " + command.subject() + " outlived its " + seconds + " s cap", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(command.name() + " " + command.subject() + " could not run", e.getCause());
        }
        if (verdict instanceof Verdict.Settled) {
            return command.result().orElse(null);
        }
        throw command.failure().<RuntimeException>map(f -> f)
                .orElseGet(() -> new IllegalStateException(command.name() + " " + command.subject() + " ended " + verdict));
    }
}
