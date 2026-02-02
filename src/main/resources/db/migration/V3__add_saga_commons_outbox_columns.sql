-- Add columns needed for saga-commons OutboxEvent compatibility
-- These columns support retry logic and error tracking for outbox events
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS error_message VARCHAR(1000);

-- Add index for status lookups (used by saga-commons repository)
CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events(status);
