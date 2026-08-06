-- ADR-003 dev mode (#oriso-codereview): make the data protection officer contact column
-- explicitly NULLABLE at the DB level. It was already created NULL in changeset 0016, but the
-- JPA entity previously declared nullable=false, re-imposing a hard NOT NULL on the H2
-- create-drop test schema and blocking testing/dev. "Required" is now enforced at the
-- application layer, gated by agency.department.require-dpo-contact (true in prod, false in
-- testing/dev). This changeset locks the nullable contract so the two layers cannot drift.
-- Idempotent: re-modifying an already-nullable column is a safe no-op.
ALTER TABLE `agencyservice`.`agency`
MODIFY COLUMN data_protection_officer_contact longtext NULL;
