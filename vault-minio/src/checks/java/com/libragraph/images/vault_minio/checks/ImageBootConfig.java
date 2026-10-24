package com.libragraph.images.vault_minio.checks;

import com.libragraph.commons.service.Param;
import com.libragraph.commons.service.Union;

/**
 * What the boot check reads, under {@code image-boot.<property>}: the image under check, the local reference this module's
 * {@code buildDockerfileImage} built, which the build script hands the run's fork as {@code IMAGE_BOOT_IMAGE}.
 *
 * @param image the local engine's reference of the built image
 */
// @comp C-IMAGES
@Union(name = ImageBootConfig.NAMESPACE, value = {})
record ImageBootConfig(@Param("image") String image) {

    /** The namespace every key here is addressed under. */
    static final String NAMESPACE = "image-boot";
}
