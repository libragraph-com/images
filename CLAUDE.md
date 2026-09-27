# images — agent orientation

LibRAGraph's **public** repo of container images of **third-party software** the product runs on, each built to carry
exactly what the product needs and nothing more. An image here carries no LibRAGraph code: it is a third-party image
LibRAGraph happens to publish, and a consumer pins it by digest as it pins any upstream image. The spine home is
`C-IMAGES` (the repo) and `C-POSTGRES` (what the vault's database needs) — `ref get C-IMAGES C-POSTGRES` in the pm repo.

**Everything committed here is public the moment it merges.** No file names a cloud account, a private host or a
secret.

## Rules

- **One module per third-party image**, each a Dockerfile over an upstream base, each a releasable module on its own
  counter version line (`<module>-X.Y.Z`). No file holds a version: the line is derived from the module's own tags.
- **Every build input is pinned in source.** Every `FROM` names its base by its multi-arch index digest. Every package
  an `apt-get install` adds over the base is named `name=version` — the packages asked for **and every dependency apt
  pulls in with them** — so the pins are the complete list the SBOM names. Debian comes from `snapshot.debian.org` at
  a fixed timestamp and PGDG from `apt-archive.postgresql.org`, which keeps every published version. A bump is a
  commit; the layer that writes the apt lists, the apt and dpkg logs and the ldconfig aux-cache removes them, so no
  file depends on when the build ran and a rebuild from a tag reproduces the tag's bytes.
- **No preloaded library.** An image sets no `shared_preload_libraries`: a preloaded background worker holds a
  session on every database, the clone template included, and `CREATE DATABASE … TEMPLATE` refuses a template
  another session holds.
- **Every image is declared public and attested on `global`.** A module names `global` among its image channels;
  `publish -Pchannel=global` pushes it multi-arch to `ghcr.io/libragraph-com/<image>`, confirms it with an anonymous
  read, and attaches a cosign attestation of its CycloneDX SBOM signed by the `global` channel's KMS key. The key's
  public half is `cosign.pub` at the repo root, so any puller verifies with no LibRAGraph credential:
  `cosign verify-attestation --type cyclonedx --key cosign.pub ghcr.io/libragraph-com/<image>@<digest>`.
- **The verbs are the only build and publish path.** Never a raw `docker build` / `docker push` / Gradle task for a
  release; the image, its tag and its upload are the convention plugin's.

## Modules

| Module | Image | What |
|---|---|---|
| `vault-postgres/` | `ghcr.io/libragraph-com/vault-postgres` | PostgreSQL 17 (`postgres:17-trixie`) with PostGIS and pgvector from PGDG — the extensions the vault's schema creates (`citext`, `pgcrypto`, `postgis`, `vector`) — preloading no library, for `linux/amd64` and `linux/arm64` |

## Build — the verbs, and nothing but the verbs

The build applies the `com.libragraph.build-tools` convention plugin; the root project is a pure aggregate over the
modules. Resolving the plugin needs `GITHUB_TOKEN` (GitHub Packages requires a credential even to read); the
Dockerfiles themselves build with plain `docker build`.

```bash
./gradlew gate                                  # the quality gate
./gradlew release                               # on main: mint <module>-X.Y.Z for each changed module
./gradlew publish -Pchannel=global              # push, attest and confirm on public GHCR
./gradlew publish -Prehearse=<module>-X.Y.Z     # rebuild a tag and assert its bytes reproduce
```

Building an image needs `docker buildx` with every declared platform (a foreign one through QEMU binfmt) and the
engine's containerd image store, which holds a multi-platform image.
