# LumenAmi 系统设计概要

**版本**：v1.0
**日期**：2026-07-03


## 项目定位
一款纯软件形态的AI桌宠，用户用自然语言定义角色人格，获得桌面陪伴式对话体验。


## 技术架构

> 详见 `系统设计/架构图.svg`

- 客户端：Electron（双窗口：启动器 + 悬浮窗）
- 后端：Spring Boot + MySQL + WebSocket
- AI：Qwen API（对话生成 + 性格控制）


## 核心模块

| 模块 | 功能 | 状态 |
|---|---|---|
| 用户认证 | 注册/登录 | 已设计 |
| 宠物管理 | 创建/切换/删除 | 已设计 |
| 对话系统 | 发送消息/获取历史/WebSocket推送 | 已设计 |
| 记忆系统 | 结构化记忆（键值对） | 已设计 |
| 角色理解 | 自然语言生成System Prompt | 已设计 |


## 数据库

> 详见 `系统设计/database-design.md` 和 `系统设计/ER.svg`

4张表：users / pets / chat_messages / pet_memories


## API

> 详见 `系统设计/api-design.md`

13个接口，涵盖认证、宠物、对话、记忆、角色理解


## 文档清单

- 需求分析.md
- 技术选型清单.md
- 架构图.svg
- ER.svg
- database-design.md
- api-design.md
- 系统设计概要.md（本文件）