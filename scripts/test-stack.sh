#!/usr/bin/env bash
# Boot a disposable Legendary stack for gate 7a with a working RCON console.
#
# Usage:
#   scripts/test-stack.sh up [<jar>]     boot a fresh stack (default: newest target/*.jar)
#   scripts/test-stack.sh rcon <cmd>...  run console commands against the running stack
#   scripts/test-stack.sh logs [args]    docker compose logs passthrough
#   scripts/test-stack.sh down           tear down and release the slot lease
#
# Why this script exists instead of a bare `docker compose up`:
#
#   1. The image ships no RCON, no screen/tmux, and blocks writing to the JVM's
#      stdin -- `docker attach` injection does not reach it either. Without RCON,
#      runtime verification can only prove the plugin loads, not that any command
#      works. RCON must be enabled in server.properties, which start.sh copies from
#      /scripts only when the file does not already exist, so a fresh volume needs it
#      seeded up front.
#
#   2. Seeding it via a single-file bind mount breaks start.sh's port rewrite:
#      `sed -i` cannot rename across a bind-mounted file and fails with
#      "Device or resource busy". The failure is silent -- the server keeps listening
#      on 25565 while Compose publishes the slot port, so the port answers TCP (via
#      docker-proxy) but no client can join. This script therefore seeds server-port
#      and query.port into the file itself, making start.sh's failed sed irrelevant.
#
# Verify a booted stack really serves Minecraft with a protocol handshake, not just
# a TCP connect -- an open port proves nothing here.

set -euo pipefail

SLUG="agua-de-florida"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SLOT_BIN="/home/carmelo/Projects/Minecraft/Plugins/minecraft-plugin-docs/bin/xpfarm-slot"
STATE_DIR="/tmp/xpfarm-test-stack/${SLUG}"
RUN_DIR="${STATE_DIR}/run"

usage() { sed -n '2,28p' "${BASH_SOURCE[0]}"; exit 1; }

load_state() {
  [ -f "${STATE_DIR}/env" ] || { echo "no running stack; run '$0 up' first" >&2; exit 1; }
  # shellcheck disable=SC1091
  . "${STATE_DIR}/env"
}

cmd_up() {
  local jar="${1:-}"
  if [ -z "${jar}" ]; then
    jar="$(find "${REPO_ROOT}/target" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*' \
           -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-)"
  fi
  [ -n "${jar}" ] && [ -f "${jar}" ] || {
    echo "no plugin JAR found; run 'mvn clean verify' first" >&2; exit 1; }
  # A missing bind-mount source is silently created by Docker as a DIRECTORY, and the
  # plugin then never loads while the stack still looks healthy. Fail loudly instead.
  echo "plugin JAR: ${jar}"

  local project="xpfarm-plugin-test-${SLUG}-$(openssl rand -hex 4)"
  # Per-run directory: the container chowns the bind-mounted server.properties to its
  # own user, so reusing one path makes every subsequent run fail with EACCES.
  RUN_DIR="${STATE_DIR}/run-${project##*-}"
  mkdir -p "${RUN_DIR}"
  local slot java_port bedrock_port
  read -r slot java_port bedrock_port < <("${SLOT_BIN}" acquire "${SLUG}" "${project}") || true
  [ -n "${slot:-}" ] || { echo "no free test slot; wait and retry" >&2; exit 1; }

  local rcon_port=$((25575 + slot))
  local rcon_pw="xpfarm-$(openssl rand -hex 8)"

  # Seed server.properties: RCON on, and the slot ports baked in (see note 2 above).
  docker run --rm --entrypoint cat "$(grep -oP '(?<=image: ).*' "${REPO_ROOT}/docker-compose.yml" | head -1)" \
    /scripts/server.properties > "${RUN_DIR}/server.properties"
  sed -i -e 's/^enable-rcon=.*/enable-rcon=true/' \
         -e "s/^rcon.password=.*/rcon.password=${rcon_pw}/" \
         -e 's/^rcon.port=.*/rcon.port=25575/' \
         -e "s/^server-port=.*/server-port=${java_port}/" \
         -e "s/^query.port=.*/query.port=${java_port}/" \
         "${RUN_DIR}/server.properties"

  cat > "${RUN_DIR}/override.yml" <<EOF
services:
  minecraftbe:
    volumes:
      - ${RUN_DIR}/server.properties:/minecraft/server.properties
EOF

  cat > "${STATE_DIR}/env" <<EOF
COMPOSE_PROJECT_NAME=${project}
XPFARM_JAVA_PORT=${java_port}
XPFARM_BEDROCK_PORT=${bedrock_port}
XPFARM_RCON_PORT=${rcon_port}
XPFARM_PLUGIN_JAR=${jar}
XPFARM_RCON_PASSWORD=${rcon_pw}
XPFARM_SLOT=${slot}
XPFARM_RUN_DIR=${RUN_DIR}
EOF

  load_state
  export COMPOSE_PROJECT_NAME XPFARM_JAVA_PORT XPFARM_BEDROCK_PORT XPFARM_RCON_PORT XPFARM_PLUGIN_JAR
  ( cd "${REPO_ROOT}" && docker compose -p "${COMPOSE_PROJECT_NAME}" \
      -f docker-compose.yml -f "${RUN_DIR}/override.yml" up -d --force-recreate )

  echo "waiting for startup (slot ${slot}, java ${java_port}, bedrock ${bedrock_port}, rcon ${rcon_port})"
  # pipefail is disabled for this pipeline on purpose: `grep -m1` exits as soon as it
  # matches, which terminates `docker compose logs -f` upstream and makes the pipeline
  # report failure on the success path. Judge the result by grep's status alone.
  # Wait on PAPER's "Done", not Geyser's. Geyser reports Done ~1s EARLIER than Paper,
  # and RCON only starts after Paper is up -- keying off Geyser races both the listener
  # and the RCON thread.
  local started=1
  ( set +o pipefail
    cd "${REPO_ROOT}"
    timeout 600 docker compose -p "${COMPOSE_PROJECT_NAME}" logs -f 2>&1 \
      | grep -m1 -E 'Done \([0-9.]+s\)! For help' ) && started=0
  [ "${started}" -eq 0 ] || { echo "server did not finish starting within 600s" >&2; exit 1; }

  # An open port proves nothing -- docker-proxy accepts connections even when the JVM
  # is bound to a different port inside the container. Require a real handshake, with a
  # short retry since the listener and RCON come up moments after the Done line.
  echo "verifying the Java port actually serves Minecraft..."
  local ok=1 i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    if python3 "${REPO_ROOT}/scripts/mc-ping.py" 127.0.0.1 "${java_port}" 2>/dev/null; then
      ok=0; break
    fi
    timeout 5 bash -c 'read -t 2 -u 0 _ || true' </dev/null || true
  done
  [ "${ok}" -eq 0 ] || {
    echo "port ${java_port} did not answer a Minecraft handshake" >&2; exit 1; }
  echo "stack up."
}

cmd_rcon() {
  load_state
  [ $# -gt 0 ] || { echo "usage: $0 rcon <command>..." >&2; exit 1; }
  python3 "${REPO_ROOT}/scripts/rcon.py" 127.0.0.1 "${XPFARM_RCON_PORT}" "${XPFARM_RCON_PASSWORD}" "$@"
}

cmd_logs() {
  load_state
  ( cd "${REPO_ROOT}" && docker compose -p "${COMPOSE_PROJECT_NAME}" logs "$@" )
}

cmd_down() {
  load_state
  case "${COMPOSE_PROJECT_NAME}" in
    xpfarm-plugin-test-${SLUG}-*) : ;;
    *) echo "refusing teardown of ${COMPOSE_PROJECT_NAME}" >&2; exit 1 ;;
  esac
  ( cd "${REPO_ROOT}" && docker compose -p "${COMPOSE_PROJECT_NAME}" down -v ) || true
  "${SLOT_BIN}" release "${SLUG}" || true
  rm -f "${STATE_DIR}/env"
  echo "stack down, slot released."
}

case "${1:-}" in
  up)   shift; cmd_up "$@" ;;
  rcon) shift; cmd_rcon "$@" ;;
  logs) shift; cmd_logs "$@" ;;
  down) cmd_down ;;
  *)    usage ;;
esac
