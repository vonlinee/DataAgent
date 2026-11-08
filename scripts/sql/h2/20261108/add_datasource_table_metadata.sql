-- 为数据源初始化结果增加表/列元数据缓存。ALTER 使用 IF NOT EXISTS，可重复执行。
ALTER TABLE agent_datasource_tables
    ADD COLUMN IF NOT EXISTS schema_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '初始化时捕获的数据表Schema名称';
ALTER TABLE agent_datasource_tables
    ADD COLUMN IF NOT EXISTS table_type VARCHAR(50) COMMENT '初始化时捕获的数据表类型：TABLE或VIEW';
ALTER TABLE agent_datasource_tables
    ADD COLUMN IF NOT EXISTS table_comment VARCHAR(1000) COMMENT '初始化时捕获的数据表注释';

CREATE TABLE IF NOT EXISTS datasource_table_columns
(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id INT NOT NULL COMMENT '数据源ID',
    schema_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '初始化时捕获的Schema名称',
    table_name VARCHAR(255) NOT NULL COMMENT '数据表名',
    column_name VARCHAR(255) NOT NULL COMMENT '列名',
    column_comment VARCHAR(1000) NULL COMMENT '列注释',
    data_type VARCHAR(255) NULL COMMENT '列数据类型',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否主键：FALSE-否，TRUE-是',
    is_notnull BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否非空：FALSE-否，TRUE-是',
    samples CLOB NULL COMMENT '初始化时采集的列样本JSON',
    ordinal_position INT NULL COMMENT '列在表中的顺序，从1开始',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_datasource_table_columns UNIQUE (datasource_id, schema_name, table_name, column_name),
    CONSTRAINT fk_datasource_table_columns_datasource FOREIGN KEY (datasource_id) REFERENCES datasource (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);
COMMENT ON TABLE datasource_table_columns IS '数据源初始化时保存的表列元数据';
