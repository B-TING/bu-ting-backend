-- Round lifecycle: DRAFT/CANCELLED added, OPEN renamed to ACTIVE.
-- Round-level default excellence reward + cancel reason. roundNo now client-supplied and required.

-- Migrate existing OPEN rounds to ACTIVE BEFORE changing the status check constraint.
UPDATE zone_event_round SET status = 'ACTIVE' WHERE status = 'OPEN';

-- Update status check constraint to include new lifecycle states.
ALTER TABLE zone_event_round DROP CONSTRAINT ck_zone_event_round_status;
ALTER TABLE zone_event_round
    ADD CONSTRAINT ck_zone_event_round_status
        CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'CLOSED', 'CANCELLED', 'SETTLED'));

-- Add new columns for round-level rewards and cancellation reason.
ALTER TABLE zone_event_round
    ADD COLUMN excellence_reward JSONB,
    ADD COLUMN cancel_reason VARCHAR(300);

-- Convert round_no from nullable (partial index) to NOT NULL (regular unique index).
DROP INDEX uk_zone_event_round_no;

-- No round should have round_no = NULL in any real environment yet (pre-launch schema).
-- Clean up any orphaned test/dev rows and their children before enforcing NOT NULL.
DELETE FROM zone_event_round_slot WHERE round_id IN (SELECT round_id FROM zone_event_round WHERE round_no IS NULL);
DELETE FROM zone_event_backup_target WHERE round_id IN (SELECT round_id FROM zone_event_round WHERE round_no IS NULL);
UPDATE zone_event SET round_id = NULL WHERE round_id IN (SELECT round_id FROM zone_event_round WHERE round_no IS NULL);
DELETE FROM zone_event_round WHERE round_no IS NULL;

ALTER TABLE zone_event_round ALTER COLUMN round_no SET NOT NULL;
CREATE UNIQUE INDEX uk_zone_event_round_no ON zone_event_round (round_no);
