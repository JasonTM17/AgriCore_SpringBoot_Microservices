ALTER TABLE chat_generations
    ADD COLUMN tool_evidence TEXT NOT NULL DEFAULT '{"facts":[]}';

ALTER TABLE chat_generations ADD CONSTRAINT ck_generation_tool_evidence_size CHECK (
    CHAR_LENGTH(tool_evidence) BETWEEN 12 AND 24000
);
