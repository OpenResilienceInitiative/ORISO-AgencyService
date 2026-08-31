-- ADR-021 decision 4: the consent sentence is a FIELD OF THE DATA PROTECTION POLICY, not a legal
-- text kind of its own. It therefore lives next to every column that already stores a DPP, and
-- shares that DPP's publication status and its version history.
--
-- One history instead of two: "which consent wording belonged to which policy" is answered by
-- "the same version", not reconstructed by correlating timestamps. Timestamp correlation was
-- already a defect source in the AVV work (second precision vs. MariaDB DATETIME(0)).
--
-- Not stored here, deliberately: the cookie/authentication notice. It is a fixed, non-editable
-- addendum the client renders beneath the sentence (ADR-021 decision 2) - persisting it per Träger
-- would let a Träger edit away the platform's own mandatory disclosure.
--
-- Same shape as the DPP itself: a JSON language -> text map.
ALTER TABLE `agencyservice`.`agency_topic`
  ADD COLUMN IF NOT EXISTS `consent_text` longtext NULL;

ALTER TABLE `agencyservice`.`agency`
  ADD COLUMN IF NOT EXISTS `consent_text` longtext NULL;

ALTER TABLE `agencyservice`.`legal_text`
  ADD COLUMN IF NOT EXISTS `consent_text` longtext NULL;

ALTER TABLE `agencyservice`.`legal_text_version`
  ADD COLUMN IF NOT EXISTS `consent_text` longtext NULL;
