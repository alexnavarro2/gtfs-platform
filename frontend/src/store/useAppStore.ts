import { create } from 'zustand';
import { setAuthToken, type AuthUser } from '../api/client';

export type MapTool = 'none' | 'add-stop' | 'draw-shape' | 'add-pattern-stop';

interface AppState {
  authUser: AuthUser | null;
  setAuth: (user: AuthUser, token: string) => void;
  clearAuth: () => void;
  feedId: string | null;
  feedVersionId: string | null;
  agencyId: string | null;
  activeRouteId: string | null;
  activePatternId: string | null;
  activeCalendarId: string | null;
  mapTool: MapTool;
  draftShapePoints: { lat: number; lon: number }[];
  draftPatternStopIds: string[];
  // Recorrido calculado por la red vial al ir uniendo las paradas seleccionadas
  // (sección 9, Modo 1/3 — igual que Conveyal construye el pattern con su motor
  // de ruteo). Nunca se guarda solo: el usuario confirma con "Guardar recorrido".
  routedPreviewPoints: { lat: number; lon: number }[];
  routedPreviewInfo: { routed: boolean; provider: string } | null;
  setFeed: (feedId: string, feedVersionId: string) => void;
  setAgency: (agencyId: string) => void;
  setActiveRoute: (routeId: string | null) => void;
  setActivePattern: (patternId: string | null) => void;
  setActiveCalendar: (calendarId: string | null) => void;
  setMapTool: (tool: MapTool) => void;
  addDraftShapePoint: (pt: { lat: number; lon: number }) => void;
  clearDraftShapePoints: () => void;
  toggleDraftPatternStop: (stopId: string) => void;
  clearDraftPatternStops: () => void;
  setRoutedPreview: (points: { lat: number; lon: number }[], info: { routed: boolean; provider: string } | null) => void;
}

export const useAppStore = create<AppState>((set, get) => ({
  authUser: null,
  setAuth: (user, token) => {
    setAuthToken(token);
    set({ authUser: user });
  },
  clearAuth: () => {
    setAuthToken(null);
    set({
      authUser: null,
      feedId: null,
      feedVersionId: null,
      agencyId: null,
      activeRouteId: null,
      activePatternId: null,
      activeCalendarId: null,
      mapTool: 'none',
      draftShapePoints: [],
      draftPatternStopIds: [],
      routedPreviewPoints: [],
      routedPreviewInfo: null,
    });
  },
  feedId: null,
  feedVersionId: null,
  agencyId: null,
  activeRouteId: null,
  activePatternId: null,
  activeCalendarId: null,
  mapTool: 'none',
  draftShapePoints: [],
  draftPatternStopIds: [],
  routedPreviewPoints: [],
  routedPreviewInfo: null,
  setFeed: (feedId, feedVersionId) =>
    set({
      feedId,
      feedVersionId,
      // Al cambiar de feed, todo lo que dependía del feed anterior (agencia,
      // ruta/patrón activo, borradores) queda inválido — sin esto, un feedId
      // o agencyId de otro feed se arrastraba y rompía las consultas.
      agencyId: null,
      activeRouteId: null,
      activePatternId: null,
      activeCalendarId: null,
      mapTool: 'none',
      draftShapePoints: [],
      draftPatternStopIds: [],
      routedPreviewPoints: [],
      routedPreviewInfo: null,
    }),
  setAgency: (agencyId) => set({ agencyId }),
  setActiveRoute: (routeId) => set({ activeRouteId: routeId, activePatternId: null }),
  setActivePattern: (patternId) =>
    set({
      activePatternId: patternId,
      draftShapePoints: [],
      draftPatternStopIds: [],
      routedPreviewPoints: [],
      routedPreviewInfo: null,
    }),
  setActiveCalendar: (calendarId) => set({ activeCalendarId: calendarId }),
  setMapTool: (tool) => set({ mapTool: tool }),
  addDraftShapePoint: (pt) => set({ draftShapePoints: [...get().draftShapePoints, pt] }),
  clearDraftShapePoints: () => set({ draftShapePoints: [], routedPreviewPoints: [], routedPreviewInfo: null }),
  toggleDraftPatternStop: (stopId) => {
    const current = get().draftPatternStopIds;
    set({
      draftPatternStopIds: current.includes(stopId)
        ? current.filter((id) => id !== stopId)
        : [...current, stopId],
    });
  },
  clearDraftPatternStops: () => set({ draftPatternStopIds: [], routedPreviewPoints: [], routedPreviewInfo: null }),
  setRoutedPreview: (points, info) => set({ routedPreviewPoints: points, routedPreviewInfo: info }),
}));
