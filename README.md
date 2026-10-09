# Lexora Server

AI-powered dictionary, translation and vocabulary learning backend built with Java 21 and Spring Boot.

基于 Java 21 / Spring Boot 的 AI 词典后端，提供中英词条查询、短文翻译、语音朗读、查询历史和单词本管理。适合作为语言学习应用的 REST API 服务。

[![CI](https://github.com/szeiyou/lexora-server/actions/workflows/ci.yml/badge.svg)](https://github.com/szeiyou/lexora-server/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 功能

- 英文单词释义、例句、词源扩展，汉译英词语和句子互译。
- 独立的短文翻译接口，支持查询结果缓存与强制刷新。
- 可配置的 LLM 提供者及优先级降级，支持 OpenAI Chat Completions 与 Anthropic Messages 协议。
- Qwen3、腾讯云、MiMo 语音服务，支持词头发音和完整词条朗读。
- 注册、登录、JWT 认证及 refresh token 轮换；按用户隔离历史与单词本。
- Caffeine 内存缓存、MySQL 持久缓存、请求限流及音频补偿调度。
- Swagger UI、Docker Compose 和 systemd 发布脚本。

## 快速开始

推荐使用 Docker Compose。需要 Docker / Compose、一个 LLM 提供者的 API key；语音功能需要另行配置 TTS 服务。

```bash
git clone https://github.com/szeiyou/lexora-server.git
cd lexora-server
cp .env.example .env
cp config/application.yml.example config/application.yml
openssl rand -hex 32
```

编辑 `.env`，设置数据库密码 `DB_PASSWORD`、随机 JWT 密钥 `APP_AUTH_JWT_SECRET`（可使用上一步输出）和 `DEEPSEEK_API_KEY`。JWT 密钥至少 32 字节，请替换示例占位值。

`config/application.yml` 默认只启用 DeepSeek。使用其他服务时，修改对应的 `protocol`、`base-url`、`model` 和 `api-key` 环境变量引用；每个启用的提供者都必须有密钥。备用提供者需要手动启用。

```bash
docker compose up -d --build
docker compose logs -f dicserver
curl http://localhost:8080/actuator/health
```

服务地址：`http://localhost:8080`；交互式 API 文档：`http://localhost:8080/swagger-ui.html`。

Compose 使用 `.env` 注入环境变量。应用直接通过 Java 启动时不会自动读取 `.env`，需要导出这些变量。真实配置文件已被 Git 忽略。

### 语音配置

| 提供者 | 配置 |
| --- | --- |
| Qwen3（默认） | `TTS_PROVIDER=qwen3`，设置 `TTS_BASE_URL` 指向自建 Qwen3 服务；容器访问宿主机可使用 `host.docker.internal` |
| 腾讯云 | `TTS_PROVIDER=tencent`，设置 `TENCENT_TTS_SECRET_ID`、`TENCENT_TTS_SECRET_KEY` 和你自己的 `TENCENT_TTS_APP_ID` |
| MiMo | `TTS_PROVIDER=mimo`，设置 `MIMO_TTS_API_KEY`，模型、音色与地址见 `.env.example` |

项目不包含 TTS 模型或自建语音服务，外部 LLM/TTS 服务可能产生费用。音频服务不可用时，词典查询仍可返回文本结果；音频可能处于待生成状态。

## API

除注册、登录、刷新 token 接口外，业务接口都需要 `Authorization: Bearer <accessToken>`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/v1/auth/register`、`/api/v1/auth/login` | 注册或登录 |
| POST | `/api/v1/auth/refresh`、`/api/v1/auth/logout` | 刷新或退出会话 |
| GET | `/api/v1/auth/me` | 当前用户 |
| GET | `/api/v1/entries?q=hello&type=ENGLISH_WORD` | 查询词条 |
| POST | `/api/v1/entries/refresh?q=hello&type=ENGLISH_WORD` | 刷新词条 |
| POST | `/api/v1/text-translations` | 短文翻译 |
| GET | `/api/v1/history`、`/api/v1/history/{historyKey}` | 当前用户的历史 |
| GET | `/api/v1/audio/by-key/{audioKey}` | 获取音频 |
| GET / POST / PUT / DELETE | `/api/v1/wordbooks` 及子资源 | 单词本管理 |

详细请求、响应及示例见 [API 接口文档](API接口文档.md) 和 [API 使用指南](docs/api-guide.md)。

## 本地开发

需要 JDK 21 和 MySQL 8；Maven Wrapper 已包含在仓库中。测试使用 H2 与模拟服务，无需真实云服务凭据。

```bash
./mvnw verify
bash scripts/test-build-release-image.sh
bash scripts/test-build-release-systemd-package.sh
```

配置数据库、JWT 密钥和 LLM 环境变量，准备 `config/application.yml` 后启动：

```bash
./mvnw spring-boot:run
# 或先打包，再运行
./mvnw package -DskipTests
java -jar target/dic-server-0.0.1-SNAPSHOT.jar
```

源码包名、JAR 名称和部署服务名沿用 `dicserver`，兼容已有部署。对外项目名称为 Lexora Server。

## 部署与贡献

[部署指南](docs/runbook.md) 包含 Docker、systemd 发布和运维说明。发布脚本支持指定输出目录：

```bash
bash scripts/build-release-image.sh /tmp/lexora-release --target docker
bash scripts/build-release-image.sh /tmp/lexora-release --target systemd --systemd-runtime host-jre
```

公开部署请配置 HTTPS 和数据库凭据；当前 Actuator 与 Swagger 端点无需认证，可在反向代理中限制访问。数据库表结构当前通过 Hibernate `ddl-auto=update` 更新，升级前请备份。

欢迎提交 Issue 和 Pull Request，贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可证

项目源码采用 [MIT License](LICENSE)。依赖及 Maven Wrapper 保留各自许可证，见 [NOTICE](NOTICE)。
