ALTER TABLE semantic_model
    ADD COLUMN IF NOT EXISTS schema_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '表所属的Schema名称';
