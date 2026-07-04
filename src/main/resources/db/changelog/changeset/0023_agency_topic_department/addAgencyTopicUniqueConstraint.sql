-- ADR-003: a topic may be linked to an agency at most once; duplicate (agency, topic) becomes
-- a prevented data error, not a supported case. Reruns are guarded by the Liquibase
-- precondition (onFail=MARK_RAN) in 0023_changeSet.xml.
ALTER TABLE `agencyservice`.`agency_topic`
ADD CONSTRAINT `uq_agency_topic` UNIQUE (`agency_id`, `topic_id`);
