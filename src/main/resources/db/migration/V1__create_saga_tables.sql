-- =====================================================
-- Saga Orchestrator Service Database Schema
-- PostgreSQL Database: orchestrator_db
-- =====================================================

-- =====================================================
-- Main saga instances table
-- =====================================================
CREATE TABLE IF NOT EXISTS saga_instances (
    id VARCHAR(36) PRIMARY KEY,
    document_type VARCHAR(20) NOT NULL,
    document_id VARCHAR(100) NOT NULL,
    current_step VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    file_path VARCHAR(500),
    xml_content TEXT,
    metadata TEXT,
    file_size BIGINT,
    mime_type VARCHAR(100),
    checksum VARCHAR(255),
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    version INTEGER NOT NULL DEFAULT 0
);

-- Indexes for saga_instances
CREATE INDEX IF NOT EXISTS idx_saga_instances_status ON saga_instances(status);
CREATE INDEX IF NOT EXISTS idx_saga_instances_document ON saga_instances(document_type, document_id);
CREATE INDEX IF NOT EXISTS idx_saga_instances_updated_at ON saga_instances(updated_at);

-- =====================================================
-- Command history for audit/compensation
-- =====================================================
CREATE TABLE IF NOT EXISTS saga_commands (
    id VARCHAR(36) PRIMARY KEY,
    saga_id VARCHAR(36) NOT NULL REFERENCES saga_instances(id) ON DELETE CASCADE,
    command_type VARCHAR(100) NOT NULL,
    target_step VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    correlation_id VARCHAR(100)
);

-- Indexes for saga_commands
CREATE INDEX IF NOT EXISTS idx_saga_commands_saga_id ON saga_commands(saga_id);
CREATE INDEX IF NOT EXISTS idx_saga_commands_status ON saga_commands(status);
CREATE INDEX IF NOT EXISTS idx_saga_commands_created_at ON saga_commands(created_at);

-- =====================================================
-- Document metadata storage (alternative storage in saga_instances)
-- This table can be used for larger document storage needs
-- =====================================================
CREATE TABLE IF NOT EXISTS saga_data (
    saga_id VARCHAR(36) PRIMARY KEY REFERENCES saga_instances(id) ON DELETE CASCADE,
    file_path VARCHAR(500),
    xml_content TEXT,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for saga_data
CREATE INDEX IF NOT EXISTS idx_saga_data_created_at ON saga_data(created_at);

-- =====================================================
-- Outbox pattern table for transactional event publishing
-- Enables Debezium CDC to capture events and publish to Kafka
-- =====================================================
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255),
    payload TEXT NOT NULL,
    headers TEXT,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    error_message VARCHAR(1000)
);

-- Indexes for outbox_events
CREATE INDEX IF NOT EXISTS idx_outbox_debezium ON outbox_events(created_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_topic ON outbox_events(topic);
CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events(status);

-- =====================================================
-- Add comments for documentation
-- =====================================================
COMMENT ON TABLE saga_instances IS 'Stores saga instances for orchestrating document processing workflows';
COMMENT ON TABLE saga_commands IS 'Stores command history for saga instances for audit and compensation';
COMMENT ON TABLE saga_data IS 'Stores document metadata and content for saga instances';
COMMENT ON TABLE outbox_events IS 'Outbox pattern table for transactional event publishing. Events are written within the same transaction as domain state changes, then published to Kafka by Debezium CDC.';

COMMENT ON COLUMN saga_instances.id IS 'Unique identifier for the saga instance (UUID)';
COMMENT ON COLUMN saga_instances.document_type IS 'Type of document (INVOICE, TAX_INVOICE, etc.)';
COMMENT ON COLUMN saga_instances.document_id IS 'External document identifier';
COMMENT ON COLUMN saga_instances.current_step IS 'Current step in the saga workflow';
COMMENT ON COLUMN saga_instances.status IS 'Current status (STARTED, IN_PROGRESS, COMPLETED, COMPENSATING, FAILED)';
COMMENT ON COLUMN saga_instances.retry_count IS 'Number of retry attempts for current step';
COMMENT ON COLUMN saga_instances.max_retries IS 'Maximum allowed retry attempts';
COMMENT ON COLUMN saga_instances.version IS 'Optimistic locking version';

COMMENT ON COLUMN saga_commands.correlation_id IS 'Correlation ID for tracking request-response pairs';
COMMENT ON COLUMN saga_commands.status IS 'Command status (PENDING, SENT, COMPLETED, FAILED, COMPENSATED)';

COMMENT ON COLUMN outbox_events.id IS 'Unique identifier for the outbox event';
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Type of the aggregate that generated the event (e.g., SagaInstance)';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'ID of the aggregate that generated the event';
COMMENT ON COLUMN outbox_events.event_type IS 'Type of event (e.g., SagaStartedEvent)';
COMMENT ON COLUMN outbox_events.topic IS 'Kafka topic to publish the event to';
COMMENT ON COLUMN outbox_events.partition_key IS 'Kafka partition key for the event';
COMMENT ON COLUMN outbox_events.payload IS 'Event payload as TEXT (JSON format)';
COMMENT ON COLUMN outbox_events.headers IS 'Kafka headers as TEXT (JSON format)';
COMMENT ON COLUMN outbox_events.status IS 'Publication status: PENDING (not yet published) or PUBLISHED (sent to Kafka)';
COMMENT ON COLUMN outbox_events.created_at IS 'Timestamp when the event was created';
COMMENT ON COLUMN outbox_events.published_at IS 'Timestamp when the event was published to Kafka';
COMMENT ON COLUMN outbox_events.retry_count IS 'Number of retry attempts for publishing';
COMMENT ON COLUMN outbox_events.error_message IS 'Error message if publishing failed';
