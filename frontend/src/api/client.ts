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
}

export interface Agency {
  id: string;
  gtfsId?: string;
  agencyName: string;
  agencyUrl: string;
  agencyTimezone: string;
  agencyLang?: string;
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
  routeType: number;
  routeColor?: string;
  routeTextColor?: string;
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
  riderCategory?: { id: string };
  fareMedia?: { id: string };
}

export interface RiderCategory {
  id: string;
  gtfsId?: string;
  riderCategoryName: string;
  isDefaultFareCategory?: number;
}

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  role: 'ADMIN' | 'EDITOR' | 'VIEWER';
}

export interface AuthResponse {
  token: string;
  user: AuthUser;
}

export const api = {
  config: () => get<{ mapTileUrl: string; mapAttribution: string; routingProvider: string }>('/config'),

  auth: {
    register: (body: { email: string; password: string; displayName: string }) =>
      post<AuthResponse>('/auth/register', body),
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
  },
  agencies: {
    list: (feedVersionId: string) => get<Agency[]>(`/feed-versions/${feedVersionId}/agencies`),
    create: (feedVersionId: string, body: Partial<Agency>) =>
      post<Agency>(`/feed-versions/${feedVersionId}/agencies`, body),
  },
  stops: {
    list: (feedVersionId: string) => get<Stop[]>(`/feed-versions/${feedVersionId}/stops`),
    create: (feedVersionId: string, body: Partial<Stop>) => post<Stop>(`/feed-versions/${feedVersionId}/stops`, body),
    update: (id: string, body: Partial<Stop>) => put<Stop>(`/stops/${id}`, body),
    remove: (id: string) => del<void>(`/stops/${id}`),
    near: (lat: number, lon: number, radiusMeters: number) =>
      get<Stop[]>(`/stops/near?lat=${lat}&lon=${lon}&radiusMeters=${radiusMeters}`),
  },
  routes: {
    list: (feedVersionId: string) => get<Route[]>(`/feed-versions/${feedVersionId}/routes`),
    create: (feedVersionId: string, body: Partial<Route>) =>
      post<Route>(`/feed-versions/${feedVersionId}/routes`, body),
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
  fares: {
    riderCategories: {
      list: (fvId: string) => get<RiderCategory[]>(`/feed-versions/${fvId}/rider-categories`),
      create: (fvId: string, body: Partial<RiderCategory>) =>
        post<RiderCategory>(`/feed-versions/${fvId}/rider-categories`, body),
    },
    products: {
      list: (fvId: string) => get<FareProduct[]>(`/feed-versions/${fvId}/fare-products`),
      create: (fvId: string, body: Partial<FareProduct>) =>
        post<FareProduct>(`/feed-versions/${fvId}/fare-products`, body),
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
};
