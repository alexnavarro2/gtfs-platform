const BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

const TOKEN_STORAGE_KEY = 'gtfsplatform.authToken';
let authToken: string | null = localStorage.getItem(TOKEN_STORAGE_KEY);
let onUnauthorized: (() => void) | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
  if (token) localStorage.setItem(TOKEN_STORAGE_KEY, token);
  else localStorage.removeItem(TOKEN_STORAGE_KEY);
}

export function getAuthToken() {
  return authToken;
}

// Shell la registra una vez al montar para poder limpiar la sesión y mandar al
// login cuando el token expira o el servidor lo rechaza (401).
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(typeof options.body === 'string' && !isFormData ? { 'Content-Type': 'application/json' } : {}),
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
      ...(options.headers || {}),
    },
  });
  if (res.status === 401) {
    setAuthToken(null);
    onUnauthorized?.();
  }
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`;
    try {
      const body = await res.json();
      message = body.error || JSON.stringify(body);
    } catch {
      // ignore
    }
    throw new Error(message);
  }
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return res.json();
  }
  return undefined as unknown as T;
}

const get = <T>(path: string) => request<T>(path);
const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body !== undefined ? JSON.stringify(body) : undefined });
const put = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'PUT', body: body !== undefined ? JSON.stringify(body) : undefined });
const del = <T>(path: string) => request<T>(path, { method: 'DELETE' });

export interface Feed {
  id: string;
  name: string;
  description?: string;
}

export interface FeedVersion {
  id: string;
  feed: Feed;
  versionNumber: number;
  status: 'DRAFT' | 'VALIDATING' | 'VALID' | 'PUBLISHED' | 'ARCHIVED';
  feedPublisherName?: string;
  feedPublisherUrl?: string;
  feedLang?: string;
  defaultLang?: string;
  feedStartDate?: string;
  feedEndDate?: string;
  feedVersionString?: string;
  feedContactEmail?: string;
  feedContactUrl?: string;
}

export interface FeedInfoRequest {
  feedPublisherName: string;
  feedPublisherUrl: string;
  feedLang: string;
  defaultLang?: string;
  feedStartDate?: string;
  feedEndDate?: string;
  feedVersionString?: string;
  feedContactEmail?: string;
  feedContactUrl?: string;
}

export interface Agency {
  id: string;
  gtfsId?: string;
  agencyName: string;
  agencyUrl: string;
  agencyTimezone: string;
  agencyLang?: string;
  agencyPhone?: string;
  agencyFareUrl?: string;
  agencyEmail?: string;
}

export interface Stop {
  id: string;
  gtfsId?: string;
  stopName: string;
  stopCode?: string;
  stopDesc?: string;
  stopLat: number;
  stopLon: number;
  wheelchairBoarding?: number;
  locationType?: number;
}

export interface Route {
  id: string;
  gtfsId?: string;
  agency: { id: string };
  routeShortName?: string;
  routeLongName?: string;
  routeDesc?: string;
  routeType: number;
  routeUrl?: string;
  routeColor?: string;
  routeTextColor?: string;
  routeSortOrder?: number;
  networkId?: string;
}

export interface RoutePattern {
  id: string;
  shapeGtfsId?: string;
  name: string;
  directionId: number;
  tripHeadsign?: string;
}

export interface PatternStop {
  id: string;
  stopSequence: number;
  stop: Stop;
}

export interface StopImportJobStatus {
  jobId: string;
  status: 'RUNNING' | 'DONE' | 'FAILED';
  totalPoints: number;
  processedCount: number;
  geocodedCount: number;
  errorMessage: string | null;
  minLat: number | null;
  maxLat: number | null;
  minLon: number | null;
  maxLon: number | null;
}

export interface KmlMatchedStopSummary {
  id: string;
  name: string;
  distanceMeters: number;
}

export interface KmlPatternImportResult {
  shapePointCount: number;
  matchedStopCount: number;
  matchRadiusMeters: number;
  matchedStops: KmlMatchedStopSummary[];
  matchedToRoadNetwork: boolean;
}

export interface KmlRouteImportResult {
  routeId: string;
  routeName: string;
  patternId: string;
  pattern: KmlPatternImportResult;
}

export interface KmlBulkRoutesImportResult {
  routeCount: number;
  routes: KmlRouteImportResult[];
}

export interface ServiceCalendar {
  id: string;
  gtfsId?: string;
  name: string;
  monday: boolean;
  tuesday: boolean;
  wednesday: boolean;
  thursday: boolean;
  friday: boolean;
  saturday: boolean;
  sunday: boolean;
  startDate: string;
  endDate: string;
}

export interface ValidationNotice {
  severity: 'ERROR' | 'WARNING' | 'INFO';
  category: 'GTFS_SPEC' | 'GTFS_BEST_PRACTICE' | 'LOCAL_QUALITY_RULE';
  code: string;
  title: string;
  description?: string;
  entityType?: string;
  entityId?: string;
  lat?: number;
  lon?: number;
}

export interface ValidationSummary {
  validationRunId: string;
  errors: number;
  warnings: number;
  infos: number;
  publishable: boolean;
  notices: ValidationNotice[];
}

export interface FareProduct {
  id: string;
  gtfsId?: string;
  fareProductName: string;
  amount: number;
  currency: string;
  riderCategory?: RiderCategory | null;
  fareMedia?: FareMedia | null;
}

export interface RiderCategory {
  id: string;
  gtfsId?: string;
  riderCategoryName: string;
  isDefaultFareCategory?: number;
}

export interface FareMedia {
  id: string;
  gtfsId?: string;
  fareMediaName?: string;
  fareMediaType: number;
}

export interface FareLegRule {
  id: string;
  gtfsLegGroupId?: string;
  networkId?: string;
  fareProduct: FareProduct;
}

export interface FareTransferRule {
  id: string;
  fromLegGroupId?: string;
  toLegGroupId?: string;
  transferCount?: number;
  durationLimitSecs?: number;
  durationLimitType?: number;
  fareTransferType: number;
  fareProduct?: FareProduct | null;
}

export interface TripFrequencyWindow {
  startTime: string;
  endTime: string;
  headwaySecs: number;
}

export interface TripSummary {
  id: string;
  gtfsId?: string;
  tripHeadsign?: string;
  serviceCalendarName?: string;
  frequencyBased: boolean;
  stopCount: number;
  firstDeparture?: string;
  lastArrival?: string;
  frequencies: TripFrequencyWindow[];
}

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  institution?: string;
  jobTitle?: string;
  role: 'ADMIN' | 'EDITOR' | 'VIEWER';
}

export interface AuthResponse {
  token: string;
  user: AuthUser;
}

export interface AdminUser {
  id: string;
  email: string;
  displayName: string;
  institution?: string;
  jobTitle?: string;
  role: 'ADMIN' | 'EDITOR' | 'VIEWER';
  createdAt: string;
  feedCount: number;
}

export const api = {
  config: () => get<{ mapTileUrl: string; mapAttribution: string; routingProvider: string }>('/config'),

  auth: {
    register: (body: {
      email: string;
      password: string;
      displayName: string;
      institution: string;
      jobTitle: string;
    }) => post<AuthResponse>('/auth/register', body),
    login: (body: { email: string; password: string }) => post<AuthResponse>('/auth/login', body),
    me: () => get<AuthUser>('/auth/me'),
  },

  geocoding: {
    suggestStopName: (lat: number, lon: number) =>
      get<{ suggestedName?: string }>(`/geocoding/suggest-stop-name?lat=${lat}&lon=${lon}`),
  },

  routing: {
    route: (points: { lat: number; lon: number }[]) =>
      post<{ points: [number, number][]; routed: boolean; provider: string }>('/routing/route', { points }),
  },

  feeds: {
    list: () => get<Feed[]>('/feeds'),
    create: (body: { name: string; description?: string }) => post<Feed>('/feeds', body),
  },
  feedVersions: {
    list: (feedId: string) => get<FeedVersion[]>(`/feeds/${feedId}/versions`),
    create: (feedId: string) => post<FeedVersion>(`/feeds/${feedId}/versions`),
    get: (id: string) => get<FeedVersion>(`/feed-versions/${id}`),
    updateFeedInfo: (id: string, body: FeedInfoRequest) => put<FeedVersion>(`/feed-versions/${id}/feed-info`, body),
  },
  agencies: {
    list: (feedVersionId: string) => get<Agency[]>(`/feed-versions/${feedVersionId}/agencies`),
    create: (feedVersionId: string, body: Partial<Agency>) =>
      post<Agency>(`/feed-versions/${feedVersionId}/agencies`, body),
    update: (id: string, body: Partial<Agency>) => put<Agency>(`/agencies/${id}`, body),
    remove: (id: string) => del<void>(`/agencies/${id}`),
  },
  stops: {
    list: (feedVersionId: string) => get<Stop[]>(`/feed-versions/${feedVersionId}/stops`),
    create: (feedVersionId: string, body: Partial<Stop>) => post<Stop>(`/feed-versions/${feedVersionId}/stops`, body),
    update: (id: string, body: Partial<Stop>) => put<Stop>(`/stops/${id}`, body),
    remove: (id: string) => del<void>(`/stops/${id}`),
    near: (lat: number, lon: number, radiusMeters: number) =>
      get<Stop[]>(`/stops/near?lat=${lat}&lon=${lon}&radiusMeters=${radiusMeters}`),
    importKml: (feedVersionId: string, file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      return request<StopImportJobStatus>(`/feed-versions/${feedVersionId}/stops/import-kml`, {
        method: 'POST',
        body: formData as any,
      });
    },
    getKmlImportJob: (jobId: string) => get<StopImportJobStatus>(`/stop-import-jobs/${jobId}`),
  },
  routes: {
    list: (feedVersionId: string) => get<Route[]>(`/feed-versions/${feedVersionId}/routes`),
    create: (feedVersionId: string, body: Partial<Route>) =>
      post<Route>(`/feed-versions/${feedVersionId}/routes`, body),
    update: (id: string, body: Partial<Route>) => put<Route>(`/routes/${id}`, body),
    importKml: (feedVersionId: string, agencyId: string, file: File, matchRadiusMeters: number) => {
      const formData = new FormData();
      formData.append('file', file);
      return request<KmlBulkRoutesImportResult>(
        `/feed-versions/${feedVersionId}/routes/import-kml?agencyId=${agencyId}&matchRadiusMeters=${matchRadiusMeters}`,
        { method: 'POST', body: formData as any },
      );
    },
  },
  patterns: {
    list: (routeId: string) => get<RoutePattern[]>(`/routes/${routeId}/patterns`),
    create: (routeId: string, body: Partial<RoutePattern>) =>
      post<RoutePattern>(`/routes/${routeId}/patterns`, body),
    getShapePoints: (patternId: string) =>
      get<{ shapePtLat: number; shapePtLon: number; shapePtSequence: number }[]>(
        `/patterns/${patternId}/shape-points`,
      ),
    replaceShapePoints: (patternId: string, points: { lat: number; lon: number }[]) =>
      put(`/patterns/${patternId}/shape-points`, points),
    getStops: (patternId: string) => get<PatternStop[]>(`/patterns/${patternId}/stops`),
    replaceStops: (patternId: string, stopIds: string[]) =>
      put<PatternStop[]>(
        `/patterns/${patternId}/stops`,
        stopIds.map((stopId) => ({ stopId })),
      ),
    importKml: (patternId: string, file: File, matchRadiusMeters: number) => {
      const formData = new FormData();
      formData.append('file', file);
      return request<KmlPatternImportResult>(
        `/patterns/${patternId}/import-kml?matchRadiusMeters=${matchRadiusMeters}`,
        { method: 'POST', body: formData as any },
      );
    },
  },
  calendars: {
    list: (feedVersionId: string) => get<ServiceCalendar[]>(`/feed-versions/${feedVersionId}/calendars`),
    create: (feedVersionId: string, body: Partial<ServiceCalendar>) =>
      post<ServiceCalendar>(`/feed-versions/${feedVersionId}/calendars`, body),
  },
  schedule: {
    explicit: (patternId: string, body: unknown) => post(`/patterns/${patternId}/schedule/explicit`, body),
    frequency: (patternId: string, body: unknown) => post(`/patterns/${patternId}/schedule/frequency`, body),
  },
  trips: {
    list: (patternId: string) => get<TripSummary[]>(`/patterns/${patternId}/trips`),
    remove: (id: string) => del<void>(`/trips/${id}`),
  },
  fares: {
    riderCategories: {
      list: (fvId: string) => get<RiderCategory[]>(`/feed-versions/${fvId}/rider-categories`),
      create: (fvId: string, body: Partial<RiderCategory>) =>
        post<RiderCategory>(`/feed-versions/${fvId}/rider-categories`, body),
      update: (fvId: string, id: string, body: Partial<RiderCategory>) =>
        put<RiderCategory>(`/feed-versions/${fvId}/rider-categories/${id}`, body),
      remove: (fvId: string, id: string) => del<void>(`/feed-versions/${fvId}/rider-categories/${id}`),
    },
    media: {
      list: (fvId: string) => get<FareMedia[]>(`/feed-versions/${fvId}/fare-media`),
      create: (fvId: string, body: Partial<FareMedia>) => post<FareMedia>(`/feed-versions/${fvId}/fare-media`, body),
      update: (fvId: string, id: string, body: Partial<FareMedia>) =>
        put<FareMedia>(`/feed-versions/${fvId}/fare-media/${id}`, body),
      remove: (fvId: string, id: string) => del<void>(`/feed-versions/${fvId}/fare-media/${id}`),
    },
    products: {
      list: (fvId: string) => get<FareProduct[]>(`/feed-versions/${fvId}/fare-products`),
      create: (fvId: string, body: Partial<FareProduct>) =>
        post<FareProduct>(`/feed-versions/${fvId}/fare-products`, body),
      update: (fvId: string, id: string, body: Partial<FareProduct>) =>
        put<FareProduct>(`/feed-versions/${fvId}/fare-products/${id}`, body),
      remove: (fvId: string, id: string) => del<void>(`/feed-versions/${fvId}/fare-products/${id}`),
    },
    legRules: {
      list: (fvId: string) => get<FareLegRule[]>(`/feed-versions/${fvId}/fare-leg-rules`),
      create: (fvId: string, body: Partial<FareLegRule>) =>
        post<FareLegRule>(`/feed-versions/${fvId}/fare-leg-rules`, body),
      remove: (fvId: string, id: string) => del<void>(`/feed-versions/${fvId}/fare-leg-rules/${id}`),
    },
    transferRules: {
      list: (fvId: string) => get<FareTransferRule[]>(`/feed-versions/${fvId}/fare-transfer-rules`),
      create: (fvId: string, body: Partial<FareTransferRule>) =>
        post<FareTransferRule>(`/feed-versions/${fvId}/fare-transfer-rules`, body),
      remove: (fvId: string, id: string) => del<void>(`/feed-versions/${fvId}/fare-transfer-rules/${id}`),
    },
  },
  gtfs: {
    export: (feedVersionId: string) =>
      post<{ sha256: string; sizeBytes: number; generatedAt: string }>(`/feed-versions/${feedVersionId}/export`),
    // Ya no es un <a href> plano: la descarga ahora requiere el header
    // Authorization, así que se trae como blob y se dispara desde JS.
    download: async (feedVersionId: string) => {
      const res = await fetch(`${BASE_URL}/feed-versions/${feedVersionId}/export/download`, {
        headers: authToken ? { Authorization: `Bearer ${authToken}` } : {},
      });
      if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'gtfs.zip';
      a.click();
      URL.revokeObjectURL(url);
    },
    validate: (feedVersionId: string, official: boolean) =>
      post<ValidationSummary>(`/feed-versions/${feedVersionId}/validate?official=${official}`),
    publish: (feedVersionId: string) => post<FeedVersion>(`/feed-versions/${feedVersionId}/publish`),
  },
  imports: {
    upload: (feedId: string, file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      return request(`/feeds/${feedId}/import`, { method: 'POST', body: formData as any });
    },
  },
  admin: {
    users: {
      list: () => get<AdminUser[]>('/admin/users'),
      updateRole: (userId: string, role: 'ADMIN' | 'EDITOR' | 'VIEWER') =>
        put<AdminUser>(`/admin/users/${userId}/role`, { role }),
    },
  },
};
