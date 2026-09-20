-- V1__initial_schema.sql
-- NETPATH Database Schema

-- Users table for authentication
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- Applications table
CREATE TABLE applications (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id BIGINT REFERENCES users(id),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_applications_owner ON applications(owner_id);
CREATE INDEX idx_applications_status ON applications(status);

-- Endpoints table
CREATE TABLE endpoints (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    region VARCHAR(100) NOT NULL,
    latency_ms INTEGER,
    packet_loss_pct DECIMAL(5,2),
    throughput_mbps DECIMAL(10,2),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_endpoints_application ON endpoints(application_id);
CREATE INDEX idx_endpoints_region ON endpoints(region);
CREATE INDEX idx_endpoints_status ON endpoints(status);

-- Network paths table
CREATE TABLE network_paths (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    source_endpoint_id BIGINT NOT NULL REFERENCES endpoints(id) ON DELETE CASCADE,
    destination_endpoint_id BIGINT NOT NULL REFERENCES endpoints(id) ON DELETE CASCADE,
    path_name VARCHAR(255) NOT NULL,
    hops INTEGER NOT NULL DEFAULT 1,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_source_endpoint FOREIGN KEY (source_endpoint_id) REFERENCES endpoints(id),
    CONSTRAINT fk_destination_endpoint FOREIGN KEY (destination_endpoint_id) REFERENCES endpoints(id),
    CONSTRAINT ck_different_endpoints CHECK (source_endpoint_id != destination_endpoint_id)
);

CREATE INDEX idx_network_paths_application ON network_paths(application_id);
CREATE INDEX idx_network_paths_source ON network_paths(source_endpoint_id);
CREATE INDEX idx_network_paths_destination ON network_paths(destination_endpoint_id);
CREATE INDEX idx_network_paths_status ON network_paths(status);
CREATE INDEX idx_network_paths_primary ON network_paths(is_primary);

-- Path metrics (telemetry history)
CREATE TABLE path_metrics (
    id BIGSERIAL PRIMARY KEY,
    path_id BIGINT NOT NULL REFERENCES network_paths(id) ON DELETE CASCADE,
    latency_ms INTEGER NOT NULL,
    packet_loss_pct DECIMAL(5,2) NOT NULL,
    throughput_mbps DECIMAL(10,2),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_path_metrics_path ON path_metrics(path_id);
CREATE INDEX idx_path_metrics_timestamp ON path_metrics(timestamp);
CREATE INDEX idx_path_metrics_path_timestamp ON path_metrics(path_id, timestamp DESC);

-- Route recommendations table
CREATE TABLE route_recommendations (
    id BIGSERIAL PRIMARY KEY,
    path_id BIGINT NOT NULL REFERENCES network_paths(id) ON DELETE CASCADE,
    recommended_path_id BIGINT REFERENCES network_paths(id) ON DELETE SET NULL,
    reason TEXT NOT NULL,
    source_endpoint_id BIGINT NOT NULL,
    destination_endpoint_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_route_recommendations_path ON route_recommendations(path_id);
CREATE INDEX idx_route_recommendations_created ON route_recommendations(created_at DESC);

-- Traffic shift logs (simulated)
CREATE TABLE traffic_shift_logs (
    id BIGSERIAL PRIMARY KEY,
    path_id BIGINT NOT NULL REFERENCES network_paths(id) ON DELETE CASCADE,
    old_path_id BIGINT REFERENCES network_paths(id) ON DELETE SET NULL,
    new_path_id BIGINT REFERENCES network_paths(id) ON DELETE SET NULL,
    reason TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SIMULATED',
    shifted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_traffic_shift_logs_path ON traffic_shift_logs(path_id);
CREATE INDEX idx_traffic_shift_logs_timestamp ON traffic_shift_logs(shifted_at DESC);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_applications_updated_at BEFORE UPDATE ON applications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_endpoints_updated_at BEFORE UPDATE ON endpoints
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_network_paths_updated_at BEFORE UPDATE ON network_paths
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
