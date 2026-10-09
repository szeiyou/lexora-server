# DicServer API接口文档

## 基本信息

- **基础URL**: `http://your-server-address:8080`
- **API版本**: v1
- **请求格式**: REST
- **响应格式**: JSON
- **认证方式**: Bearer access token（`POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh` 免认证；其余 `/api/**` 路径需认证，Actuator 和 Swagger UI 免认证）

## 1. Breaking Changes

以下旧接口已移除，客户端继续调用会返回 `404 Not Found`：

- `/api/v1/words/{word}`
- `/api/v1/words/{word}/refresh`
- `/api/v1/audio/{word}`

当前 v1 查询与音频契约改为：

- `/api/v1/entries` 是词典式查询入口
- `/api/v1/text-translations` 是独立的短文翻译入口
- `/api/v1/entries/refresh` 是唯一强制刷新入口
- `/api/v1/audio/by-key/{audioKey}` 是唯一音频获取入口（覆盖 `headwordAudio`、短语音频和 `fullReadingAudio`）
- `/api/v1/history` 是唯一公开历史接口，返回去重后的分页摘要列表
- `/api/v1/history/{historyKey}` 用于按去重分组键查询历史详情（`historyKey` 不是事件 `id`）
- `/api/v1/recent-searches` 已移除

---

## 认证与会话

除 `POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh` 外，所有 `/api/**` 端点都要求 Bearer access token。
`POST /api/v1/auth/logout` 与 `GET /api/v1/auth/me` 也要求已登录。

### 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/register` | 注册并返回 access/refresh token |
| POST | `/api/v1/auth/login` | 登录并返回 access/refresh token |
| POST | `/api/v1/auth/refresh` | 使用 refresh token 轮换会话并签发新的 access token |
| POST | `/api/v1/auth/logout` | 需要 Bearer access token，撤销指定 refresh token |
| GET | `/api/v1/auth/me` | 返回当前 access token 对应的用户信息 |

认证响应格式：

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "X5W...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "username": "alice"
  }
}
```

登录示例：

```bash
curl -X POST "http://your-server-address:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "Password123"
  }'
```

访问业务接口时请携带 `Authorization: Bearer <accessToken>` 头。例如：

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  "http://your-server-address:8080/api/v1/history"
```

说明：

- 历史、单词本等个人数据严格按当前认证用户隔离。
- 查询结果快照、英语词条快照和音频仍然全局复用，以避免重复消耗 LLM/TTS 成本。
- 登录、注册、刷新接口按 IP + path 限流；其他已认证接口按 userId + path 限流。
- 下文的 HTTP 示例为简洁起见通常省略 `Authorization` 头；实际调用受保护接口时必须携带 Bearer token。

---

## 2. 通用词典查询与音频接口

### 2.1 `GET /api/v1/entries`

#### 接口信息

- **URL路径**: `/api/v1/entries`
- **HTTP方法**: GET
- **功能描述**: 统一处理英文单词查询、汉译英词语/短语查询、以及中英文句子互译

#### 查询参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| q | String | 是 | 用户输入内容，不能为空，最大 300 字符 |
| type | String | 否 | 强约束查询类型，可选值为 `ENGLISH_WORD`、`ZH_TO_EN_TERM`、`SENTENCE_TRANSLATION` |

#### `type` 规则

- 当 `type` 有值时，服务端按该类型构建提示词，并校验 LLM 返回的 `resultType`
- 当 `type` 缺失时，由 LLM 自动分类
- 如果显式指定了 `type`，但 LLM 返回了其他 `resultType`，服务端返回 `LLM_RESPONSE_INVALID`
- `/api/v1/entries` 不接受 `TEXT_TRANSLATION`；短文翻译必须改用 `POST /api/v1/text-translations`

#### 统一响应外壳

```json
{
  "query": "苹果",
  "normalizedQuery": "苹果",
  "resultType": "ZH_TO_EN_TERM",
  "sourceLanguage": "zh",
  "targetLanguage": "en",
  "englishWord": null,
  "zhToEnTerm": {},
  "sentenceTranslation": null
}
```

#### 支持的结果类型

`ENGLISH_WORD` 示例：

```json
{
  "query": "phenomenon",
  "normalizedQuery": "phenomenon",
  "resultType": "ENGLISH_WORD",
  "sourceLanguage": "en",
  "targetLanguage": "zh",
  "englishWord": {
    "word": "phenomenon",
    "pronunciation": {
      "uk": "/fəˈnɒm.ɪ.nən/",
      "us": "/fəˈnɑː.mə.nɑːn/"
    },
    "definitions": [
      {
        "partOfSpeech": "n.",
        "meaning": "现象",
        "synonyms": ["occurrence"]
      }
    ],
    "examples": [
      {
        "sentence": "The northern lights are a natural phenomenon.",
        "translation": "北极光是一种自然现象。"
      }
    ],
    "etymology": "From Greek.",
    "extension": "常用于描述自然或社会现象。",
    "headwordAudio": {
      "audioKey": "english_word:headword:phenomenon",
      "audioUrl": "/api/v1/audio/by-key/english_word%3Aheadword%3Aphenomenon"
    },
    "fullReadingAudio": {
      "audioKey": "english_word:full_reading:phenomenon",
      "audioUrl": "/api/v1/audio/by-key/english_word%3Afull_reading%3Aphenomenon"
    }
  },
  "zhToEnTerm": null,
  "sentenceTranslation": null
}
```

`ZH_TO_EN_TERM` 示例：

```json
{
  "query": "苹果",
  "normalizedQuery": "苹果",
  "resultType": "ZH_TO_EN_TERM",
  "sourceLanguage": "zh",
  "targetLanguage": "en",
  "englishWord": null,
  "zhToEnTerm": {
    "input": "苹果",
    "usageTip": "水果通常用 apple；公司名称用 Apple。",
    "candidates": [
      {
        "term": "apple",
        "pronunciation": {
          "uk": "/ˈæp.əl/",
          "us": "/ˈæp.əl/"
        },
        "partOfSpeech": "n.",
        "coreMeaning": "苹果",
        "usageContext": "日常指水果时使用",
        "difference": "最常见、最自然的表达。",
        "example": "I eat an apple every morning.",
        "exampleTranslation": "我每天早上吃一个苹果。",
        "headwordAudio": {
          "audioKey": "zh_to_en_term:headword:apple",
          "audioUrl": "/api/v1/audio/by-key/zh_to_en_term%3Aheadword%3Aapple"
        }
      }
    ]
  },
  "sentenceTranslation": null
}
```

`SENTENCE_TRANSLATION` 示例：

```json
{
  "query": "你今天怎么样？",
  "normalizedQuery": "你今天怎么样？",
  "resultType": "SENTENCE_TRANSLATION",
  "sourceLanguage": "zh",
  "targetLanguage": "en",
  "englishWord": null,
  "zhToEnTerm": null,
  "sentenceTranslation": {
    "sourceSentence": "你今天怎么样？",
    "translatedSentence": "How are you today?",
    "literalTranslation": "How are you today?",
    "grammarBreakdown": "How are you today 是常见问候语。",
    "keyPhrases": [
      {
        "phrase": "How are you",
        "explanation": "用于询问对方近况。"
      }
    ],
    "alternativeExpressions": ["How have you been today?"],
    "learningNotes": "可用于日常打招呼。"
  }
}
```

#### 当前 TTS 范围

- `ENGLISH_WORD` 返回 `headwordAudio` 和 `fullReadingAudio`
- `ZH_TO_EN_TERM` 返回候选词/短语级 `headwordAudio`
- `SENTENCE_TRANSLATION` 不返回整句音频

#### 音频生成策略

- `/api/v1/entries` 中的音频字段始终返回稳定的 `audioKey` 与 `audioUrl`
- 查询路径会做最佳努力生成
- 未成功的音频任务会持久化并在夜间 `00:00 ~ 02:00` 集中补生成

#### 调用示例

```http
GET /api/v1/entries?q=phenomenon&type=ENGLISH_WORD HTTP/1.1
Host: your-server-address
```

```http
GET /api/v1/entries?q=苹果 HTTP/1.1
Host: your-server-address
```

### 2.2 `POST /api/v1/entries/refresh`

#### 接口信息

- **URL路径**: `/api/v1/entries/refresh`
- **HTTP方法**: POST
- **功能描述**: 强制绕过缓存重新查询，并刷新统一快照/缓存

#### 查询参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| q | String | 是 | 用户输入内容，不能为空，最大 300 字符 |
| type | String | 否 | 强约束查询类型，可选值为 `ENGLISH_WORD`、`ZH_TO_EN_TERM`、`SENTENCE_TRANSLATION` |

#### 响应内容

响应结构与 `GET /api/v1/entries` 完全一致。

#### 调用示例

```http
POST /api/v1/entries/refresh?q=visible&type=ENGLISH_WORD HTTP/1.1
Host: your-server-address
```

### 2.3 `POST /api/v1/text-translations`

#### 接口信息

- **URL路径**: `/api/v1/text-translations`
- **HTTP方法**: POST
- **功能描述**: 独立的中英短文翻译接口，使用 JSON body，不复用 `EntryQueryResponse`

#### 请求体

| 字段名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| text | String | 是 | 翻译原文。服务端会先 `trim`；`trim` 后不能为空，且长度不能超过 3000 字符 |
| sourceLanguage | String | 否 | 源语言，默认 `auto`，允许值 `auto`、`zh`、`en` |
| targetLanguage | String | 是 | 目标语言，允许值 `zh`、`en` |

> `text` 的长度限制按 `trim` 后计算。如果只是首尾空白让原始 JSON 中的字符串长度超过 3000，而 `trim` 后不超过 3000，服务端仍会接受。

请求示例：

```json
{
  "text": "  这周先把接口联调完，下周再补文档。  ",
  "sourceLanguage": "auto",
  "targetLanguage": "en"
}
```

#### 响应内容

响应模型为独立的 `TextTranslationResponse`：

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

#### 字段说明

| 字段名 | 类型 | 描述 |
|--------|------|------|
| text | String | 用户可见的原文（控制器在入参阶段已做 `trim`） |
| normalizedText | String | 用于 dedupe/cache/history identity 的规范化文本 |
| sourceLanguage | String | 最终识别或确认的源语言，值为 `zh` 或 `en` |
| targetLanguage | String | 目标语言，值为 `zh` 或 `en` |
| translatedText | String | 整段译文 |
| segments | Array | 原文与译文的分段对齐结果 |
| keyPhrases | Array | 关键短语及其译法说明 |
| notes | String | 整体翻译说明 |

#### 错误语义

- 请求体缺字段、语言码非法、`text` 在 `trim` 后为空或超过 3000 字符时，返回 `400` + `VALIDATION_ERROR`
- malformed JSON 返回确定性的 `400` + `VALIDATION_ERROR`

#### 调用示例

```http
POST /api/v1/text-translations HTTP/1.1
Host: your-server-address
Content-Type: application/json

{
  "text": "Can we finish the API integration this week?",
  "sourceLanguage": "en",
  "targetLanguage": "zh"
}
```

### 2.4 `GET /api/v1/audio/by-key/{audioKey}`

#### 接口信息

- **URL路径**: `/api/v1/audio/by-key/{audioKey}`
- **HTTP方法**: GET
- **功能描述**: 获取 `/api/v1/entries` 返回的 `audioKey` 对应音频
- **返回格式**: 二进制音频数据 (`audio/mpeg`)

#### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| audioKey | String | 是 | `/entries` 返回的音频键，可对应词头、短语或整条朗读 |

#### 响应说明

- `200 OK`：音频已生成，返回 `audio/mpeg`
- `202 Accepted`：音频任务已登记但尚未生成，客户端应继续轮询同一个 `audioUrl`
- `404 Not Found`：系统中不存在该 `audioKey`

#### 调用示例

```http
GET /api/v1/audio/by-key/english_word%3Aheadword%3Avisible HTTP/1.1
Host: your-server-address
```

---

## 3. 查询历史接口

### 3.1 `GET /api/v1/history`

#### 接口信息

- **URL路径**: `/api/v1/history`
- **HTTP方法**: GET
- **功能描述**: 分页获取当前认证用户去重后的查询历史摘要列表（唯一公开历史接口）

#### 查询参数

| 参数名 | 类型    | 必填 | 默认值 | 描述 |
|--------|---------|------|--------|------|
| page   | Integer | 否   | 1      | 页码（当前项目启用了 one-indexed parameters） |
| size   | Integer | 否   | 20     | 每页记录数 |
| sort   | String  | 否   | latestSearchTime,desc | 排序字段和方向（推荐使用 `latestSearchTime`） |

#### 响应内容

`/api/v1/history` 返回去重后的摘要字段，不返回 `id`、`searchTime`、`responseJson`、`responseSchema`。
其中 `historyKey` 为稳定的历史分组键（去重键指纹），用于后续访问 `/api/v1/history/{historyKey}`。
客户端应直接复制摘要中的 `historyKey` 原样调用详情接口，不要根据 `query` 文本自行推导。
`latestSearchTime` 与 `searchTimes` 在当前 HTTP wire format 中均序列化为 ISO-8601 `LocalDateTime` 字符串，例如 `2026-03-30T16:53:10.524466`。
历史摘要只包含当前认证用户的数据；其他用户的分组键不会出现在列表中。

短文翻译结果也会进入同一历史表，但以：

- `resultType = TEXT_TRANSLATION`
- `responseSchema = TEXT_TRANSLATION_RESPONSE`

进行持久化。短文翻译历史摘要取自 `translatedText`，先 `trim`，再截断到 100 字符并在需要时追加 `...`。

```json
{
  "content": [
    {
      "historyKey": "c609be9ac5d1b6e64a023c5e76cb6cdd2d639c264fe2d2aa2277db199a8a17ec",
      "query": "visible",
      "normalizedQuery": "visible",
      "resultType": "ENGLISH_WORD",
      "summary": "可见的",
      "latestSearchTime": "2026-03-25T18:30:00",
      "searchCount": 3,
      "searchTimes": [
        "2026-03-25T18:30:00",
        "2026-03-24T09:15:00",
        "2026-03-20T08:00:00"
      ]
    }
  ]
}
```

#### `content` 字段说明

| 字段名 | 类型 | 描述 |
|--------|------|------|
| historyKey | String | 稳定的历史分组键（去重键指纹），不是事件ID |
| query | String | 用户原始查询 |
| normalizedQuery | String | 归一化后的查询文本 |
| resultType | String | 结果类型 |
| summary | String | 用于列表展示的摘要 |
| latestSearchTime | String | 该去重分组最近一次搜索时间，按 ISO-8601 `LocalDateTime` 字符串返回 |
| searchCount | Number | 该去重分组累计搜索次数 |
| searchTimes | String[] | 该去重分组所有搜索时间（按新到旧），每个元素均为 ISO-8601 `LocalDateTime` 字符串 |

> 注：`/api/v1/recent-searches` 已移除，继续调用会得到 `404 Not Found`。

#### 调用示例

```http
GET /api/v1/history?page=1&size=10 HTTP/1.1
Host: your-server-address
```

### 3.2 `GET /api/v1/history/{historyKey}`

#### 接口信息

- **URL路径**: `/api/v1/history/{historyKey}`
- **HTTP方法**: GET
- **功能描述**: 按 `historyKey` 返回该去重分组最新一条完整记录（包括完整 `response`）

#### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| historyKey | String | 是 | `/api/v1/history` 摘要项返回的分组键；稳定分组标识，不是事件ID。必须原样传入，不应自行推导 |

#### 响应内容

详情响应结构：

- `historyKey`、`query`、`normalizedQuery`、`latestSearchTime`、`searchCount`、`searchTimes` 表示该分组聚合信息
- `latestSearchTime` 与 `searchTimes` 的时间值均按 ISO-8601 `LocalDateTime` 字符串返回
- `response` 为该分组最新记录的完整响应体（实际返回完整 schema）
- `response` 由 `resultType` 判别：
  - `ENGLISH_WORD` / `ZH_TO_EN_TERM` / `SENTENCE_TRANSLATION` -> `EntryQueryResponse`
  - `TEXT_TRANSLATION` -> `TextTranslationResponse`
- `historyKey` 不存在或不属于当前认证用户时返回 `404 Not Found`
- 若持久化历史载荷损坏、缺字段或服务端内部反序列化失败，则返回 `500 Internal Server Error`，响应体为标准错误结构 `{"code":"INTERNAL_ERROR","message":"服务器内部错误"}`。这是服务端数据完整性故障，不是客户端参数错误

说明：以下示例中的 `response` 为字段裁剪（abbreviated）示例，仅用于说明判别关系；实际返回会包含对应类型的完整字段。

entry-query 明细示例：

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

text-translation 明细示例：

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

#### 调用示例

```http
GET /api/v1/history/c609be9ac5d1b6e64a023c5e76cb6cdd2d639c264fe2d2aa2277db199a8a17ec HTTP/1.1
Host: your-server-address
```

## 错误处理

所有 API 在发生业务异常、参数异常或未捕获异常时，统一返回如下结构：

```json
{
  "code": "BUSINESS_ERROR_CODE",
  "message": "错误说明"
}
```

## 跨域支持

本API支持跨域请求（CORS），前端应用可以直接从浏览器发起跨域请求。

## 版本控制

当前API版本为v1，未来版本更新可能会使用新的路径前缀，如`/api/v2/`。

## 联系与支持

如遇到API使用问题，请联系管理员。

---

## 6. 单词本管理接口

所有单词本资源都归属于当前认证用户。不同用户可以拥有同名单词本；跨用户访问、修改或删除其他用户的单词本统一返回 `404 Not Found`。

### 6.1 获取所有单词本

#### 接口信息

- **URL路径**: `/api/v1/wordbooks`
- **HTTP方法**: GET
- **功能描述**: 获取当前认证用户的所有单词本列表

#### 响应内容

```json
[
  {
    "id": 1,
    "name": "重要单词",
    "createTime": "2023-03-05T12:30:45.123"
  },
  {
    "id": 2,
    "name": "考试词汇",
    "createTime": "2023-03-10T08:20:15.789"
  }
]
```

#### 响应字段说明

| 字段名     | 类型   | 描述                      |
|------------|--------|---------------------------|
| id         | Long   | 单词本ID                  |
| name       | String | 单词本名称                |
| createTime | String | 创建时间（ISO日期时间格式）|

#### 调用示例

```
GET /api/v1/wordbooks HTTP/1.1
Host: your-server-address
```

### 6.2 获取特定单词本

#### 接口信息

- **URL路径**: `/api/v1/wordbooks/{id}`
- **HTTP方法**: GET
- **功能描述**: 获取当前认证用户下指定ID的单词本详细信息

#### 请求参数

| 参数名 | 类型 | 必填 | 描述        |
|--------|------|------|-------------|
| id     | Long | 是   | 单词本的ID  |

#### 响应内容

```json
{
  "id": 1,
  "name": "重要单词",
  "createTime": "2023-03-05T12:30:45.123"
}
```

#### 响应字段说明

| 字段名     | 类型   | 描述                      |
|------------|--------|---------------------------|
| id         | Long   | 单词本ID                  |
| name       | String | 单词本名称                |
| createTime | String | 创建时间（ISO日期时间格式）|

#### 调用示例

```
GET /api/v1/wordbooks/1 HTTP/1.1
Host: your-server-address
```

#### 可能的错误响应

```json
{
  "code": "WORDBOOK_NOT_FOUND",
  "message": "单词本不存在"
}
```

### 6.3 创建单词本

#### 接口信息

- **URL路径**: `/api/v1/wordbooks`
- **HTTP方法**: POST
- **功能描述**: 为当前认证用户创建一个新的单词本

#### 请求参数

| 参数名 | 类型   | 必填 | 描述       |
|--------|--------|------|------------|
| name   | String | 是   | 单词本名称 |

#### 响应内容

返回创建的单词本信息：

```json
{
  "id": 3,
  "name": "新建单词本",
  "createTime": "2023-05-15T09:45:30.123"
}
```

#### 调用示例

```
POST /api/v1/wordbooks?name=新建单词本 HTTP/1.1
Host: your-server-address
```

### 6.4 更新单词本

#### 接口信息

- **URL路径**: `/api/v1/wordbooks/{id}`
- **HTTP方法**: PUT
- **功能描述**: 更新当前认证用户指定单词本的名称

#### 请求参数

| 参数名 | 类型   | 必填 | 描述         |
|--------|--------|------|--------------|
| id     | Long   | 是   | 单词本ID     |
| name   | String | 是   | 新的单词本名称 |

#### 响应内容

返回更新后的单词本信息：

```json
{
  "id": 1,
  "name": "更新后的名称",
  "createTime": "2023-03-05T12:30:45.123"
}
```

#### 调用示例

```
PUT /api/v1/wordbooks/1?name=更新后的名称 HTTP/1.1
Host: your-server-address
```

#### 可能的错误响应

```json
{
  "code": "WORDBOOK_NOT_FOUND",
  "message": "单词本不存在"
}
```

### 6.5 删除单词本

#### 接口信息

- **URL路径**: `/api/v1/wordbooks/{id}`
- **HTTP方法**: DELETE
- **功能描述**: 删除当前认证用户指定的单词本及其包含的所有单词

#### 请求参数

| 参数名 | 类型 | 必填 | 描述        |
|--------|------|------|-------------|
| id     | Long | 是   | 单词本的ID  |

#### 响应内容

成功时返回HTTP 200状态码，无内容返回。

#### 调用示例

```
DELETE /api/v1/wordbooks/1 HTTP/1.1
Host: your-server-address
```

#### 可能的错误响应

```json
{
  "code": "WORDBOOK_NOT_FOUND",
  "message": "单词本不存在"
}
```

### 6.6 获取单词本中的单词

#### 接口信息

- **URL路径**: `/api/v1/wordbooks/{id}/words`
- **HTTP方法**: GET
- **功能描述**: 获取当前认证用户指定单词本中包含的所有单词

#### 请求参数

| 参数名 | 类型 | 必填 | 描述        |
|--------|------|------|-------------|
| id     | Long | 是   | 单词本的ID  |

#### 响应内容

```json
[
  {
    "word": "example",
    "partOfSpeech": "n.",
    "meaning": "一个代表性的形式或模式",
    "createTime": "2023-03-06T14:20:45.123"
  },
  {
    "word": "dictionary",
    "partOfSpeech": "n.",
    "meaning": "词典，字典",
    "createTime": "2023-03-06T14:22:30.456"
  }
]
```

#### 响应字段说明

| 字段名       | 类型   | 描述                      |
|--------------|--------|---------------------------|
| word         | String | 单词                      |
| partOfSpeech | String | 英文词性缩写，如 `n.`、`v.`、`adj.`；多个词性用 `, ` 分隔 |
| meaning      | String | 单词的简要含义            |
| createTime   | String | 添加时间（ISO日期时间格式）|

#### 调用示例

```
GET /api/v1/wordbooks/1/words HTTP/1.1
Host: your-server-address
```

#### 可能的错误响应

```json
{
  "code": "WORDBOOK_NOT_FOUND",
  "message": "单词本不存在"
}
```

### 6.7 添加单词到单词本

#### 接口信息

- **URL路径**: `/api/v1/wordbooks/{id}/words`
- **HTTP方法**: POST
- **功能描述**: 向当前认证用户指定单词本添加一个或多个单词

#### 请求参数

| 参数名 | 类型         | 必填 | 描述             |
|--------|--------------|------|------------------|
| id     | Long         | 是   | 单词本的ID       |
| words  | List<String> | 是   | 要添加的单词列表 |

#### 请求体示例

```json
["example", "dictionary", "vocabulary"]
```

#### 响应内容

成功时返回HTTP 200状态码，无内容返回。

#### 调用示例

```
POST /api/v1/wordbooks/1/words HTTP/1.1
Host: your-server-address
Content-Type: application/json

["example", "dictionary", "vocabulary"]
```

#### 可能的错误响应

```json
{
  "code": "WORDBOOK_NOT_FOUND",
  "message": "单词本不存在"
}
```

### 6.8 从单词本中删除单词

#### 接口信息

- **URL路径**: `/api/v1/wordbooks/{id}/words`
- **HTTP方法**: DELETE
- **功能描述**: 从当前认证用户指定单词本中删除一个或多个单词

#### 请求参数

| 参数名 | 类型         | 必填 | 描述             |
|--------|--------------|------|------------------|
| id     | Long         | 是   | 单词本的ID       |
| words  | List<String> | 是   | 要删除的单词列表 |

#### 请求体示例

```json
["example", "dictionary"]
```

#### 响应内容

成功时返回HTTP 200状态码，无内容返回。

#### 调用示例

```
DELETE /api/v1/wordbooks/1/words HTTP/1.1
Host: your-server-address
Content-Type: application/json

["example", "dictionary"]
```

#### 可能的错误响应

```json
{
  "code": "WORDBOOK_NOT_FOUND",
  "message": "单词本不存在"
}
```

### 6.9 清空单词本

#### 接口信息

- **URL路径**: `/api/v1/wordbooks/{id}/words/all`
- **HTTP方法**: DELETE
- **功能描述**: 清空当前认证用户指定单词本中的所有单词

#### 请求参数

| 参数名 | 类型 | 必填 | 描述        |
|--------|------|------|-------------|
| id     | Long | 是   | 单词本的ID  |

#### 响应内容

成功时返回HTTP 200状态码，无内容返回。

#### 调用示例

```
DELETE /api/v1/wordbooks/1/words/all HTTP/1.1
Host: your-server-address
```

#### 可能的错误响应

```json
{
  "code": "WORDBOOK_NOT_FOUND",
  "message": "单词本不存在"
}
```

### 6.10 查找包含特定单词的单词本

#### 接口信息

- **URL路径**: `/api/v1/wordbooks/containing`
- **HTTP方法**: GET
- **功能描述**: 查找当前认证用户下包含指定单词的所有单词本ID

#### 请求参数

| 参数名 | 类型   | 必填 | 描述         |
|--------|--------|------|--------------|
| word   | String | 是   | 要查找的单词 |

#### 响应内容

```json
[1, 3, 5]
```

返回一个包含单词本ID的数组，表示该单词存在于这些单词本中。如果该单词不存在于任何单词本中，将返回空数组`[]`。

#### 调用示例

```
GET /api/v1/wordbooks/containing?word=example HTTP/1.1
Host: your-server-address
```

#### 前端使用示例

```javascript
// 查找包含特定单词的单词本
async function findWordbooksContainingWord(word) {
  const response = await fetch(`http://your-server-address:8080/api/v1/wordbooks/containing?word=${encodeURIComponent(word)}`);
  return await response.json();
}
```

### 前端使用示例

```javascript
// 获取所有单词本
async function fetchAllWordbooks() {
  const response = await fetch('http://your-server-address:8080/api/v1/wordbooks');
  return await response.json();
}

// 创建新单词本
async function createWordbook(name) {
  const response = await fetch(`http://your-server-address:8080/api/v1/wordbooks?name=${encodeURIComponent(name)}`, {
    method: 'POST'
  });
  return await response.json();
}

// 添加单词到单词本
async function addWordsToWordbook(wordbookId, words) {
  await fetch(`http://your-server-address:8080/api/v1/wordbooks/${wordbookId}/words`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(words)
  });
}
```
