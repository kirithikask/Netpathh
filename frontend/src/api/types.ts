/** Mirrors the DTOs returned by the NETPATH Spring Boot API. */

export type PathStatus = 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN';

export const PATH_STATUSES: PathStatus[] = ['HEALTHY', 'DEGRADED', 'DOWN', 'UNKNOWN'];

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface DashboardStats {
  totalApplications: number;
  totalEndpoints: number;
  totalPaths: number;
  healthyPaths: number;
  degradedPaths: number;
  downPaths: number;
  unknownPaths: number;
}

export interface ApplicationDto {
  id: number;
  name: string;
  description: string | null;
  ownerId: number | null;
  status: string;
  endpointCount: number | null;
  pathCount: number | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface EndpointDto {
  id: number;
  name: string;
  ipAddress: string;
  region: string;
  status: string;
  applicationId: number | null;
  applicationName: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface NetworkPathDto {
  id: number;
  pathName: string;
  description: string | null;
  sourceEndpointId: number;
  destinationEndpointId: number;
  applicationId: number | null;
  applicationName: string | null;
  sourceEndpointName: string | null;
  sourceEndpointIp: string | null;
  sourceEndpointRegion: string | null;
  destinationEndpointName: string | null;
  destinationEndpointIp: string | null;
  destinationEndpointRegion: string | null;
  hops: number | null;
  isPrimary: boolean | null;
  status: PathStatus;
  averageLatencyMs: number | null;
  averagePacketLossPct: number | null;
  metricsCount: number | null;
  lastUpdated: string | null;
}

export interface TelemetryDto {
  id: number;
  pathId: number;
  latencyMs: number;
  packetLossPct: number;
  throughputMbps: number | null;
  timestamp: string;
}

export interface PathHealthDto {
  pathId: number;
  status: PathStatus;
  averageLatencyMs: number | null;
  averagePacketLossPct: number | null;
  maxLatencyMs: number | null;
  maxPacketLossPct: number | null;
  metricsCount: number;
  lastUpdated: string;
}

export interface RouteRecommendationDto {
  currentPathId: number;
  currentPathName: string;
  currentStatus: PathStatus;
  currentAvgLatencyMs: number | null;
  currentAvgPacketLossPct: number | null;
  sourceEndpoint: string;
  destinationEndpoint: string;
  recommendedPathId: number | null;
  recommendedPathName: string | null;
  recommendedStatus: PathStatus | null;
  recommendedAvgLatencyMs: number | null;
  recommendedAvgPacketLossPct: number | null;
  reason: string;
  hasAlternative: boolean;
  recommendationTimestamp: string;
}

export interface TrafficShiftDto {
  id: number | null;
  pathId: number;
  oldPathId: number | null;
  oldPathName: string | null;
  newPathId: number | null;
  newPathName: string | null;
  reason: string;
  status: string;
  shiftedAt: string;
}

export interface AuthResponse {
  token: string;
  email: string;
  name: string;
  role: string;
}

export interface ApiError {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path?: string;
}

export interface PathHealthConfig {
  degradedLatencyMs: number;
  downLatencyMs: number;
  degradedPacketLossPct: number;
  downPacketLossPct: number;
  minMetricsForEvaluation: number;
  windowMinutes: number;
  cacheTtlSeconds: number;
}

export interface TelemetryRequest {
  latencyMs: number;
  packetLossPct: number;
  throughputMbps?: number | null;
}
