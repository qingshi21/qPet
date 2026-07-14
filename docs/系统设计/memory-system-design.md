# LumenAmi 记忆系统优化技术方案

## 一、概述

### 1.1 现有问题

当前记忆系统存在以下不足：
- **全量注入**：每次对话将所有记忆塞入 System Prompt，记忆增多后会超出 token 限制
- **提取粒度粗**：仅用单轮对话提取记忆，缺少上下文，容易漏判误判
- **无衰减机制**：非永久记忆也永远留存，没有遗忘能力
- **无向量检索**：无法根据当前对话语义动态筛选相关记忆
- **缺失兜底机制**：Qwen 提取不稳定时，重要信息可能遗漏

### 1.2 优化目标

构建一套具备**分层存储、向量检索、动态衰减、对话级兜底**能力的记忆系统，使 AI 能够像人一样"记住重要的事、忘掉不重要的、偶尔回忆起来"。

---

## 二、整体架构

记忆系统分为 **Record（写入）** 和 **Retrieve（检索）** 两条独立链路。

```
用户发送消息
    │
    ▼
┌─────────────────────────────────────────────┐
│              Retrieve 链路                    │
│  1. 时效性状态记忆主动注入（12h内 STATUS）     │
│  2. L1 固定层注入（永久记忆，必注入）           │
│  3. L2 检索层注入（向量检索 top5）             │
│     └─ 兜底：对话级检索（条件触发）             │
│  4. L3 上下文层注入（最近N条，语义筛选）        │
│  5. 拼装 Prompt → 调用 Qwen API              │
└─────────────────────────────────────────────┘
    │
    ▼
AI 回复
    │
    ▼
┌─────────────────────────────────────────────┐
│              Record 链路（异步）               │
│  1. 调用 Qwen 提取记忆 + 分类（永久/长期/短期）│
│  2. 生成 embedding 向量                      │
│  3. 写入 pet_memory 表（含版本控制）           │
│  4. 检查是否需要自动总结（version > 3）        │
└─────────────────────────────────────────────┘
注：访问计数在 Retrieve 阶段更新，权重衰减由定时任务处理
```

---

## 三、记忆分层模型

### 3.1 三层架构

| 层级 | 名称 | 内容 | 注入方式 | 对应记忆级别 |
|------|------|------|----------|-------------|
| L1 | 固定层 | 姓名、职业、核心设定 | 每次必注入，无需检索 | 永久记忆 |
| L2 | 检索层 | 长短期记忆、经历、兴趣、项目 | 向量检索 top 5 | 长期/短期记忆 |
| L3 | 上下文层 | 最近 N 条对话消息 | 直接加载（语义筛选） | chat_message 表 |

### 3.2 记忆级别定义

| 级别 | 说明 | 衰减系数 λ | 示例 |
|------|------|-----------|------|
| 永久（PERMANENT） | 核心信息，永不衰减 | 不参与衰减 | 用户姓名、职业、核心人格设定 |
| 长期（LONG_TERM） | 重要信息，缓慢衰减 | 0.01 | 在做的项目、兴趣爱好、重要经历 |
| 短期（SHORT_TERM） | 临时信息，快速衰减 | 0.1 | 临时兴趣、突发事件、当天状态 |

### 3.3 记忆类型扩展

在原有 PROFILE / PROJECT / PREFERENCE 基础上，新增 **STATUS** 类型：

| 类型 | 说明 | 示例 |
|------|------|------|
| PROFILE | 用户画像 | 姓名、年龄、职业 |
| PROJECT | 项目信息 | 正在做的项目、技术栈 |
| PREFERENCE | 偏好 | 沟通方式、兴趣 |
| **STATUS** | **用户状态** | **心情、近期状态、临时感受** |

---

## 四、数据库变更

### 4.1 pet_memories 表改造

```sql
ALTER TABLE pet_memories
    ADD COLUMN level ENUM('PERMANENT', 'LONG_TERM', 'SHORT_TERM') 
        NOT NULL DEFAULT 'LONG_TERM' COMMENT '记忆级别：永久/长期/短期' AFTER type,
    ADD COLUMN state ENUM('ACTIVE', 'ARCHIVED', 'DELETED') 
        NOT NULL DEFAULT 'ACTIVE' COMMENT '生命周期状态' AFTER level,
    ADD COLUMN weight DECIMAL(5,3) DEFAULT 0.500 
        COMMENT '当前权重（0-1）' AFTER state,
    ADD COLUMN access_count INT DEFAULT 0 
        COMMENT '被访问次数（top5且相似度>0.75）' AFTER weight,
    ADD COLUMN embedding JSON 
        COMMENT 'Qwen embedding 向量' AFTER access_count,
    ADD COLUMN archived_at TIMESTAMP NULL 
        COMMENT '归档时间' AFTER embedding,
    ADD COLUMN deleted_at TIMESTAMP NULL 
        COMMENT '删除时间' AFTER archived_at;

-- importance 改为 0-1 范围
ALTER TABLE pet_memories 
    MODIFY COLUMN importance DECIMAL(3,2) DEFAULT 0.50 
    COMMENT '重要性评分（0-1）';

-- 移除旧的 is_permanent 字段（由 level 字段替代）
ALTER TABLE pet_memories DROP COLUMN is_permanent;
```

### 4.2 chat_messages 表改造（对话级检索用）

```sql
ALTER TABLE chat_messages
    ADD COLUMN embedding JSON 
        COMMENT '消息 embedding 向量（对话级检索兜底用）' AFTER token_count;
```

### 4.3 索引调整

```sql
-- 记忆检索核心索引
CREATE INDEX idx_pet_state_level ON pet_memories(pet_id, state, level);

-- 时效性状态记忆查询索引
CREATE INDEX idx_pet_type_state_created ON pet_memories(pet_id, type, state, created_at);
```

---

## 五、Record 链路详细设计

### 5.1 触发时机

用户与 AI 对话完成后，**异步**触发（`@Async`），不阻塞主聊天流程。

### 5.2 记忆提取

调用 Qwen API，传入本轮对话（用户消息 + AI 回复），由 Qwen 判断并提取：

- 输出结构化的 JSON 数组，每条记忆包含：
  - `key`：记忆键名
  - `value`：记忆值
  - `type`：PROFILE / PROJECT / PREFERENCE / STATUS
  - `level`：PERMANENT / LONG_TERM / SHORT_TERM
  - `importance`：0-1 的重要性评分

提取 Prompt 中需明确定义 STATUS 类型及示例，确保 Qwen 能识别显式和隐式的状态信息。

### 5.3 存储与版本控制

```
提取到记忆 → 查库中是否已有同 key 的记忆
  ├─ 不存在 → 新建（version=1）
  ├─ 存在且值相同 → 跳过
  └─ 存在且值不同 → 新建版本（version+1），记录 previous_value
```

### 5.4 Embedding 生成

记忆存入时，调用 Qwen Embedding API（如 `text-embedding-v3`），将 `key + value` 拼接后生成向量，存入 `embedding` 字段。

### 5.5 自动总结机制

当同一 key 的 version 超过 3 次时，触发自动总结：

1. 取出该 key 的所有版本
2. 调用 Qwen API 生成一条总结性记忆
3. 保留最新一条原始记忆 + 总结记忆，清理中间版本
4. 总结记忆的 key 命名为 `{原key}_summary`（如 `current_project_summary`），version 重置为 1，level 继承原记忆的最高级别
5. 若后续再次更新，`{原key}_summary` 本身也参与版本管理，当 summary 的 version 也超过 3 时，重新总结

### 5.6 遗忘与衰减

#### 5.6.1 权重计算公式（混合模型）

```
final_weight = time_decay_weight × access_decay_weight + access_bonus

其中：
  time_decay_weight = importance × e^(-λ × days)
  access_decay_weight = 1.0 + 0.1 × min(access_count, 5)   // 上限 1.5
  access_bonus = 0.05 × min(access_count, 6)               // 上限 0.3
```

- **永久记忆**：不参与衰减计算，weight 恒定为 importance 值
- **长期记忆**：λ = 0.01（30天后剩余约74%）
- **短期记忆**：λ = 0.1（30天后剩余约5%）

#### 5.6.2 状态流转

```
ACTIVE ──(weight < 0.2)──▶ ARCHIVED ──(30天无操作)──▶ DELETED ──(30天)──▶ 物理删除
```

- **归档触发**：定时任务扫描，weight < 0.2 的 active 记忆自动设为 archived
- **archived 记忆**：不参与向量检索，不再计算衰减
- **archived → deleted**：归档后 30 天无手动恢复，自动变为 deleted
- **deleted → 物理删除**：deleted 状态 30 天后，定时任务物理移除记录

#### 5.6.3 访问计数更新

在 Retrieve 阶段，当记忆被检索命中且满足以下条件时，`access_count + 1`：
- 位于 top 5 结果中
- 余弦相似度 > 0.75

---

## 六、Retrieve 链路详细设计

### 6.1 触发时机

用户发送消息后，在调用 Qwen 聊天 API **之前**执行。

**消息保存与 embedding 生成顺序**：
```
用户发送消息
    │
    ├─ 1. 保存用户消息到 chat_messages（不含 embedding）
    ├─ 2. 异步生成用户消息的 embedding（不阻塞主流程）
    ├─ 3. 执行 Retrieve 链路（L1/L2/L3 拼装）
    └─ 4. 调用 Qwen 聊天 API
    │
    ▼
AI 回复
    │
    ├─ 5. 保存 AI 回复到 chat_messages（不含 embedding）
    └─ 6. 异步生成 AI 回复的 embedding（不阻塞主流程）
```
chat_messages 的 embedding 均为异步生成，Retrieve 阶段不依赖其就绪。

### 6.2 Prompt 拼装顺序

```
最终 Prompt = 
    ① 宠物身份/性格设定（原有，不变）
    ② L1 固定层（永久记忆，查 level=PERMANENT 且 state=ACTIVE）
    ③ 时效性状态记忆（12h内，type=STATUS，state=ACTIVE，高权重）
    ④ L2 检索层（向量检索 top 5，排除已通过③注入的 STATUS 类型记忆）
    ⑤ L3 上下文层（最近 N 条历史对话，不含当前消息，语义筛选）
    ⑥ 用户当前问题（当前消息单独追加，确保不重复）
    ⑦ 行为约束规则（原有，放在末尾确保遵从度）
```

### 6.3 时效性主动注入

针对用户状态类记忆（如"今天心情不好"），这类信息：
- 语义上不易被向量相似度检索命中
- 但可能持续影响多轮对话

**注入条件**（需同时满足）：
- `created_at` 在 12 小时以内
- `type = STATUS`
- `state = ACTIVE`
- `weight > 0.6`（状态类记忆 importance 通常较高）

### 6.4 向量检索（L2 层）

1. 对用户当前消息调用 Qwen Embedding API 生成查询向量
2. 查询 `pet_memories` 表中 `pet_id` 匹配、`state = ACTIVE`、`level != PERMANENT`（永久记忆已在 L1 注入）的所有记忆
3. 在代码层计算余弦相似度
4. 按相似度降序取 top 5，**排除 type=STATUS 且满足时效性注入条件（12h内 + weight>0.6）的记忆**（避免与步骤③重复注入）
5. 更新命中记忆的 `access_count`（相似度 > 0.75 时 +1）

### 6.5 对话级检索兜底

#### 6.5.1 触发条件（满足任一即触发）

1. L2 检索 top 5 中**全部**相似度 < 0.5
2. L2 检索 top 5 的**平均**相似度 < 0.3
3. 该宠物的记忆表为空

#### 6.5.2 执行逻辑

1. 查询该宠物最近 20-30 条对话消息
2. 如果 chat_messages 有预存 embedding，直接计算相似度排序
3. 如果没有预存 embedding，实时调用 Qwen Embedding API 生成后计算
4. 取 top 5 条最相关的对话作为补充上下文

#### 6.5.3 chat_messages 的 pet_id 区分

对话级检索需严格按 `pet_id` 过滤，确保不同宠物的对话不会交叉污染。查询条件：`pet_id = ? AND role IN ('user', 'assistant')`。

### 6.6 L3 上下文层优化

在原有"取最近 N 条"的基础上，增加语义相关性筛选：

```
最终上下文 = 最近 2-3 条保底消息 + 语义相似的高相关历史消息
```

- 保底消息：无论相似度多低都保留最近 2-3 条，让 AI 感知话题切换
- 高相关消息：与当前消息 embedding 相似度 > 0.6 的历史消息，按相似度排序补充

**embedding 来源**：chat_messages 的 embedding 均为**异步生成**（用户消息和 AI 回复都在保存后通过 `@Async` 调用 Qwen Embedding API）。Retrieve 时：
- 若历史消息的 embedding 已就绪，直接读取进行语义相似度计算
- 若 embedding 尚未生成（异步延迟），则跳过该条的语义比较，仅按时间排序处理
- 当前用户消息始终直接包含在上下文中，不依赖 embedding

### 6.7 Prompt 标注规则

检索结果注入 Prompt 时，按来源分层标注，告知 AI 如何引用：

```
【已知信息（可自然引用，视为事实）】
- 用户叫小明，职业是软件工程师
- 用户最近在做 LumenAmi 项目

【近期状态（注意语气适配）】
- 用户今天心情不太好

【相关记忆参考】
- 用户之前提到过对 AI 工具感兴趣
- 用户聊过某部电影

【近期对话参考（帮助理解上下文）】
- 用户之前聊过想试用新的 AI 工具

【使用规则】
- 已知信息：可以在回答中自然引用，视为已确认的事实
- 近期状态：注意调整语气和回应方式，但不要直接说"我记得你之前说心情不好"
- 相关记忆/对话参考：仅用于理解上下文，不要主动提及除非自然相关
```

---

## 七、关键阈值参数汇总

| 参数 | 值 | 说明 |
|------|-----|------|
| 归档阈值 | 0.2 | weight < 0.2 时记忆归档为 archived |
| 高相似度阈值 | 0.75 | 访问计数更新条件 |
| 低相似度阈值 | 0.5 | 对话级检索兜底触发条件之一 |
| 平均相似度阈值 | 0.3 | 对话级检索兜底触发条件之二 |
| 时效性窗口 | 12h | STATUS 类记忆主动注入的时间窗口 |
| 时效性权重阈值 | 0.6 | STATUS 类记忆主动注入的最低权重 |
| 长期记忆 λ | 0.01 | 30天后剩余约74% |
| 短期记忆 λ | 0.1 | 30天后剩余约5% |
| 向量检索 top N | 5 | L2 检索层返回的记忆数量 |
| 上下文保底条数 | 2-3 | L3 层无论相似度都保留的最近消息数 |
| 自动总结触发 | version > 3 | 同一 key 更新超过 3 次触发总结 |
| archived → deleted | 30天 | 归档后 30 天无恢复自动删除 |
| deleted → 物理删除 | 30天 | 删除状态 30 天后物理移除 |

---

## 八、涉及的文件变更清单

### 后端

| 文件 | 变更内容 |
|------|---------|
| `PetMemory.java` | 新增 level/state/weight/embedding 等字段 |
| `PetMemoryMapper.java` + `.xml` | 新增按 level/state 查询、embedding 更新等 SQL |
| `ChatMessage.java` | 新增 embedding 字段 |
| `ChatMessageMapper.java` + `.xml` | 新增 embedding 更新、按 pet_id 范围查询 |
| `MemoryExtractorImpl.java` | 重写提取 Prompt，支持 level + type（含 STATUS）分类 |
| `MemoryServiceImpl.java` | 重写检索逻辑（向量检索 + 时效性注入 + 分层拼装）、衰减计算、自动总结 |
| `ChatServiceImpl.java` | 重写 Prompt 拼装流程，集成新的 Retrieve 链路 |
| `QwenServiceImpl.java` | 新增 embedding 调用方法 |
| `application.properties` | 新增 embedding 模型配置 |
| `pet_memories.sql` | 表结构更新 |
| 新增 `MemoryDecayScheduler.java` | 定时任务：衰减计算 + 归档 + 清理 |

### 前端（后续）

| 文件 | 变更内容 |
|------|---------|
| 记忆管理页面 | 展示记忆层级、状态、权重，支持手动归档/恢复 |

---

## 九、实施优先级

| 优先级 | 内容 | 理由 |
|--------|------|------|
| P0 | 数据库表结构改造 + Model 更新 | 基础设施，所有功能依赖 |
| P1 | Embedding 服务集成 | Record 和 Retrieve 都依赖 |
| P2 | Retrieve 链路重写（L1/L2/L3 分层 + 向量检索 + Prompt 标注） | 核心功能，直接影响 AI 表现 |
| P3 | Record 链路重写（提取 Prompt + 分类 + 版本控制） | 配合 Retrieve 的写入侧 |
| P4 | 时效性状态记忆主动注入 | 增强用户体验 |
| P5 | 衰减定时任务 + 状态流转 | 长期运行必要机制 |
| P6 | 对话级检索兜底 | 安全网，防止记忆缺失 |
| P7 | 记忆自动总结 | 长期运行优化 |
| P8 | L3 上下文语义筛选 | 体验优化 |
