ALTER TABLE semantic_model
    ADD COLUMN schema_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '表所属的Schema名称' AFTER datasource_id;
