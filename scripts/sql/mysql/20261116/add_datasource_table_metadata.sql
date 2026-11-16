ALTER TABLE business_knowledge
    ADD COLUMN sql_fragment VARCHAR(500) NOT NULL DEFAULT '' COMMENT '相关的SQL片段' AFTER synonyms;
