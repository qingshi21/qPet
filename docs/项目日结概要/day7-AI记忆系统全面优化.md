# Day 7 — AI 记忆系统全面优化

## 一、今日完成概览

完成了 AI 记忆系统从 P0 到 P8 的全部开发工作，并修复了 4 个关键 bug。

| 优先级 | 内容 | 状态 |
|--------|------|------|
| P0 | 数据库表结构改造 + Model 更新 | ✅ |
| P1 | Embedding 服务集成（QwenServiceImpl） | ✅ |
| P2 | Retrieve 链路重写（L1/L2/L3 + 向量检索 + Prompt 标注） | ✅ |
| P3 | Record 链路重写（提取 Prompt + 分类 + 版本控制） | ✅ |
| P4 | 时效性状态记忆主动注入 + access_count 更新 + 记忆 embedding | ✅ |
| P5 | 衰减定时任务 + 状态流转 | ✅ |
| P6 | 对话级检索兜底 | ✅ |
| P7 | 记忆自动总结 | ✅ |
| P8 | L3 上下文语义筛选 | ✅ |
| Bug Fix | @Async 自调用失效 + embedding 重复调用 + JSON 解析失败 | ✅ |

---

## 二、各任务详细记录

### P0：数据库表结构改造 + Model 更新

**数据库变更：**
- `pet_memories` 表重建：新增 `level`（PERMANENT/LONG_TERM/SHORT_TERM）、`state`（ACTIVE/ARCHIVED/DELETED）、`weight`、`access_count`、`embedding`（JSON）、`archived_at`、`deleted_at` 字段；`importance` 改为 DECIMAL(3,2) 范围 0-1；移除 `is_permanent`
- `chat_messages` 表重建：新增 `embedding`（JSON）字段
- 新增索引：`idx_pet_state_level`、`idx_pet_type_state_created`

**代码变更：**
- `PetMemory.java` — 完全重写，新增 `MemoryLevel`、`MemoryState` 枚举，`MemoryType` 新增 STATUS
- `ChatMessage.java` — 新增 `embedding` 字段
- `PetMemoryMapper.java` + `.xml` — 新增分层检索、衰减归档、embedding 更新等方法
- `ChatMessageMapper.java` + `.xml` — 新增 `findRecentByPetId`、`updateEmbedding`
- `MemoryService.java` — 接口签名更新，新增 `getPermanentMemories`、`getTimelinessStatusMemories`
- `MemoryServiceImpl.java` — 适配新接口
- `MemoryController.java` — importance 改为 BigDecimal，saveMemory 增加 level 参数
- `application.properties` — 新增 `qwen.api.embedding-url` 和 `qwen.api.embedding-model`

### P1：Embedding 服务集成

- `QwenService.java` — 新增 `embedding(String text)` 方法
- `QwenServiceImpl.java` — 实现 embedding 调用，使用 `text-embedding-v3` 模型，返回 JSON 字符串格式向量

### P2：Retrieve 链路重写

- 新建 `VectorUtils.java` — 向量解析 + 余弦相似度计算工具类
- `ChatServiceImpl.java` — 重写 `chat()` 方法的 Retrieve 链路：
  - ① 宠物身份/性格设定
  - ② L1 固定层（永久记忆，必注入）
  - ③ 时效性状态记忆（12h 内 STATUS，weight > 0.6）
  - ④ L2 检索层（向量检索 top 5，排除已注入的 STATUS 记忆）
  - ⑤ L3 上下文层（最近 N 条历史对话）
  - ⑦ 行为约束规则（放末尾确保遵从度）
- Prompt 按来源分层标注：【已知信息】→【近期状态】→【相关记忆参考】→【重要规则】

### P3：Record 链路重写

- `MemoryExtractorImpl.java` — 重写提取 Prompt：
  - 支持 4 种 type：PROFILE / PROJECT / PREFERENCE / STATUS
  - 支持 3 种 level：PERMANENT / LONG_TERM / SHORT_TERM
  - importance 范围 0.0-1.0
  - 含正确/错误示例引导
- `MemoryServiceImpl.saveMemory()` — 版本控制逻辑：
  - 同 key 值相同 → 跳过
  - 同 key 值不同 → 新建版本（version+1），记录 previous_value

### P4：时效性状态记忆 + access_count + 记忆 embedding

- 时效性注入：`getTimelinessStatusMemories()` 查询 12h 内、type=STATUS、weight>0.6 的记忆
- access_count 更新：L2 向量检索命中 top5 且相似度 > 0.75 时调用 `incrementAccessCount`
- 记忆 embedding：`saveMemory` 后异步生成 embedding（key + value 拼接）

### P5：衰减定时任务 + 状态流转

- 新建 `MemoryDecayScheduler.java` — 每天凌晨 3 点执行：
  1. 衰减计算：`final_weight = importance × e^(-λ×days) × (1.0 + 0.1×min(access_count,5)) + 0.05×min(access_count,6)`
  2. 归档：weight < 0.2 → ARCHIVED
  3. ARCHIVED → DELETED（30 天无恢复）
  4. DELETED → 物理删除（30 天后）
- `BackendApplication.java` — 新增 `@EnableScheduling`
- Mapper 新增 `findDecayable()` 方法，`updateWeightAndState` 增加 `deletedAt` 参数

### P6：对话级检索兜底

- 触发条件（任一）：L2 top5 全部 < 0.5 / 平均 < 0.3 / 记忆表为空
- 从最近 30 条对话中向量检索 top 5 补充上下文
- Prompt 注入【近期对话参考】层

### P7：记忆自动总结

- `QwenService` + `QwenServiceImpl` — 新增 `summarize(key, values)` 方法
- `MemoryServiceImpl.autoSummarizeAsync()` — version > 3 时触发：
  - 取出所有版本 → 调用 Qwen 总结 → 保存为 `{key}_summary`
  - 总结继承原类型、最高级别、importance
  - 清理中间版本（保留最新一条 + 总结记忆）

### P8：L3 上下文语义筛选

- `getSemanticFilteredHistory()` — 替代原来的纯时间排序：
  - 最近 3 条保底（无论相似度都保留）
  - 更早消息中有 embedding 的，计算语义相似度，> 0.6 的补充
  - embedding 不可用时降级为纯时间排序

---

## 三、Bug 修复

### Bug 1（严重）：@Async 自调用失效

**问题：** `ChatServiceImpl` 中 `generateEmbeddingAsync` 和 `extractAndSaveMemoriesAsync` 从同类 `chat()` 方法内调用，Spring `@Async` 依赖 AOP 代理，同类自调用不走代理，导致异步方法实际同步执行，阻塞聊天响应。

**修复：** 创建独立的 `ChatAsyncService.java`，将两个异步方法迁移到该服务，通过不同 Bean 调用确保 `@Async` 生效。

### Bug 2（性能）：同一条消息重复调 embedding API 3 次

**问题：** L2 检索、兜底检索、L3 语义筛选各自调用了一次 `qwenService.embedding(userMessage)`，产生 3 次相同的 API 请求。

**修复：** 在 `chat()` 中只调一次 embedding API，将 `double[]` 向量传给所有需要的方法。

### Bug 3（连接风险）：@Transactional 覆盖整个 chat 方法

**问题：** `chat()` 方法标注了 `@Transactional`，事务在 Qwen API 调用期间一直开着，长时间占用数据库连接。

**修复：** 移除 `chat()` 上的 `@Transactional`。

### Bug 4（核心 bug）：记忆提取 JSON 解析失败

**问题：** Qwen API 返回的记忆提取结果经常被 ` ```json ... ``` ` markdown 代码块包裹，`parseMemories` 直接 `readTree` 解析失败，静默返回空列表，导致记忆无法存入数据库。

**修复：**
- `parseMemories()` 增加 markdown 代码块剥离逻辑
- 提取 Prompt 中明确要求"不要使用 markdown 代码块包裹，直接输出纯 JSON"
- 增加原始返回内容的日志输出，方便排查

---

## 四、新增/修改文件清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `PetMemory.java` | 重写 | 新增 level/state/weight/embedding 等字段 + 枚举 |
| `ChatMessage.java` | 修改 | 新增 embedding 字段 |
| `PetMemoryMapper.java` + `.xml` | 重写 | 分层检索、衰减、embedding 更新等 |
| `ChatMessageMapper.java` + `.xml` | 重写 | findRecentByPetId、updateEmbedding |
| `MemoryService.java` | 重写 | 接口签名更新 |
| `MemoryServiceImpl.java` | 重写 | 版本控制、embedding 生成、自动总结 |
| `MemoryController.java` | 修改 | BigDecimal importance + level 参数 |
| `MemoryExtractorImpl.java` | 重写 | 新 Prompt 支持 level + STATUS + markdown 修复 |
| `ChatServiceImpl.java` | 重写 | Retrieve 分层链路 + 向量检索 + 兜底 + 语义筛选 |
| `ChatAsyncService.java` | **新增** | 独立异步服务（embedding + 记忆提炼） |
| `ChatService.java` | 修改 | 移除 extractAndSaveMemoriesAsync |
| `QwenService.java` | 修改 | 新增 embedding + summarize 方法 |
| `QwenServiceImpl.java` | 修改 | 实现 embedding + summarize |
| `VectorUtils.java` | **新增** | 向量解析 + 余弦相似度工具 |
| `MemoryDecayScheduler.java` | **新增** | 衰减定时任务 |
| `BackendApplication.java` | 修改 | 新增 @EnableScheduling |
| `application.properties` | 修改 | 新增 embedding 配置 |
| `pet_memories.sql` | 重写 | v2 建表 SQL |
| `chat_messages.sql` | 重写 | v2 建表 SQL |

---

## 五、关键技术决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 向量存储 | MySQL JSON 列 + 代码层余弦相似度 | 项目规模小，不引入额外存储系统 |
| Embedding 模型 | Qwen text-embedding-v3 | 与现有 Qwen API 统一，OpenAI 兼容格式 |
| 衰减公式 | 混合模型（时间衰减×访问衰减 + 访问保底） | 纯乘法无法保护高频访问记忆 |
| 异步方案 | 独立 Service 类 + @Async | 避免同类自调用 @Async 失效 |
| 时效性窗口 | 12h | 状态类信息的有效期 |
| 自动总结触发 | version > 3 | 平衡信息保留与存储成本 |
