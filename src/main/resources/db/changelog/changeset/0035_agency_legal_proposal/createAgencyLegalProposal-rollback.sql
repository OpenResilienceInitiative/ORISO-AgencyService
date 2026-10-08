DROP TABLE IF EXISTS agency_legal_draft_archive;
DROP TABLE IF EXISTS agency_legal_proposal;
DROP TABLE IF EXISTS agency_legal_proposal_distribution;
DROP SEQUENCE IF EXISTS sequence_agency_legal_draft_archive;
DROP SEQUENCE IF EXISTS sequence_agency_legal_proposal;
ALTER TABLE agency_legal_draft DROP COLUMN origin_proposal_id;
