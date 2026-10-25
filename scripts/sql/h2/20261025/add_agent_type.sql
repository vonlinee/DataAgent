-- 为已有 H2 环境增加 Agent 实现方式字段和索引。
-- 脚本可重复执行；执行前提：agent 表已存在。
ALTER TABLE agent
    ADD COLUMN IF NOT EXISTS agent_type VARCHAR(32) NOT NULL DEFAULT 'workflow'
    COMMENT 'Agent实现方式：workflow-工作流，agentic-智能体自主编排';
CREATE INDEX IF NOT EXISTS idx_agent_type ON agent (agent_type);
