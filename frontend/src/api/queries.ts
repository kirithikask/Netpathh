import { api } from './client';
import type {
  ApplicationDto,
  AuthResponse,
  DashboardStats,
  EndpointDto,
  NetworkPathDto,
  Page,
  PathHealthDto,
  RouteRecommendationDto,
  TelemetryDto,
  TelemetryRequest,
  TrafficShiftDto,
  PathHealthConfig,
  PathStatus,
} from './types';

export interface PathQuery {
  page?: number;
  size?: number;
  status?: PathStatus | '';
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', { email, password });
  return data;
}

export async function fetchPathHealthConfig(): Promise<PathHealthConfig> {
  const { data } = await api.get<PathHealthConfig>('/config/path-health');
  return data;
}

export async function fetchDashboardStats(): Promise<DashboardStats> {
  const { data } = await api.get<DashboardStats>('/dashboard/stats');
  return data;
}

export async function fetchApplications(): Promise<ApplicationDto[]> {
  const { data } = await api.get<ApplicationDto[]>('/applications');
  return data;
}

/**
 * Region filtering is the API's job: it pages the whole estate, not the slice already loaded.
 */
export async function fetchEndpoints(params: {
  page?: number;
  size?: number;
  region?: string;
}): Promise<Page<EndpointDto>> {
  const path = params.region
    ? `/endpoints/region/${encodeURIComponent(params.region)}`
    : '/endpoints';

  const { data } = await api.get<Page<EndpointDto>>(path, {
    params: { page: params.page ?? 0, size: params.size ?? 25 },
  });
  return data;
}

export async function fetchRegions(): Promise<string[]> {
  const { data } = await api.get<string[]>('/endpoints/regions');
  return data;
}

export async function fetchPaths(query: PathQuery): Promise<Page<NetworkPathDto>> {
  const { data } = await api.get<Page<NetworkPathDto>>('/paths', {
    params: {
      page: query.page ?? 0,
      size: query.size ?? 25,
      ...(query.status ? { status: query.status } : {}),
    },
  });
  return data;
}

export async function fetchPath(id: number): Promise<NetworkPathDto> {
  const { data } = await api.get<NetworkPathDto>(`/paths/${id}`);
  return data;
}

export async function fetchPathHealth(id: number): Promise<PathHealthDto> {
  const { data } = await api.get<PathHealthDto>(`/paths/${id}/health`);
  return data;
}

export async function fetchPathMetrics(id: number, size = 25): Promise<Page<TelemetryDto>> {
  const { data } = await api.get<Page<TelemetryDto>>(`/paths/${id}/metrics`, {
    params: { page: 0, size },
  });
  return data;
}

export async function fetchRecommendation(id: number): Promise<RouteRecommendationDto | null> {
  const { data } = await api.get<RouteRecommendationDto | null>(`/paths/${id}/recommendation`);
  return data;
}

export async function fetchShiftHistory(id: number): Promise<TrafficShiftDto[]> {
  const { data } = await api.get<TrafficShiftDto[]>(`/paths/${id}/shifts`);
  return data;
}

export async function fetchRecentShifts(limit = 8): Promise<TrafficShiftDto[]> {
  const { data } = await api.get<TrafficShiftDto[]>('/shifts/recent', { params: { limit } });
  return data;
}

export async function submitTelemetry(id: number, request: TelemetryRequest): Promise<PathHealthDto> {
  const { data } = await api.post<PathHealthDto>(`/paths/${id}/metrics`, request);
  return data;
}

export async function simulateShift(
  id: number,
  body: { reason: string; recommendedPathId: number },
): Promise<TrafficShiftDto> {
  const { data } = await api.post<TrafficShiftDto>(`/paths/${id}/shift`, body);
  return data;
}
