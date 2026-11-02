-- 为 Agentic 智能体增加最大循环次数配置。
ALTER TABLE agent
    ADD COLUMN IF NOT EXISTS max_loop_count INT NOT NULL DEFAULT 10 COMMENT 'Agentic模式最大循环次数，取值范围1-100';
