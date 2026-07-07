# Day 2 - 启动器 UI 搭建与前后端联调准备

**日期**：2026-07-07
**阶段**：开发阶段 - 第2天


## 今日产出

### 前端（Electron）启动器

- [x] 调整 `main.js` 加载路径，指向 `src/renderer/launcher/index.html`
- [x] 搭建启动器窗口 UI 骨架（`index.html` + `style.css` + `renderer.js`）
- [x] 实现登录 / 注册 Tab 切换
- [x] 实现窗口控制（关闭按钮、拖拽栏）
- [x] 集成自定义应用图标（替换启动器左侧 图片 为 `logo.png`）
- [x] 实现错误提示 5 秒后淡出消失（`showError` 函数 + CSS transition）
- [x] 完成前端登录 / 注册界面与后端 API 的联调准备

### 后端（Spring Boot）

- [x] 确认注册接口：`POST /api/auth/register`
- [x] 确认登录接口：`POST /api/auth/login`
- [x] 确认后端服务正常运行在 `localhost:8080`


## 今日技术细节

### 错误提示淡出机制

- 使用 `showError(elementId, message)` 显示错误
- 10 秒后自动添加 `fade-out` 类，触发 CSS transition
- 1 秒淡出动画完成后自动清空文字
- 使用 `clearError(elementId)` 在 Tab 切换时手动清除错误

### 前端与后端联调

- 前端通过 `fetch` 发送 POST 请求到 `http://localhost:8080/api/auth/login`
- 前端通过 `fetch` 发送 POST 请求到 `http://localhost:8080/api/auth/register`
- 根据返回的 `code` 字段判断成功（200）或失败（400/401）


## 决策记录

- 启动器采用独立文件夹 `src/renderer/launcher/`，与悬浮窗 `floating/` 分离
- 每个窗口独立管理自己的 HTML/CSS/JS，便于后期维护
- 错误提示统一使用 `showError` 函数管理，保证交互一致性


## 当前可运行状态

| 模块              | 状态    | 验证方式                                      |
|-----------------|-------|-------------------------------------------|
| Spring Boot 后端  | ✅ 正常  | `http://localhost:8080/api/auth/register` |
| Electron 启动器 UI | ✅ 正常  | `npm start` 显示启动器窗口                       |
| 登录 / 注册表单       | ✅ 正常  | Tab 切换、输入框、按钮交互正常                         |
| 错误提示淡出          | ✅ 正常  | 错误信息 10 秒后淡出消失                            |
| 前后端联调           | ✅ 已就绪 | 登录/注册请求已对接后端 API                          |


## 明天计划（Day 3）

**目标**：打通登录后的完整流程，实现启动器→悬浮窗切换

1. 登录成功后，关闭启动器，打开悬浮窗（`src/renderer/floating/index.html`）
2. 悬浮窗基础 UI 搭建（透明置顶窗口、宠物形象、聊天入口）
3. 实现悬浮窗点击弹出聊天窗口
4. 用 Electron IPC 实现窗口间通信


## 今日感悟

今天发现 ZcChat 和 AI吟美两个开源项目，技术架构和产品形态都和 qPet 有不少相似之处。它们证明了 AI 桌宠这个方向已经被市场验证过，也提供了很多可以借鉴的技术方案。但 qPet 的差异点在于“自定义角色人格”和“普适性”，这是可以优化改进的地方。

明天开始进入悬浮窗和聊天功能的开发，进一步跑通前后端联调。