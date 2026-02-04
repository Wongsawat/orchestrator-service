package com.wpanther.orchestrator.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for orchestrator database schema.
 * Verifies database structure without requiring Kafka/Debezium.
 * <p>
 * Prerequisites:
 *   1. Database must be accessible: jdbc:postgresql://localhost:5433/orchestrator_db
 *   2. Flyway migrations must have been run
 */
@DisplayName("Orchestrator Database Schema Tests")
class SagaDatabaseIntegrationTest extends AbstractCdcIntegrationTest {

    @Test
    @DisplayName("Should have saga_instances table")
    void shouldHaveSagaInstancesTable() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'saga_instances'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should have saga_commands table")
    void shouldHaveSagaCommandsTable() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'saga_commands'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should have outbox_events table")
    void shouldHaveOutboxEventsTable() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'outbox_events'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should have saga_data table")
    void shouldHaveSagaDataTable() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'saga_data'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should have core columns in saga_instances table")
    void shouldHaveCoreSagaInstancesColumns() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_name = 'saga_instances' " +
            "AND column_name IN ('id', 'document_type', 'document_id', 'current_step', 'status', 'created_at', 'updated_at')");

        List<String> columnNames = columns.stream()
            .map(c -> (String) c.get("column_name"))
            .toList();

        assertThat(columnNames).containsExactlyInAnyOrder(
            "id", "document_type", "document_id", "current_step", "status", "created_at", "updated_at");
    }

    @Test
    @DisplayName("Should have core columns in saga_commands table")
    void shouldHaveCoreSagaCommandsColumns() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_name = 'saga_commands' " +
            "AND column_name IN ('id', 'saga_id', 'command_type', 'target_step', 'payload', 'status', 'created_at')");

        List<String> columnNames = columns.stream()
            .map(c -> (String) c.get("column_name"))
            .toList();

        assertThat(columnNames).containsExactlyInAnyOrder(
            "id", "saga_id", "command_type", "target_step", "payload", "status", "created_at");
    }

    @Test
    @DisplayName("Should have Debezium routing columns in outbox_events")
    void shouldHaveDebeziumRoutingColumns() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_name = 'outbox_events' " +
            "AND column_name IN ('topic', 'partition_key', 'headers')");

        List<String> columnNames = columns.stream()
            .map(c -> (String) c.get("column_name"))
            .toList();

        assertThat(columnNames).containsExactlyInAnyOrder("topic", "partition_key", "headers");
    }

    @Test
    @DisplayName("Should have saga-commons columns in outbox_events")
    void shouldHaveSagaCommonsColumns() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_name = 'outbox_events' " +
            "AND column_name IN ('retry_count', 'error_message', 'published_at')");

        List<String> columnNames = columns.stream()
            .map(c -> (String) c.get("column_name"))
            .toList();

        assertThat(columnNames).containsExactlyInAnyOrder("retry_count", "error_message", "published_at");
    }

    @Test
    @DisplayName("Should have status index on outbox_events for Debezium polling")
    void shouldHaveStatusIndex() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'outbox_events' AND indexname LIKE '%status%'");

        assertThat(indexes).isNotEmpty();
    }

    @Test
    @DisplayName("Should have foreign key constraint from saga_commands to saga_instances")
    void shouldHaveForeignKeyConstraint() {
        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(
            "SELECT tc.constraint_name " +
            "FROM information_schema.table_constraints tc " +
            "JOIN information_schema.key_column_usage kcu " +
            "  ON tc.constraint_name = kcu.constraint_name " +
            "WHERE tc.table_name = 'saga_commands' " +
            "  AND tc.constraint_type = 'FOREIGN KEY' " +
            "  AND kcu.column_name = 'saga_id'");

        assertThat(constraints).isNotEmpty();
    }

    @Test
    @DisplayName("Should have foreign key constraint from saga_data to saga_instances")
    void shouldHaveSagaDataForeignKeyConstraint() {
        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(
            "SELECT tc.constraint_name " +
            "FROM information_schema.table_constraints tc " +
            "JOIN information_schema.key_column_usage kcu " +
            "  ON tc.constraint_name = kcu.constraint_name " +
            "WHERE tc.table_name = 'saga_data' " +
            "  AND tc.constraint_type = 'FOREIGN KEY' " +
            "  AND kcu.column_name = 'saga_id'");

        assertThat(constraints).isNotEmpty();
    }

    @Test
    @DisplayName("Should have updated_at index on saga_instances")
    void shouldHaveUpdatedAtIndex() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'saga_instances' AND indexname LIKE '%updated_at%'");

        assertThat(indexes).isNotEmpty();
    }

    @Test
    @DisplayName("Should have status index on saga_instances")
    void shouldHaveStatusIndexOnSagaInstances() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'saga_instances' AND indexname LIKE '%status%'");

        assertThat(indexes).isNotEmpty();
    }

    @Test
    @DisplayName("Should have composite document index on saga_instances")
    void shouldHaveCompositeDocumentIndex() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'saga_instances' AND indexname LIKE '%document%'");

        assertThat(indexes).isNotEmpty();
    }

    @Test
    @DisplayName("Should have saga_id index on saga_commands")
    void shouldHaveSagaIdIndexOnCommands() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'saga_commands' AND indexname LIKE '%saga_id%'");

        assertThat(indexes).isNotEmpty();
    }

    @Test
    @DisplayName("Should verify status column allows valid values")
    void shouldVerifyStatusColumnAllowsValidValues() {
        // The status column is a VARCHAR(20) without explicit CHECK constraint
        // Valid values are enforced at application level
        String statusType = jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_name = 'saga_instances' AND column_name = 'status'",
            String.class);

        assertThat(statusType).isEqualTo("character varying");
    }

    @Test
    @DisplayName("Should verify varchar type for primary keys")
    void shouldVerifyVarcharTypeForPrimaryKeys() {
        // Check saga_instances.id type
        String sagaInstancesIdType = jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_name = 'saga_instances' AND column_name = 'id'",
            String.class);

        // IDs are stored as VARCHAR(36) for UUID string representation
        assertThat(sagaInstancesIdType).isEqualTo("character varying");

        // Check saga_commands.id type
        String sagaCommandsIdType = jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_name = 'saga_commands' AND column_name = 'id'",
            String.class);

        assertThat(sagaCommandsIdType).isEqualTo("character varying");
    }

    @Test
    @DisplayName("Should have text column for metadata in saga_data")
    void shouldHaveTextMetadataColumn() {
        String metadataType = jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_name = 'saga_data' AND column_name = 'metadata'",
            String.class);

        assertThat(metadataType).isEqualTo("text");
    }

    @Test
    @DisplayName("Should have text column for payload in saga_commands")
    void shouldHaveTextPayloadColumn() {
        String payloadType = jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_name = 'saga_commands' AND column_name = 'payload'",
            String.class);

        assertThat(payloadType).isEqualTo("text");
    }

    @Test
    @DisplayName("Should have timestamp columns for tracking in saga_instances")
    void shouldHaveTimestampColumnsForTracking() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_name = 'saga_instances' " +
            "AND column_name IN ('created_at', 'updated_at', 'completed_at')");

        List<String> columnNames = columns.stream()
            .map(c -> (String) c.get("column_name"))
            .toList();

        assertThat(columnNames).containsExactlyInAnyOrder("created_at", "updated_at", "completed_at");
    }
}
