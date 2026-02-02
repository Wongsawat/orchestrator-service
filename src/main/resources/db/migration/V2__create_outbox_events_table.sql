-- Outbox pattern table for orchestrator-service
-- Enables transactional event publishing via Debezium CDC

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255),
    payload JSONB NOT NULL,
    headers JSONB,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

-- Index for Debezium CDC to efficiently poll for new events
CREATE INDEX idx_outbox_debezium ON outbox_events(created_at) WHERE status = 'PENDING';

-- Index for querying events by aggregate
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);

-- Index for topic-based queries
CREATE INDEX idx_outbox_topic ON outbox_events(topic);

-- Comment explaining the outbox pattern
COMMENT ON TABLE outbox_events IS 'Outbox pattern table for transactional event publishing. Events are written within the same transaction as domain state changes, then published to Kafka by Debezium CDC.';
COMMENT ON COLUMN outbox_events.id IS 'Unique identifier for the outbox event';
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Type of the aggregate that generated the event (e.g., SagaInstance)';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'ID of the aggregate that generated the event';
COMMENT ON COLUMN outbox_events.event_type IS 'Type of event (e.g., SagaStartedEvent)';
COMMENT ON COLUMN outbox_events.topic IS 'Kafka topic to publish the event to';
COMMENT ON COLUMN outbox_events.partition_key IS 'Kafka partition key for the event';
COMMENT ON COLUMN outbox_events.payload IS 'Event payload as JSONB';
COMMENT ON COLUMN outbox_events.headers IS 'Kafka headers as JSONB';
COMMENT ON COLUMN outbox_events.status IS 'Publication status: PENDING (not yet published) or PUBLISHED (sent to Kafka)';
COMMENT ON COLUMN outbox_events.created_at IS 'Timestamp when the event was created';
COMMENT ON COLUMN outbox_events.published_at IS 'Timestamp when the event was published to Kafka';
