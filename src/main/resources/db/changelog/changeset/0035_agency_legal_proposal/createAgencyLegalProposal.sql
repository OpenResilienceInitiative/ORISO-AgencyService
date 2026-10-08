-- ORISO-AgencyService#303 / ORISO-Admin#1070: a Träger forwards its imprint or privacy policy to
-- its Beratungsstellen as a template. Same model as TenantService 0035 one rung up: an immutable
-- snapshot per recipient, adoption copies it into the agency draft (never publishes), and a
-- replaced draft is archived in full.
ALTER TABLE agency_legal_draft ADD COLUMN origin_proposal_id BIGINT NULL;

CREATE TABLE agency_legal_proposal_distribution (
  id VARCHAR(36) NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  tenant_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  audience VARCHAR(16) NOT NULL,
  source VARCHAR(16) NOT NULL,
  source_revision VARCHAR(64) NOT NULL,
  request_fingerprint VARCHAR(64) NOT NULL,
  recipient_ids LONGTEXT NOT NULL,
  content LONGTEXT NOT NULL,
  consent_text LONGTEXT NULL,
  created_by VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_agency_legal_proposal_distribution_request UNIQUE (request_key)
);
CREATE INDEX idx_agency_legal_proposal_distribution_history
  ON agency_legal_proposal_distribution (tenant_id, kind, created_at);

CREATE SEQUENCE IF NOT EXISTS sequence_agency_legal_proposal START WITH 1 INCREMENT BY 1;
CREATE TABLE agency_legal_proposal (
  id BIGINT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  recipient_agency_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  distribution_id VARCHAR(36) NOT NULL,
  audience VARCHAR(16) NOT NULL,
  source VARCHAR(16) NOT NULL,
  source_revision VARCHAR(64) NOT NULL,
  content LONGTEXT NOT NULL,
  consent_text LONGTEXT NULL,
  status VARCHAR(16) NOT NULL,
  created_by VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  decided_by VARCHAR(255) NULL,
  decided_at DATETIME(6) NULL,
  superseded_by_proposal_id BIGINT NULL,
  superseded_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  -- Forwarding the same Träger revision twice never gives one Beratungsstelle two offers.
  CONSTRAINT uk_agency_legal_proposal_source_recipient
    UNIQUE (source, source_revision, recipient_agency_id),
  CONSTRAINT fk_agency_legal_proposal_agency FOREIGN KEY (recipient_agency_id)
    REFERENCES agency (id) ON DELETE CASCADE,
  CONSTRAINT fk_agency_legal_proposal_distribution FOREIGN KEY (distribution_id)
    REFERENCES agency_legal_proposal_distribution (id)
);
CREATE INDEX idx_agency_legal_proposal_inbox
  ON agency_legal_proposal (recipient_agency_id, kind, status, id);

CREATE SEQUENCE IF NOT EXISTS sequence_agency_legal_draft_archive START WITH 1 INCREMENT BY 1;
CREATE TABLE agency_legal_draft_archive (
  id BIGINT NOT NULL,
  agency_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  draft_row_id VARCHAR(36) NOT NULL,
  draft_revision VARCHAR(64) NOT NULL,
  content LONGTEXT NOT NULL,
  consent_text LONGTEXT NULL,
  draft_saved_at DATETIME(6) NOT NULL,
  origin_proposal_id BIGINT NULL,
  replaced_by_proposal_id BIGINT NOT NULL,
  archived_by VARCHAR(255) NULL,
  archived_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_agency_legal_draft_archive_agency FOREIGN KEY (agency_id)
    REFERENCES agency (id) ON DELETE CASCADE
);
CREATE INDEX idx_agency_legal_draft_archive_history
  ON agency_legal_draft_archive (agency_id, kind, archived_at, id);
