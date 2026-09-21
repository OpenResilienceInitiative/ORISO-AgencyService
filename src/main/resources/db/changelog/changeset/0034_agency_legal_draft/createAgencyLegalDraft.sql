CREATE TABLE agency_legal_draft (
  row_id VARCHAR(36) NOT NULL,
  agency_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  content LONGTEXT NOT NULL,
  consent_text LONGTEXT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  saved_at DATETIME(6) NOT NULL,
  PRIMARY KEY (row_id),
  CONSTRAINT uk_agency_legal_draft_owner_kind UNIQUE (agency_id, kind),
  CONSTRAINT fk_agency_legal_draft_agency FOREIGN KEY (agency_id) REFERENCES agency (id)
    ON DELETE CASCADE
);
