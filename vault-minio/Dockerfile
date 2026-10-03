# vault-minio — MinIO and its `mc` client built from source at the last upstream release tags, on a minimal Debian
# runtime, the dev vault's object store (C-IMAGES, TASK-IMAGES-022VXK).
#
# minio/minio and minio/mc are archived and publish source only, so each is cloned at its release tag's commit and
# compiled here. Every input is pinned in source (BFR-004): each source by the commit sha its ADD carries and
# checks (BuildKit refuses a clone whose head is another commit), the Go module graph by each repo's committed go.sum,
# the Go toolchain and the Debian runtime by multi-arch index digest. Nothing is installed over the runtime.
#
# The build stage runs on the builder's own platform and cross-compiles, so no emulator runs a compiler. MinIO's
# gen-ldflags.go reads the version and commit from `git log`, and an ADD of a pinned sha leaves no .git, so the ldflags
# are written here from the tag and sha ARGs, naming the fields gen-ldflags.go sets and no input from the wall clock.
FROM --platform=$BUILDPLATFORM golang:1.26.8-trixie@sha256:eae2aaa6add2936cbf350dd0d2628b363461542f0c4b3c0b558957e0f2997379 AS build

ARG TARGETARCH
ARG MINIO_TAG=RELEASE.2025-10-15T17-29-55Z
ARG MINIO_COMMIT=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
ARG MC_TAG=RELEASE.2025-08-13T08-35-41Z
ARG MC_COMMIT=7394ce0dd2a80935aded936b09fa12cbb3cb8096

ADD --checksum=${MINIO_COMMIT} https://github.com/minio/minio.git#${MINIO_COMMIT} /src/minio
ADD --checksum=${MC_COMMIT} https://github.com/minio/mc.git#${MC_COMMIT} /src/mc

ENV GOTOOLCHAIN=local GOOS=linux GOARCH=${TARGETARCH} CGO_ENABLED=0 GOFLAGS=-trimpath

# A tag RELEASE.2025-10-15T17-29-55Z is the commit time 2025-10-15T17:29:55Z, which is cmd.Version; the year is
# cmd.CopyrightYear and the tag itself cmd.ReleaseTag.
RUN set -eux; \
    build() { \
        repo="$1"; tag="$2"; commit="$3"; \
        stamp="${tag#RELEASE.}"; \
        day="${stamp%%T*}"; clock="${stamp#*T}"; \
        version="${day}T$(echo "${clock}" | sed 's/-/:/; s/-/:/')"; \
        pkg="github.com/minio/${repo}/cmd"; \
        cd "/src/${repo}"; \
        go build -tags kqueue -buildvcs=false \
            -ldflags "-s -w -X ${pkg}.Version=${version} -X ${pkg}.CopyrightYear=${day%%-*} -X ${pkg}.ReleaseTag=${tag} -X ${pkg}.CommitID=${commit} -X ${pkg}.ShortCommitID=$(echo "${commit}" | cut -c1-12)" \
            -o "/out/${repo}" .; \
    }; \
    build minio "${MINIO_TAG}" "${MINIO_COMMIT}"; \
    build mc "${MC_TAG}" "${MC_COMMIT}"

# The root the runtime stage takes whole: the two binaries, the upstream entrypoint verbatim from the pinned commit, and the
# source and licence texts the image carries with the written offer AGPL-3.0 §6(d) asks for. Every entry of it gets a fixed
# modification time, directories included: the build's timestamp rewrite only lowers a time above the commit's, so a layer
# cached before the commit would otherwise carry a different time than a fresh build, and the one layer is what makes
# a rebuild from the tag reproduce the tag's bytes whatever the build cache holds.
RUN set -eux; \
    mkdir -p /rootfs/usr/bin /rootfs/licenses/minio /rootfs/licenses/mc; \
    cp /out/minio /out/mc /rootfs/usr/bin/; \
    install -m 0755 /src/minio/dockerscripts/docker-entrypoint.sh /rootfs/usr/bin/docker-entrypoint.sh; \
    for repo in minio mc; do \
        for file in LICENSE CREDITS go.mod go.sum; do cp "/src/${repo}/${file}" "/rootfs/licenses/${repo}/${file}"; done; \
    done; \
    { \
        echo 'vault-minio carries MinIO and its mc client, each licensed AGPL-3.0-only (the texts are in /licenses/<component>/LICENSE).'; \
        echo; \
        echo "minio  https://github.com/minio/minio  tag ${MINIO_TAG}  commit ${MINIO_COMMIT}"; \
        echo "mc     https://github.com/minio/mc     tag ${MC_TAG}  commit ${MC_COMMIT}"; \
        echo "Go toolchain  $(go env GOVERSION)  (golang:1.26.8-trixie)"; \
        echo; \
        echo 'Each component is the upstream source at that tag and commit, compiled unmodified by the Dockerfile in https://github.com/libragraph-com/images (vault-minio/Dockerfile).'; \
        echo 'Written offer (AGPL-3.0 section 6(d)): the corresponding source is that tag at that URL plus that Dockerfile, and LibRAGraph supplies it to anyone who asks, through https://github.com/libragraph-com/images.'; \
        echo '/licenses/<component>/ also holds go.mod and go.sum, the module graph each binary was built from, and CREDITS, the third-party licence texts upstream vendors for it.'; \
    } > /rootfs/licenses/SOURCE; \
    find /rootfs -exec touch -h -d @0 {} +

FROM debian:trixie-slim@sha256:a99cfc517144bc59b1978475ec53b46ecabec7e43635402ee5b77cc54cd1b20a

LABEL org.opencontainers.image.source=https://github.com/libragraph-com/images \
      org.opencontainers.image.licenses=AGPL-3.0-only

COPY --from=build /rootfs/ /

VOLUME ["/data"]
EXPOSE 9000
ENTRYPOINT ["/usr/bin/docker-entrypoint.sh"]
CMD ["minio"]
