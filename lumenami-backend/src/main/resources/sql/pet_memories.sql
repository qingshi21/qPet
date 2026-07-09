-- 宠物记忆表
CREATE TABLE IF NOT EXISTS pet_memories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    pet_id INT NOT NULL COMMENT '宠物ID',
    memory_key VARCHAR(100) NOT NULL COMMENT '记忆键（如：user_name, favorite_food）',
    memory_value TEXT NOT NULL COMMENT '记忆值',
    type ENUM('PROFILE', 'PROJECT', 'PREFERENCE') NOT NULL DEFAULT 'PROFILE' COMMENT '记忆类型：画像/项目/偏好',
    importance INT DEFAULT 5 COMMENT '重要性评分（1-10，越高越重要）',
    is_permanent TINYINT(1) DEFAULT 0 COMMENT '是否永久记忆（1=不衰减，0=可衰减）',
    version INT DEFAULT 1 COMMENT '版本号（同一key的多个版本）',
    previous_value TEXT COMMENT '上一个版本的值（用于追溯历史）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_pet_id (pet_id),
    INDEX idx_type (type),
    INDEX idx_is_permanent (is_permanent),
    FOREIGN KEY (pet_id) REFERENCES pets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
