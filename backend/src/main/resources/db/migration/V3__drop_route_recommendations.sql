-- V3__drop_route_recommendations.sql
--
-- Recommendations used to be written to this table as an audit trail by the GET endpoint that
-- computed them, which meant a read request mutated the database and a polling dashboard grew the
-- table one row per poll. Nothing ever read the rows back, so the table was write-only.
--
-- Recommendation evaluation is now a pure read. The decision an operator actually takes is the
-- action worth recording, and that is already persisted by traffic_shift_logs. Keeping a second,
-- unread history of suggestions would be a duplicate source of truth for the same event.

DROP TABLE IF EXISTS route_recommendations;
