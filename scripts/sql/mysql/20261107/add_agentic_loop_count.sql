-- 为 Agentic 智能体增加最大循环次数配置。
-- 执行前提：agent.max_loop_count 尚不存在；本脚本不可重复执行。
ALTER TABLE agent
    ADD COLUMN max_loop_count INT NOT NULL DEFAULT 10
    COMMENT 'Agentic模式最大循环次数，取值范围1-100'
    AFTER agent_type;
