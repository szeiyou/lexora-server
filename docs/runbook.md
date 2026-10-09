# Lexora Server 部署指南

## 1. 环境要求

- Java 21+
- MySQL 8.0+
- Docker & Docker Compose (推荐)

## 2. 环境变量配置

LLM provider 拓扑不再通过环境变量选择（旧的环境变量选择方式已移除）。
现在 provider 拓扑统一配置在 YAML 中：

- Docker: `config/application.yml`
- systemd: `/etc/dicserver/application.yml`

密钥等敏感信息（例如 `DEEPSEEK_API_KEY`、`ANTHROPIC_API_KEY`）继续放在：

- Docker: `.env`
- systemd: `/etc/dicserver/dicserver.env`

注意：`application.yml.example` 默认只启用 DeepSeek，备用 provider 默认禁用。任何 `enabled: true` 的 provider 都必须配置对应的 `api-key`（来自环境变量），否则应用启动校验会失败。未使用的 provider 请在 YAML 中禁用（`enabled: false`）或删除。

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `DB_URL` | 是 | - | MySQL连接URL |
| `DB_USERNAME` | 是 | - | 数据库用户名 |
| `DB_PASSWORD` | 是 | - | 数据库密码 |
| `APP_AUTH_JWT_SECRET` | 是 | - | Bearer access token 的签名密钥，至少 32 字节，建议用 `openssl rand -hex 32` 生成 |
| `APP_AUTH_ACCESS_TOKEN_TTL_SECONDS` | 否 | 900 | access token 有效期（秒） |
| `APP_AUTH_REFRESH_TOKEN_TTL_SECONDS` | 否 | 2592000 | refresh token 有效期（秒） |
| `DEEPSEEK_API_KEY` | 启用时必填 | - | DeepSeek API Key |
| `BAILIAN_API_KEY` | 启用时必填 | - | 百炼API Key |
| `TENCENT_API_KEY` | 启用时必填 | - | 腾讯混元 API Key |
| `ANTHROPIC_API_KEY` | 启用时必填 | - | Anthropic API Key |
| `TTS_PROVIDER` | 否 | qwen3 | TTS提供商（qwen3/tencent/mimo） |
| `TTS_BASE_URL` | 否 | - | Qwen3 TTS base URL（Docker下常用 `http://host.docker.internal:8000`） |
| `TTS_API_KEY` | 否 | - | Qwen3 TTS API Key |
| `TTS_SPEAKER` | 否 | vivian | Qwen3 TTS speaker |
| `TTS_QWEN3_GENERATION_MAX_NEW_TOKENS` | 否 | 1024 | Qwen3 TTS 最大生成 token 数 |
| `TTS_QWEN3_GENERATION_TEMPERATURE` | 否 | 0.1 | Qwen3 TTS 采样温度，越低越稳定 |
| `TTS_QWEN3_GENERATION_TOP_K` | 否 | 20 | Qwen3 TTS Top-K 采样参数 |
| `TTS_QWEN3_GENERATION_TOP_P` | 否 | 0.9 | Qwen3 TTS Top-P 采样参数 |
| `TTS_QWEN3_GENERATION_REPETITION_PENALTY` | 否 | 1.05 | Qwen3 TTS 重复惩罚参数 |
| `TENCENT_TTS_SECRET_ID` | 否 | - | 腾讯云TTS SecretId |
| `TENCENT_TTS_SECRET_KEY` | 否 | - | 腾讯云TTS SecretKey |
| `AUDIO_STORAGE_DIR` | 否 | ./audio-data | 音频文件存储目录 |

## 3. Docker 部署

```bash
# 复制并编辑环境变量
cp .env.example .env
cp config/application.yml.example config/application.yml
vim .env
vim config/application.yml    # 配置 LLM provider 拓扑（不在环境变量里）

# 启动服务
docker compose up -d

# 检查状态
docker compose ps
docker compose logs -f dicserver
```

## 4. 发布包构建与部署（Docker/systemd）

```bash
# Edit the checked-in template first, then run with no args
vim scripts/build-release-image.conf
bash scripts/build-release-image.sh

# systemd upgrade shortcut
bash scripts/build-release-image.sh --preset systemd-upgrade

# Docker package (existing behavior)
bash scripts/build-release-image.sh /tmp/dicserver-release

# systemd package using host Java
bash scripts/build-release-image.sh /tmp/dicserver-release --target systemd --systemd-runtime host-jre --layout mirror

# systemd package with bundled JRE and MySQL examples
bash scripts/build-release-image.sh /tmp/dicserver-release --target systemd --systemd-runtime bundled-jre --systemd-mysql-example yes --layout mirror
```

systemd 发布包会生成 `opt/`、`etc/`、`usr/`、`var/` 的 mirror 目录树，部署时需要同步到目标机根目录 `/`。
使用 `--systemd-runtime host-jre` 时，目标机必须预先安装 Java 21。
使用 `--systemd-mysql-example yes` 时，可参考 `/etc/dicserver/mysql.env.example` 和 `/usr/share/dicserver/mysql/` 中的示例文件来初始化本机 MySQL。
systemd 发布包根目录还会生成 `deploy-systemd-release.sh`，建议直接在包目录执行它完成首次部署或后续版本更新。该脚本默认保留现有 `/etc/dicserver/dicserver.env`，首次部署如果发现目标机还没有这个文件，会先写入示例配置并提示你编辑后再重跑一次。
`bash scripts/build-release-image.sh` 在没有任何参数时会读取 `scripts/build-release-image.conf`。这个文件现在是带注释的模板，第一次使用前需要先填写至少 `OUTPUT_DIR` 和 `PRESET` 或 `TARGET`。只要带了任意 CLI 参数，包括仅传一个输出目录，就会完全忽略这个配置文件。

```bash
sudo bash ./deploy-systemd-release.sh
sudo vim /etc/dicserver/dicserver.env
sudo vim /etc/dicserver/application.yml    # 配置 LLM provider 拓扑（不在环境变量里）
sudo bash ./deploy-systemd-release.sh
sudo systemctl status dicserver
sudo journalctl -u dicserver -f
curl http://127.0.0.1:8080/actuator/health
```

## 5. 手动部署

```bash
# 构建
./mvnw clean package -DskipTests

# 需要提供 LLM provider 拓扑（llm.providers），否则应用无法启动
cp config/application.yml.example config/application.yml

# 运行
java -jar target/dic-server-0.0.1-SNAPSHOT.jar \
  --spring.config.additional-location=file:./config/application.yml \
  --spring.datasource.url=jdbc:mysql://localhost:3306/dictionary \
  --spring.datasource.username=root \
  --spring.datasource.password=changeme \
  --app.auth.jwt-secret=$(openssl rand -hex 32)
```

## 6. 健康检查

```bash
# Actuator健康端点（无需认证）
curl http://localhost:8080/actuator/health

# API端点（需要 Bearer access token）
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  "http://localhost:8080/api/v1/entries?q=hello&type=ENGLISH_WORD"
```

## 7. 监控

- **Health**: `GET /actuator/health`（无需认证）
- **Metrics**: `GET /actuator/metrics`（无需认证）
- **Swagger UI**: `GET /swagger-ui.html`（无需认证）

## 8. 故障排查

### 数据库连接失败
```
检查MySQL是否运行、连接参数是否正确
日志中搜索 "Cannot acquire connection"
```

### LLM API调用失败
```
检查API Key配置是否正确
检查网络连接是否正常
系统会自动降级到备用提供商
```

### 音频生成失败
```
检查腾讯云Secret配置
检查网络连接
查看日志搜索 "TTS_ERROR"
```

### 429 Too Many Requests
```
默认限流：60 次/分钟；认证接口按 IP，业务接口按用户和路径
可通过 rate-limit.requests-per-minute 调整
```

## 9. 日志查看

```bash
# Docker环境
docker compose logs -f dicserver | grep -E "ERROR|WARN"

# 直接运行
tail -f logs/dicserver.log
```

## 10. 数据备份

```bash
# MySQL备份
docker exec dicserver-mysql mysqldump -u root -p dictionary > backup.sql

# 音频文件备份
tar -czf audio-backup.tar.gz ./audio-data
```
