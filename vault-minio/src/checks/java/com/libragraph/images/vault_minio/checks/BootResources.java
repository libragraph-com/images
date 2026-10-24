package com.libragraph.images.vault_minio.checks;

import com.libragraph.commons.service.ServiceModule;
import com.libragraph.commons.service.Slot;
import com.libragraph.commons.service.container.ContainerRuntimeDriver;
import com.libragraph.commons.service.container.ContainerRuntimeService;
import com.libragraph.commons.service.container.DockerRuntimeOperations;
import com.libragraph.mustr.annotation.ResourceModule;

/**
 * The environment of the boot check: {@code docker} attaches the box's container daemon as a {@link ContainerRuntimeService}, so an
 * absent daemon is the node not reaching {@code UP} and the check not evaluated, never a pass. Which daemon is
 * {@code ContainerRuntimeService.docker.address}, unset for the default socket.
 */
// @comp C-IMAGES
@ResourceModule
@Slot(name = BootResources.DOCKER, description = "The box's container daemon: the boot check builds, runs and removes through it")
public final class BootResources extends ServiceModule {

    /** The container daemon's slot. */
    static final String DOCKER = "docker";

    @Override
    protected void configure() {
        bindService(DOCKER).to(ContainerRuntimeService.class, ContainerRuntimeDriver.class, DockerRuntimeOperations.class);
    }
}
