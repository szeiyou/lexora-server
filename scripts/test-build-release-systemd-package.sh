#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$REPO_ROOT/scripts/build-release-image.sh"
CONFIG_PATH="$REPO_ROOT/scripts/build-release-image.conf"
SYSTEMD_TEMPLATE_DIR="$REPO_ROOT/scripts/systemd-release-template"
DEPLOY_SCRIPT_TEMPLATE_PATH="$SYSTEMD_TEMPLATE_DIR/deploy-systemd-release.sh"
DEPLOY_CONFIG_TEMPLATE_PATH="$SYSTEMD_TEMPLATE_DIR/deploy-systemd-release.conf"
TMP_DIR="$(mktemp -d)"
OUTPUT_DIR="$TMP_DIR/output"
BUNDLED_OUTPUT_DIR="$TMP_DIR/output-bundled"
MYSQL_OUTPUT_DIR="$TMP_DIR/output-mysql"
PRESET_OUTPUT_DIR="$TMP_DIR/output-preset"
CONFIG_OUTPUT_DIR="$TMP_DIR/output-config"
CLI_OVERRIDE_OUTPUT_DIR="$TMP_DIR/output-cli-override"
STUB_BIN_DIR="$TMP_DIR/bin"
FAKE_JAVA_HOME="$TMP_DIR/fake-jdk"
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

for required_cmd in bash cat chmod cp dirname head mkdir rm touch; do
  ln -s "$(command -v "$required_cmd")" "$STUB_BIN_DIR/$required_cmd"
done

CALL_LOG="$TMP_DIR/calls.log"

cat > "$STUB_BIN_DIR/id" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "-u" ]]; then
  exit 1
fi
exit 0
EOF
chmod +x "$STUB_BIN_DIR/id"

cat > "$STUB_BIN_DIR/useradd" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf 'useradd:%s\n' "\$*" >> "$CALL_LOG"
exit 0
EOF
chmod +x "$STUB_BIN_DIR/useradd"

cat > "$STUB_BIN_DIR/systemctl" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf 'systemctl:%s\n' "\$*" >> "$CALL_LOG"
exit 0
EOF
chmod +x "$STUB_BIN_DIR/systemctl"

cat > "$STUB_BIN_DIR/journalctl" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf 'journalctl:%s\n' "\$*" >> "$CALL_LOG"
exit 0
EOF
chmod +x "$STUB_BIN_DIR/journalctl"

cat > "$STUB_BIN_DIR/chown" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf 'chown:%s\n' "\$*" >> "$CALL_LOG"
exit 0
EOF
chmod +x "$STUB_BIN_DIR/chown"

cat > "$STUB_BIN_DIR/curl" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf 'curl:%s\n' "\$*" >> "$CALL_LOG"
exit 0
EOF
chmod +x "$STUB_BIN_DIR/curl"

cat > "$STUB_BIN_DIR/sleep" <<EOF
#!/usr/bin/env bash
set -euo pipefail
exit 0
EOF
chmod +x "$STUB_BIN_DIR/sleep"

cat > "$STUB_BIN_DIR/seq" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo "1"
exit 0
EOF
chmod +x "$STUB_BIN_DIR/seq"

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
ln -s "$FAKE_JAVA_HOME/bin/java" "$STUB_BIN_DIR/java"

cat > "$STUB_BIN_DIR/mvnw" <<EOF
#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$REPO_ROOT/target"
touch "$REPO_ROOT/target/dic-server-0.0.1-SNAPSHOT.jar"
EOF
chmod +x "$STUB_BIN_DIR/mvnw"

cat > "$CONFIG_PATH" <<EOF
PRESET=systemd-upgrade
OUTPUT_DIR=$CONFIG_OUTPUT_DIR
EOF

CONFIG_STDOUT="$TMP_DIR/config.stdout"
JAVA_HOME="$FAKE_JAVA_HOME" \
PATH="$STUB_BIN_DIR" \
MVNW_BIN="$STUB_BIN_DIR/mvnw" \
"$SCRIPT_PATH" >"$CONFIG_STDOUT"

test -f "$CONFIG_OUTPUT_DIR/opt/dicserver/app.jar"
test -f "$CONFIG_OUTPUT_DIR/etc/systemd/system/dicserver.service"
grep -q "User=dicserver" "$CONFIG_OUTPUT_DIR/etc/systemd/system/dicserver.service"
grep -q "No CLI args provided, loading config" "$CONFIG_STDOUT"

cat > "$CONFIG_PATH" <<EOF
PRESET=systemd-upgrade
OUTPUT_DIR=$TMP_DIR/should-not-be-used
TARGET=broken
EOF

JAVA_HOME="$FAKE_JAVA_HOME" \
PATH="$STUB_BIN_DIR" \
MVNW_BIN="$STUB_BIN_DIR/mvnw" \
"$SCRIPT_PATH" "$CLI_OVERRIDE_OUTPUT_DIR" --target systemd --systemd-runtime host-jre --layout mirror >/dev/null

test -f "$CLI_OVERRIDE_OUTPUT_DIR/opt/dicserver/app.jar"
test ! -e "$TMP_DIR/should-not-be-used"

cat > "$CONFIG_PATH" <<EOF
OUTPUT_DIR=$TMP_DIR/unused-invalid-config
TARGET=broken
EOF

INVALID_CONFIG_STDERR="$TMP_DIR/invalid-config.stderr"
if JAVA_HOME="$FAKE_JAVA_HOME" \
  PATH="$STUB_BIN_DIR" \
  MVNW_BIN="$STUB_BIN_DIR/mvnw" \
  "$SCRIPT_PATH" >"$TMP_DIR/invalid-config.stdout" 2>"$INVALID_CONFIG_STDERR"; then
  echo "invalid config must fail when no CLI args are provided" >&2
  exit 1
fi
grep -q "unsupported --target: broken" "$INVALID_CONFIG_STDERR"
grep -q "Usage: scripts/build-release-image.sh" "$INVALID_CONFIG_STDERR"

cat > "$CONFIG_PATH" <<'EOF'
PRESET=
OUTPUT_DIR=
TARGET=
SYSTEMD_RUNTIME=
SYSTEMD_MYSQL_EXAMPLE=
LAYOUT=
EOF

EMPTY_TEMPLATE_STDERR="$TMP_DIR/empty-template.stderr"
if JAVA_HOME="$FAKE_JAVA_HOME" \
  PATH="$STUB_BIN_DIR" \
  MVNW_BIN="$STUB_BIN_DIR/mvnw" \
  "$SCRIPT_PATH" >"$TMP_DIR/empty-template.stdout" 2>"$EMPTY_TEMPLATE_STDERR"; then
  echo "empty config template must fail when required fields are blank" >&2
  exit 1
fi
grep -q "config file must set OUTPUT_DIR" "$EMPTY_TEMPLATE_STDERR"
grep -q "Usage: scripts/build-release-image.sh" "$EMPTY_TEMPLATE_STDERR"

JAVA_HOME="$FAKE_JAVA_HOME" \
PATH="$STUB_BIN_DIR" \
MVNW_BIN="$STUB_BIN_DIR/mvnw" \
"$SCRIPT_PATH" "$OUTPUT_DIR" --target systemd --systemd-runtime host-jre --layout mirror >/dev/null

test -f "$OUTPUT_DIR/opt/dicserver/app.jar"
test -f "$OUTPUT_DIR/opt/dicserver/bin/run.sh"
test -f "$OUTPUT_DIR/opt/dicserver/bin/healthcheck.sh"
test -f "$OUTPUT_DIR/etc/dicserver/dicserver.env"
test -f "$OUTPUT_DIR/etc/dicserver/dicserver.env.example"
test -f "$OUTPUT_DIR/etc/dicserver/application.yml"
test -f "$OUTPUT_DIR/etc/dicserver/application.yml.example"
test -f "$OUTPUT_DIR/etc/systemd/system/dicserver.service"
test -f "$OUTPUT_DIR/deploy-systemd-release.sh"
test -f "$OUTPUT_DIR/deploy-systemd-release.conf"
test -f "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
test -f "$OUTPUT_DIR/var/lib/dicserver/audio-data/.gitkeep"
test ! -e "$OUTPUT_DIR/opt/dicserver/runtime"
cmp -s "$OUTPUT_DIR/deploy-systemd-release.sh" "$DEPLOY_SCRIPT_TEMPLATE_PATH"
cmp -s "$OUTPUT_DIR/deploy-systemd-release.conf" "$DEPLOY_CONFIG_TEMPLATE_PATH"
grep -q "exec java -jar /opt/dicserver/app.jar" "$OUTPUT_DIR/opt/dicserver/bin/run.sh"
grep -q "command -v java >/dev/null 2>&1" "$OUTPUT_DIR/opt/dicserver/bin/run.sh"
grep -q "java is required on PATH for --systemd-runtime host-jre" "$OUTPUT_DIR/opt/dicserver/bin/run.sh"
grep -q -- "--spring.config.additional-location=file:/etc/dicserver/application.yml" "$OUTPUT_DIR/opt/dicserver/bin/run.sh"
grep -q "ExecStart=/opt/dicserver/bin/run.sh" "$OUTPUT_DIR/etc/systemd/system/dicserver.service"
grep -q "Wants=network.target" "$OUTPUT_DIR/etc/systemd/system/dicserver.service"
grep -q "WorkingDirectory=/opt/dicserver" "$OUTPUT_DIR/etc/systemd/system/dicserver.service"
grep -q "User=dicserver" "$OUTPUT_DIR/etc/systemd/system/dicserver.service"
grep -q "Group=dicserver" "$OUTPUT_DIR/etc/systemd/system/dicserver.service"
grep -q "StateDirectory=dicserver" "$OUTPUT_DIR/etc/systemd/system/dicserver.service"
grep -q "Restart=on-failure" "$OUTPUT_DIR/etc/systemd/system/dicserver.service"
grep -q "DB_URL=jdbc:mysql://127.0.0.1:3306/dictionary?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" "$OUTPUT_DIR/etc/dicserver/dicserver.env"
grep -q "AUDIO_STORAGE_DIR=/var/lib/dicserver/audio-data" "$OUTPUT_DIR/etc/dicserver/dicserver.env"
grep -q "DB_URL=jdbc:mysql://127.0.0.1:3306/dictionary?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" "$OUTPUT_DIR/etc/dicserver/dicserver.env.example"
grep -q "AUDIO_STORAGE_DIR=/var/lib/dicserver/audio-data" "$OUTPUT_DIR/etc/dicserver/dicserver.env.example"
grep -q "^TTS_PROVIDER=" "$OUTPUT_DIR/etc/dicserver/dicserver.env.example"
grep -q "^ANTHROPIC_API_KEY=" "$OUTPUT_DIR/etc/dicserver/dicserver.env.example"
if grep -q '^LLM_API_'CHOICE'=' "$OUTPUT_DIR/etc/dicserver/dicserver.env.example"; then
  echo "systemd env example must not include deprecated LLM_API_"'CHOICE' >&2
  exit 1
fi
if grep -q '^BAILIAN_API_URL=' "$OUTPUT_DIR/etc/dicserver/dicserver.env.example"; then
  echo "systemd env example must not include deprecated provider topology URLs" >&2
  exit 1
fi
if grep -q '^LLM_FALLBACK_'ORDER'=' "$OUTPUT_DIR/etc/dicserver/dicserver.env.example"; then
  echo "systemd env example must not include deprecated LLM fallback order" >&2
  exit 1
fi
grep -q "name: deepseek-main" "$OUTPUT_DIR/etc/dicserver/application.yml"
grep -q "name: bailian-backup" "$OUTPUT_DIR/etc/dicserver/application.yml"
grep -q "name: tencent-backup" "$OUTPUT_DIR/etc/dicserver/application.yml.example"
grep -q "host-JRE mode requires Java 21 on the target host" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
grep -q "/etc/dicserver/mysql.env.example" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
grep -q "/usr/share/dicserver/mysql/" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
grep -q "sudo bash ./deploy-systemd-release.sh" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
grep -q "update DicServer on the same host" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
grep -q "sudo useradd --system --home /opt/dicserver --shell /usr/sbin/nologin dicserver" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
grep -q "sudo systemctl status dicserver" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
grep -q "sudo journalctl -u dicserver -f" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
grep -q "curl http://127.0.0.1:8080/actuator/health" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
grep -q "systemctl enable --now dicserver" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"
grep -q "sudo systemctl enable --now dicserver" "$OUTPUT_DIR/usr/share/doc/dicserver/RUN.md"

FAKE_ROOT="$TMP_DIR/fake-root"
mkdir -p "$FAKE_ROOT"
mkdir -p "$FAKE_ROOT/opt/dicserver" "$FAKE_ROOT/etc/systemd/system"
touch "$FAKE_ROOT/opt/dicserver/app.jar" "$FAKE_ROOT/etc/systemd/system/dicserver.service"

# Run the deploy script against a temp root, to cover upgrade gating:
# - /etc/dicserver/dicserver.env exists already
# - /etc/dicserver/application.yml does not
DEPLOY_SCRIPT="$OUTPUT_DIR/deploy-systemd-release.fake-root.sh"
sed \
  -e "s|/etc/|$FAKE_ROOT/etc/|g" \
  -e "s|/opt/|$FAKE_ROOT/opt/|g" \
  -e "s|/usr/|$FAKE_ROOT/usr/|g" \
  -e "s|/var/|$FAKE_ROOT/var/|g" \
  -e "s|\\\$PACKAGE_ROOT$FAKE_ROOT/etc/|\\\$PACKAGE_ROOT/etc/|g" \
  -e "s|\\\$PACKAGE_ROOT$FAKE_ROOT/opt/|\\\$PACKAGE_ROOT/opt/|g" \
  -e "s|\\\$PACKAGE_ROOT$FAKE_ROOT/usr/|\\\$PACKAGE_ROOT/usr/|g" \
  -e "s|\\\$PACKAGE_ROOT$FAKE_ROOT/var/|\\\$PACKAGE_ROOT/var/|g" \
  -e '/^require_root() {$/,/^}$/c\
require_root() {\
  return 0\
}' \
  "$OUTPUT_DIR/deploy-systemd-release.sh" >"$DEPLOY_SCRIPT"
chmod +x "$DEPLOY_SCRIPT"

mkdir -p "$FAKE_ROOT/etc/dicserver"
echo "EXISTING_ENV=yes" >"$FAKE_ROOT/etc/dicserver/dicserver.env"
rm -f "$CALL_LOG"
PATH="$STUB_BIN_DIR" bash "$DEPLOY_SCRIPT" --remote-install --mode upgrade >/dev/null
if grep -q '^systemctl:restart dicserver$' "$CALL_LOG"; then
  echo "deploy script must not restart when application.yml was newly installed" >&2
  exit 1
fi

# When both env + application.yml already exist, it should restart.
echo "existing: true" >"$FAKE_ROOT/etc/dicserver/application.yml"
rm -f "$CALL_LOG"
PATH="$STUB_BIN_DIR" bash "$DEPLOY_SCRIPT" --remote-install --mode upgrade >/dev/null
grep -q '^systemctl:restart dicserver$' "$CALL_LOG"

BUNDLED_JRE_DIR="$TMP_DIR/bundled-jre"
mkdir -p "$BUNDLED_JRE_DIR/bin"
cp "$FAKE_JAVA_HOME/bin/java" "$BUNDLED_JRE_DIR/bin/java"

cat > "$CONFIG_PATH" <<EOF
OUTPUT_DIR=$TMP_DIR/ignored-by-preset
TARGET=docker
EOF

JAVA_HOME="$FAKE_JAVA_HOME" \
PATH="$STUB_BIN_DIR" \
MVNW_BIN="$STUB_BIN_DIR/mvnw" \
"$SCRIPT_PATH" "$PRESET_OUTPUT_DIR" --preset systemd-upgrade >/dev/null

test -f "$PRESET_OUTPUT_DIR/opt/dicserver/app.jar"
test -f "$PRESET_OUTPUT_DIR/etc/systemd/system/dicserver.service"
test ! -e "$TMP_DIR/ignored-by-preset/dicserver-image.tar"

DICSERVER_SYSTEMD_JRE_DIR="$BUNDLED_JRE_DIR" \
JAVA_HOME="$FAKE_JAVA_HOME" \
PATH="$STUB_BIN_DIR" \
MVNW_BIN="$STUB_BIN_DIR/mvnw" \
"$SCRIPT_PATH" "$BUNDLED_OUTPUT_DIR" --target systemd --systemd-runtime bundled-jre --layout mirror >/dev/null

test -f "$BUNDLED_OUTPUT_DIR/opt/dicserver/runtime/bin/java"
grep -q "/opt/dicserver/runtime/bin/java" "$BUNDLED_OUTPUT_DIR/opt/dicserver/bin/run.sh"
grep -q "exec java -jar /opt/dicserver/app.jar" "$BUNDLED_OUTPUT_DIR/opt/dicserver/bin/run.sh"

JAVA_HOME="$FAKE_JAVA_HOME" \
PATH="$STUB_BIN_DIR" \
MVNW_BIN="$STUB_BIN_DIR/mvnw" \
"$SCRIPT_PATH" "$MYSQL_OUTPUT_DIR" --target systemd --systemd-runtime host-jre --systemd-mysql-example yes --layout mirror >/dev/null

test -f "$MYSQL_OUTPUT_DIR/etc/dicserver/mysql.env.example"
test -f "$MYSQL_OUTPUT_DIR/usr/share/dicserver/mysql/create-database.sql"
test -f "$MYSQL_OUTPUT_DIR/usr/share/dicserver/mysql/mysql-config.example.cnf"
grep -q "CREATE DATABASE IF NOT EXISTS dictionary" "$MYSQL_OUTPUT_DIR/usr/share/dicserver/mysql/create-database.sql"
grep -q "MYSQL_DATABASE=dictionary" "$MYSQL_OUTPUT_DIR/etc/dicserver/mysql.env.example"

REUSED_OUTPUT_DIR="$TMP_DIR/output-reused"
DICSERVER_SYSTEMD_JRE_DIR="$BUNDLED_JRE_DIR" \
JAVA_HOME="$FAKE_JAVA_HOME" \
PATH="$STUB_BIN_DIR" \
MVNW_BIN="$STUB_BIN_DIR/mvnw" \
"$SCRIPT_PATH" "$REUSED_OUTPUT_DIR" --target systemd --systemd-runtime bundled-jre --systemd-mysql-example yes --layout mirror >/dev/null

test -f "$REUSED_OUTPUT_DIR/opt/dicserver/runtime/bin/java"
test -f "$REUSED_OUTPUT_DIR/etc/dicserver/mysql.env.example"
test -f "$REUSED_OUTPUT_DIR/usr/share/dicserver/mysql/create-database.sql"
printf '%s\n' 'REMOTE_HOST=preserved.example.test' > "$REUSED_OUTPUT_DIR/deploy-systemd-release.conf"

JAVA_HOME="$FAKE_JAVA_HOME" \
PATH="$STUB_BIN_DIR" \
MVNW_BIN="$STUB_BIN_DIR/mvnw" \
"$SCRIPT_PATH" "$REUSED_OUTPUT_DIR" --target systemd --systemd-runtime host-jre --systemd-mysql-example no --layout mirror >/dev/null

test ! -e "$REUSED_OUTPUT_DIR/opt/dicserver/runtime"
test ! -e "$REUSED_OUTPUT_DIR/etc/dicserver/mysql.env.example"
test ! -e "$REUSED_OUTPUT_DIR/usr/share/dicserver/mysql/create-database.sql"
test ! -e "$REUSED_OUTPUT_DIR/usr/share/dicserver/mysql/mysql-config.example.cnf"
grep -q '^REMOTE_HOST=preserved.example.test$' "$REUSED_OUTPUT_DIR/deploy-systemd-release.conf"

MALFORMED_STDERR="$TMP_DIR/malformed.stderr"
if JAVA_HOME="$FAKE_JAVA_HOME" \
  PATH="$STUB_BIN_DIR" \
  MVNW_BIN="$STUB_BIN_DIR/mvnw" \
  "$SCRIPT_PATH" "$OUTPUT_DIR" --target >"$TMP_DIR/malformed.stdout" 2>"$MALFORMED_STDERR"; then
  echo "expected missing-value invocation to fail" >&2
  exit 1
fi
grep -q "missing value for --target" "$MALFORMED_STDERR"

DOCKER_FLAG_STDERR="$TMP_DIR/docker-flag.stderr"
if "$SCRIPT_PATH" "$TMP_DIR/bad" --target docker --systemd-runtime host-jre >"$TMP_DIR/docker-flag.stdout" 2>"$DOCKER_FLAG_STDERR"; then
  echo "docker target must reject systemd-only flags" >&2
  exit 1
fi
grep -q "systemd-specific options are only valid with --target systemd" "$DOCKER_FLAG_STDERR"
grep -q "Usage: scripts/build-release-image.sh" "$DOCKER_FLAG_STDERR"

INVALID_LAYOUT_STDERR="$TMP_DIR/invalid-layout.stderr"
if "$SCRIPT_PATH" "$TMP_DIR/bad" --target systemd --layout flat >"$TMP_DIR/invalid-layout.stdout" 2>"$INVALID_LAYOUT_STDERR"; then
  echo "systemd target must reject unsupported layout" >&2
  exit 1
fi
grep -q "unsupported --layout: flat" "$INVALID_LAYOUT_STDERR"
grep -q "Usage: scripts/build-release-image.sh" "$INVALID_LAYOUT_STDERR"

INVALID_RUNTIME_STDERR="$TMP_DIR/invalid-runtime.stderr"
if "$SCRIPT_PATH" "$TMP_DIR/bad" --target systemd --systemd-runtime broken >"$TMP_DIR/invalid-runtime.stdout" 2>"$INVALID_RUNTIME_STDERR"; then
  echo "invalid runtime value must fail" >&2
  exit 1
fi
grep -q "unsupported --systemd-runtime: broken" "$INVALID_RUNTIME_STDERR"
grep -q "Usage: scripts/build-release-image.sh" "$INVALID_RUNTIME_STDERR"

INVALID_TARGET_STDERR="$TMP_DIR/invalid-target.stderr"
if "$SCRIPT_PATH" "$TMP_DIR/bad" --target broken >"$TMP_DIR/invalid-target.stdout" 2>"$INVALID_TARGET_STDERR"; then
  echo "invalid target value must fail" >&2
  exit 1
fi
grep -q "unsupported --target: broken" "$INVALID_TARGET_STDERR"
grep -q "Usage: scripts/build-release-image.sh" "$INVALID_TARGET_STDERR"

INVALID_MYSQL_EXAMPLE_STDERR="$TMP_DIR/invalid-mysql-example.stderr"
if "$SCRIPT_PATH" "$TMP_DIR/bad" --target systemd --systemd-mysql-example maybe >"$TMP_DIR/invalid-mysql-example.stdout" 2>"$INVALID_MYSQL_EXAMPLE_STDERR"; then
  echo "invalid mysql example value must fail" >&2
  exit 1
fi
grep -q "unsupported --systemd-mysql-example: maybe" "$INVALID_MYSQL_EXAMPLE_STDERR"
grep -q "Usage: scripts/build-release-image.sh" "$INVALID_MYSQL_EXAMPLE_STDERR"

INVALID_PRESET_STDERR="$TMP_DIR/invalid-preset.stderr"
if "$SCRIPT_PATH" "$TMP_DIR/bad" --preset broken >"$TMP_DIR/invalid-preset.stdout" 2>"$INVALID_PRESET_STDERR"; then
  echo "invalid preset value must fail" >&2
  exit 1
fi
grep -q "unsupported --preset: broken" "$INVALID_PRESET_STDERR"
grep -q "Usage: scripts/build-release-image.sh" "$INVALID_PRESET_STDERR"

echo "ok"
