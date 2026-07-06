# Day 1 - 基础环境搭建与项目骨架

**日期**：2026-07-06
**阶段**：开发阶段 - 第1天


## 今日产出

### 后端（Spring Boot）

- [x] Spring Boot 项目初始化（`qpet-backend`）
- [x] 确认 Spring Boot 版本：3.5.16
- [x] 添加核心依赖：
  - Spring Web
  - WebSocket
  - MyBatis（3.0.5）
  - MySQL Driver
  - Lombok
  - DevTools
- [x] 数据库连接配置（`application.properties`）
- [x] MySQL 8.0 本地环境验证（`qpet_db` 已创建，四张表已建好）
- [x] 验证启动：`BackendApplication` 正常启动，控制台显示 `Started ... in 1.99 seconds`
- [x] 编写测试接口：`GET /api/health` 返回 JSON，证明 Web 层工作正常


### 前端（Electron）

- [x] Electron 项目初始化（`qpet-frontend`）
- [x] 安装 Electron 依赖（版本：35.1.5）
- [x] 编写 `main.js`（主进程：创建透明置顶悬浮窗）
- [x] 编写 `index.html`（渲染进程：桌宠界面 + 点击交互 + 关闭按钮）
- [x] 验证启动：`npm start` 成功显示 🐱 悬浮窗
- [x] 悬浮窗特性：透明背景、可拖拽、始终置顶、点击弹出提示、右上角关闭

## 关键决策记录

- Spring Boot 版本锁定为 3.5.16（MyBatis 3.0.5 兼容）
- 数据库名：`qpet_db`
- 前端使用 Electron + 原生 JS（暂不引入前端框架）
- 前后端分离开发，通过 REST API 通信


## 当前可运行状态

| 模块 | 状态 | 验证方式 |
|---|---|---|
| Spring Boot 后端 | ✅ 正常 | 访问 `http://localhost:8080/api/health` 返回 JSON |
| MySQL | ✅ 正常 | `qpet_db` 已连接，四张表存在 |
| Electron 前端 | ✅ 正常 | `npm start` 显示悬浮窗 |

## 明天计划（Day 2）

**目标**：实现用户注册 + 登录接口，完成前后端第一次对接

1. 后端：编写 `UserController`，实现 `POST /api/auth/register` 和 `POST /api/auth/login`
2. 后端：编写 `UserMapper`，实现数据库操作
3. 前端：启动器窗口实现注册/登录界面
4. 前端：调用后端 API 完成注册和登录流程
5. 联调确认：注册 → 登录 → 返回用户信息