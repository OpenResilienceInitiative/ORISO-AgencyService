-- ADR-003: remove duplicate (agency_id, topic_id) departments, keeping the lowest id per pair.
-- Plain self-join DELETE: safe no-op on empty or already-clean databases.
DELETE t1 FROM `agencyservice`.`agency_topic` t1
INNER JOIN `agencyservice`.`agency_topic` t2
  ON t1.agency_id = t2.agency_id
  AND t1.topic_id = t2.topic_id
  AND t1.id > t2.id;
