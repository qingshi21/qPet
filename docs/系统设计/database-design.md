# LumenAmi 数据库设计文档

**数据库名**：`lumenami_db`
**数据库版本**：MySQL 8.0
**字符集**：utf8mb4
**存储引擎**：InnoDB


## 实体关系图

```mermaid
erDiagram
    users ||--o{ pets : "拥有"
    pets ||--o{ chat_messages : "产生"
    pets ||--o{ pet_memories : "拥有"

    users {
        int id PK
        string username
        string password
        timestamp created_at
    }

    pets {
        int id PK
        int user_id FK
        string name
        string role_name
        text system_prompt
        boolean is_active
        timestamp created_at
        timestamp updated_at
    }

    chat_messages {
        int id PK
        int pet_id FK
        string role
        text content
        timestamp created_at
    }

    pet_memories {
        int id PK
        int pet_id FK
        string memory_key
        string memory_value
        timestamp created_at
        timestamp updated_at
    }