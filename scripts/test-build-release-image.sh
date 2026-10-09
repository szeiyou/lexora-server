#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$REPO_ROOT/scripts/build-release-image.sh"
CONFIG_PATH="$REPO_ROOT/scripts/build-release-image.conf"
TMP_DIR="$(mktemp -d)"
OUTPUT_DIR="$TMP_DIR/output"
CONFIG_OUTPUT_DIR="$TMP_DIR/config-output"
STUB_BIN_DIR="$TMP_DIR/bin"
FAKE_JAVA_HOME="$TMP_DIR/fake-jdk"
CALL_LOG="$TMP_DIR/calls.log"
ORIGINAL_CONFIG_EXISTS="false"
ORIGINAL_CONFIG_CONTENT=""

if [[ -f "$CONFIG_PATH" ]]; then
  ORIGINAL_CONFIG_EXISTS="true"
  ORIGINAL_CONFIG_CONTENT="$(cat "$CONFIG_PATH")"
fi

cleanup() {
  if [[ "$ORIGINAL_CONFIG_EXISTS" == "true" ]]; then
    printf '%s' "$ORIGINAL_CONFIG_CONTENT" > "$CONFIG_PATH"
  else
    rm -f "$CONFIG_PATH"
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p "$STUB_BIN_DIR" "$FAKE_JAVA_HOME/bin"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local expected="$2"
  if ! grep -Fq "$expected" "$file"; then
    echo "Expected to find: $expected" >&2
    echo "Actual content:" >&2
    cat "$file" >&2
    fail "assert_contains failed for $file"
  fi
}

assert_not_contains() {
  local file="$1"
  local unexpected="$2"
  if grep -Fq "$unexpected" "$file"; then
    echo "Did not expect to find: $unexpected" >&2
    echo "Actual content:" >&2
    cat "$file" >&2
    fail "assert_not_contains failed for $file"
  fi
}

cat > "$FAKE_JAVA_HOME/bin/java" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "-version" ]]; then
  echo 'openjdk version "21.0.10"'
  exit 0
fi
exit 0
EOF
chmod +x "$FAKE_JAVA_HOME/bin/java"

cat > "$STUB_BIN_DIR/mvnw" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf 'mvnw:%s\n' "\$*" >> "$CALL_LOG"
mkdir -p "$REPO_ROOT/target"
touch "$REPO_ROOT/target/dic-server-0.0.1-SNAPSHOT.jar"
EOF
chmod +x "$STUB_BIN_DIR/mvnw"

cat > "$STUB_BIN_DIR/docker" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf 'docker:%s\n' "\$*" >> "$CALL_LOG"
if [[ "\${1:-}" == "save" ]]; then
  shift
  if [[ "\${1:-}" != "-o" ]]; then
    echo "expected docker save -o <file> <image>" >&2
    exit 1
  fi
  output_file="\$2"
  mkdir -p "\$(dirname "\$output_file")"
  printf 'fake image archive' > "\$output_file"
fi
EOF
chmod +x "$STUB_BIN_DIR/docker"

make_fake_runtime_docker() {
  local bin_dir="$1"

  mkdir -p "$bin_dir"
  cat > "$bin_dir/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

log_file="${FAKE_RUNTIME_DOCKER_LOG:?}"
printf '%s\n' "$*" >> "$log_file"

case "${1:-}" in
  info)
    exit 0
    ;;
  load)
    echo "Loaded image: dicserver:local"
    exit 0
    ;;
  image)
    if [[ "${2:-}" == "inspect" ]]; then
      case "${3:-}" in
        dicserver:local)
          [ "${FAKE_RUNTIME_HAVE_DICSERVER:-1}" = "1" ]
          exit
          ;;
        mysql:8.0)
          [ "${FAKE_RUNTIME_HAVE_MYSQL:-1}" = "1" ]
          exit
          ;;
      esac
    fi
    ;;
  inspect)
    state_file="${FAKE_RUNTIME_STATE_FILE:?}"
    current_state="$(cat "$state_file")"
    if [[ "$current_state" == "starting" ]]; then
      printf 'healthy' > "$state_file"
      printf 'starting\n'
      exit 0
    fi
    printf '%s\n' "$current_state"
    exit 0
    ;;
  compose)
    case "${2:-}" in
      version)
        exit 0
        ;;
      up)
        exit 0
        ;;
      ps)
        printf 'NAME STATUS\n'
        printf 'dicserver-app running\n'
        printf 'dicserver-mysql running\n'
        exit 0
        ;;
      logs)
        printf 'fake compose logs\n'
        exit 0
        ;;
    esac
    ;;
  pull)
    [ "${2:-}" = "mysql:8.0" ] && exit 0
    ;;
esac

echo "unsupported docker invocation: $*" >&2
exit 1
EOF
  chmod +x "$bin_dir/docker"
}

make_release_runner_workspace() {
  local workspace="$1"

  mkdir -p "$workspace/bin" "$workspace/config"
  cp "$OUTPUT_DIR/run-release.sh" "$workspace/run-release.sh"
  cp "$OUTPUT_DIR/docker-compose.yml" "$workspace/docker-compose.yml"
  cp "$OUTPUT_DIR/dicserver-image.tar" "$workspace/dicserver-image.tar"
  chmod +x "$workspace/run-release.sh"
}

run_release_runner() {
  local workspace="$1"
  local output_file="$2"

  (
    cd "$workspace"
    APP_STARTUP_TIMEOUT_SECONDS=5 APP_STARTUP_POLL_SECONDS=0 \
      PATH="$workspace/bin:$PATH" "$workspace/run-release.sh"
  ) >"$output_file" 2>&1 || return $?
}

test_generated_runner_requires_application_config() {
  local workspace output_file log_file state_file status
  workspace="$TMP_DIR/release-runner-missing-config"
  output_file="$workspace/output.log"
  log_file="$workspace/docker.log"
  state_file="$workspace/docker-state.txt"

  mkdir -p "$workspace"
  make_release_runner_workspace "$workspace"
  : > "$workspace/.env"
  : > "$log_file"
  printf 'healthy' > "$state_file"
  make_fake_runtime_docker "$workspace/bin"

  if FAKE_RUNTIME_DOCKER_LOG="$log_file" FAKE_RUNTIME_STATE_FILE="$state_file" \
    run_release_runner "$workspace" "$output_file"; then
    status=0
  else
    status=$?
  fi

  [ "$status" -ne 0 ] || fail "missing config/application.yml should fail"
  assert_contains "$output_file" "config/application.yml"
}

test_generated_runner_waits_for_healthy_app() {
  local workspace output_file log_file state_file
  workspace="$TMP_DIR/release-runner-healthy"
  output_file="$workspace/output.log"
  log_file="$workspace/docker.log"
  state_file="$workspace/docker-state.txt"

  mkdir -p "$workspace"
  make_release_runner_workspace "$workspace"
  cp "$OUTPUT_DIR/.env.example" "$workspace/.env"
  cp "$OUTPUT_DIR/config/application.yml.example" "$workspace/config/application.yml"
  : > "$log_file"
  printf 'starting' > "$state_file"
  make_fake_runtime_docker "$workspace/bin"

  FAKE_RUNTIME_DOCKER_LOG="$log_file" FAKE_RUNTIME_STATE_FILE="$state_file" \
    run_release_runner "$workspace" "$output_file"

  assert_contains "$log_file" "compose up -d --force-recreate --remove-orphans"
  assert_contains "$log_file" "inspect --format"
  assert_not_contains "$log_file" "compose logs --tail=80 dicserver"
}

cat > "$CONFIG_PATH" <<EOF
PRESET=systemd-upgrade
OUTPUT_DIR=$CONFIG_OUTPUT_DIR
EOF

JAVA_HOME="$FAKE_JAVA_HOME" \
PATH="$STUB_BIN_DIR:$PATH" \
MVNW_BIN="$STUB_BIN_DIR/mvnw" \
DOCKER_BIN="$STUB_BIN_DIR/docker" \
"$SCRIPT_PATH" "$OUTPUT_DIR" >/dev/null

test -f "$OUTPUT_DIR/dicserver-image.tar"
test -f "$OUTPUT_DIR/.env.example"
test -f "$OUTPUT_DIR/docker-compose.yml"
test -f "$OUTPUT_DIR/config/application.yml.example"
test -f "$OUTPUT_DIR/run-release.sh"
test -f "$OUTPUT_DIR/RUN.md"
test ! -e "$CONFIG_OUTPUT_DIR/opt/dicserver/app.jar"

grep -q "image: dicserver:local" "$OUTPUT_DIR/docker-compose.yml"
if grep -q "build:" "$OUTPUT_DIR/docker-compose.yml"; then
  echo "release docker-compose.yml must not contain build:" >&2
  exit 1
fi
grep -q "./config:/app/config:ro" "$OUTPUT_DIR/docker-compose.yml"
if grep -q "LLM_API_"'CHOICE' "$OUTPUT_DIR/docker-compose.yml"; then
  echo "release docker-compose.yml must not inject deprecated llm topology env vars" >&2
  exit 1
fi
grep -q "ANTHROPIC_API_KEY" "$OUTPUT_DIR/docker-compose.yml"

grep -q "name: deepseek-main" "$OUTPUT_DIR/config/application.yml.example"
grep -q "name: bailian-backup" "$OUTPUT_DIR/config/application.yml.example"
grep -q "name: tencent-backup" "$OUTPUT_DIR/config/application.yml.example"
grep -q '^ANTHROPIC_API_KEY=' "$OUTPUT_DIR/.env.example"

if grep -q '^LLM_API_'CHOICE'=' "$OUTPUT_DIR/.env.example"; then
  echo ".env.example must not include deprecated LLM_API_"'CHOICE' >&2
  exit 1
fi
if grep -q '^DEEPSEEK_API_URL=' "$OUTPUT_DIR/.env.example"; then
  echo ".env.example must not include deprecated provider topology URLs" >&2
  exit 1
fi
if grep -q '^LLM_FALLBACK_'ORDER'=' "$OUTPUT_DIR/.env.example"; then
  echo ".env.example must not include deprecated LLM fallback order" >&2
  exit 1
fi

grep -q "docker load -i dicserver-image.tar" "$OUTPUT_DIR/RUN.md"
grep -q "./run-release.sh" "$OUTPUT_DIR/RUN.md"

grep -q '^!target/dic-server-0.0.1-SNAPSHOT.jar$' "$REPO_ROOT/.dockerignore"

grep -q "mvnw:clean package -DskipTests" "$CALL_LOG"
grep -q '^docker:' "$CALL_LOG"
grep -q "docker:build -f $REPO_ROOT/Dockerfile.release -t dicserver:local $REPO_ROOT" "$CALL_LOG"
grep -q "docker:save -o $OUTPUT_DIR/dicserver-image.tar dicserver:local" "$CALL_LOG"

test_generated_runner_requires_application_config
test_generated_runner_waits_for_healthy_app

echo "ok"
