```markdown
# qPet API 设计文档

**版本**：v1.0
**基础路径**：`/api`
**响应格式**：JSON
**字符编码**：UTF-8


## 统一响应格式

所有接口返回统一格式：

```json
{
  "code": 200,
  "data": { ... },
  "message": "success"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | Integer | 状态码，200表示成功 |
| data | Object/Array | 业务数据 |
| message | String | 提示信息 |


## 一、认证模块

### 1.1 用户注册

```
POST /api/auth/register
```

**请求体：**

```json
{
  "username": "zhangsan",
  "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | String | 是 | 用户名，3-20位字母或数字 |
| password | String | 是 | 密码，6-20位 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "userId": 1,
    "username": "zhangsan"
  }
}
```


### 1.2 用户登录

```
POST /api/auth/login
```

**请求体：**

```json
{
  "username": "zhangsan",
  "password": "123456"
}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "userId": 1,
    "username": "zhangsan",
    "pets": [
      {
        "petId": 1,
        "name": "小艾",
        "roleName": "艾莎公主",
        "isActive": true
      },
      {
        "petId": 2,
        "name": "太白",
        "roleName": "李白",
        "isActive": false
      }
    ]
  }
}
```


## 二、宠物模块

### 2.1 创建宠物

```
POST /api/pets
```

**请求体：**

```json
{
  "name": "小艾",
  "roleName": "艾莎公主",
  "systemPrompt": "你是一个高贵、温暖、拥有冰雪魔法的女王..."
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | String | 是 | 宠物名字，1-20字符 |
| roleName | String | 否 | 角色名称，如"李白" |
| systemPrompt | String | 是 | System Prompt，完整的性格设定文本 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "petId": 3,
    "name": "小艾",
    "roleName": "艾莎公主",
    "isActive": false
  }
}
```


### 2.2 获取宠物列表

```
GET /api/pets
```

**响应：**

```json
{
  "code": 200,
  "data": [
    {
      "petId": 1,
      "name": "小艾",
      "roleName": "艾莎公主",
      "isActive": true,
      "createdAt": "2026-07-01 10:00:00"
    },
    {
      "petId": 2,
      "name": "太白",
      "roleName": "李白",
      "isActive": false,
      "createdAt": "2026-07-02 14:30:00"
    }
  ]
}
```


### 2.3 切换当前宠物

```
PUT /api/pets/{petId}/activate
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| petId | Path | Integer | 是 | 宠物ID |

**响应：**

```json
{
  "code": 200,
  "data": {
    "petId": 1,
    "isActive": true
  }
}
```


### 2.4 删除宠物

```
DELETE /api/pets/{petId}
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| petId | Path | Integer | 是 | 宠物ID |

**响应：**

```json
{
  "code": 200,
  "data": {
    "success": true
  }
}
```


### 2.5 更新宠物信息

```
PUT /api/pets/{petId}
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| petId | Path | Integer | 是 | 宠物ID |

**请求体（所有字段可选）：**

```json
{
  "name": "小艾",
  "roleName": "艾莎公主",
  "systemPrompt": "你是一个高贵、温暖、拥有冰雪魔法的女王..."
}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "petId": 1,
    "name": "小艾",
    "roleName": "艾莎公主"
  }
}
```


## 三、对话模块

### 3.1 发送消息

```
POST /api/chat/send
```

**请求体：**

```json
{
  "petId": 1,
  "message": "今天好累啊"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| petId | Integer | 是 | 当前对话的宠物ID |
| message | String | 是 | 用户消息内容 |

**后端处理流程：**

1. 通过 `petId` 查询 `pets` 表，获取 `system_prompt`
2. 通过 `petId` 查询 `pet_memories` 表，获取该宠物的所有记忆
3. 通过 `petId` 查询 `chat_messages` 表，获取最近 10 条对话
4. 拼接：`system_prompt` + 记忆 + 最近对话 + 当前消息 → 调用 Qwen API
5. 将用户消息和 AI 回复分别存入 `chat_messages` 表
6. 返回 AI 回复

**响应：**

```json
{
  "code": 200,
  "data": {
    "reply": "辛苦啦～你值得好好休息一下。",
    "messageId": 123,
    "createdAt": "2026-07-03 14:30:05"
  }
}
```


### 3.2 获取对话历史

```
GET /api/chat/history
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| petId | Integer | 是 | 宠物ID |
| limit | Integer | 否 | 返回条数，默认20，最大100 |
| offset | Integer | 否 | 偏移量，默认0 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "total": 156,
    "messages": [
      {
        "id": 1,
        "role": "user",
        "content": "今天好累啊",
        "createdAt": "2026-07-03 14:30:00"
      },
      {
        "id": 2,
        "role": "assistant",
        "content": "辛苦啦～你值得好好休息一下。",
        "createdAt": "2026-07-03 14:30:05"
      }
    ]
  }
}
```


## 四、记忆模块

### 4.1 获取所有记忆

```
GET /api/memories
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| petId | Integer | 是 | 宠物ID |

**响应：**

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "key": "name",
      "value": "小张",
      "createdAt": "2026-07-01 10:00:00"
    },
    {
      "id": 2,
      "key": "hobby",
      "value": "篮球",
      "createdAt": "2026-07-02 14:30:00"
    }
  ]
}
```


### 4.2 添加或更新记忆

```
POST /api/memories
```

**请求体：**

```json
{
  "petId": 1,
  "key": "hobby",
  "value": "打篮球"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| petId | Integer | 是 | 宠物ID |
| key | String | 是 | 记忆键，如"name"、"hobby" |
| value | String | 是 | 记忆值 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "id": 2,
    "key": "hobby",
    "value": "打篮球"
  }
}
```


### 4.3 删除记忆

```
DELETE /api/memories/{memoryId}
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| memoryId | Path | Integer | 是 | 记忆ID |

**响应：**

```json
{
  "code": 200,
  "data": {
    "success": true
  }
}
```


## 五、角色理解模块（进阶）

### 5.1 深度理解角色

```
POST /api/role/understand
```

**请求体：**

```json
{
  "prompt": "希望你像李白一样"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| prompt | String | 是 | 用户对角色的描述 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "roleName": "李白",
    "tags": ["豪放不羁", "浪漫主义", "嗜酒如命", "热爱自由", "诗意澎湃"],
    "style": "说话如吟诗，用词华丽，善用比喻和典故",
    "systemPrompt": "你是一个豪放不羁、浪漫主义的诗人...",
    "examples": [
      {
        "user": "今天好累",
        "assistant": "劳生如逆旅，何不把酒问青天？"
      }
    ],
    "clarificationQuestions": [
      "你希望我完全按唐代李白的身份来，还是可以保留现代知识？"
    ]
  }
}
```


## API 汇总表

| 方法 | 路径 | 功能 | 涉及表 |
|---|---|---|---|
| POST | /api/auth/register | 注册 | users |
| POST | /api/auth/login | 登录 | users, pets |
| POST | /api/pets | 创建宠物 | pets |
| GET | /api/pets | 获取宠物列表 | pets |
| PUT | /api/pets/{petId}/activate | 切换宠物 | pets |
| DELETE | /api/pets/{petId} | 删除宠物 | pets |
| PUT | /api/pets/{petId} | 更新宠物 | pets |
| POST | /api/chat/send | 发送消息 | pets, chat_messages, pet_memories |
| GET | /api/chat/history | 获取对话历史 | chat_messages |
| GET | /api/memories | 获取所有记忆 | pet_memories |
| POST | /api/memories | 添加/更新记忆 | pet_memories |
| DELETE | /api/memories/{memoryId} | 删除记忆 | pet_memories |
| POST | /api/role/understand | 深度理解角色 | —（调用AI） |
```