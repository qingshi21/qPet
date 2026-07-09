# Day 4 - 记忆系统前端实现与 AI 对话风格优化

**日期**：2026-07-09  
**阶段**：开发阶段 - 第4天


## 今日产出

### 前端（Electron）聊天窗口

- [x] **记忆管理侧边栏** - 完整的 HTML 结构 + CSS 样式
- [x] **编辑记忆模态框** - 支持修改记忆值和重要性
- [x] **记忆历史模态框** - 查看某条记忆的所有版本变更
- [x] **记忆渲染逻辑** - ~370 行 JS 代码，包含：
  - 侧边栏展开/收起动画
  - 加载宠物所有记忆（按类型分组）
  - 友好标签映射（`user_name` → "名字"）
  - 添加/编辑/删除/历史查看功能
  - 空状态提示
- [x] **聊天窗口尺寸优化** - 从 400×550 增大到 450×600

### 后端（Spring Boot）

- [x] **优化 MemoryExtractor Prompt** - 防止错误提取 AI 角色扮演内容为用户记忆
  - 新增核心原则：只提取用户明确陈述的信息
  - 禁止提取 AI 说的话、角色扮演、虚构设定
  - 新增错误示例：专门针对"重庆口音"场景

### Bug 修复

- [x] **退出登录输入框禁用** - `showAuthPanel()` 中调用 `resetFormStates()`
- [x] **删除所有宠物后输入框禁用** - `openCreateModal()` 中显式启用模态框输入框


## 今日技术细节

### 1. 记忆管理前端架构

#### HTML 结构（index.html）

```html
<!-- 记忆侧边栏 -->
<div class="memory-sidebar" id="memorySidebar">
    <div class="memory-header">
        <h3> 记忆库</h3>
        <button onclick="toggleMemorySidebar()">×</button>
    </div>
    <div class="memory-list" id="memoryList"></div>
    <button class="add-memory-btn" onclick="openAddMemoryModal()">+ 添加记忆</button>
</div>

<!-- 编辑记忆模态框 -->
<div class="modal" id="editMemoryModal">
    <!-- 友好标签（disabled）、记忆值（textarea）、重要性星级 -->
</div>

<!-- 记忆历史模态框 -->
<div class="modal" id="memoryHistoryModal">
    <!-- 显示某条记忆的所有版本 -->
</div>
```

#### 友好标签映射（renderer.js）

```javascript
const memoryLabelMap = {
    'user_name': '名字',
    'user_nickname': '昵称',
    'user_age': '年龄',
    'user_occupation': '职业',
    'current_project': '当前项目',
    'communication_style': '沟通风格',
    // ...
};

const memoryTypeLabels = {
    'PROFILE': '📋 用户画像',
    'PROJECT': '💼 项目信息',
    'PREFERENCE': '❤️ 偏好设置'
};
```

#### 记忆渲染逻辑

```javascript
function renderMemories(memories) {
    // 1. 只保留每个 key 的最新版本
    const latestMap = {};
    for (const mem of memories) {
        const key = mem.memoryKey;
        if (!latestMap[key] || mem.version > latestMap[key].version) {
            latestMap[key] = mem;
        }
    }
    
    // 2. 按类型分组展示
    // 3. 每张卡片包含：友好标签、记忆值、重要性星级、版本号、操作按钮
}
```

### 2. 记忆提取逻辑优化（MemoryExtractor.java）

#### 核心原则（必须严格遵守）

```java
⚠️ 核心原则：
- **只提取用户明确陈述的关于自己的信息**
- **绝对不要提取 AI 说的话作为用户记忆**
- **如果信息来自 AI 的角色扮演、虚构设定、玩笑话，一律不要提取**
- 例如：AI说"我是重庆人"是角色设定，不能提取成用户的 location；
       用户说"你说话像重庆人"只是猜测，也不能提取
```

#### 提取规则

```java
- 只提取用户主动陈述的、明确的、有价值的事实性信息
- 忽略闲聊、问候、无关内容
- 忽略用户对 AI 的猜测、评价、提问（如"你是哪里的？""你说话像重庆人"）
- 忽略 AI 的角色扮演内容、虚构设定、玩笑话
- 如果对话中没有值得记忆的信息，返回空数组 []
```

#### 错误示例（专门针对 bug）

```java
❌ 错误示例1（AI 的角色扮演内容，不能提取）：
用户："你说话像重庆口音"
AI："哈哈，其实我是重庆人，从小在那儿长大~"
输出：[]  （这是 AI 的角色设定，不是用户的信息）

❌ 错误示例2（用户对 AI 的猜测，不能提取）：
用户："你说话像重庆人"
AI："是吗？可能是最近吃火锅多了"
输出：[]
```

### 3. 输入框禁用 Bug 修复

#### 新增 resetFormStates() 函数

```javascript
function resetFormStates() {
    // 启用登录表单输入框
    const loginUsername = document.getElementById('loginUsername');
    const loginPassword = document.getElementById('loginPassword');
    const loginButton = document.querySelector('#loginForm button');
    if (loginUsername) loginUsername.disabled = false;
    if (loginPassword) loginPassword.disabled = false;
    if (loginButton) loginButton.disabled = false;
    
    // 启用注册表单输入框
    // ...
}
```

#### 在关键位置调用

```javascript
// 退出登录时
logoutBtn.addEventListener('click', () => {
    localStorage.removeItem('token');
    localStorage.removeItem('currentUserId');
    pets = [];
    showAuthPanel();  // ← 这里会调用 resetFormStates()
});

// 打开创建宠物模态框时
function openCreateModal() {
    createPetForm.reset();
    clearError('createPetError');
    
    // 确保模态框输入框可用
    const petNameInput = document.getElementById('petNameInput');
    const petRoleInput = document.getElementById('petRoleInput');
    const petPromptInput = document.getElementById('petPromptInput');
    if (petNameInput) petNameInput.disabled = false;
    if (petRoleInput) petRoleInput.disabled = false;
    if (petPromptInput) petPromptInput.disabled = false;
    
    createPetModal.style.display = 'flex';
    // ...
}
```

## 决策记录

### 1. 记忆前端采用侧边栏 + 模态框设计

**理由**：
- 侧边栏可以随时查看记忆，不干扰聊天
- 模态框用于编辑/添加/历史查看，交互清晰
- 符合桌面应用的常见模式

### 2. 使用友好标签而非原始 key

**理由**：
- 用户体验更好（"名字" vs `user_name`）
- 隐藏技术细节，更贴近普通用户认知
- 便于后期国际化

### 3. 记忆提取严格区分用户信息和 AI 角色扮演

**理由**：
- 避免污染用户画像（如把 AI 的"重庆人"设定当成用户的 location）
- 保证记忆的准确性和可信度
- 符合陪伴型 AI 的产品定位（真实朋友，不是角色扮演游戏）


## 当前可运行状态

| 模块                  | 状态    | 验证方式                                      |
|---------------------|-------|-------------------------------------------|
| Spring Boot 后端      | ✅ 正常  | `http://localhost:8080`                     |
| Electron 启动器       | ✅ 正常  | `npm start`                                 |
| 登录 / 注册           | ✅ 正常  | 输入框不再被禁用                                |
| 创建宠物              | ✅ 正常  | 模态框输入框可用                                |
| 聊天窗口              | ✅ 正常  | 尺寸增大到 450×600                            |
| 记忆侧边栏            | ✅ 正常  | 可展开/收起，显示记忆列表                        |
| 记忆编辑/历史         | ✅ 正常  | 模态框正常工作                                  |
| 记忆提取逻辑          | ✅ 已优化 | 不会错误提取 AI 角色扮演内容                      |
| Lombok 编译          | ✅ 已修复 | Maven 编译成功                                |


## 明天计划（Day 5）

**目标**：实现 WebSocket 实时通讯、日志系统、全局异常处理，优化对话存储和角色设定理解

### 1. WebSocket 双端通讯
- [ ] 后端集成 Spring WebSocket，建立长连接通道
- [ ] 前端使用 `WebSocket` API 连接后端
- [ ] 实现消息推送机制（AI 主动发送消息、状态通知等）
- [ ] 心跳检测与断线重连逻辑

### 2. 日志系统与全局异常处理
- [ ] 配置 Logback/Log4j2，统一日志格式（INFO/WARN/ERROR）
- [ ] 实现全局异常处理器 `@RestControllerAdvice`
- [ ] 定义统一的错误码和错误响应结构
- [ ] 记录关键操作日志（登录、创建宠物、删除记忆等）

### 3. 对话时间戳与按日期存储优化
- [ ] 在 `chat_messages` 表添加 `created_at` 字段（精确到秒）
- [ ] 前端聊天界面显示每条消息的具体时间（如 "今天 14:30"、"昨天 09:15"）
- [ ] **按日期分组存储方案**：
  - 方案 A：新增 `conversation_date` 字段，按日期索引查询
  - 方案 B：物理分表（`chat_messages_202607`、`chat_messages_202608`...）
  - 方案 C：冷热数据分离（最近 30 天热数据 + 历史冷数据归档）
- [ ] 评估性能提升效果，选择最优方案

### 4. 角色设定理解接口
- [ ] 新增接口：`POST /api/pets/{petId}/understand-personality`
- [ ] 功能描述：
  - AI 分析宠物的 `system_prompt` 和自定义设定
  - 从网络搜索相似角色设定（可选）
  - 生成性格画像报告（性格标签、沟通风格、禁忌话题等）
- [ ] 前端展示：
  - 角色设定理解结果卡片
  - 性格雷达图（外向性、幽默感、专业性等维度）
  - 推荐话题列表
- [ ] 实现精准性格定位，帮助用户快速了解 AI 伙伴


## 今日感悟

今天最大的收获是意识到**记忆提取的逻辑陷阱**：AI 在角色扮演时说的"我是重庆人"很容易被误认为是用户的属性。这提醒我们，在设计 AI 系统时，必须严格区分：
- **用户的信息**（应该存储为长期记忆）
- **AI 的角色设定**（不应该影响用户画像）

另外，前端记忆管理的实现让我感受到**用户体验的细节很重要**：
- 友好标签比原始 key 更直观
- 模态框的交互要流畅
- 错误提示要及时反馈

另外，我翻阅了一些关于ai长期记忆的文档，在合理使用chat表和记忆提取的方面还有很长的路要走，后续可能会考虑使用RAG向量数据库储存，还将参考其他先进技术进行ai记忆的优化。
