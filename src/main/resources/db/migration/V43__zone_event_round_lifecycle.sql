-- Round lifecycle: DRAFT/CANCELLED added, OPEN renamed to ACTIVE.
-- Round-level default excellence reward + cancel reason. roundNo now client-supplied and required.

ALTER TABLE zone_event_round DROP CONSTRAINT ck_zone_event_round_status;
ALTER TABLE zone_event_round
    ADD CONSTRAINT ck_zone_event_round_status
        CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'CLOSED', 'CANCELLED', 'SETTLED'));

UPDATE zone_event_round SET status = 'ACTIVE' WHERE status = 'OPEN';

ALTER TABLE zone_event_round
    ADD COLUMN excellence_reward JSONB,
    ADD COLUMN cancel_reason VARCHAR(300);

DROP INDEX uk_zone_event_round_no;
DELETE FROM zone_event_round WHERE round_no IS NULL;
ALTER TABLE zone_event_round ALTER COLUMN round_no SET NOT NULL;
CREATE UNIQUE INDEX uk_zone_event_round_no ON zone_event_round (round_no);
