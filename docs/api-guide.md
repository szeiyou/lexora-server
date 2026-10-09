# Lexora Server API 使用指南

## API 接口说明

### 认证

除 `POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh` 外，所有 `/api/**` 端点都要求 Bearer access token。`POST /api/v1/auth/logout` 和 `GET /api/v1/auth/me` 也要求已登录。Actuator 和 Swagger UI 无需认证。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/register` | 注册并返回 access/refresh token |
| POST | `/api/v1/auth/login` | 登录并返回 access/refresh token |
| POST | `/api/v1/auth/refresh` | 使用 refresh token 轮换会话并签发新的 access token |
| POST | `/api/v1/auth/logout` | 需要 Bearer access token，撤销指定 refresh token |
| GET | `/api/v1/auth/me` | 返回当前 access token 对应的用户信息 |

注册或登录成功后，把返回的 `accessToken` 放进 `Authorization` 头即可访问业务接口：

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  "http://localhost:8080/api/v1/entries?q=hello&type=ENGLISH_WORD"
```

说明：
- 历史和单词本等个人数据严格按当前登录用户隔离。
- 词典查询结果快照、英语词条快照和音频仍然全局复用，以避免多账户上线后重复消耗 LLM/TTS 成本。
- 登录、注册、刷新接口继续按 IP 限流；其他已认证接口按 `userId + path` 限流。

### 词典接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/entries?q=...&type=...` | 通用词典查询，支持显式类型或 LLM 自动分类 |
| POST | `/api/v1/entries/refresh?q=...&type=...` | 强制绕过缓存刷新统一查询结果 |
| POST | `/api/v1/text-translations` | 独立的短文翻译接口，使用 JSON body |
| GET | `/api/v1/audio/by-key/{audioKey}` | 获取 `/entries` 返回的音频（词头、短语或整条朗读） |
| GET | `/api/v1/history` | 分页查询去重后的搜索历史（page/size） |
| GET | `/api/v1/history/{historyKey}` | 按历史分组键查询该分组最新一条完整记录 |

### Breaking Changes

- 以下旧接口已彻底移除，客户端继续调用会得到 `404`：
  - `/api/v1/words/{word}`
  - `/api/v1/words/{word}/refresh`
  - `/api/v1/audio/{word}`
- `/api/v1/entries` 负责词典式查询；`/api/v1/text-translations` 负责独立的短文翻译。
- `/api/v1/history` 是唯一公开历史接口，只返回当前登录用户的去重摘要列表。
- `/api/v1/history/{historyKey}` 已启用；其中 `historyKey` 是稳定的历史分组键（去重键指纹），不是事件 `id`。
- 客户端应从 `/api/v1/history` 的摘要结果中原样复制 `historyKey` 用于详情查询，不应根据 `query` 文本自行推导或拼接。
- 其他用户的 `historyKey` 会按不存在处理，统一返回 `404 Not Found`。
- `/api/v1/recent-searches` 已移除。

### `/api/v1/entries` 使用说明

`GET /api/v1/entries?q=...&type=...`

- `q` 必填，最大 300 字符
- `type` 可选，仅支持 `ENGLISH_WORD`、`ZH_TO_EN_TERM`、`SENTENCE_TRANSLATION`
- 当 `type` 有值时，它是强约束，服务端会校验大模型返回的 `resultType`
- 当 `type` 为空时，由 LLM 根据输入内容自动分类
- `/api/v1/entries` 与 `/api/v1/entries/refresh` 都不接受 `TEXT_TRANSLATION`

示例：

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  "http://localhost:8080/api/v1/entries?q=phenomenon&type=ENGLISH_WORD"

curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  "http://localhost:8080/api/v1/entries?q=苹果"

curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  "http://localhost:8080/api/v1/entries?q=How%20are%20you%20today%3F"
```

返回结果包含统一外壳字段：

- `query`
- `normalizedQuery`
- `resultType`
- `sourceLanguage`
- `targetLanguage`

当前支持的 `resultType`：

- `ENGLISH_WORD`：英文单词释义，返回 `headwordAudio` 和 `fullReadingAudio`
- `ZH_TO_EN_TERM`：汉译英候选词/短语列表，每个候选可能包含 `headwordAudio`
- `SENTENCE_TRANSLATION`：中英文句子互译，不返回音频

### `/api/v1/text-translations` 使用说明

`POST /api/v1/text-translations`

- 使用 JSON body，不接受 query params
- `text` 必填；服务端会先 `trim`，空串会被拒绝
- `text` 的最大长度按 `trim` 后计算，不能超过 3000 字符
- `sourceLanguage` 可选，默认 `auto`，允许值：`auto`、`zh`、`en`
- `targetLanguage` 必填，允许值：`zh`、`en`
- 返回模型是独立的 `TextTranslationResponse`，不是 `EntryQueryResponse`

请求示例：

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -X POST "http://localhost:8080/api/v1/text-translations" \
  -d '{
    "text": "  这周先把接口联调完，下周再补文档。  ",
    "sourceLanguage": "auto",
    "targetLanguage": "en"
  }'
```

响应示例：

```json
{
  "text": "这周先把接口联调完，下周再补文档。",
  "normalizedText": "这周先把接口联调完，下周再补文档。",
  "sourceLanguage": "zh",
  "targetLanguage": "en",
  "translatedText": "Let's finish the API integration this week and update the documentation next week.",
  "segments": [
    {
      "sourceText": "这周先把接口联调完，",
      "translatedText": "Let's finish the API integration this week,"
    },
    {
      "sourceText": "下周再补文档。",
      "translatedText": "and update the documentation next week."
    }
  ],
  "keyPhrases": [
    {
      "sourceText": "联调完",
      "translatedText": "finish the integration",
      "note": "偏向工程协作语境"
    }
  ],
  "notes": "整体采用自然表达，不逐字直译。"
}
```

### `/api/v1/entries/refresh` 使用说明

`POST /api/v1/entries/refresh?q=...&type=...`

- 请求参数与 `/api/v1/entries` 相同
- 语义是强制绕过缓存重新查询，并在成功后覆盖最新快照/缓存
- `ENGLISH_WORD`、`ZH_TO_EN_TERM`、`SENTENCE_TRANSLATION` 都走同一入口

### 当前 TTS 范围

`/api/v1/entries` 返回的音频字段始终包含稳定的 `audioKey` 和 `audioUrl`，即使音频还没生成完成：

- `ENGLISH_WORD` 返回：
  - 单词本身的 `headwordAudio`
  - 整条释义朗读的 `fullReadingAudio`
- `ZH_TO_EN_TERM` 返回候选词或候选短语本身的 `headwordAudio`
- `SENTENCE_TRANSLATION` 不返回整句音频

### `/api/v1/audio/by-key/{audioKey}` 轮询语义

- `200 OK`：音频已生成，直接返回 `audio/mpeg`
- `202 Accepted`：音频任务已登记但尚未就绪，客户端可继续轮询同一个 `audioUrl`
- `404 Not Found`：系统中不存在该 `audioKey`

### 音频生成策略

- `ENGLISH_WORD.headwordAudio` 在查询路径上会立即尝试生成
- `ENGLISH_WORD.fullReadingAudio` 只有在词头音频已就绪时才会继续尝试生成
- `ZH_TO_EN_TERM.headwordAudio` 会在查询路径上做最佳努力生成
- 所有未成功的任务会持久化到 `audio_generation_task`
- 默认在 `Asia/Shanghai` 时区的 `00:00 ~ 02:00` 窗口内，系统会集中扫描 `PENDING` / 可重试 `FAILED` 任务并补生成
- 补偿窗口可通过 `AUDIO_GENERATION_SCHEDULER_WINDOW_START`、`AUDIO_GENERATION_SCHEDULER_WINDOW_END`、`AUDIO_GENERATION_SCHEDULER_TICK_SECONDS`、`AUDIO_GENERATION_SCHEDULER_ZONE_ID` 覆盖
- 进入补偿窗口时会尝试调用 Qwen TTS 的 `/admin/models/load`，离开窗口后会尝试 `/admin/models/unload`

### 历史接口语义

- `/api/v1/history` 是唯一公开历史接口，返回分页去重摘要。
- 历史摘要和详情都只针对当前认证用户，其他用户的分组键不会泄露存在性。
- 每条摘要包含：`historyKey`、`query`、`normalizedQuery`、`resultType`、`summary`、`latestSearchTime`、`searchCount`、`searchTimes`。
- `/api/v1/history/{historyKey}` 返回该去重分组最新一条完整记录（同一 `historyKey` 下按最新时间选取）。
- `historyKey` 是稳定的历史分组键，不是单次搜索事件 ID。
- 客户端必须直接使用 `/api/v1/history` 返回的 `historyKey` 原样请求详情，不要从查询词推导 `historyKey`。
- `latestSearchTime` 与 `searchTimes` 在当前 HTTP wire format 中均序列化为 ISO-8601 `LocalDateTime` 字符串，例如 `2026-03-30T16:53:10.524466`。
- 详情返回中的 `response` 字段由 `resultType` 判别：
  - `ENGLISH_WORD` / `ZH_TO_EN_TERM` / `SENTENCE_TRANSLATION` -> `EntryQueryResponse`
  - `TEXT_TRANSLATION` -> `TextTranslationResponse`
- 若 `historyKey` 不存在，`GET /api/v1/history/{historyKey}` 返回 `404 Not Found`。
- 若该 `historyKey` 对应的持久化历史载荷损坏、缺字段或服务端无法反序列化，接口返回 `500 Internal Server Error`，响应体为标准错误结构 `{"code":"INTERNAL_ERROR","message":"服务器内部错误"}`；这属于服务端数据完整性故障，不是客户端输入错误。
- 短文翻译会以 `resultType = TEXT_TRANSLATION` 写入历史，并使用 `responseSchema = TEXT_TRANSLATION_RESPONSE` 持久化独立响应体。
- 短文翻译的 `summary` 取自 `translatedText`，会先 `trim`，再截断到 100 字符并在需要时追加 `...`。

说明：以下示例中嵌套的 `response` 仅为字段裁剪（abbreviated）示例；实际接口会按对应 `resultType` 返回完整 schema。

`GET /api/v1/history/{historyKey}`（entry-query 明细）示例：

```json
{
  "historyKey": "c609be9ac5d1b6e64a023c5e76cb6cdd2d639c264fe2d2aa2277db199a8a17ec",
  "resultType": "ENGLISH_WORD",
  "query": "visible",
  "normalizedQuery": "visible",
  "latestSearchTime": "2026-03-25T18:30:00",
  "searchCount": 2,
  "searchTimes": [
    "2026-03-25T18:30:00",
    "2026-03-24T09:15:00"
  ],
  "response": {
    "query": "visible",
    "normalizedQuery": "visible",
    "resultType": "ENGLISH_WORD",
    "sourceLanguage": "en",
    "targetLanguage": "zh",
    "englishWord": {
      "word": "visible"
    }
  }
}
```

`GET /api/v1/history/{historyKey}`（text-translation 明细）示例：

```json
{
  "historyKey": "1d6787f3acbc48a8ee88f9d6e52dc314bae0fe5200bde80dbc82b4bffb8d807e",
  "resultType": "TEXT_TRANSLATION",
  "query": "你好",
  "normalizedQuery": "你好",
  "latestSearchTime": "2026-03-25T19:00:00",
  "searchCount": 1,
  "searchTimes": [
    "2026-03-25T19:00:00"
  ],
  "response": {
    "text": "你好",
    "normalizedText": "你好",
    "sourceLanguage": "zh",
    "targetLanguage": "en",
    "translatedText": "Hello there"
  }
}
```

### 单词本接口

所有单词本资源都归属于当前认证用户。不同用户可以使用相同的单词本名称；跨用户访问、修改或删除其他用户的单词本统一返回 `404 Not Found`。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/wordbooks` | 获取当前用户的所有单词本 |
| GET | `/api/v1/wordbooks/{id}` | 获取当前用户的单词本详情 |
| POST | `/api/v1/wordbooks?name=` | 为当前用户创建单词本 |
| PUT | `/api/v1/wordbooks/{id}?name=` | 更新当前用户单词本名称 |
| DELETE | `/api/v1/wordbooks/{id}` | 删除当前用户单词本 |
| GET | `/api/v1/wordbooks/{id}/words` | 获取当前用户单词本中的单词 |
| POST | `/api/v1/wordbooks/{id}/words` | 为当前用户单词本批量添加单词（JSON 数组） |
| DELETE | `/api/v1/wordbooks/{id}/words` | 从当前用户单词本批量删除单词 |
| DELETE | `/api/v1/wordbooks/{id}/words/all` | 清空当前用户单词本 |
| GET | `/api/v1/wordbooks/containing?word=` | 查找当前用户中包含单词的单词本 |

### 监控端点

| 路径 | 说明 |
|------|------|
| `/actuator/health` | 健康检查 |
| `/actuator/metrics` | 应用指标 |
| `/swagger-ui.html` | Swagger UI 文档 |
