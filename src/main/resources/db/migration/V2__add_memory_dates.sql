ALTER TABLE memories ADD COLUMN memory_date DATE;
ALTER TABLE memories ADD COLUMN created_at TIMESTAMP;
ALTER TABLE memories ADD COLUMN updated_at TIMESTAMP;

UPDATE memories
SET memory_date = CURRENT_DATE,
    created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE memories ALTER COLUMN memory_date SET NOT NULL;
ALTER TABLE memories ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE memories ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX idx_memories_user_memory_date ON memories (user_id, memory_date);
