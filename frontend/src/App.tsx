import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api/client';
import type { Route, Stop, ValidationSummary } from './api/client';
import { useAppStore } from './store/useAppStore';
import { MapView } from './map/MapView';

type Tab = 'stops' | 'routes' | 'calendars' | 'fares' | 'validation';

export default function App() {
  const feedVersionId = useAppStore((s) => s.feedVersionId);
  if (!feedVersionId) return <Bootstrap />;
  return <Shell />;
}

// ---------------------------------------------------------------------------
// Bootstrap: crear feed + version + agencia inicial (secciones 1-2 del prompt)
// ---------------------------------------------------------------------------
function Bootstrap() {
  const setFeed = useAppStore((s) => s.setFeed);
  const setAgency = useAppStore((s) => s.setAgency);
  const feedsQuery = useQuery({ queryKey: ['feeds'], queryFn: api.feeds.list });
  const [name, setName] = useState('IMTES Demo');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function tryAutoSelect() {
      if (!feedsQuery.data || feedsQuery.data.length === 0) return;
      const feed = feedsQuery.data[0];
      const versions = await api.feedVersions.list(feed.id);
      if (versions.length === 0) return;
      const latest = versions.reduce((a, b) => (a.versionNumber > b.versionNumber ? a : b));
      const agencies = await api.agencies.list(latest.id);
      setFeed(feed.id, latest.id);
      if (agencies.length > 0) setAgency(agencies[0].id);
    }
    tryAutoSelect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [feedsQuery.data]);

  async function createFeed() {
    setBusy(true);
    setError(null);
    try {
      const feed = await api.feeds.create({ name });
      const version = await api.feedVersions.create(feed.id);
      setFeed(feed.id, version.id);
    } catch (e: any) {
      setError(e.message || 'Error creando el feed');
    } finally {
      setBusy(false);
    }
  }

  if (feedsQuery.isLoading) {
    return (
      <div className="bootstrap-screen">
        <div className="bootstrap-card">Cargando…</div>
      </div>
    );
  }

  return (
    <div className="bootstrap-screen">
      <div className="bootstrap-card">
        <h2>GTFS Platform</h2>
        <p className="hint">
          Crea tu primer feed para empezar. Podrás definir la agencia, paradas, rutas y horarios desde el mapa.
        </p>
        <div className="field">
          <label>Nombre del feed</label>
          <input value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        {error && <div className="notice ERROR">{error}</div>}
        <button className="btn block" disabled={busy || !name.trim()} onClick={createFeed}>
          {busy ? 'Creando…' : 'Crear feed'}
        </button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Shell principal: topbar + sidebar de herramientas + mapa (sección 5)
// ---------------------------------------------------------------------------
function Shell() {
  const feedVersionId = useAppStore((s) => s.feedVersionId)!;
  const agencyId = useAppStore((s) => s.agencyId);
  const setAgency = useAppStore((s) => s.setAgency);
  const [tab, setTab] = useState<Tab>(agencyId ? 'stops' : 'routes');

  const configQuery = useQuery({ queryKey: ['config'], queryFn: api.config });
  const feedVersionQuery = useQuery({
    queryKey: ['feedVersion', feedVersionId],
    queryFn: () => api.feedVersions.get(feedVersionId),
  });
  const agenciesQuery = useQuery({
    queryKey: ['agencies', feedVersionId],
    queryFn: () => api.agencies.list(feedVersionId),
  });

  useEffect(() => {
    if (!agencyId && agenciesQuery.data && agenciesQuery.data.length > 0) {
      setAgency(agenciesQuery.data[0].id);
    }
  }, [agenciesQuery.data, agencyId, setAgency]);

  if (!agenciesQuery.isLoading && (!agenciesQuery.data || agenciesQuery.data.length === 0)) {
    return <AgencySetup feedVersionId={feedVersionId} />;
  }

  return (
    <div className="app-shell">
      <Topbar feedVersion={feedVersionQuery.data} />
      <div className="main-area">
        <div className="sidebar">
          <div className="tabs">
            <TabButton current={tab} value="stops" onClick={setTab} label="Paradas" />
            <TabButton current={tab} value="routes" onClick={setTab} label="Rutas" />
            <TabButton current={tab} value="calendars" onClick={setTab} label="Calendarios" />
            <TabButton current={tab} value="fares" onClick={setTab} label="Tarifas" />
            <TabButton current={tab} value="validation" onClick={setTab} label="Validación" />
          </div>
          <div className="tab-content">
            {tab === 'stops' && <StopsPanel feedVersionId={feedVersionId} />}
            {tab === 'routes' && <RoutesPanel feedVersionId={feedVersionId} agencyId={agencyId!} />}
            {tab === 'calendars' && <CalendarsPanel feedVersionId={feedVersionId} />}
            {tab === 'fares' && <FaresPanel feedVersionId={feedVersionId} />}
            {tab === 'validation' && <ValidationPanel feedVersionId={feedVersionId} />}
          </div>
        </div>
        <div className="map-area">
          <MapArea feedVersionId={feedVersionId} tileUrl={configQuery.data?.mapTileUrl} attribution={configQuery.data?.mapAttribution} />
        </div>
      </div>
    </div>
  );
}

function AgencySetup({ feedVersionId }: { feedVersionId: string }) {
  const setAgency = useAppStore((s) => s.setAgency);
  const [form, setForm] = useState({
    agencyName: 'IMTES Demo',
    agencyUrl: 'https://example.org',
    agencyTimezone: 'America/Hermosillo',
    agencyLang: 'es',
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function create() {
    setBusy(true);
    setError(null);
    try {
      const agency = await api.agencies.create(feedVersionId, form);
      setAgency(agency.id);
    } catch (e: any) {
      setError(e.message || 'Error creando la agencia');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="bootstrap-screen">
      <div className="bootstrap-card">
        <h2>Información de la agencia</h2>
        <p className="hint">Toda ruta necesita una agencia (agency.txt). Complétala una sola vez por feed.</p>
        <div className="field">
          <label>Nombre</label>
          <input value={form.agencyName} onChange={(e) => setForm({ ...form, agencyName: e.target.value })} />
        </div>
        <div className="field">
          <label>Sitio web</label>
          <input value={form.agencyUrl} onChange={(e) => setForm({ ...form, agencyUrl: e.target.value })} />
        </div>
        <div className="field">
          <label>Zona horaria</label>
          <input value={form.agencyTimezone} onChange={(e) => setForm({ ...form, agencyTimezone: e.target.value })} />
        </div>
        {error && <div className="notice ERROR">{error}</div>}
        <button className="btn block" disabled={busy} onClick={create}>
          {busy ? 'Creando…' : 'Crear agencia y continuar'}
        </button>
      </div>
    </div>
  );
}

function TabButton({
  current,
  value,
  onClick,
  label,
}: {
  current: Tab;
  value: Tab;
  onClick: (t: Tab) => void;
  label: string;
}) {
  return (
    <div className={`tab ${current === value ? 'active' : ''}`} onClick={() => onClick(value)}>
      {label}
    </div>
  );
}

function Topbar({ feedVersion }: { feedVersion?: { feed: { name: string }; versionNumber: number; status: string } }) {
  return (
    <div className="topbar">
      <img className="brand-logo" src="/imtes-logo.png" alt="IMTES - Instituto de Movilidad y Transporte para el Estado de Sonora" />
      <div className="brand">GTFS Platform</div>
      {feedVersion && (
        <div className="brand-sub">
          {feedVersion.feed.name} · v{feedVersion.versionNumber}
        </div>
      )}
      <div className="spacer" />
      {feedVersion && <span className="status-pill">{feedVersion.status}</span>}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Mapa: enrutado a store según el tab/tool activo
// ---------------------------------------------------------------------------
function MapArea({
  feedVersionId,
  tileUrl,
  attribution,
}: {
  feedVersionId: string;
  tileUrl?: string;
  attribution?: string;
}) {
  const queryClient = useQueryClient();
  const mapTool = useAppStore((s) => s.mapTool);
  const setMapTool = useAppStore((s) => s.setMapTool);
  const activePatternId = useAppStore((s) => s.activePatternId);
  const addDraftShapePoint = useAppStore((s) => s.addDraftShapePoint);
  const toggleDraftPatternStop = useAppStore((s) => s.toggleDraftPatternStop);
  const [pendingStopLatLon, setPendingStopLatLon] = useState<{ lat: number; lon: number } | null>(null);

  const stopsQuery = useQuery({ queryKey: ['stops', feedVersionId], queryFn: () => api.stops.list(feedVersionId) });
  const savedShapeQuery = useQuery({
    queryKey: ['shapePoints', activePatternId],
    queryFn: () => api.patterns.getShapePoints(activePatternId!),
    enabled: !!activePatternId,
  });
  const patternStopsQuery = useQuery({
    queryKey: ['patternStops', activePatternId],
    queryFn: () => api.patterns.getStops(activePatternId!),
    enabled: !!activePatternId,
  });

  function handleMapClick(lat: number, lon: number) {
    if (mapTool === 'add-stop') {
      setPendingStopLatLon({ lat, lon });
    } else if (mapTool === 'draw-shape') {
      addDraftShapePoint({ lat, lon });
    }
  }

  function handleStopClick(stopId: string) {
    if (mapTool === 'add-pattern-stop') {
      toggleDraftPatternStop(stopId);
    }
  }

  return (
    <>
      <MapView
        tileUrl={tileUrl || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'}
        attribution={attribution || '© OpenStreetMap contributors'}
        stops={stopsQuery.data || []}
        patternStops={patternStopsQuery.data || []}
        savedShapePoints={(savedShapeQuery.data || []).map((p) => ({ lat: p.shapePtLat, lon: p.shapePtLon }))}
        onMapClick={handleMapClick}
        onStopClick={handleStopClick}
      />
      <div className="attribution-badge">{attribution || '© OpenStreetMap contributors'}</div>
      {pendingStopLatLon && (
        <StopQuickForm
          feedVersionId={feedVersionId}
          lat={pendingStopLatLon.lat}
          lon={pendingStopLatLon.lon}
          onClose={() => setPendingStopLatLon(null)}
          onSaved={() => {
            setPendingStopLatLon(null);
            setMapTool('none');
            queryClient.invalidateQueries({ queryKey: ['stops', feedVersionId] });
          }}
        />
      )}
    </>
  );
}

function StopQuickForm({
  feedVersionId,
  lat,
  lon,
  onClose,
  onSaved,
}: {
  feedVersionId: string;
  lat: number;
  lon: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState('');
  const [nameTouched, setNameTouched] = useState(false);
  const [suggesting, setSuggesting] = useState(true);
  const [wheelchair, setWheelchair] = useState(false);
  const [busy, setBusy] = useState(false);
  const [nearby, setNearby] = useState<Stop[]>([]);

  useEffect(() => {
    api.stops.near(lat, lon, 60).then(setNearby).catch(() => {});
    setSuggesting(true);
    api.geocoding
      .suggestStopName(lat, lon)
      .then((res) => {
        // Sección 6: sugerencia por intersección más cercana (estilo Conveyal),
        // siempre editable — solo se aplica si el usuario no escribió nada todavía.
        if (res.suggestedName && !nameTouched) setName(res.suggestedName);
      })
      .catch(() => {})
      .finally(() => setSuggesting(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lat, lon]);

  async function save() {
    setBusy(true);
    try {
      await api.stops.create(feedVersionId, {
        stopName: name,
        stopLat: lat,
        stopLon: lon,
        locationType: 0,
        wheelchairBoarding: wheelchair ? 1 : 0,
      });
      onSaved();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ position: 'absolute', top: 16, left: 16, width: 300, background: 'white', borderRadius: 8, padding: 14, boxShadow: '0 4px 16px rgba(0,0,0,0.15)', zIndex: 10 }}>
      <h3 style={{ marginTop: 0 }}>Nueva parada</h3>
      {nearby.length > 0 && (
        <div className="notice WARNING">⚠ Hay {nearby.length} parada(s) a menos de 60 m. Verifica que no sea un duplicado.</div>
      )}
      <div className="field">
        <label>Nombre {suggesting && <span style={{ color: '#999' }}>· buscando intersección más cercana…</span>}</label>
        <input
          autoFocus
          value={name}
          onChange={(e) => {
            setNameTouched(true);
            setName(e.target.value);
          }}
          placeholder="Ej. Hospital General"
        />
      </div>
      <div className="field">
        <label>Lat / Lon</label>
        <input disabled value={`${lat.toFixed(6)}, ${lon.toFixed(6)}`} />
      </div>
      <div className="field">
        <label>
          <input type="checkbox" checked={wheelchair} onChange={(e) => setWheelchair(e.target.checked)} /> Accesible en silla de ruedas
        </label>
      </div>
      <div className="btn-row">
        <button className="btn secondary" onClick={onClose}>Cancelar</button>
        <button className="btn" disabled={busy || !name.trim()} onClick={save}>{busy ? 'Guardando…' : 'Guardar parada'}</button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Paradas
// ---------------------------------------------------------------------------
function StopsPanel({ feedVersionId }: { feedVersionId: string }) {
  const mapTool = useAppStore((s) => s.mapTool);
  const setMapTool = useAppStore((s) => s.setMapTool);
  const stopsQuery = useQuery({ queryKey: ['stops', feedVersionId], queryFn: () => api.stops.list(feedVersionId) });

  return (
    <div>
      <div className="panel-section">
        <h3>Herramienta</h3>
        <div className="tool-toggle">
          <button className={mapTool === 'add-stop' ? 'active' : ''} onClick={() => setMapTool(mapTool === 'add-stop' ? 'none' : 'add-stop')}>
            + Agregar parada
          </button>
        </div>
        <p className="hint">Activa la herramienta y haz clic en el mapa para crear una parada.</p>
      </div>
      <div className="panel-section">
        <h3>Paradas ({stopsQuery.data?.length || 0})</h3>
        {(stopsQuery.data || []).map((s) => (
          <div className="list-item" key={s.id}>
            <span>{s.stopName || '(sin nombre)'} <span style={{ color: '#999' }}>· {s.gtfsId}</span></span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Rutas + Patrones (sección 7-12)
// ---------------------------------------------------------------------------
function RoutesPanel({ feedVersionId, agencyId }: { feedVersionId: string; agencyId: string }) {
  const activeRouteId = useAppStore((s) => s.activeRouteId);
  const setActiveRoute = useAppStore((s) => s.setActiveRoute);
  const queryClient = useQueryClient();
  const routesQuery = useQuery({ queryKey: ['routes', feedVersionId], queryFn: () => api.routes.list(feedVersionId) });

  const [form, setForm] = useState({ routeShortName: '', routeLongName: '', routeColor: '1E88E5', routeTextColor: 'FFFFFF' });
  const createRoute = useMutation({
    mutationFn: () =>
      api.routes.create(feedVersionId, { agency: { id: agencyId }, routeType: 3, ...form }),
    onSuccess: (route) => {
      queryClient.invalidateQueries({ queryKey: ['routes', feedVersionId] });
      setActiveRoute(route.id);
      setForm({ routeShortName: '', routeLongName: '', routeColor: '1E88E5', routeTextColor: 'FFFFFF' });
    },
  });

  const activeRoute = routesQuery.data?.find((r) => r.id === activeRouteId);

  return (
    <div>
      <div className="panel-section">
        <h3>Nueva ruta</h3>
        <div className="field-row">
          <div className="field">
            <label>Clave corta</label>
            <input value={form.routeShortName} onChange={(e) => setForm({ ...form, routeShortName: e.target.value })} placeholder="18" />
          </div>
          <div className="field">
            <label>Color</label>
            <input value={form.routeColor} onChange={(e) => setForm({ ...form, routeColor: e.target.value })} placeholder="1E88E5" />
          </div>
        </div>
        <div className="field">
          <label>Nombre largo</label>
          <input value={form.routeLongName} onChange={(e) => setForm({ ...form, routeLongName: e.target.value })} placeholder="Hospitales - Universidades" />
        </div>
        <button className="btn block" disabled={createRoute.isPending || !form.routeShortName} onClick={() => createRoute.mutate()}>
          {createRoute.isPending ? 'Creando…' : 'Crear ruta'}
        </button>
      </div>

      <div className="panel-section">
        <h3>Rutas ({routesQuery.data?.length || 0})</h3>
        {(routesQuery.data || []).map((r) => (
          <div key={r.id} className={`list-item ${activeRouteId === r.id ? 'active' : ''}`} onClick={() => setActiveRoute(r.id)}>
            <span className="route-chip" style={{ background: `#${r.routeColor || 'ccc'}`, color: `#${r.routeTextColor || '000'}` }}>
              {r.routeShortName}
            </span>
            <span>{r.routeLongName}</span>
          </div>
        ))}
      </div>

      {activeRoute && <PatternsPanel route={activeRoute} />}
    </div>
  );
}

function PatternsPanel({ route }: { route: Route }) {
  const activePatternId = useAppStore((s) => s.activePatternId);
  const setActivePattern = useAppStore((s) => s.setActivePattern);
  const queryClient = useQueryClient();
  const patternsQuery = useQuery({ queryKey: ['patterns', route.id], queryFn: () => api.patterns.list(route.id) });
  const [form, setForm] = useState({ name: 'IDA', directionId: 0, tripHeadsign: '' });

  const createPattern = useMutation({
    mutationFn: () => api.patterns.create(route.id, form),
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: ['patterns', route.id] });
      setActivePattern(p.id);
    },
  });

  return (
    <div className="panel-section">
      <h3>Sentidos de {route.routeShortName}</h3>
      <div className="field-row">
        <div className="field">
          <label>Nombre</label>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </div>
        <div className="field">
          <label>Sentido</label>
          <select value={form.directionId} onChange={(e) => setForm({ ...form, directionId: Number(e.target.value) })}>
            <option value={0}>0 - IDA</option>
            <option value={1}>1 - REGRESO</option>
          </select>
        </div>
      </div>
      <div className="field">
        <label>Headsign (destino mostrado al usuario)</label>
        <input value={form.tripHeadsign} onChange={(e) => setForm({ ...form, tripHeadsign: e.target.value })} placeholder="Universidad" />
      </div>
      <button className="btn block" disabled={createPattern.isPending} onClick={() => createPattern.mutate()}>
        {createPattern.isPending ? 'Creando…' : '+ Nuevo sentido'}
      </button>

      {(patternsQuery.data || []).map((p) => (
        <div key={p.id} className={`list-item ${activePatternId === p.id ? 'active' : ''}`} onClick={() => setActivePattern(p.id)}>
          <span>{p.name} → {p.tripHeadsign || '(sin headsign)'}</span>
        </div>
      ))}

      {activePatternId && <PatternEditor patternId={activePatternId} />}
    </div>
  );
}

function PatternEditor({ patternId }: { patternId: string }) {
  const queryClient = useQueryClient();
  const mapTool = useAppStore((s) => s.mapTool);
  const setMapTool = useAppStore((s) => s.setMapTool);
  const draftShapePoints = useAppStore((s) => s.draftShapePoints);
  const clearDraftShapePoints = useAppStore((s) => s.clearDraftShapePoints);
  const draftPatternStopIds = useAppStore((s) => s.draftPatternStopIds);
  const clearDraftPatternStops = useAppStore((s) => s.clearDraftPatternStops);

  const patternStopsQuery = useQuery({ queryKey: ['patternStops', patternId], queryFn: () => api.patterns.getStops(patternId) });

  const saveShape = useMutation({
    mutationFn: () => api.patterns.replaceShapePoints(patternId, draftShapePoints),
    onSuccess: () => {
      clearDraftShapePoints();
      setMapTool('none');
      queryClient.invalidateQueries({ queryKey: ['shapePoints', patternId] });
    },
  });

  const saveStops = useMutation({
    mutationFn: () => api.patterns.replaceStops(patternId, draftPatternStopIds),
    onSuccess: () => {
      clearDraftPatternStops();
      setMapTool('none');
      queryClient.invalidateQueries({ queryKey: ['patternStops', patternId] });
    },
  });

  return (
    <div style={{ marginTop: 10, borderTop: '1px dashed var(--border)', paddingTop: 10 }}>
      <h3>Recorrido</h3>
      <div className="tool-toggle">
        <button
          className={mapTool === 'draw-shape' ? 'active' : ''}
          onClick={() => setMapTool(mapTool === 'draw-shape' ? 'none' : 'draw-shape')}
        >
          ✏️ Dibujar
        </button>
        <button
          className={mapTool === 'add-pattern-stop' ? 'active' : ''}
          onClick={() => setMapTool(mapTool === 'add-pattern-stop' ? 'none' : 'add-pattern-stop')}
        >
          📍 Agregar paradas
        </button>
      </div>

      {mapTool === 'draw-shape' && (
        <div>
          <p className="hint">Haz clic en el mapa para agregar vértices del recorrido ({draftShapePoints.length} puntos).</p>
          <div className="btn-row">
            <button className="btn secondary" onClick={clearDraftShapePoints}>Limpiar</button>
            <button className="btn" disabled={draftShapePoints.length < 2 || saveShape.isPending} onClick={() => saveShape.mutate()}>
              {saveShape.isPending ? 'Guardando…' : 'Guardar recorrido'}
            </button>
          </div>
        </div>
      )}

      {mapTool === 'add-pattern-stop' && (
        <div>
          <p className="hint">Haz clic en las paradas del mapa, en el orden en que las visita el recorrido ({draftPatternStopIds.length} seleccionadas).</p>
          <div className="btn-row">
            <button className="btn secondary" onClick={clearDraftPatternStops}>Limpiar</button>
            <button className="btn" disabled={draftPatternStopIds.length < 2 || saveStops.isPending} onClick={() => saveStops.mutate()}>
              {saveStops.isPending ? 'Guardando…' : 'Guardar orden'}
            </button>
          </div>
        </div>
      )}

      <div className="panel-section" style={{ marginTop: 10 }}>
        <h3>Paradas del recorrido ({patternStopsQuery.data?.length || 0})</h3>
        {(patternStopsQuery.data || []).map((ps, i) => (
          <div key={ps.id} className="list-item">
            <span>{String(i + 1).padStart(2, '0')} — {ps.stop.stopName}</span>
          </div>
        ))}
      </div>

      <ScheduleEditor patternId={patternId} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Horarios / Frecuencias (sección 15-16)
// ---------------------------------------------------------------------------
function ScheduleEditor({ patternId }: { patternId: string }) {
  const feedVersionId = useAppStore((s) => s.feedVersionId)!;
  const calendarsQuery = useQuery({ queryKey: ['calendars', feedVersionId], queryFn: () => api.calendars.list(feedVersionId) });
  const [mode, setMode] = useState<'frequency' | 'explicit'>('frequency');
  const [serviceCalendarId, setServiceCalendarId] = useState('');
  const [speedKmh, setSpeedKmh] = useState(20);
  const [startTime, setStartTime] = useState('05:00:00');
  const [endTime, setEndTime] = useState('23:00:00');
  const [headwayMin, setHeadwayMin] = useState(15);
  const [departureTimes, setDepartureTimes] = useState('05:00:00, 05:20:00, 05:40:00');
  const [tripHeadsign, setTripHeadsign] = useState('');
  const [result, setResult] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!serviceCalendarId && calendarsQuery.data && calendarsQuery.data.length > 0) {
      setServiceCalendarId(calendarsQuery.data[0].id);
    }
  }, [calendarsQuery.data, serviceCalendarId]);

  async function generate() {
    if (!serviceCalendarId) return;
    setBusy(true);
    setResult(null);
    try {
      if (mode === 'frequency') {
        await api.schedule.frequency(patternId, {
          serviceCalendarId,
          method: 'AVERAGE_SPEED',
          speedKmh,
          windows: [{ startTime, endTime, headwaySeconds: headwayMin * 60, exactTimes: 0 }],
          tripHeadsign,
        });
        setResult('Trip de frecuencia generado correctamente.');
      } else {
        const times = departureTimes.split(',').map((t) => t.trim()).filter(Boolean);
        const res: any = await api.schedule.explicit(patternId, {
          serviceCalendarId,
          method: 'AVERAGE_SPEED',
          speedKmh,
          departureTimes: times,
          tripHeadsign,
        });
        setResult(`${Array.isArray(res) ? res.length : 0} trips generados.`);
      }
    } catch (e: any) {
      setResult('Error: ' + e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel-section" style={{ marginTop: 10 }}>
      <h3>Horario</h3>
      {(calendarsQuery.data || []).length === 0 && (
        <div className="notice WARNING">Crea primero un calendario en la pestaña "Calendarios".</div>
      )}
      <div className="field">
        <label>Servicio</label>
        <select value={serviceCalendarId} onChange={(e) => setServiceCalendarId(e.target.value)}>
          {(calendarsQuery.data || []).map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
      </div>
      <div className="tool-toggle">
        <button className={mode === 'frequency' ? 'active' : ''} onClick={() => setMode('frequency')}>Frecuencia</button>
        <button className={mode === 'explicit' ? 'active' : ''} onClick={() => setMode('explicit')}>Horario explícito</button>
      </div>
      <div className="field">
        <label>Velocidad promedio (km/h)</label>
        <input type="number" value={speedKmh} onChange={(e) => setSpeedKmh(Number(e.target.value))} />
      </div>
      <div className="field">
        <label>Headsign del trip</label>
        <input value={tripHeadsign} onChange={(e) => setTripHeadsign(e.target.value)} placeholder="Universidad" />
      </div>
      {mode === 'frequency' ? (
        <div className="field-row">
          <div className="field"><label>Desde</label><input value={startTime} onChange={(e) => setStartTime(e.target.value)} /></div>
          <div className="field"><label>Hasta</label><input value={endTime} onChange={(e) => setEndTime(e.target.value)} /></div>
          <div className="field"><label>Cada (min)</label><input type="number" value={headwayMin} onChange={(e) => setHeadwayMin(Number(e.target.value))} /></div>
        </div>
      ) : (
        <div className="field">
          <label>Horas de salida (separadas por coma)</label>
          <textarea rows={2} value={departureTimes} onChange={(e) => setDepartureTimes(e.target.value)} />
        </div>
      )}
      <button className="btn block" disabled={busy || !serviceCalendarId} onClick={generate}>
        {busy ? 'Generando…' : 'Generar trips y stop_times'}
      </button>
      {result && <div className="notice INFO">{result}</div>}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Calendarios (sección 13-14)
// ---------------------------------------------------------------------------
function CalendarsPanel({ feedVersionId }: { feedVersionId: string }) {
  const queryClient = useQueryClient();
  const calendarsQuery = useQuery({ queryKey: ['calendars', feedVersionId], queryFn: () => api.calendars.list(feedVersionId) });
  const [form, setForm] = useState({
    name: 'Lunes a Viernes',
    monday: true, tuesday: true, wednesday: true, thursday: true, friday: true, saturday: false, sunday: false,
    startDate: '2027-01-01', endDate: '2027-12-31',
  });

  const create = useMutation({
    mutationFn: () => api.calendars.create(feedVersionId, form),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['calendars', feedVersionId] }),
  });

  const days: { key: keyof typeof form; label: string }[] = [
    { key: 'monday', label: 'L' }, { key: 'tuesday', label: 'M' }, { key: 'wednesday', label: 'X' },
    { key: 'thursday', label: 'J' }, { key: 'friday', label: 'V' }, { key: 'saturday', label: 'S' }, { key: 'sunday', label: 'D' },
  ];

  return (
    <div>
      <div className="panel-section">
        <h3>Nuevo calendario</h3>
        <div className="field">
          <label>Nombre</label>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </div>
        <div className="field">
          <label>Días de operación</label>
          <div style={{ display: 'flex', gap: 6 }}>
            {days.map((d) => (
              <label key={d.key as string} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', fontSize: 11 }}>
                {d.label}
                <input
                  type="checkbox"
                  checked={form[d.key] as boolean}
                  onChange={(e) => setForm({ ...form, [d.key]: e.target.checked })}
                />
              </label>
            ))}
          </div>
        </div>
        <div className="field-row">
          <div className="field"><label>Inicio</label><input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} /></div>
          <div className="field"><label>Fin</label><input type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} /></div>
        </div>
        <button className="btn block" disabled={create.isPending} onClick={() => create.mutate()}>
          {create.isPending ? 'Creando…' : 'Crear calendario'}
        </button>
      </div>
      <div className="panel-section">
        <h3>Calendarios ({calendarsQuery.data?.length || 0})</h3>
        {(calendarsQuery.data || []).map((c) => (
          <div key={c.id} className="list-item"><span>{c.name} · {c.startDate} → {c.endDate}</span></div>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Tarifas simples (sección 21)
// ---------------------------------------------------------------------------
function FaresPanel({ feedVersionId }: { feedVersionId: string }) {
  const queryClient = useQueryClient();
  const productsQuery = useQuery({ queryKey: ['fareProducts', feedVersionId], queryFn: () => api.fares.products.list(feedVersionId) });
  const [form, setForm] = useState({ fareProductName: 'Tarifa General', amount: 9, currency: 'MXN' });

  const create = useMutation({
    mutationFn: () => api.fares.products.create(feedVersionId, form),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['fareProducts', feedVersionId] }),
  });

  return (
    <div>
      <div className="panel-section">
        <h3>Nueva tarifa</h3>
        <div className="field">
          <label>Nombre</label>
          <input value={form.fareProductName} onChange={(e) => setForm({ ...form, fareProductName: e.target.value })} />
        </div>
        <div className="field-row">
          <div className="field"><label>Monto</label><input type="number" step="0.5" value={form.amount} onChange={(e) => setForm({ ...form, amount: Number(e.target.value) })} /></div>
          <div className="field"><label>Moneda</label><input value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })} /></div>
        </div>
        <button className="btn block" disabled={create.isPending} onClick={() => create.mutate()}>
          {create.isPending ? 'Creando…' : 'Crear tarifa'}
        </button>
      </div>
      <div className="panel-section">
        <h3>Tarifas ({productsQuery.data?.length || 0})</h3>
        {(productsQuery.data || []).map((p) => (
          <div key={p.id} className="list-item"><span>{p.fareProductName} — ${p.amount} {p.currency}</span></div>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Validación + exportación (sección 24-27)
// ---------------------------------------------------------------------------
function ValidationPanel({ feedVersionId }: { feedVersionId: string }) {
  const [summary, setSummary] = useState<ValidationSummary | null>(null);
  const [busy, setBusy] = useState<'export' | 'validate' | null>(null);
  const [exportInfo, setExportInfo] = useState<{ sha256: string; sizeBytes: number } | null>(null);

  async function runExport() {
    setBusy('export');
    try {
      const res = await api.gtfs.export(feedVersionId);
      setExportInfo(res);
    } finally {
      setBusy(null);
    }
  }

  async function runValidate(official: boolean) {
    setBusy('validate');
    try {
      const res = await api.gtfs.validate(feedVersionId, official);
      setSummary(res);
    } finally {
      setBusy(null);
    }
  }

  return (
    <div>
      <div className="panel-section">
        <h3>Generar GTFS</h3>
        <button className="btn block" disabled={busy !== null} onClick={runExport}>
          {busy === 'export' ? 'Generando…' : 'Generar GTFS'}
        </button>
        {exportInfo && (
          <div className="notice INFO">
            gtfs.zip generado ({exportInfo.sizeBytes} bytes). SHA-256: {exportInfo.sha256.slice(0, 12)}…
          </div>
        )}
        <a href={api.gtfs.downloadUrl(feedVersionId)} target="_blank" rel="noreferrer">
          <button className="btn secondary block" style={{ marginTop: 6 }}>⬇ Descargar gtfs.zip</button>
        </a>
      </div>

      <div className="panel-section">
        <h3>Validar</h3>
        <div className="btn-row">
          <button className="btn secondary" disabled={busy !== null} onClick={() => runValidate(false)}>Rápida (interna)</button>
          <button className="btn" disabled={busy !== null} onClick={() => runValidate(true)}>
            {busy === 'validate' ? 'Validando…' : 'Completa (MobilityData)'}
          </button>
        </div>

        {summary && (
          <>
            <div className="summary-grid" style={{ marginTop: 12 }}>
              <div className="summary-card"><div className="value" style={{ color: 'var(--danger)' }}>{summary.errors}</div><div className="label">Errores</div></div>
              <div className="summary-card"><div className="value" style={{ color: 'var(--warning)' }}>{summary.warnings}</div><div className="label">Avisos</div></div>
              <div className="summary-card"><div className="value" style={{ color: 'var(--accent)' }}>{summary.infos}</div><div className="label">Info</div></div>
            </div>
            {summary.publishable ? (
              <div className="notice INFO">✓ 0 errores críticos — el feed puede publicarse.</div>
            ) : (
              <div className="notice ERROR">✕ Hay errores que impiden publicar. Corrígelos antes de continuar.</div>
            )}
            {summary.notices.map((n, i) => (
              <div key={i} className={`notice ${n.severity}`}>
                <div className="category">{n.category}</div>
                {n.title}
              </div>
            ))}
          </>
        )}
      </div>
    </div>
  );
}
