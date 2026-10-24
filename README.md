# images
Container images LibRAGraph publishes for third-party software it runs on (PostgreSQL + PostGIS + pgvector, MinIO)

Modules: `vault-postgres` (PostgreSQL 17 + PostGIS + pgvector) and `vault-minio` (MinIO and `mc`, built from source).

Each module's `gate` runs its **boot check**, a MUSTr check (`src/checks`, the house lanes of `C-SHARED-MUSTR`) the gate's
roll-up counts, so a coordinate can be graded (`BFR-140`). It boots the image this module just built on the `docker` slot's
runtime, reads its readiness probe inside the container under a 30 s deadline and a 60 s cell budget, and removes the
container on every exit; its negative cell rebuilds the image with an entrypoint that exits 1 and holds the same boot red.

## vault-postgres

Boot check `BootChecks.bootsAndAnswersPgIsready`: a container started with only `POSTGRES_PASSWORD` answers
`pg_isready -h 127.0.0.1` (the server initdb leaves, not its socket-only first start). Control:
`BootChecks.aBrokenEntrypointFailsTheBoot`.

## vault-minio

Boot check `BootChecks.bootsAndAnswersHealthLive`: a container started as `server /data` with root credentials answers
`GET /minio/health/live` 200 on 9000 inside the container. Control: `BootChecks.aBrokenEntrypointFailsTheBoot`. Beside it,
`assertObjectStoreStarts` (`check-start.sh`) drives the image as the dev vault's driver does: the `mc` reset, the arm64
variant and the licence files.
