-- ORISO-Admin#1026: agencies for the invite-bar type-ahead search (AgencyAdminSearchPickerIT).
-- Runs after AgencyDatabase.sql. Names share the token "Zebrafink" so a name search is isolated
-- from the 1133 seed agencies; "Lotsenhaus" only matches through its topic.
INSERT INTO AGENCY (ID, TENANT_ID, NAME, POSTCODE, CITY, IS_TEAM_AGENCY, CONSULTING_TYPE, IS_OFFLINE, IS_EXTERNAL, CREATE_DATE, UPDATE_DATE, DELETE_DATE)
VALUES (9001, 1, 'Zebrafink Mitte', '50667', 'Köln', 0, 0, 0, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00', null),
       (9002, 1, 'Zebrafink Süd', '50999', 'Köln', 0, 0, 0, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00', null),
       (9003, 2, 'Zebrafink Hafen', '20457', 'Hamburg', 0, 0, 0, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00', null),
       (9004, 1, 'Zebrafink Alt', '50668', 'Köln', 0, 0, 0, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00', '2026-09-10 10:00:00'),
       (9005, 1, 'Lotsenhaus', '50670', 'Köln', 0, 0, 0, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00', null);

INSERT INTO AGENCY_TOPIC (ID, AGENCY_ID, TOPIC_ID, CREATE_DATE, UPDATE_DATE)
VALUES (90011, 9001, 501, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (90021, 9002, 502, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (90031, 9003, 601, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (90041, 9004, 501, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (90051, 9005, 501, '2026-09-01 10:00:00', '2026-09-01 10:00:00');
