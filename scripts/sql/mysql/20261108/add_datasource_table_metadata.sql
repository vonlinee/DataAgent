-- 为数据源初始化结果增加表/列元数据缓存。
-- 执行前提：目标环境中的 agent_datasource_tables 尚未增加以下三个字段；本脚本不可重复执行 ALTER 部分。
ALTER TABLE agent_datasource_tables
    ADD COLUMN schema_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '表所属的Schema名称' AFTER agent_datasource_id,
    ADD COLUMN table_type VARCHAR(50) NULL COMMENT '表的类型: TABLE或VIEW' AFTER table_name,
    ADD COLUMN table_comment VARCHAR(1000) NULL COMMENT '表的注释' AFTER table_type;

CREATE TABLE IF NOT EXISTS datasource_table_columns
(
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    datasource_id INT NOT NULL COMMENT '数据源ID',
    schema_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '表所属的Schema名称',
    table_name VARCHAR(255) NOT NULL COMMENT '数据表名',
    column_name VARCHAR(255) NOT NULL COMMENT '列名',
    column_comment VARCHAR(1000) NULL COMMENT '列注释',
    data_type VARCHAR(255) NULL COMMENT '列数据类型',
    is_primary TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主键：0-否，1-是',
    is_notnull TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否非空：0-否，1-是',
    samples TEXT NULL COMMENT '示例值',
    ordinal_position INT NULL COMMENT '列在表中的顺序，从1开始',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_datasource_table_columns UNIQUE (datasource_id, schema_name, table_name, column_name),
    CONSTRAINT fk_datasource_table_columns_datasource FOREIGN KEY (datasource_id) REFERENCES datasource (id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '表的列信息元数据';
