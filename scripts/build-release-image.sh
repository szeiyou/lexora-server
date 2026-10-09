#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_OUTPUT_DIR="$REPO_ROOT/release/dicserver"
DEFAULT_JAVA_HOME="${JAVA_HOME:-}"
CONFIG_FILE="$REPO_ROOT/scripts/build-release-image.conf"
IMAGE_REF="${DICSERVER_IMAGE_REF:-dicserver:local}"
IMAGE_ARCHIVE_NAME="${DICSERVER_IMAGE_ARCHIVE_NAME:-dicserver-image.tar}"
MVNW_BIN="${MVNW_BIN:-$REPO_ROOT/mvnw}"
DOCKER_BIN="${DOCKER_BIN:-docker}"
JAR_PATH="${JAR_PATH:-$REPO_ROOT/target/dic-server-0.0.1-SNAPSHOT.jar}"
APPLICATION_CONFIG_EXAMPLE_PATH="$REPO_ROOT/config/application.yml.example"
SYSTEMD_DEPLOY_TEMPLATE_DIR="$REPO_ROOT/scripts/systemd-release-template"
SYSTEMD_DEPLOY_SCRIPT_TEMPLATE_PATH="$SYSTEMD_DEPLOY_TEMPLATE_DIR/deploy-systemd-release.sh"
SYSTEMD_DEPLOY_CONFIG_TEMPLATE_PATH="$SYSTEMD_DEPLOY_TEMPLATE_DIR/deploy-systemd-release.conf"

DEFAULT_TARGET="docker"
DEFAULT_SYSTEMD_RUNTIME="host-jre"
DEFAULT_SYSTEMD_MYSQL_EXAMPLE="no"
DEFAULT_LAYOUT="mirror"

TARGET="$DEFAULT_TARGET"
SYSTEMD_RUNTIME="$DEFAULT_SYSTEMD_RUNTIME"
SYSTEMD_MYSQL_EXAMPLE="$DEFAULT_SYSTEMD_MYSQL_EXAMPLE"
LAYOUT="$DEFAULT_LAYOUT"
OUTPUT_DIR=""
IMAGE_ARCHIVE_PATH=""
PRESET=""

ARG_TARGET=""
ARG_SYSTEMD_RUNTIME=""
ARG_SYSTEMD_MYSQL_EXAMPLE=""
ARG_LAYOUT=""
ARG_OUTPUT_DIR=""
ARG_PRESET=""

CFG_TARGET=""
CFG_SYSTEMD_RUNTIME=""
CFG_SYSTEMD_MYSQL_EXAMPLE=""
CFG_LAYOUT=""
CFG_OUTPUT_DIR=""
CFG_PRESET=""
CFG_HAS_TARGET="false"
CFG_HAS_OUTPUT_DIR="false"
CFG_HAS_PRESET="false"

SYSTEMD_RUNTIME_EXPLICIT="false"
SYSTEMD_MYSQL_EXPLICIT="false"
LAYOUT_EXPLICIT="false"

if [[ -z "${JAVA_HOME:-}" && -x "$DEFAULT_JAVA_HOME/bin/java" ]]; then
  export JAVA_HOME="$DEFAULT_JAVA_HOME"
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "missing required command: $command_name" >&2
    exit 1
  fi
}

usage() {
  cat <<'EOF'
Usage: scripts/build-release-image.sh [OUTPUT_DIR] [options]
  --preset systemd-upgrade
  --target docker|systemd
  --systemd-runtime host-jre|bundled-jre
  --systemd-mysql-example yes|no
  --layout mirror

With no CLI arguments, the script loads config from scripts/build-release-image.conf.
EOF
}

fail_usage() {
  local message="$1"
  echo "$message" >&2
  usage >&2
  exit 1
}

ensure_java_21() {
  local version_output
  version_output="$(java -version 2>&1 || true)"
  if [[ "$version_output" != *'"21.'* && "$version_output" != *'"21"'* ]]; then
    echo "java 21 is required to build this project" >&2
    echo "$version_output" >&2
    exit 1
  fi
}

apply_preset() {
  local preset_name="$1"
  case "$preset_name" in
    systemd-upgrade)
      TARGET="systemd"
      SYSTEMD_RUNTIME="host-jre"
      SYSTEMD_MYSQL_EXAMPLE="no"
      LAYOUT="mirror"
      SYSTEMD_RUNTIME_EXPLICIT="true"
      SYSTEMD_MYSQL_EXPLICIT="true"
      LAYOUT_EXPLICIT="true"
      ;;
    *)
      fail_usage "unsupported --preset: $preset_name"
      ;;
  esac
}

apply_defaults() {
  TARGET="$DEFAULT_TARGET"
  SYSTEMD_RUNTIME="$DEFAULT_SYSTEMD_RUNTIME"
  SYSTEMD_MYSQL_EXAMPLE="$DEFAULT_SYSTEMD_MYSQL_EXAMPLE"
  LAYOUT="$DEFAULT_LAYOUT"
  OUTPUT_DIR=""
  PRESET=""
  SYSTEMD_RUNTIME_EXPLICIT="false"
  SYSTEMD_MYSQL_EXPLICIT="false"
  LAYOUT_EXPLICIT="false"
}

load_config_file() {
  local line=""
  local line_number=0
  local key=""
  local value=""

  if [[ ! -f "$CONFIG_FILE" ]]; then
    fail_usage "config file not found: $CONFIG_FILE"
  fi

  echo "==> No CLI args provided, loading config from $CONFIG_FILE"

  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ "$line" != *=* ]]; then
      fail_usage "invalid config line $line_number in $CONFIG_FILE: $line"
    fi
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      PRESET)
        CFG_PRESET="$value"
        if [[ -n "$value" ]]; then
          CFG_HAS_PRESET="true"
        fi
        ;;
      OUTPUT_DIR)
        CFG_OUTPUT_DIR="$value"
        if [[ -n "$value" ]]; then
          CFG_HAS_OUTPUT_DIR="true"
        fi
        ;;
      TARGET)
        CFG_TARGET="$value"
        if [[ -n "$value" ]]; then
          CFG_HAS_TARGET="true"
        fi
        ;;
      SYSTEMD_RUNTIME)
        CFG_SYSTEMD_RUNTIME="$value"
        ;;
      SYSTEMD_MYSQL_EXAMPLE)
        CFG_SYSTEMD_MYSQL_EXAMPLE="$value"
        ;;
      LAYOUT)
        CFG_LAYOUT="$value"
        ;;
      *)
        fail_usage "unsupported config key in $CONFIG_FILE: $key"
        ;;
    esac
  done < "$CONFIG_FILE"

  if [[ "$CFG_HAS_OUTPUT_DIR" != "true" ]]; then
    fail_usage "config file must set OUTPUT_DIR: $CONFIG_FILE"
  fi
  if [[ "$CFG_HAS_TARGET" != "true" && "$CFG_HAS_PRESET" != "true" ]]; then
    fail_usage "config file must set TARGET or PRESET: $CONFIG_FILE"
  fi
}

parse_args() {
  require_option_value() {
    local option_name="$1"
    local option_value="${2-}"
    if [[ -z "$option_value" || "$option_value" == -* ]]; then
      fail_usage "missing value for $option_name"
    fi
  }

  apply_defaults

  if [[ $# -eq 0 ]]; then
    load_config_file

    if [[ -n "$CFG_PRESET" ]]; then
      PRESET="$CFG_PRESET"
      apply_preset "$PRESET"
    fi
    if [[ -n "$CFG_TARGET" ]]; then
      TARGET="$CFG_TARGET"
    fi
    if [[ -n "$CFG_SYSTEMD_RUNTIME" ]]; then
      SYSTEMD_RUNTIME="$CFG_SYSTEMD_RUNTIME"
      SYSTEMD_RUNTIME_EXPLICIT="true"
    fi
    if [[ -n "$CFG_SYSTEMD_MYSQL_EXAMPLE" ]]; then
      SYSTEMD_MYSQL_EXAMPLE="$CFG_SYSTEMD_MYSQL_EXAMPLE"
      SYSTEMD_MYSQL_EXPLICIT="true"
    fi
    if [[ -n "$CFG_LAYOUT" ]]; then
      LAYOUT="$CFG_LAYOUT"
      LAYOUT_EXPLICIT="true"
    fi
    OUTPUT_DIR="$CFG_OUTPUT_DIR"
    return
  fi

  while [[ $# -gt 0 ]]; do
    case "$1" in
      -h|--help)
        usage
        exit 0
        ;;
      --preset)
        require_option_value "--preset" "${2-}"
        ARG_PRESET="$2"
        shift 2
        ;;
      --target)
        require_option_value "--target" "${2-}"
        ARG_TARGET="$2"
        shift 2
        ;;
      --systemd-runtime)
        require_option_value "--systemd-runtime" "${2-}"
        ARG_SYSTEMD_RUNTIME="$2"
        shift 2
        ;;
      --systemd-mysql-example)
        require_option_value "--systemd-mysql-example" "${2-}"
        ARG_SYSTEMD_MYSQL_EXAMPLE="$2"
        shift 2
        ;;
      --layout)
        require_option_value "--layout" "${2-}"
        ARG_LAYOUT="$2"
        shift 2
        ;;
      -*)
        fail_usage "unknown argument: $1"
        ;;
      *)
        if [[ -n "$ARG_OUTPUT_DIR" ]]; then
          fail_usage "unexpected extra positional argument: $1"
        fi
        ARG_OUTPUT_DIR="$1"
        shift
        ;;
    esac
  done

  if [[ -n "$ARG_PRESET" ]]; then
    PRESET="$ARG_PRESET"
    apply_preset "$PRESET"
  fi
  if [[ -n "$ARG_TARGET" ]]; then
    TARGET="$ARG_TARGET"
  fi
  if [[ -n "$ARG_SYSTEMD_RUNTIME" ]]; then
    SYSTEMD_RUNTIME="$ARG_SYSTEMD_RUNTIME"
    SYSTEMD_RUNTIME_EXPLICIT="true"
  fi
  if [[ -n "$ARG_SYSTEMD_MYSQL_EXAMPLE" ]]; then
    SYSTEMD_MYSQL_EXAMPLE="$ARG_SYSTEMD_MYSQL_EXAMPLE"
    SYSTEMD_MYSQL_EXPLICIT="true"
  fi
  if [[ -n "$ARG_LAYOUT" ]]; then
    LAYOUT="$ARG_LAYOUT"
    LAYOUT_EXPLICIT="true"
  fi
  OUTPUT_DIR="${ARG_OUTPUT_DIR:-${DICSERVER_RELEASE_DIR:-$DEFAULT_OUTPUT_DIR}}"
}

validate_args() {
  if [[ "$TARGET" != "docker" && "$TARGET" != "systemd" ]]; then
    fail_usage "unsupported --target: $TARGET"
  fi
  if [[ "$SYSTEMD_RUNTIME" != "host-jre" && "$SYSTEMD_RUNTIME" != "bundled-jre" ]]; then
    fail_usage "unsupported --systemd-runtime: $SYSTEMD_RUNTIME"
  fi
  if [[ "$SYSTEMD_MYSQL_EXAMPLE" != "yes" && "$SYSTEMD_MYSQL_EXAMPLE" != "no" ]]; then
    fail_usage "unsupported --systemd-mysql-example: $SYSTEMD_MYSQL_EXAMPLE"
  fi
  if [[ "$LAYOUT" != "mirror" ]]; then
    fail_usage "unsupported --layout: $LAYOUT"
  fi
  if [[ "$TARGET" == "docker" && ( "$SYSTEMD_RUNTIME_EXPLICIT" == "true" || "$SYSTEMD_MYSQL_EXPLICIT" == "true" || "$LAYOUT_EXPLICIT" == "true" ) ]]; then
    fail_usage "systemd-specific options are only valid with --target systemd"
  fi
}

build_application_jar() {
  require_command java
  test -x "$MVNW_BIN" || {
    echo "maven wrapper is not executable: $MVNW_BIN" >&2
    exit 1
  }
  ensure_java_21

  echo "==> Building application jar"
  "$MVNW_BIN" clean package -DskipTests

  test -f "$JAR_PATH" || {
    echo "built jar not found: $JAR_PATH" >&2
    exit 1
  }
}

copy_application_config_example() {
  local output_dir="$1"
  mkdir -p "$output_dir"
  cp "$APPLICATION_CONFIG_EXAMPLE_PATH" "$output_dir/application.yml.example"
}

write_release_compose() {
  cat > "$OUTPUT_DIR/docker-compose.yml" <<EOF
services:
  mysql:
    image: mysql:8.0
    container_name: dicserver-mysql
    environment:
      MYSQL_ROOT_PASSWORD: \${DB_PASSWORD:-change-me}
      MYSQL_DATABASE: dictionary
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  dicserver:
    image: $IMAGE_REF
    container_name: dicserver-app
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:mysql://mysql:3306/dictionary?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
      DB_USERNAME: root
      DB_PASSWORD: \${DB_PASSWORD:-change-me}
      APP_AUTH_JWT_SECRET: \${APP_AUTH_JWT_SECRET:?APP_AUTH_JWT_SECRET must be set}
      APP_AUTH_ACCESS_TOKEN_TTL_SECONDS: \${APP_AUTH_ACCESS_TOKEN_TTL_SECONDS:-900}
      APP_AUTH_REFRESH_TOKEN_TTL_SECONDS: \${APP_AUTH_REFRESH_TOKEN_TTL_SECONDS:-2592000}
      TENCENT_SECRET_ID: \${TENCENT_SECRET_ID:-}
      TENCENT_SECRET_KEY: \${TENCENT_SECRET_KEY:-}
      TENCENT_API_KEY: \${TENCENT_API_KEY:-}
      DEEPSEEK_API_KEY: \${DEEPSEEK_API_KEY:-}
      BAILIAN_API_KEY: \${BAILIAN_API_KEY:-}
      ANTHROPIC_API_KEY: \${ANTHROPIC_API_KEY:-}
      MIMO_TTS_BASE_URL: \${MIMO_TTS_BASE_URL:-https://api.xiaomimimo.com/v1}
      MIMO_TTS_API_KEY: \${MIMO_TTS_API_KEY:-}
      MIMO_TTS_MODEL: \${MIMO_TTS_MODEL:-mimo-v2.5-tts}
      MIMO_TTS_VOICE: \${MIMO_TTS_VOICE:-Mia}
      MIMO_TTS_FORMAT: \${MIMO_TTS_FORMAT:-mp3}
      RATE_LIMIT_REQUESTS_PER_MINUTE: \${RATE_LIMIT_REQUESTS_PER_MINUTE:-60}
      CLEANUP_SEARCH_HISTORY_RETENTION_DAYS: \${CLEANUP_SEARCH_HISTORY_RETENTION_DAYS:-90}
      AUDIO_STORAGE_DIR: /app/audio-data
      TTS_PROVIDER: \${TTS_PROVIDER:-qwen3}
      TTS_BASE_URL: \${TTS_BASE_URL:-http://host.docker.internal:8000}
      TTS_API_KEY: \${TTS_API_KEY:-}
      TTS_SPEAKER: \${TTS_SPEAKER:-vivian}
      TTS_QWEN3_GENERATION_MAX_NEW_TOKENS: \${TTS_QWEN3_GENERATION_MAX_NEW_TOKENS:-1024}
      TTS_QWEN3_GENERATION_TEMPERATURE: \${TTS_QWEN3_GENERATION_TEMPERATURE:-0.1}
      TTS_QWEN3_GENERATION_TOP_K: \${TTS_QWEN3_GENERATION_TOP_K:-20}
      TTS_QWEN3_GENERATION_TOP_P: \${TTS_QWEN3_GENERATION_TOP_P:-0.9}
      TTS_QWEN3_GENERATION_REPETITION_PENALTY: \${TTS_QWEN3_GENERATION_REPETITION_PENALTY:-1.05}
      AUDIO_GENERATION_SCHEDULER_WINDOW_START: \${AUDIO_GENERATION_SCHEDULER_WINDOW_START:-00:00}
      AUDIO_GENERATION_SCHEDULER_WINDOW_END: \${AUDIO_GENERATION_SCHEDULER_WINDOW_END:-02:00}
      AUDIO_GENERATION_SCHEDULER_TICK_SECONDS: \${AUDIO_GENERATION_SCHEDULER_TICK_SECONDS:-300}
      AUDIO_GENERATION_SCHEDULER_ZONE_ID: \${AUDIO_GENERATION_SCHEDULER_ZONE_ID:-Asia/Shanghai}
      TENCENT_TTS_SECRET_ID: \${TENCENT_TTS_SECRET_ID:-}
      TENCENT_TTS_SECRET_KEY: \${TENCENT_TTS_SECRET_KEY:-}
      TENCENT_TTS_APP_ID: \${TENCENT_TTS_APP_ID:-}
    depends_on:
      mysql:
        condition: service_healthy
    extra_hosts:
      - "host.docker.internal:host-gateway"
    volumes:
      - ./config:/app/config:ro
      - audio-data:/app/audio-data

volumes:
  mysql-data:
  audio-data:
EOF
}

write_release_runner() {
  cat > "$OUTPUT_DIR/run-release.sh" <<EOF
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="\$(cd "\$(dirname "\$0")" && pwd)"

IMAGE_TAR="./$IMAGE_ARCHIVE_NAME"
COMPOSE_FILE="./docker-compose.yml"
ENV_FILE="./.env"
APP_CONFIG_FILE="./config/application.yml"
APP_IMAGE="$IMAGE_REF"
MYSQL_IMAGE="mysql:8.0"
APP_SERVICE="dicserver"
APP_CONTAINER_NAME="dicserver-app"
APP_STARTUP_TIMEOUT_SECONDS="\${APP_STARTUP_TIMEOUT_SECONDS:-90}"
APP_STARTUP_POLL_SECONDS="\${APP_STARTUP_POLL_SECONDS:-2}"

log() {
  echo "[INFO] \$*"
}

die() {
  echo "[ERROR] \$*" >&2
  exit 1
}

require_file() {
  local path="\$1"
  local label="\$2"

  [ -f "\$path" ] || die "missing \$label: \$path"
}

require_command() {
  local name="\$1"
  command -v "\$name" >/dev/null 2>&1 || die "missing required command: \$name"
}

wait_for_application() {
  local deadline="\$((SECONDS + APP_STARTUP_TIMEOUT_SECONDS))"
  local status=""

  while (( SECONDS < deadline )); do
    status="\$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "\$APP_CONTAINER_NAME" 2>/dev/null || true)"
    case "\$status" in
      healthy|running)
        log "Application container state: \$status"
        return 0
        ;;
      starting|created|restarting|"")
        sleep "\$APP_STARTUP_POLL_SECONDS"
        ;;
      unhealthy|exited|dead|removing)
        log "Application container state: \$status"
        docker compose logs --tail=80 "\$APP_SERVICE" >&2 || true
        die "application failed to become healthy"
        ;;
      *)
        log "Application container state: \$status"
        sleep "\$APP_STARTUP_POLL_SECONDS"
        ;;
    esac
  done

  log "Application container did not become healthy within \${APP_STARTUP_TIMEOUT_SECONDS}s"
  docker compose logs --tail=80 "\$APP_SERVICE" >&2 || true
  die "application failed to become healthy"
}

main() {
  cd "\$SCRIPT_DIR"

  require_command docker
  docker compose version >/dev/null 2>&1 || die "docker compose is not available"
  docker info >/dev/null 2>&1 || die "cannot connect to the Docker daemon"

  require_file "\$IMAGE_TAR" "image archive"
  require_file "\$COMPOSE_FILE" "compose file"
  require_file "\$ENV_FILE" ".env file"
  require_file "\$APP_CONFIG_FILE" "application config file"

  log "Loading application image from \$IMAGE_TAR"
  docker load -i "\$IMAGE_TAR" >/dev/null

  docker image inspect "\$APP_IMAGE" >/dev/null 2>&1 || \\
    die "loaded image archive does not provide required tag: \$APP_IMAGE"

  if ! docker image inspect "\$MYSQL_IMAGE" >/dev/null 2>&1; then
    log "Pulling missing dependency image \$MYSQL_IMAGE"
    docker pull "\$MYSQL_IMAGE" >/dev/null
  fi

  log "Starting or replacing mysql and dicserver containers"
  docker compose up -d --force-recreate --remove-orphans

  log "Waiting for dicserver application health"
  wait_for_application

  log "Current compose status"
  docker compose ps
}

main "\$@"
EOF
  chmod +x "$OUTPUT_DIR/run-release.sh"
}

write_docker_runbook() {
  cat > "$OUTPUT_DIR/RUN.md" <<EOF
# DicServer Release Package

## Files

- \`$IMAGE_ARCHIVE_NAME\`: Docker image archive
- \`.env.example\`: environment variable template
- \`config/application.yml.example\`: external LLM provider topology template
- \`docker-compose.yml\`: runtime compose file using prebuilt image \`$IMAGE_REF\`
- \`run-release.sh\`: one-command Docker runner with startup validation

## Run

1. Prepare environment variables:

\`\`\`bash
cp .env.example .env
\`\`\`

2. Prepare the external provider topology:

\`\`\`bash
cp config/application.yml.example config/application.yml
\`\`\`

3. Start the stack:

\`\`\`bash
./run-release.sh
\`\`\`

4. Manual fallback if you do not want the helper script:

\`\`\`bash
docker load -i $IMAGE_ARCHIVE_NAME
docker compose up -d
\`\`\`

## Notes

- The compose file uses \`image: $IMAGE_REF\` and will not build locally.
- \`run-release.sh\` validates \`.env\` and \`config/application.yml\`, then waits for the dicserver container to become healthy before returning.
- The compose file mounts \`./config\` into \`/app/config\`, so Spring Boot will auto-load \`/app/config/application.yml\`.
- If Qwen3-TTS runs on the host, keep \`TTS_BASE_URL=http://host.docker.internal:8000\`.
- Override the Qwen TTS compensation window with \`AUDIO_GENERATION_SCHEDULER_WINDOW_START\`, \`AUDIO_GENERATION_SCHEDULER_WINDOW_END\`, \`AUDIO_GENERATION_SCHEDULER_TICK_SECONDS\`, and \`AUDIO_GENERATION_SCHEDULER_ZONE_ID\` when needed.
EOF
}

write_systemd_env_files() {
  write_systemd_env_content() {
    local output_path="$1"
    : > "$output_path"
    while IFS= read -r line || [[ -n "$line" ]]; do
      case "$line" in
        DB_URL=*)
          line="DB_URL=jdbc:mysql://127.0.0.1:3306/dictionary?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
          ;;
        AUDIO_STORAGE_DIR=*)
          line="AUDIO_STORAGE_DIR=/var/lib/dicserver/audio-data"
          ;;
        "# Provider topology lives in config/application.yml.example.")
          line="# Provider topology lives in /etc/dicserver/application.yml.example."
          ;;
      esac
      printf '%s\n' "$line" >> "$output_path"
    done < "$REPO_ROOT/.env.example"
  }

  mkdir -p "$OUTPUT_DIR/etc/dicserver"
  write_systemd_env_content "$OUTPUT_DIR/etc/dicserver/dicserver.env"
  write_systemd_env_content "$OUTPUT_DIR/etc/dicserver/dicserver.env.example"
}

write_systemd_application_config_files() {
  mkdir -p "$OUTPUT_DIR/etc/dicserver"
  cp "$APPLICATION_CONFIG_EXAMPLE_PATH" "$OUTPUT_DIR/etc/dicserver/application.yml"
  cp "$APPLICATION_CONFIG_EXAMPLE_PATH" "$OUTPUT_DIR/etc/dicserver/application.yml.example"
}

write_systemd_service() {
  mkdir -p "$OUTPUT_DIR/etc/systemd/system"
  cat > "$OUTPUT_DIR/etc/systemd/system/dicserver.service" <<'EOF'
[Unit]
Description=DicServer
After=network.target
Wants=network.target

[Service]
Type=simple
WorkingDirectory=/opt/dicserver
User=dicserver
Group=dicserver
StateDirectory=dicserver
EnvironmentFile=/etc/dicserver/dicserver.env
ExecStart=/opt/dicserver/bin/run.sh
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
}

write_systemd_run_scripts() {
  mkdir -p "$OUTPUT_DIR/opt/dicserver/bin"
  cat > "$OUTPUT_DIR/opt/dicserver/bin/run.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
cd /opt/dicserver
if [[ -x /opt/dicserver/runtime/bin/java ]]; then
  exec /opt/dicserver/runtime/bin/java -jar /opt/dicserver/app.jar --spring.config.additional-location=file:/etc/dicserver/application.yml
fi
if ! command -v java >/dev/null 2>&1; then
  echo "java is required on PATH for --systemd-runtime host-jre" >&2
  exit 1
fi
exec java -jar /opt/dicserver/app.jar --spring.config.additional-location=file:/etc/dicserver/application.yml
EOF
  cat > "$OUTPUT_DIR/opt/dicserver/bin/healthcheck.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health >/dev/null
EOF
  chmod +x "$OUTPUT_DIR/opt/dicserver/bin/run.sh" "$OUTPUT_DIR/opt/dicserver/bin/healthcheck.sh"
}

copy_systemd_deploy_templates() {
  [[ -f "$SYSTEMD_DEPLOY_SCRIPT_TEMPLATE_PATH" ]] || {
    echo "missing systemd deploy script template: $SYSTEMD_DEPLOY_SCRIPT_TEMPLATE_PATH" >&2
    exit 1
  }
  [[ -f "$SYSTEMD_DEPLOY_CONFIG_TEMPLATE_PATH" ]] || {
    echo "missing systemd deploy config template: $SYSTEMD_DEPLOY_CONFIG_TEMPLATE_PATH" >&2
    exit 1
  }

  cp "$SYSTEMD_DEPLOY_SCRIPT_TEMPLATE_PATH" "$OUTPUT_DIR/deploy-systemd-release.sh"
  if [[ ! -f "$OUTPUT_DIR/deploy-systemd-release.conf" ]]; then
    cp "$SYSTEMD_DEPLOY_CONFIG_TEMPLATE_PATH" "$OUTPUT_DIR/deploy-systemd-release.conf"
  fi
  chmod +x "$OUTPUT_DIR/deploy-systemd-release.sh"
}

copy_systemd_runtime_if_needed() {
  if [[ "$SYSTEMD_RUNTIME" != "bundled-jre" ]]; then
    return
  fi

  local runtime_source="${DICSERVER_SYSTEMD_JRE_DIR:-${JAVA_HOME:-$DEFAULT_JAVA_HOME}}"
  if [[ ! -x "$runtime_source/bin/java" ]]; then
    echo "bundled JRE is missing bin/java: $runtime_source" >&2
    exit 1
  fi

  mkdir -p "$OUTPUT_DIR/opt/dicserver/runtime"
  cp -R "$runtime_source"/. "$OUTPUT_DIR/opt/dicserver/runtime/"
}

write_systemd_mysql_examples() {
  mkdir -p "$OUTPUT_DIR/etc/dicserver" "$OUTPUT_DIR/usr/share/dicserver/mysql"
  cat > "$OUTPUT_DIR/etc/dicserver/mysql.env.example" <<'EOF'
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=dictionary
MYSQL_USER=dicserver
MYSQL_PASSWORD=change-me
EOF

  cat > "$OUTPUT_DIR/usr/share/dicserver/mysql/create-database.sql" <<'EOF'
CREATE DATABASE IF NOT EXISTS dictionary CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'dicserver'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON dictionary.* TO 'dicserver'@'%';
FLUSH PRIVILEGES;
EOF

  cat > "$OUTPUT_DIR/usr/share/dicserver/mysql/mysql-config.example.cnf" <<'EOF'
[mysqld]
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default-time-zone=+00:00
max_connections=200
EOF
}

cleanup_systemd_conditional_outputs() {
  rm -rf "$OUTPUT_DIR/opt/dicserver/runtime"
  rm -f "$OUTPUT_DIR/etc/dicserver/mysql.env.example"
  rm -rf "$OUTPUT_DIR/usr/share/dicserver"
}

write_systemd_runbook() {
  mkdir -p "$OUTPUT_DIR/usr/share/doc/dicserver"
  {
    cat <<'EOF'
# DicServer Systemd Package

## Setup

1. From the package root, run the deploy script:

```bash
sudo bash ./deploy-systemd-release.sh
```

2. On first deployment, if `/etc/dicserver/dicserver.env` or `/etc/dicserver/application.yml` did not already exist, the script installs the packaged file(s) and stops before restarting the service. Edit/review them, then rerun:

```bash
sudo bash ./deploy-systemd-release.sh
```

3. When you want to update DicServer on the same host, unpack the new package and run the same deploy script again. It will keep the existing `/etc/dicserver/dicserver.env` and `/etc/dicserver/application.yml` by default and restart the service with the new files.

4. If you intentionally want to replace the target env file from the package, use:

```bash
sudo bash ./deploy-systemd-release.sh --overwrite-env
```

5. Manual fallback steps if you do not want to use the deploy script:

```bash
sudo cp etc/* /etc/ -r
sudo cp opt/* /opt/ -r
sudo cp usr/* /usr/ -r
sudo cp var/* /var/ -r
sudo useradd --system --home /opt/dicserver --shell /usr/sbin/nologin dicserver
sudo systemctl daemon-reload
sudo systemctl enable --now dicserver
```

## Notes

EOF
    if [[ "$SYSTEMD_RUNTIME" == "host-jre" ]]; then
      cat <<'EOF'
- host-JRE mode requires Java 21 on the target host before you start `dicserver`.
EOF
    else
      cat <<'EOF'
- This package includes a bundled JRE under `/opt/dicserver/runtime`.
EOF
    fi

    cat <<'EOF'
- The service loads provider topology from `/etc/dicserver/application.yml`. The packaged template is also refreshed at `/etc/dicserver/application.yml.example`.
EOF

    if [[ "$SYSTEMD_MYSQL_EXAMPLE" == "yes" ]]; then
      cat <<'EOF'
- Optional MySQL example files are included at `/etc/dicserver/mysql.env.example` and `/usr/share/dicserver/mysql/`. Use them when provisioning a local MySQL instance for DicServer.
EOF
    else
      cat <<'EOF'
- Rebuild with `--systemd-mysql-example yes` if you want example MySQL provisioning files at `/etc/dicserver/mysql.env.example` and `/usr/share/dicserver/mysql/`.
EOF
    fi

    cat <<'EOF'

## Verify

```bash
sudo systemctl status dicserver
sudo journalctl -u dicserver -f
curl http://127.0.0.1:8080/actuator/health
```
EOF
  } > "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
}

write_systemd_layout() {
  cleanup_systemd_conditional_outputs
  mkdir -p "$OUTPUT_DIR/opt/dicserver"
  cp "$JAR_PATH" "$OUTPUT_DIR/opt/dicserver/app.jar"

  copy_systemd_runtime_if_needed
  write_systemd_run_scripts
  copy_systemd_deploy_templates
  write_systemd_env_files
  write_systemd_application_config_files
  write_systemd_service
  write_systemd_runbook
  if [[ "$SYSTEMD_MYSQL_EXAMPLE" == "yes" ]]; then
    write_systemd_mysql_examples
  fi

  mkdir -p "$OUTPUT_DIR/var/lib/dicserver/audio-data"
  touch "$OUTPUT_DIR/var/lib/dicserver/audio-data/.gitkeep"
}

parse_args "$@"
validate_args
IMAGE_ARCHIVE_PATH="$OUTPUT_DIR/$IMAGE_ARCHIVE_NAME"

echo "==> Using repository: $REPO_ROOT"
echo "==> Output directory: $OUTPUT_DIR"
echo "==> Target: $TARGET"
echo "==> Image reference: $IMAGE_REF"

mkdir -p "$OUTPUT_DIR"

case "$TARGET" in
  docker)
    build_application_jar
    require_command "$DOCKER_BIN"

    echo "==> Building Docker image"
    "$DOCKER_BIN" build -f "$REPO_ROOT/Dockerfile.release" -t "$IMAGE_REF" "$REPO_ROOT"

    echo "==> Exporting Docker image archive"
    rm -f "$IMAGE_ARCHIVE_PATH"
    "$DOCKER_BIN" save -o "$IMAGE_ARCHIVE_PATH" "$IMAGE_REF"

    echo "==> Writing runtime files"
    cp "$REPO_ROOT/.env.example" "$OUTPUT_DIR/.env.example"
    copy_application_config_example "$OUTPUT_DIR/config"
    write_release_compose
    write_release_runner
    write_docker_runbook
    ;;
  systemd)
    build_application_jar

    echo "==> Writing systemd runtime files"
    write_systemd_layout
    ;;
  *)
    fail_usage "unsupported --target: $TARGET"
    ;;
esac

echo "==> Release package ready"
echo "    $OUTPUT_DIR"
