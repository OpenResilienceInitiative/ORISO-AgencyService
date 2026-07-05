-- Rollback: restore the NOT NULL constraint on the data protection officer contact column.
-- Only safe when every existing agency row has a non-null value; otherwise this fails, which is
-- the intended guard (ADR-003 production requirement).
ALTER TABLE `agencyservice`.`agency`
MODIFY COLUMN data_protection_officer_contact longtext NOT NULL;
