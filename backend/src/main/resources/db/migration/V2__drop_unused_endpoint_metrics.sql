-- V2__drop_unused_endpoint_metrics.sql
--
-- Latency, packet loss and throughput are attributes of a network path, and only path_metrics is
-- ever written. These endpoint columns were never populated by any code path, so they are removed
-- rather than left as a second, permanently empty place to look for the same numbers.

ALTER TABLE endpoints DROP COLUMN IF EXISTS latency_ms;
ALTER TABLE endpoints DROP COLUMN IF EXISTS packet_loss_pct;
ALTER TABLE endpoints DROP COLUMN IF EXISTS throughput_mbps;
