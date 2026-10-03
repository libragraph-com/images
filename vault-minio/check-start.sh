#!/usr/bin/env bash
# assertObjectStoreStarts — the built vault-minio image, driven as the dev vault's cmdb driver drives it
# (TASK-IMAGES-022VXK AC3, AC4, AC6). Usage: check-start.sh <image-reference> <minio-tag> <minio-commit> <mc-commit> <minio-url> <mc-url>
# The whole check runs under a 60 s wall-clock cap that fails it (NFR-203); every container it starts is removed on every exit.
set -u

if [ -z "${CHECK_START_CAPPED:-}" ]; then
    CHECK_START_CAPPED=1 exec timeout --signal=TERM 60 "$0" "$@"
fi

image="$1"; minio_tag="$2"; minio_commit="$3"; mc_commit="$4"; minio_repo="$5"; mc_repo="$6"
user="check-user"
pass="check-pass-$$-secret"
prefix="vault-minio-check-$$"
workdir="$(mktemp -d)"
containers=()
failures=0

cleanup() {
    for c in "${containers[@]:-}"; do [ -n "$c" ] && docker rm -f "$c" >/dev/null 2>&1; done
    rm -rf "$workdir"
}
trap cleanup EXIT
trap 'echo "FAIL: wall-clock cap (60 s) reached" >&2; exit 124' TERM

fail() { echo "FAIL: $*" >&2; failures=$((failures + 1)); }
ok() { echo "ok: $*"; }

# start_and_probe <name> <command words...> — the driver's run: root credentials, 9000 published on 127.0.0.1.
# Returns 0 once the port holds a TCP connection and /minio/health/live answers 200 within 15 s.
start_and_probe() {
    local name="$1"; shift
    containers+=("$name")
    docker run -d --name "$name" --platform linux/amd64 \
        -e "MINIO_ROOT_USER=$user" -e "MINIO_ROOT_PASSWORD=$pass" \
        -p 127.0.0.1::9000 "$image" "$@" >/dev/null || return 1
    local port deadline=$((SECONDS + 15)) code=""
    port="$(docker port "$name" 9000/tcp | head -1 | sed 's/.*://')"
    while [ "$SECONDS" -lt "$deadline" ]; do
        if [ "$(docker inspect -f '{{.State.Running}}' "$name")" != "true" ]; then return 1; fi
        # a failed redirect on `exec` ends the shell that runs it, so the held connection lives in a subshell
        code="$( (exec 3<>"/dev/tcp/127.0.0.1/$port" || exit 7; curl -s -o /dev/null -m 3 -w '%{http_code}' "http://127.0.0.1:$port/minio/health/live") 2>/dev/null )"
        [ "$code" = "200" ] && return 0
        sleep 0.2
    done
    return 1
}

# AC3 — the command as the driver gives it, and again with `minio` in front.
start_and_probe "$prefix-a" server /data && ok "server /data holds a TCP connection and answers /minio/health/live 200" || fail "server /data did not answer /minio/health/live 200 within 15 s"
start_and_probe "$prefix-b" minio server /data && ok "minio server /data holds a TCP connection and answers /minio/health/live 200" || fail "minio server /data did not answer /minio/health/live 200 within 15 s"

# AC3 negative control — a command naming no such subcommand exits non-zero and the probe fails.
if start_and_probe "$prefix-neg" nosuchsubcommand; then
    fail "negative control: 'nosuchsubcommand' answered /minio/health/live 200"
else
    sleep 1
    rc="$(docker inspect -f '{{.State.ExitCode}}' "$prefix-neg" 2>/dev/null || echo unknown)"
    status="$(docker inspect -f '{{.State.Status}}' "$prefix-neg" 2>/dev/null || echo unknown)"
    if [ "$status" = "exited" ] && [ "$rc" != "0" ]; then
        ok "negative control: 'nosuchsubcommand' exits $rc and the probe fails"
    else
        fail "negative control: 'nosuchsubcommand' left the container $status with exit code $rc"
    fi
fi

# AC3 — the arm64 variant reports the pinned tag, commit and platform.
version="$(docker run --rm --platform linux/arm64 "$image" minio --version 2>&1)"
for want in "$minio_tag" "$minio_commit" "linux/arm64"; do
    case "$version" in *"$want"*) ok "arm64 minio --version names $want" ;; *) fail "arm64 minio --version lacks $want: $version" ;; esac
done

# AC6 — the first-run reset's commands, by `sh -c` inside the container as the driver runs them.
box="$prefix-a"
bucket="check-bucket"
reset() { docker exec "$box" sh -c "export MC_HOST_box=http://\$MINIO_ROOT_USER:\$MINIO_ROOT_PASSWORD@127.0.0.1:9000; $1"; }
reset "mc mb box/$bucket" >/dev/null || fail "mc mb box/$bucket failed"
listing="$(reset 'mc --json ls box')"
if echo "$listing" | grep '"type":"folder"' | grep -q "\"key\":\"$bucket/\""; then
    ok "mc --json ls box lists $bucket as a folder row whose key ends in /"
else
    fail "mc --json ls box does not list $bucket as a folder row ending in /: $listing"
fi
reset "mc rb --force --dangerous box/$bucket" >/dev/null || fail "mc rb --force --dangerous box/$bucket failed"
if reset 'mc --json ls box' | grep -q "\"key\":\"$bucket/\""; then fail "$bucket still listed after mc rb"; else ok "mc rb --force --dangerous removed $bucket"; fi

# AC4 — the licence files, read from the image; each is the pinned commit's blob byte for byte (git blob id), and SOURCE names the pins.
git init -q "$workdir/minio" && git init -q "$workdir/mc"
git -C "$workdir/minio" fetch -q --depth 1 "$minio_repo" "$minio_commit" || fail "could not fetch $minio_repo at $minio_commit"
git -C "$workdir/mc" fetch -q --depth 1 "$mc_repo" "$mc_commit" || fail "could not fetch $mc_repo at $mc_commit"
for component in minio mc; do
    commit="$minio_commit"; [ "$component" = mc ] && commit="$mc_commit"
    for file in LICENSE CREDITS go.mod go.sum; do
        have="$(docker run --rm --platform linux/amd64 --entrypoint cat "$image" "/licenses/$component/$file" | git hash-object --stdin)"
        want="$(git -C "$workdir/$component" rev-parse "$commit:$file" 2>/dev/null)"
        if [ -n "$want" ] && [ "$have" = "$want" ]; then ok "/licenses/$component/$file is the blob of $commit"; else fail "/licenses/$component/$file blob '$have' is not the pinned commit's '$want'"; fi
    done
done
source_text="$(docker run --rm --platform linux/amd64 --entrypoint cat "$image" /licenses/SOURCE)"
for want in "${minio_repo%.git}" "${mc_repo%.git}" "$minio_tag" "$minio_commit" "$mc_commit" "go1." "6(d)"; do
    case "$source_text" in *"$want"*) ok "/licenses/SOURCE names $want" ;; *) fail "/licenses/SOURCE lacks $want" ;; esac
done
for label in org.opencontainers.image.licenses=AGPL-3.0-only org.opencontainers.image.source=https://github.com/libragraph-com/images; do
    key="${label%%=*}"; want="${label#*=}"
    have="$(docker image inspect --format "{{index .Config.Labels \"$key\"}}" "$image")"
    [ "$have" = "$want" ] && ok "label $key=$want" || fail "label $key is '$have', not '$want'"
done

if [ "$failures" -ne 0 ]; then echo "assertObjectStoreStarts: $failures check(s) failed" >&2; exit 1; fi
echo "assertObjectStoreStarts: every check passed"
