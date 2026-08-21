import { create } from 'zustand';

export type MapTool = 'none' | 'add-stop' | 'draw-shape' | 'add-pattern-stop';

interface AppState {
  feedId: string | null;
  feedVersionId: string | null;
  agencyId: string | null;
  activeRouteId: string | null;
  activePatternId: string | null;
  activeCalendarId: string | null;
  mapTool: MapTool;
  draftShapePoints: { lat: number; lon: number }[];
  draftPatternStopIds: string[];
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
}

export const useAppStore = create<AppState>((set, get) => ({
  feedId: null,
  feedVersionId: null,
  agencyId: null,
  activeRouteId: null,
  activePatternId: null,
  activeCalendarId: null,
  mapTool: 'none',
  draftShapePoints: [],
  draftPatternStopIds: [],
  setFeed: (feedId, feedVersionId) => set({ feedId, feedVersionId }),
  setAgency: (agencyId) => set({ agencyId }),
  setActiveRoute: (routeId) => set({ activeRouteId: routeId, activePatternId: null }),
  setActivePattern: (patternId) =>
    set({ activePatternId: patternId, draftShapePoints: [], draftPatternStopIds: [] }),
  setActiveCalendar: (calendarId) => set({ activeCalendarId: calendarId }),
  setMapTool: (tool) => set({ mapTool: tool }),
  addDraftShapePoint: (pt) => set({ draftShapePoints: [...get().draftShapePoints, pt] }),
  clearDraftShapePoints: () => set({ draftShapePoints: [] }),
  toggleDraftPatternStop: (stopId) => {
    const current = get().draftPatternStopIds;
    set({
      draftPatternStopIds: current.includes(stopId)
        ? current.filter((id) => id !== stopId)
        : [...current, stopId],
    });
  },
  clearDraftPatternStops: () => set({ draftPatternStopIds: [] }),
}));
