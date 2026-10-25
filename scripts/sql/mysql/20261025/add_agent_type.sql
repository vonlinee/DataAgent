-- 为已有环境增加 Agent 实现方式字段。
-- 执行前提：agent 表已存在。脚本可重复执行，适用于 MySQL 5.7+。
SET @agent_type_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent'
      AND column_name = 'agent_type'
);
SET @add_agent_type_sql := IF(
    @agent_type_column_exists = 0,
    'ALTER TABLE agent ADD COLUMN agent_type VARCHAR(32) NOT NULL DEFAULT ''workflow'' COMMENT ''Agent实现方式：workflow-工作流，agentic-智能体自主编排'' AFTER avatar',
    'SELECT 1'
);
PREPARE add_agent_type_statement FROM @add_agent_type_sql;
EXECUTE add_agent_type_statement;
DEALLOCATE PREPARE add_agent_type_statement;

SET @agent_type_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'agent'
      AND index_name = 'idx_agent_type'
);
SET @add_agent_type_index_sql := IF(
    @agent_type_index_exists = 0,
    'CREATE INDEX idx_agent_type ON agent (agent_type)',
    'SELECT 1'
);
PREPARE add_agent_type_index_statement FROM @add_agent_type_index_sql;
EXECUTE add_agent_type_index_statement;
DEALLOCATE PREPARE add_agent_type_index_statement;
