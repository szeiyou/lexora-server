#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_ROOT="$SCRIPT_DIR"
CONFIG_FILE="$PACKAGE_ROOT/deploy-systemd-release.conf"

SERVICE_NAME="dicserver"
SERVICE_USER="dicserver"
SERVICE_GROUP="dicserver"

DEFAULT_DEPLOY_MODE="upgrade"
DEFAULT_REMOTE_PORT="22"
DEFAULT_REMOTE_USER="root"
DEFAULT_REMOTE_DEPLOY_ROOT="/root/dicserver-deploy"
DEFAULT_REMOTE_CLEANUP="yes"
DEFAULT_SSH_STRICT_HOST_KEY_CHECKING="no"

MODE="local"
DEPLOY_MODE="$DEFAULT_DEPLOY_MODE"
REMOTE_HOST=""
REMOTE_PORT="$DEFAULT_REMOTE_PORT"
REMOTE_USER="$DEFAULT_REMOTE_USER"
REMOTE_PASSWORD=""
REMOTE_DEPLOY_ROOT="$DEFAULT_REMOTE_DEPLOY_ROOT"
REMOTE_CLEANUP="$DEFAULT_REMOTE_CLEANUP"
SSH_STRICT_HOST_KEY_CHECKING="$DEFAULT_SSH_STRICT_HOST_KEY_CHECKING"
OVERWRITE_ENV="no"
FORCE_RESTART="no"
ENV_READY="yes"
APP_READY="yes"
LOCAL_ARCHIVE=""
SSH_OPTIONS=()
SCP_OPTIONS=()

usage() {
  cat <<'USAGE'
Usage: bash ./deploy-systemd-release.sh [options]

Deploy the current systemd package from the local machine to a remote host.

Modes:
  upgrade  Update only /opt/dicserver/app.jar, /opt/dicserver/bin/*,
           and /etc/systemd/system/dicserver.service.
           Keeps the existing runtime, env, and /var/lib/dicserver data.
  full     First-time deployment or full package sync. In addition to upgrade
           files, it aligns runtime, example config files, and packaged docs.

Config behavior:
  - No CLI arguments: load ./deploy-systemd-release.conf
  - Any CLI argument: ignore ./deploy-systemd-release.conf completely

Options:
  --mode upgrade|full                 Deploy mode, default upgrade
  --host <host>                       Remote host or IP
  --port <port>                       Remote SSH port, default 22
  --user <user>                       Remote SSH user, default root
  --password <password>               Remote SSH password
  --remote-deploy-root <path>         Remote temp deploy root
  --remote-cleanup yes|no             Remove remote temp files after success
  --ssh-strict-host-key-checking yes|no
                                       SSH StrictHostKeyChecking value
  --overwrite-env                     Replace /etc/dicserver/dicserver.env remotely
  --force-restart                     Restart remotely even after dicserver.env or
                                       application.yml was just installed or replaced
  --remote-install                    Internal mode used on the remote host
  -h, --help                          Show this help text
USAGE
}

fail() {
  echo "$*" >&2
  exit 1
}

cleanup_local_archive() {
  if [[ -n "$LOCAL_ARCHIVE" && -f "$LOCAL_ARCHIVE" ]]; then
    rm -f "$LOCAL_ARCHIVE"
  fi
}
trap cleanup_local_archive EXIT

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    fail "missing required command: $command_name"
  fi
}

require_root() {
  if [[ $EUID -ne 0 ]]; then
    fail "please run as root"
  fi
}

copy_tree_contents() {
  local src="$1"
  local dest="$2"
  mkdir -p "$dest"
  cp -a "$src"/. "$dest"/
}

require_path() {
  local path="$1"
  [[ -e "$path" ]] || fail "missing packaged file: $path"
}

validate_yes_no() {
  local field_name="$1"
  local field_value="$2"
  if [[ "$field_value" != "yes" && "$field_value" != "no" ]]; then
    fail "$field_name must be yes or no, got: $field_value"
  fi
}

validate_mode() {
  case "$DEPLOY_MODE" in
    upgrade|full) ;;
    *)
      fail "DEPLOY_MODE must be upgrade or full, got: $DEPLOY_MODE"
      ;;
  esac
}

verify_package_root() {
  require_path "$PACKAGE_ROOT/opt/dicserver/app.jar"
  require_path "$PACKAGE_ROOT/opt/dicserver/bin/run.sh"
  require_path "$PACKAGE_ROOT/etc/dicserver/dicserver.env"
  require_path "$PACKAGE_ROOT/etc/dicserver/application.yml"
  require_path "$PACKAGE_ROOT/etc/systemd/system/dicserver.service"
}

package_has_runtime() {
  [[ -x "$PACKAGE_ROOT/opt/dicserver/runtime/bin/java" ]]
}

package_runtime_label() {
  if package_has_runtime; then
    echo "bundled-jre"
  else
    echo "host-jre"
  fi
}

remote_has_runtime() {
  [[ -x /opt/dicserver/runtime/bin/java ]]
}

remote_has_java_21() {
  local version_output

  if ! command -v java >/dev/null 2>&1; then
    return 1
  fi

  version_output="$(java -version 2>&1 || true)"
  [[ "$version_output" == *'"21.'* || "$version_output" == *'"21"'* ]]
}

host_java_version_line() {
  if command -v java >/dev/null 2>&1; then
    java -version 2>&1 | head -n1
  else
    echo "missing"
  fi
}

ensure_service_user() {
  if ! id -u "$SERVICE_USER" >/dev/null 2>&1; then
    useradd --system --home /opt/dicserver --shell /usr/sbin/nologin "$SERVICE_USER"
  fi
}

backup_existing_env() {
  if [[ -f /etc/dicserver/dicserver.env ]]; then
    cp -f /etc/dicserver/dicserver.env "/etc/dicserver/dicserver.env.bak.$(date +%Y%m%d%H%M%S)"
  fi
}

install_packaged_binaries() {
  mkdir -p /opt/dicserver
  rm -rf /opt/dicserver/bin
  mkdir -p /opt/dicserver/bin
  cp -f "$PACKAGE_ROOT/opt/dicserver/app.jar" /opt/dicserver/app.jar
  copy_tree_contents "$PACKAGE_ROOT/opt/dicserver/bin" /opt/dicserver/bin
  chmod +x /opt/dicserver/bin/run.sh
  if [[ -f /opt/dicserver/bin/healthcheck.sh ]]; then
    chmod +x /opt/dicserver/bin/healthcheck.sh
  fi
}

sync_opt_upgrade() {
  echo "==> Syncing /opt/dicserver for upgrade"
  install_packaged_binaries
}

sync_opt_full() {
  echo "==> Syncing /opt/dicserver for full deployment"
  install_packaged_binaries

  if package_has_runtime; then
    rm -rf /opt/dicserver/runtime
    mkdir -p /opt/dicserver/runtime
    copy_tree_contents "$PACKAGE_ROOT/opt/dicserver/runtime" /opt/dicserver/runtime
  else
    rm -rf /opt/dicserver/runtime
  fi
}

sync_etc_upgrade() {
  echo "==> Syncing systemd service for upgrade"
  mkdir -p /etc/dicserver /etc/systemd/system
  cp -f "$PACKAGE_ROOT/etc/systemd/system/dicserver.service" /etc/systemd/system/dicserver.service

  if [[ -f "$PACKAGE_ROOT/etc/dicserver/application.yml.example" ]]; then
    cp -f "$PACKAGE_ROOT/etc/dicserver/application.yml.example" /etc/dicserver/application.yml.example
  fi

  if [[ -f /etc/dicserver/application.yml ]]; then
    echo "existing /etc/dicserver/application.yml preserved"
    APP_READY="yes"
  else
    cp -f "$PACKAGE_ROOT/etc/dicserver/application.yml" /etc/dicserver/application.yml
    APP_READY="no"
    echo "installed packaged /etc/dicserver/application.yml; review it before restarting dicserver"
  fi

  ENV_READY="yes"
}

sync_etc_full() {
  echo "==> Syncing /etc files for full deployment"
  mkdir -p /etc/dicserver /etc/systemd/system

  cp -f "$PACKAGE_ROOT/etc/systemd/system/dicserver.service" /etc/systemd/system/dicserver.service

  if [[ -f "$PACKAGE_ROOT/etc/dicserver/application.yml.example" ]]; then
    cp -f "$PACKAGE_ROOT/etc/dicserver/application.yml.example" /etc/dicserver/application.yml.example
  fi

  if [[ -f "$PACKAGE_ROOT/etc/dicserver/dicserver.env.example" ]]; then
    cp -f "$PACKAGE_ROOT/etc/dicserver/dicserver.env.example" /etc/dicserver/dicserver.env.example
  else
    rm -f /etc/dicserver/dicserver.env.example
  fi

  if [[ -f "$PACKAGE_ROOT/etc/dicserver/mysql.env.example" ]]; then
    cp -f "$PACKAGE_ROOT/etc/dicserver/mysql.env.example" /etc/dicserver/mysql.env.example
  else
    rm -f /etc/dicserver/mysql.env.example
  fi

  if [[ -f /etc/dicserver/application.yml ]]; then
    echo "existing /etc/dicserver/application.yml preserved"
    APP_READY="yes"
  else
    cp -f "$PACKAGE_ROOT/etc/dicserver/application.yml" /etc/dicserver/application.yml
    APP_READY="no"
    echo "installed packaged /etc/dicserver/application.yml; review it before restarting dicserver"
  fi

  if [[ -f /etc/dicserver/dicserver.env && "$OVERWRITE_ENV" != "yes" ]]; then
    echo "existing /etc/dicserver/dicserver.env preserved"
    ENV_READY="yes"
    return
  fi

  backup_existing_env
  cp -f "$PACKAGE_ROOT/etc/dicserver/dicserver.env" /etc/dicserver/dicserver.env
  ENV_READY="no"
  echo "installed packaged /etc/dicserver/dicserver.env; edit it before starting dicserver"
}

sync_usr_full() {
  echo "==> Syncing packaged docs/examples"
  rm -rf /usr/share/doc/dicserver /usr/share/dicserver

  if [[ -d "$PACKAGE_ROOT/usr/share/doc/dicserver" ]]; then
    mkdir -p /usr/share/doc/dicserver
    copy_tree_contents "$PACKAGE_ROOT/usr/share/doc/dicserver" /usr/share/doc/dicserver
  fi

  if [[ -d "$PACKAGE_ROOT/usr/share/dicserver" ]]; then
    mkdir -p /usr/share/dicserver
    copy_tree_contents "$PACKAGE_ROOT/usr/share/dicserver" /usr/share/dicserver
  fi
}

ensure_state_dirs() {
  mkdir -p /var/lib/dicserver/audio-data
  chown -R "$SERVICE_USER:$SERVICE_GROUP" /var/lib/dicserver
}

reload_service() {
  systemctl daemon-reload
  systemctl enable "$SERVICE_NAME" >/dev/null
}

wait_for_health() {
  local health_script="/opt/dicserver/bin/healthcheck.sh"

  for _ in $(seq 1 30); do
    if [[ -x "$health_script" ]]; then
      if "$health_script" >/dev/null 2>&1; then
        echo "healthcheck passed"
        return 0
      fi
    elif command -v curl >/dev/null 2>&1 && curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
      echo "healthcheck passed"
      return 0
    fi
    sleep 2
  done

  echo "healthcheck did not pass within timeout" >&2
  systemctl --no-pager --full status "$SERVICE_NAME" || true
  journalctl -u "$SERVICE_NAME" -n 50 --no-pager || true
  return 1
}

restart_if_ready() {
  reload_service

  if [[ "$ENV_READY" != "yes" && "$FORCE_RESTART" != "yes" ]]; then
    echo
    echo "deployment complete, but dicserver was not restarted"
    echo "reason: dicserver.env was newly installed or replaced"
    echo "next: edit /etc/dicserver/dicserver.env, then rerun this script"
    echo "or: use --force-restart if the packaged env is already correct"
    return 0
  fi

  if [[ "$APP_READY" != "yes" && "$FORCE_RESTART" != "yes" ]]; then
    echo
    echo "deployment complete, but dicserver was not restarted"
    echo "reason: application.yml was newly installed or replaced"
    echo "next: review /etc/dicserver/application.yml, then rerun this script"
    echo "or: use --force-restart if the packaged application.yml is already correct"
    return 0
  fi

  systemctl restart "$SERVICE_NAME"
  wait_for_health
}

verify_upgrade_prerequisites() {
  [[ -f /opt/dicserver/app.jar ]] || fail "upgrade requires existing /opt/dicserver/app.jar; use --mode full for first deployment"
  [[ -f /etc/systemd/system/dicserver.service ]] || fail "upgrade requires existing dicserver.service; use --mode full for first deployment"

  if remote_has_runtime; then
    return 0
  fi

  remote_has_java_21 || fail "upgrade requires existing /opt/dicserver/runtime/bin/java or host Java 21; current host does not satisfy this, use --mode full with a bundled-jre package"
}

verify_full_prerequisites() {
  if package_has_runtime; then
    return 0
  fi

  remote_has_java_21 || fail "full deployment of a host-jre package requires host Java 21 or a bundled-jre package"
}

run_remote_preflight() {
  echo "==> Remote install mode: $DEPLOY_MODE"
  echo "==> Package runtime: $(package_runtime_label)"
  if remote_has_runtime; then
    echo "==> Remote runtime: present at /opt/dicserver/runtime/bin/java"
  else
    echo "==> Remote runtime: not present"
  fi
  echo "==> Host java: $(host_java_version_line)"

  case "$DEPLOY_MODE" in
    upgrade)
      verify_upgrade_prerequisites
      ;;
    full)
      verify_full_prerequisites
      ;;
  esac
}

run_remote_install() {
  require_root
  verify_package_root
  ensure_service_user
  run_remote_preflight

  case "$DEPLOY_MODE" in
    upgrade)
      sync_opt_upgrade
      sync_etc_upgrade
      ;;
    full)
      sync_opt_full
      sync_etc_full
      sync_usr_full
      ;;
  esac

  ensure_state_dirs
  restart_if_ready

  echo
  systemctl --no-pager --full status "$SERVICE_NAME" || true
}

load_config_file() {
  local line=""
  local line_number=0
  local key=""
  local value=""

  if [[ ! -f "$CONFIG_FILE" ]]; then
    fail "config file not found: $CONFIG_FILE"
  fi

  echo "==> Loading config from $CONFIG_FILE"

  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ "$line" != *=* ]]; then
      fail "invalid config line $line_number in $CONFIG_FILE: $line"
    fi
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      DEPLOY_MODE)
        if [[ -n "$value" ]]; then
          DEPLOY_MODE="$value"
        fi
        ;;
      REMOTE_HOST)
        if [[ -n "$value" ]]; then
          REMOTE_HOST="$value"
        fi
        ;;
      REMOTE_PORT)
        if [[ -n "$value" ]]; then
          REMOTE_PORT="$value"
        fi
        ;;
      REMOTE_USER)
        if [[ -n "$value" ]]; then
          REMOTE_USER="$value"
        fi
        ;;
      REMOTE_PASSWORD)
        if [[ -n "$value" ]]; then
          REMOTE_PASSWORD="$value"
        fi
        ;;
      REMOTE_DEPLOY_ROOT)
        if [[ -n "$value" ]]; then
          REMOTE_DEPLOY_ROOT="$value"
        fi
        ;;
      OVERWRITE_ENV)
        if [[ -n "$value" ]]; then
          OVERWRITE_ENV="$value"
        fi
        ;;
      FORCE_RESTART)
        if [[ -n "$value" ]]; then
          FORCE_RESTART="$value"
        fi
        ;;
      REMOTE_CLEANUP)
        if [[ -n "$value" ]]; then
          REMOTE_CLEANUP="$value"
        fi
        ;;
      SSH_STRICT_HOST_KEY_CHECKING)
        if [[ -n "$value" ]]; then
          SSH_STRICT_HOST_KEY_CHECKING="$value"
        fi
        ;;
      *)
        fail "unsupported config key in $CONFIG_FILE: $key"
        ;;
    esac
  done < "$CONFIG_FILE"
}

parse_args() {
  local original_arg_count=$#

  require_option_value() {
    local option_name="$1"
    local option_value="${2-}"
    if [[ -z "$option_value" || "$option_value" == -* ]]; then
      fail "missing value for $option_name"
    fi
  }

  if [[ "${1-}" == "--remote-install" ]]; then
    MODE="remote-install"
    shift
  fi

  if [[ "$MODE" == "local" && $original_arg_count -eq 0 ]]; then
    load_config_file
    return
  fi

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --mode)
        require_option_value "--mode" "${2-}"
        DEPLOY_MODE="$2"
        shift 2
        ;;
      --host)
        require_option_value "--host" "${2-}"
        REMOTE_HOST="$2"
        shift 2
        ;;
      --port)
        require_option_value "--port" "${2-}"
        REMOTE_PORT="$2"
        shift 2
        ;;
      --user)
        require_option_value "--user" "${2-}"
        REMOTE_USER="$2"
        shift 2
        ;;
      --password)
        require_option_value "--password" "${2-}"
        REMOTE_PASSWORD="$2"
        shift 2
        ;;
      --remote-deploy-root)
        require_option_value "--remote-deploy-root" "${2-}"
        REMOTE_DEPLOY_ROOT="$2"
        shift 2
        ;;
      --remote-cleanup)
        require_option_value "--remote-cleanup" "${2-}"
        REMOTE_CLEANUP="$2"
        shift 2
        ;;
      --ssh-strict-host-key-checking)
        require_option_value "--ssh-strict-host-key-checking" "${2-}"
        SSH_STRICT_HOST_KEY_CHECKING="$2"
        shift 2
        ;;
      --overwrite-env)
        OVERWRITE_ENV="yes"
        shift
        ;;
      --force-restart)
        FORCE_RESTART="yes"
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        usage >&2
        fail "unknown option: $1"
        ;;
    esac
  done
}

validate_settings() {
  validate_mode
  validate_yes_no "OVERWRITE_ENV" "$OVERWRITE_ENV"
  validate_yes_no "FORCE_RESTART" "$FORCE_RESTART"
  validate_yes_no "REMOTE_CLEANUP" "$REMOTE_CLEANUP"
  validate_yes_no "SSH_STRICT_HOST_KEY_CHECKING" "$SSH_STRICT_HOST_KEY_CHECKING"

  if [[ "$MODE" == "remote-install" ]]; then
    return
  fi

  [[ -n "$REMOTE_HOST" ]] || fail "REMOTE_HOST is required"
  [[ -n "$REMOTE_USER" ]] || fail "REMOTE_USER is required"
  [[ -n "$REMOTE_PASSWORD" ]] || fail "REMOTE_PASSWORD is required"
  [[ -n "$REMOTE_DEPLOY_ROOT" ]] || fail "REMOTE_DEPLOY_ROOT is required"
  [[ "$REMOTE_PORT" =~ ^[0-9]+$ ]] || fail "REMOTE_PORT must be numeric: $REMOTE_PORT"
}

build_ssh_options() {
  SSH_OPTIONS=(-p "$REMOTE_PORT")
  SCP_OPTIONS=(-P "$REMOTE_PORT")

  if [[ "$SSH_STRICT_HOST_KEY_CHECKING" == "no" ]]; then
    SSH_OPTIONS+=(-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null)
    SCP_OPTIONS+=(-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null)
  else
    SSH_OPTIONS+=(-o StrictHostKeyChecking=yes)
    SCP_OPTIONS+=(-o StrictHostKeyChecking=yes)
  fi
}

run_local_deploy() {
  local release_id=""
  local remote_archive_path=""

  verify_package_root
  require_command sshpass
  require_command ssh
  require_command scp
  require_command tar

  build_ssh_options

  release_id="dicserver-systemd-$(date +%Y%m%d%H%M%S)"
  LOCAL_ARCHIVE="$(mktemp "/tmp/${release_id}.XXXXXX.tar.gz")"
  remote_archive_path="${REMOTE_DEPLOY_ROOT%/}/${release_id}.tar.gz"

  echo "==> Local deploy mode: $DEPLOY_MODE"
  echo "==> Package runtime: $(package_runtime_label)"
  echo "==> Packaging local release directory"
  tar -czf "$LOCAL_ARCHIVE" -C "$PACKAGE_ROOT" .

  echo "==> Preparing remote deploy root on ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DEPLOY_ROOT}"
  sshpass -p "$REMOTE_PASSWORD" ssh "${SSH_OPTIONS[@]}" "${REMOTE_USER}@${REMOTE_HOST}" \
    "mkdir -p '$REMOTE_DEPLOY_ROOT'"

  echo "==> Uploading package archive"
  sshpass -p "$REMOTE_PASSWORD" scp "${SCP_OPTIONS[@]}" "$LOCAL_ARCHIVE" \
    "${REMOTE_USER}@${REMOTE_HOST}:${remote_archive_path}"

  echo "==> Running remote install"
  sshpass -p "$REMOTE_PASSWORD" ssh "${SSH_OPTIONS[@]}" "${REMOTE_USER}@${REMOTE_HOST}" \
    /bin/bash -s -- "$REMOTE_DEPLOY_ROOT" "$release_id" "$remote_archive_path" "$DEPLOY_MODE" "$OVERWRITE_ENV" "$FORCE_RESTART" "$REMOTE_CLEANUP" <<'EOF'
set -euo pipefail

deploy_root="$1"
release_id="$2"
archive_path="$3"
deploy_mode="$4"
overwrite_env="$5"
force_restart="$6"
remote_cleanup="$7"
workdir="${deploy_root%/}/${release_id}"

mkdir -p "$workdir"
tar -xzf "$archive_path" -C "$workdir"
cd "$workdir"

cmd=(bash ./deploy-systemd-release.sh --remote-install --mode "$deploy_mode")
if [[ "$overwrite_env" == "yes" ]]; then
  cmd+=(--overwrite-env)
fi
if [[ "$force_restart" == "yes" ]]; then
  cmd+=(--force-restart)
fi
"${cmd[@]}"

if [[ "$remote_cleanup" == "yes" ]]; then
  rm -rf "$workdir" "$archive_path"
fi
EOF

  echo "==> Remote deployment completed"
}

parse_args "$@"
validate_settings

if [[ "$MODE" == "remote-install" ]]; then
  run_remote_install
else
  run_local_deploy
fi
