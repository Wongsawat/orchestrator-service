-- Add document_number column to saga_instances for storing the external document number
-- This avoids needing to load the metadata TEXT column when handling saga replies
ALTER TABLE saga_instances
ADD COLUMN IF NOT EXISTS document_number VARCHAR(100);

-- Backfill document_number from metadata JSON where present
-- documentNumber is stored in metadata as: {"eventId":"...","documentNumber":"...","source":"..."}
UPDATE saga_instances
SET document_number = NULLIF(TRIM(BOTH '"' FROM (metadata::json ->> 'documentNumber')), '')::VARCHAR
WHERE metadata IS NOT NULL AND metadata != '' AND metadata::json ->> 'documentNumber' IS NOT NULL;

-- Make document_number not nullable since every saga should have one
-- But first ensure all existing sagas have values (backfill above handles this)
-- The column is only for tracking; the actual value is in metadata JSON
