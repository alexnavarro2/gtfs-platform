import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, getAuthToken, setUnauthorizedHandler } from './api/client';
import type { AdminUser, Agency, Feed, FeedInfoRequest, Route, Stop, ValidationSummary } from './api/client';
import { useAppStore } from './store/useAppStore';
import { MapView } from './map/MapView';

type Tab = 'agency' | 'stops' | 'routes' | 'calendars' | 'fares' | 'validation' | 'admin';

export default function App() {
  const authUser = useAppStore((s) => s.authUser);
  const setAuth = useAppStore((s) => s.setAuth);
  const clearAuth = useAppStore((s) => s.clearAuth);
  const feedVersionId = useAppStore((s) => s.feedVersionId);
  // El token persiste en localStorage entre recargas; se valida contra /auth/me
  // una vez al montar para restaurar la sesión (o limpiarla si ya expiró).
  const [checkingSession, setCheckingSession] = useState(!!getAuthToken() && !authUser);

  useEffect(() => {
    setUnauthorizedHandler(() => clearAuth());
    return () => setUnauthorizedHandler(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!getAuthToken() || authUser) {
      setCheckingSession(false);
      return;
    }
    api.auth
      .me()
      .then((user) => setAuth(user, getAuthToken()!))
      .catch(() => clearAuth())
      .finally(() => setCheckingSession(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (checkingSession) {
    return (
      <div className="bootstrap-screen">
        <div className="bootstrap-card">Cargando sesión…</div>
      </div>
    );
  }
  if (!authUser) return <AuthScreen />;
  if (!feedVersionId) return <Bootstrap />;
  return <Shell />;
}

// ---------------------------------------------------------------------------
// AuthScreen: login / registro (cada usuario administra sus propios feeds)
// ---------------------------------------------------------------------------
function AuthScreen() {
  const setAuth = useAppStore((s) => s.setAuth);
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [institution, setInstitution] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const res =
        mode === 'login'
          ? await api.auth.login({ email, password })
          : await api.auth.register({ email, password, displayName, institution, jobTitle });
      setAuth(res.user, res.token);
    } catch (e: any) {
      setError(e.message || 'Error de autenticación');
    } finally {
      setBusy(false);
    }
  }

  const registerIncomplete = !displayName.trim() || !institution.trim() || !jobTitle.trim();

  return (
    <div className="bootstrap-screen">
      <div className="bootstrap-card">
        <h2>GTFS Platform</h2>
        <p className="hint">
          {mode === 'login' ? 'Inicia sesión para administrar tus feeds.' : 'Crea tu cuenta para empezar a crear feeds GTFS.'}
        </p>
        {mode === 'register' && (
          <>
            <div className="field">
              <label>Nombre</label>
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            </div>
            <div className="field">
              <label>Institución</label>
              <input value={institution} onChange={(e) => setInstitution(e.target.value)} />
            </div>
            <div className="field">
              <label>Puesto</label>
              <input value={jobTitle} onChange={(e) => setJobTitle(e.target.value)} />
            </div>
          </>
        )}
        <div className="field">
          <label>Correo</label>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="field">
          <label>Contraseña</label>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        {error && <div className="notice ERROR">{error}</div>}
        <button
          className="btn block"
          disabled={busy || !email.trim() || !password.trim() || (mode === 'register' && registerIncomplete)}
          onClick={submit}
        >
          {busy ? 'Un momento…' : mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}
        </button>
        <button
          className="btn secondary block"
          style={{ marginTop: 8 }}
          onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login');
            setError(null);
          }}
        >
          {mode === 'login' ? '¿No tienes cuenta? Regístrate' : '¿Ya tienes cuenta? Inicia sesión'}
        </button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Bootstrap: crear feed + version + agencia inicial (secciones 1-2 del prompt)
// ---------------------------------------------------------------------------
function Bootstrap() {
  const setFeed = useAppStore((s) => s.setFeed);
  const setAgency = useAppStore((s) => s.setAgency);
  const clearAuth = useAppStore((s) => s.clearAuth);
  const authUser = useAppStore((s) => s.authUser);
  const queryClient = useQueryClient();
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
      // Igual que en AgencySetup: sin invalidar, el selector de feeds del Topbar
      // sigue mostrando la lista vieja (sin el feed recién creado) al entrar al Shell.
      await queryClient.invalidateQueries({ queryKey: ['feeds'] });
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
          Hola {authUser?.displayName}. Crea tu primer feed para empezar. Podrás definir la agencia, paradas, rutas y
          horarios desde el mapa.
        </p>
        <div className="field">
          <label>Nombre del feed</label>
          <input value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        {error && <div className="notice ERROR">{error}</div>}
        <button className="btn block" disabled={busy || !name.trim()} onClick={createFeed}>
          {busy ? 'Creando…' : 'Crear feed'}
        </button>
        <button className="btn secondary block" style={{ marginTop: 8 }} onClick={clearAuth}>
          Cerrar sesión
        </button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Shell principal: topbar + sidebar de herramientas + mapa (sección 5)
// ---------------------------------------------------------------------------
function Shell() {
  const feedId = useAppStore((s) => s.feedId);
  const feedVersionId = useAppStore((s) => s.feedVersionId)!;
  const agencyId = useAppStore((s) => s.agencyId);
  const setAgency = useAppStore((s) => s.setAgency);
  const setFeed = useAppStore((s) => s.setFeed);
  const authUser = useAppStore((s) => s.authUser);
  const clearAuth = useAppStore((s) => s.clearAuth);
  const [tab, setTab] = useState<Tab>(agencyId ? 'stops' : 'routes');

  const configQuery = useQuery({ queryKey: ['config'], queryFn: api.config });
  const feedsQuery = useQuery({ queryKey: ['feeds'], queryFn: api.feeds.list });
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

  async function switchFeed(newFeedId: string) {
    if (newFeedId === feedId) return;
    const versions = await api.feedVersions.list(newFeedId);
    if (versions.length === 0) return;
    const latest = versions.reduce((a, b) => (a.versionNumber > b.versionNumber ? a : b));
    setFeed(newFeedId, latest.id);
    setTab('stops');
  }

  if (!agenciesQuery.isLoading && (!agenciesQuery.data || agenciesQuery.data.length === 0)) {
    return <AgencySetup feedVersionId={feedVersionId} />;
  }

  return (
    <div className="app-shell">
      <Topbar
        feedVersion={feedVersionQuery.data}
        feeds={feedsQuery.data}
        activeFeedId={feedId}
        onSwitchFeed={switchFeed}
        user={authUser}
        onLogout={clearAuth}
      />
      <div className="main-area">
        <div className="sidebar">
          <div className="tabs">
            <TabButton current={tab} value="agency" onClick={setTab} label="Agencia" />
            <TabButton current={tab} value="stops" onClick={setTab} label="Paradas" />
            <TabButton current={tab} value="routes" onClick={setTab} label="Rutas" />
            <TabButton current={tab} value="calendars" onClick={setTab} label="Calendarios" />
            <TabButton current={tab} value="fares" onClick={setTab} label="Tarifas" />
            <TabButton current={tab} value="validation" onClick={setTab} label="Validación" />
            {authUser?.role === 'ADMIN' && (
              <TabButton current={tab} value="admin" onClick={setTab} label="Administración" />
            )}
          </div>
          <div className="tab-content">
            {tab === 'agency' && <AgencyPanel feedVersionId={feedVersionId} />}
            {tab === 'stops' && <StopsPanel feedVersionId={feedVersionId} />}
            {tab === 'routes' && <RoutesPanel feedVersionId={feedVersionId} agencyId={agencyId!} />}
            {tab === 'calendars' && <CalendarsPanel feedVersionId={feedVersionId} />}
            {tab === 'fares' && <FaresPanel feedVersionId={feedVersionId} />}
            {tab === 'validation' && <ValidationPanel feedVersionId={feedVersionId} />}
            {tab === 'admin' && authUser?.role === 'ADMIN' && <AdminPanel currentUserId={authUser.id} />}
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
  const queryClient = useQueryClient();
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
      // Sin esto, el Shell sigue leyendo la lista de agencias vacía que ya tenía
      // en caché y vuelve a mostrar esta misma pantalla en vez de avanzar.
      await queryClient.invalidateQueries({ queryKey: ['agencies', feedVersionId] });
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

// ---------------------------------------------------------------------------
// Datos del feed (feed_info.txt): publicador, idioma, vigencia. Sin esto no
// había forma de completarlos para un feed creado desde el portal — el
// exportador omitía el archivo en silencio si feedPublisherName era null.
// ---------------------------------------------------------------------------
function FeedInfoForm({ feedVersionId }: { feedVersionId: string }) {
  const feedVersionQuery = useQuery({
    queryKey: ['feedVersion', feedVersionId],
    queryFn: () => api.feedVersions.get(feedVersionId),
  });
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FeedInfoRequest | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const fv = feedVersionQuery.data;
  useEffect(() => {
    if (fv && !form) {
      setForm({
        feedPublisherName: fv.feedPublisherName || '',
        feedPublisherUrl: fv.feedPublisherUrl || '',
        feedLang: fv.feedLang || 'es',
        feedStartDate: fv.feedStartDate || '',
        feedEndDate: fv.feedEndDate || '',
        feedVersionString: fv.feedVersionString || '',
        feedContactEmail: fv.feedContactEmail || '',
        feedContactUrl: fv.feedContactUrl || '',
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fv]);

  async function save() {
    if (!form) return;
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      await api.feedVersions.updateFeedInfo(feedVersionId, form);
      setSaved(true);
      queryClient.invalidateQueries({ queryKey: ['feedVersion', feedVersionId] });
    } catch (e: any) {
      setError(e.message || 'Error guardando los datos del feed');
    } finally {
      setBusy(false);
    }
  }

  if (!form) return null;

  return (
    <div className="panel-section" style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 10 }}>
      <h3>Datos del feed (feed_info.txt)</h3>
      <p className="hint">Quién publica este GTFS y hasta cuándo es válido — sin esto, el archivo no se genera al exportar.</p>
      <div className="field">
        <label>Nombre del publicador</label>
        <input value={form.feedPublisherName} onChange={(e) => setForm({ ...form, feedPublisherName: e.target.value })} />
      </div>
      <div className="field">
        <label>Sitio web del publicador</label>
        <input value={form.feedPublisherUrl} onChange={(e) => setForm({ ...form, feedPublisherUrl: e.target.value })} />
      </div>
      <div className="field-row">
        <div className="field">
          <label>Idioma</label>
          <input value={form.feedLang} onChange={(e) => setForm({ ...form, feedLang: e.target.value })} />
        </div>
        <div className="field">
          <label>Versión (texto libre)</label>
          <input value={form.feedVersionString} onChange={(e) => setForm({ ...form, feedVersionString: e.target.value })} />
        </div>
      </div>
      <div className="field-row">
        <div className="field">
          <label>Válido desde</label>
          <input type="date" value={form.feedStartDate} onChange={(e) => setForm({ ...form, feedStartDate: e.target.value })} />
        </div>
        <div className="field">
          <label>Válido hasta</label>
          <input type="date" value={form.feedEndDate} onChange={(e) => setForm({ ...form, feedEndDate: e.target.value })} />
        </div>
      </div>
      <div className="field">
        <label>Correo de contacto</label>
        <input value={form.feedContactEmail} onChange={(e) => setForm({ ...form, feedContactEmail: e.target.value })} />
      </div>
      {error && <div className="notice ERROR">{error}</div>}
      {saved && !error && <div className="notice INFO">✓ Guardado</div>}
      <button
        className="btn"
        disabled={busy || !form.feedPublisherName.trim() || !form.feedPublisherUrl.trim() || !form.feedLang.trim()}
        onClick={save}
      >
        {busy ? 'Guardando…' : 'Guardar cambios'}
      </button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Agencia: editar los datos de agency.txt después de la creación inicial
// (AgencySetup arriba solo se muestra una vez, al crear el feed).
// ---------------------------------------------------------------------------
function AgencyPanel({ feedVersionId }: { feedVersionId: string }) {
  const queryClient = useQueryClient();
  const agenciesQuery = useQuery({ queryKey: ['agencies', feedVersionId], queryFn: () => api.agencies.list(feedVersionId) });
  const [showNewForm, setShowNewForm] = useState(false);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['agencies', feedVersionId] });

  return (
    <div>
      <SectionIntro title="¿Qué es esto?">
        Aquí defines quién publica este GTFS (<code>feed_info.txt</code>) y la o las agencias que operan las rutas
        (<code>agency.txt</code>). Complétalo una sola vez por feed — Google Maps, Moovit y demás apps lo usan para
        saber a quién atribuir el servicio y hasta cuándo confiar en los datos.
      </SectionIntro>
      <FeedInfoForm feedVersionId={feedVersionId} />
      <div className="panel-section">
        <h3>Agencias ({agenciesQuery.data?.length || 0})</h3>
        {(agenciesQuery.data || []).map((a) => (
          <AgencyForm key={a.id} agency={a} onSaved={invalidate} />
        ))}
      </div>
      <div className="panel-section">
        {showNewForm ? (
          <AgencyForm
            feedVersionId={feedVersionId}
            onSaved={() => {
              invalidate();
              setShowNewForm(false);
            }}
            onCancel={() => setShowNewForm(false)}
          />
        ) : (
          <button className="btn secondary block" onClick={() => setShowNewForm(true)}>
            + Agregar otra agencia
          </button>
        )}
      </div>
    </div>
  );
}

function AgencyForm({
  agency,
  feedVersionId,
  onSaved,
  onCancel,
}: {
  agency?: Agency;
  feedVersionId?: string;
  onSaved: () => void;
  onCancel?: () => void;
}) {
  const [form, setForm] = useState({
    agencyName: agency?.agencyName || '',
    agencyUrl: agency?.agencyUrl || 'https://',
    agencyTimezone: agency?.agencyTimezone || 'America/Hermosillo',
    agencyLang: agency?.agencyLang || 'es',
    agencyPhone: agency?.agencyPhone || '',
    agencyFareUrl: agency?.agencyFareUrl || '',
    agencyEmail: agency?.agencyEmail || '',
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  async function save() {
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      if (agency) {
        await api.agencies.update(agency.id, form);
      } else if (feedVersionId) {
        await api.agencies.create(feedVersionId, form);
      }
      setSaved(true);
      onSaved();
    } catch (e: any) {
      setError(e.message || 'Error guardando la agencia');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel-section" style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 10 }}>
      <div className="field">
        <label>Nombre</label>
        <input value={form.agencyName} onChange={(e) => setForm({ ...form, agencyName: e.target.value })} />
      </div>
      <div className="field">
        <label>Sitio web</label>
        <input value={form.agencyUrl} onChange={(e) => setForm({ ...form, agencyUrl: e.target.value })} />
      </div>
      <div className="field-row">
        <div className="field">
          <label>Zona horaria</label>
          <input value={form.agencyTimezone} onChange={(e) => setForm({ ...form, agencyTimezone: e.target.value })} />
        </div>
        <div className="field">
          <label>Idioma</label>
          <input value={form.agencyLang} onChange={(e) => setForm({ ...form, agencyLang: e.target.value })} />
        </div>
      </div>
      <div className="field">
        <label>Teléfono</label>
        <input value={form.agencyPhone} onChange={(e) => setForm({ ...form, agencyPhone: e.target.value })} />
      </div>
      <div className="field">
        <label>Correo</label>
        <input value={form.agencyEmail} onChange={(e) => setForm({ ...form, agencyEmail: e.target.value })} />
      </div>
      <div className="field">
        <label>URL de tarifas</label>
        <input value={form.agencyFareUrl} onChange={(e) => setForm({ ...form, agencyFareUrl: e.target.value })} />
      </div>
      {error && <div className="notice ERROR">{error}</div>}
      {saved && !error && <div className="notice INFO">✓ Guardado</div>}
      <div className="btn-row">
        {onCancel && (
          <button className="btn secondary" onClick={onCancel}>
            Cancelar
          </button>
        )}
        <button className="btn" disabled={busy || !form.agencyName.trim()} onClick={save}>
          {busy ? 'Guardando…' : agency ? 'Guardar cambios' : 'Crear agencia'}
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

// Explica, en el lenguaje de la especificación GTFS, qué representa esta
// sección y qué se espera que haga el usuario ahí — para alguien nuevo en
// GTFS, "Rutas" o "Calendarios" por sí solos no dicen a qué archivo/concepto
// del estándar corresponden ni por dónde empezar.
function SectionIntro({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="section-intro">
      <div className="section-intro-title">{title}</div>
      <p style={{ margin: 0 }}>{children}</p>
    </div>
  );
}

function Topbar({
  feedVersion,
  feeds,
  activeFeedId,
  onSwitchFeed,
  user,
  onLogout,
}: {
  feedVersion?: { feed: { name: string }; versionNumber: number; status: string };
  feeds?: Feed[];
  activeFeedId?: string | null;
  onSwitchFeed?: (feedId: string) => void;
  user?: { displayName: string; email: string } | null;
  onLogout?: () => void;
}) {
  return (
    <div className="topbar">
      <img className="brand-logo" src="/imtes-logo.png" alt="IMTES - Instituto de Movilidad y Transporte para el Estado de Sonora" />
      <div className="brand">GTFS Platform</div>
      {feeds && feeds.length > 0 && (
        <select value={activeFeedId ?? ''} onChange={(e) => onSwitchFeed?.(e.target.value)}>
          {feeds.map((f) => (
            <option key={f.id} value={f.id} style={{ color: '#000' }}>
              {f.name}
            </option>
          ))}
        </select>
      )}
      {feedVersion && (
        <div className="brand-sub">
          v{feedVersion.versionNumber}
        </div>
      )}
      <div className="spacer" />
      {feedVersion && <span className="status-pill">{feedVersion.status}</span>}
      {user && (
        <div className="user-menu">
          <span className="user-name" title={user.email}>
            {user.displayName}
          </span>
          <button className="btn secondary" onClick={onLogout}>
            Cerrar sesión
          </button>
        </div>
      )}
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
  const draftPatternStopIds = useAppStore((s) => s.draftPatternStopIds);
  const draftShapePoints = useAppStore((s) => s.draftShapePoints);
  const routedPreviewPoints = useAppStore((s) => s.routedPreviewPoints);
  const routedPreviewInfo = useAppStore((s) => s.routedPreviewInfo);
  const setRoutedPreview = useAppStore((s) => s.setRoutedPreview);
  const [pendingStopLatLon, setPendingStopLatLon] = useState<{ lat: number; lon: number } | null>(null);
  const [routing, setRouting] = useState(false);

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

  // Modo 1/3 (sección 9): tanto al unir paradas existentes como al dibujar por
  // puntos de control, cada tramo se rutea automáticamente por la red vial. La
  // geometría es solo una propuesta — nunca se guarda hasta "Guardar recorrido".
  useEffect(() => {
    let waypoints: { lat: number; lon: number }[] = [];
    if (mapTool === 'add-pattern-stop' && stopsQuery.data) {
      const byId = new Map(stopsQuery.data.map((s) => [s.id, s]));
      waypoints = draftPatternStopIds
        .map((id) => byId.get(id))
        .filter((s): s is Stop => !!s)
        .map((s) => ({ lat: s.stopLat, lon: s.stopLon }));
    } else if (mapTool === 'draw-shape') {
      waypoints = draftShapePoints;
    }

    if (waypoints.length < 2) {
      setRoutedPreview([], null);
      return;
    }
    let cancelled = false;
    setRouting(true);
    api.routing
      .route(waypoints)
      .then((res) => {
        if (cancelled) return;
        setRoutedPreview(
          res.points.map(([lat, lon]) => ({ lat, lon })),
          { routed: res.routed, provider: res.provider },
        );
      })
      .catch(() => {
        if (!cancelled) setRoutedPreview(waypoints, { routed: false, provider: 'error' });
      })
      .finally(() => {
        if (!cancelled) setRouting(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapTool, draftPatternStopIds, draftShapePoints, stopsQuery.data]);

  function handleMapClick(lat: number, lon: number) {
    if (mapTool === 'add-stop' || mapTool === 'add-pattern-stop') {
      // En "Agregar paradas", un clic fuera de una parada existente ofrece
      // crear una nueva ahí mismo (sección 6) y sumarla directo al recorrido —
      // sin esto había que salir a la pestaña Paradas y volver.
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
        routedPreviewPoints={routedPreviewPoints}
        onMapClick={handleMapClick}
        onStopClick={handleStopClick}
      />
      <div className="attribution-badge">{attribution || '© OpenStreetMap contributors'}</div>
      {((mapTool === 'add-pattern-stop' && draftPatternStopIds.length >= 2) ||
        (mapTool === 'draw-shape' && draftShapePoints.length >= 2)) && (
        <div style={{ position: 'absolute', top: 16, left: 16, background: 'white', borderRadius: 8, padding: '8px 14px', boxShadow: '0 4px 16px rgba(0,0,0,0.15)', zIndex: 10, fontSize: 12 }}>
          {routing
            ? 'Ruteando por la red vial…'
            : routedPreviewPoints.length > 0
              ? `✓ Ruteado por calles (${routedPreviewInfo?.provider ?? 'osrm'})`
              : '⚠ Sin ruteo — líneas rectas entre paradas'}
        </div>
      )}
      {pendingStopLatLon && (
        <StopQuickForm
          feedVersionId={feedVersionId}
          lat={pendingStopLatLon.lat}
          lon={pendingStopLatLon.lon}
          onClose={() => setPendingStopLatLon(null)}
          onSaved={(stop) => {
            setPendingStopLatLon(null);
            queryClient.invalidateQueries({ queryKey: ['stops', feedVersionId] });
            if (mapTool === 'add-pattern-stop') {
              // Sumarla al recorrido que se está armando y seguir en la misma
              // herramienta — igual que agregar cualquier otra parada existente.
              toggleDraftPatternStop(stop.id);
            } else {
              setMapTool('none');
            }
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
  onSaved: (stop: Stop) => void;
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
      const stop = await api.stops.create(feedVersionId, {
        stopName: name,
        stopLat: lat,
        stopLon: lon,
        locationType: 0,
        wheelchairBoarding: wheelchair ? 1 : 0,
      });
      onSaved(stop);
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
  const queryClient = useQueryClient();
  const stopsQuery = useQuery({ queryKey: ['stops', feedVersionId], queryFn: () => api.stops.list(feedVersionId) });
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const deleteStop = useMutation({
    mutationFn: (id: string) => api.stops.remove(id),
    onSuccess: () => {
      setDeleteError(null);
      queryClient.invalidateQueries({ queryKey: ['stops', feedVersionId] });
    },
    onError: (e: any) => setDeleteError(e.message || 'Error eliminando la parada'),
  });

  function handleDelete(s: Stop) {
    if (!window.confirm(`¿Eliminar la parada "${s.stopName || s.gtfsId}"? Esta acción no se puede deshacer.`)) return;
    deleteStop.mutate(s.id);
  }

  return (
    <div>
      <SectionIntro title="¿Qué es esto?">
        <code>stops.txt</code>: las ubicaciones físicas donde suben y bajan pasajeros. Créalas aquí antes de armar
        rutas — cada parada se reutiliza en tantos recorridos como haga falta, así que conviene primero mapear las
        paradas reales de tu ciudad y después ir a "Rutas" a unirlas en recorridos.
      </SectionIntro>
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
        {deleteError && <div className="notice ERROR">{deleteError}</div>}
        {(stopsQuery.data || []).map((s) => (
          <div className="list-item" key={s.id}>
            <span>{s.stopName || '(sin nombre)'} <span style={{ color: '#999' }}>· {s.gtfsId}</span></span>
            <button
              className="btn danger icon-btn"
              disabled={deleteStop.isPending}
              title="Eliminar parada"
              onClick={(e) => {
                e.stopPropagation();
                handleDelete(s);
              }}
            >
              ✕
            </button>
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
      <SectionIntro title="¿Qué es esto?">
        <code>routes.txt</code>: cada línea que operas (p. ej. "18 — Hospitales-Universidades"). Al seleccionar una
        ruta de la lista se abren sus <strong>sentidos</strong> (IDA/REGRESO u otras variantes), donde defines el
        recorrido por calles, las paradas que visita y el horario — normalmente: crea la ruta, agrega un sentido,
        arma su recorrido y por último genera el horario.
      </SectionIntro>
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

      {activeRoute && <RouteEditForm key={activeRoute.id} route={activeRoute} feedVersionId={feedVersionId} />}
      {activeRoute && <PatternsPanel route={activeRoute} />}
    </div>
  );
}

// Edición de una ruta ya creada — "Nueva ruta" arriba solo cubre la creación;
// sin esto no había forma de cambiar el color (ni nada más) después.
function RouteEditForm({ route, feedVersionId }: { route: Route; feedVersionId: string }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    routeShortName: route.routeShortName || '',
    routeLongName: route.routeLongName || '',
    routeDesc: route.routeDesc || '',
    routeUrl: route.routeUrl || '',
    routeColor: route.routeColor || '1E88E5',
    routeTextColor: route.routeTextColor || 'FFFFFF',
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  async function save() {
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      await api.routes.update(route.id, form);
      setSaved(true);
      queryClient.invalidateQueries({ queryKey: ['routes', feedVersionId] });
    } catch (e: any) {
      setError(e.message || 'Error guardando la ruta');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel-section" style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 10 }}>
      <h3>Editar ruta {route.routeShortName}</h3>
      <div className="field-row">
        <div className="field">
          <label>Clave corta</label>
          <input value={form.routeShortName} onChange={(e) => setForm({ ...form, routeShortName: e.target.value })} />
        </div>
        <div className="field">
          <label>Color</label>
          <div style={{ display: 'flex', gap: 6 }}>
            <input
              type="color"
              style={{ width: 40, padding: 2 }}
              value={`#${form.routeColor}`}
              onChange={(e) => setForm({ ...form, routeColor: e.target.value.replace('#', '').toUpperCase() })}
            />
            <input
              value={form.routeColor}
              onChange={(e) => setForm({ ...form, routeColor: e.target.value.replace('#', '').toUpperCase() })}
            />
          </div>
        </div>
      </div>
      <div className="field">
        <label>Nombre largo</label>
        <input value={form.routeLongName} onChange={(e) => setForm({ ...form, routeLongName: e.target.value })} />
      </div>
      <div className="field">
        <label>Descripción</label>
        <input value={form.routeDesc} onChange={(e) => setForm({ ...form, routeDesc: e.target.value })} />
      </div>
      <div className="field-row">
        <div className="field">
          <label>Sitio web</label>
          <input value={form.routeUrl} onChange={(e) => setForm({ ...form, routeUrl: e.target.value })} />
        </div>
        <div className="field">
          <label>Color del texto</label>
          <div style={{ display: 'flex', gap: 6 }}>
            <input
              type="color"
              style={{ width: 40, padding: 2 }}
              value={`#${form.routeTextColor}`}
              onChange={(e) => setForm({ ...form, routeTextColor: e.target.value.replace('#', '').toUpperCase() })}
            />
            <input
              value={form.routeTextColor}
              onChange={(e) => setForm({ ...form, routeTextColor: e.target.value.replace('#', '').toUpperCase() })}
            />
          </div>
        </div>
      </div>
      {error && <div className="notice ERROR">{error}</div>}
      {saved && !error && <div className="notice INFO">✓ Guardado</div>}
      <button className="btn" disabled={busy || !form.routeShortName.trim()} onClick={save}>
        {busy ? 'Guardando…' : 'Guardar cambios'}
      </button>
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
      <SectionIntro title="¿Qué es un sentido?">
        Es la variante de recorrido de esta ruta — normalmente IDA y REGRESO, aunque puede haber más si hay ramales.
        Cada sentido tiene su propio trazo por calles y su propio orden de paradas, aunque compartan número y color
        de ruta. Internamente es lo que GTFS agrupa como <code>trips.txt</code> que comparten <code>shape_id</code>.
      </SectionIntro>
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
  const routedPreviewPoints = useAppStore((s) => s.routedPreviewPoints);
  const routedPreviewInfo = useAppStore((s) => s.routedPreviewInfo);

  const patternStopsQuery = useQuery({ queryKey: ['patternStops', patternId], queryFn: () => api.patterns.getStops(patternId) });

  const saveShape = useMutation({
    mutationFn: () =>
      api.patterns.replaceShapePoints(
        patternId,
        routedPreviewPoints.length >= 2 ? routedPreviewPoints : draftShapePoints,
      ),
    onSuccess: () => {
      clearDraftShapePoints();
      setMapTool('none');
      queryClient.invalidateQueries({ queryKey: ['shapePoints', patternId] });
    },
  });

  // Guarda el orden de paradas Y el recorrido ruteado por la red vial entre ellas
  // en un solo paso — igual que Conveyal construye el pattern uniendo paradas.
  const saveStopsAndRoute = useMutation({
    mutationFn: async () => {
      await api.patterns.replaceStops(patternId, draftPatternStopIds);
      if (routedPreviewPoints.length >= 2) {
        await api.patterns.replaceShapePoints(patternId, routedPreviewPoints);
      }
    },
    onSuccess: () => {
      clearDraftPatternStops();
      setMapTool('none');
      queryClient.invalidateQueries({ queryKey: ['patternStops', patternId] });
      queryClient.invalidateQueries({ queryKey: ['shapePoints', patternId] });
      // replacePatternStops borra los trips del pattern en el backend (dejan de
      // ser válidos si cambia el orden/paradas) — sin esto, TripsList seguía
      // mostrando trips que ya no existen hasta un refresh manual.
      queryClient.invalidateQueries({ queryKey: ['trips', patternId] });
    },
  });

  // Quitar una parada YA GUARDADA del recorrido (no del borrador que se está
  // dibujando — eso es clearDraftPatternStops/clearDraftShapePoints arriba).
  // Recalcula el trazo entre las paradas restantes para que la línea guardada
  // no quede pasando por un punto que ya no forma parte del recorrido.
  const removePatternStop = useMutation({
    mutationFn: async (stopIdToRemove: string) => {
      const remainingStops = (patternStopsQuery.data || [])
        .filter((ps) => ps.stop.id !== stopIdToRemove)
        .map((ps) => ps.stop);
      await api.patterns.replaceStops(patternId, remainingStops.map((s) => s.id));
      if (remainingStops.length >= 2) {
        const routed = await api.routing.route(remainingStops.map((s) => ({ lat: s.stopLat, lon: s.stopLon })));
        await api.patterns.replaceShapePoints(patternId, routed.points.map(([lat, lon]) => ({ lat, lon })));
      } else {
        await api.patterns.replaceShapePoints(patternId, []);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patternStops', patternId] });
      queryClient.invalidateQueries({ queryKey: ['shapePoints', patternId] });
      queryClient.invalidateQueries({ queryKey: ['trips', patternId] });
    },
  });

  // Vaciar todo el recorrido guardado (paradas + trazo) para empezar de cero,
  // sin tener que ir quitando parada por parada.
  const clearSavedPattern = useMutation({
    mutationFn: async () => {
      await api.patterns.replaceStops(patternId, []);
      await api.patterns.replaceShapePoints(patternId, []);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patternStops', patternId] });
      queryClient.invalidateQueries({ queryKey: ['shapePoints', patternId] });
      queryClient.invalidateQueries({ queryKey: ['trips', patternId] });
    },
  });

  return (
    <div style={{ marginTop: 10, borderTop: '1px dashed var(--border)', paddingTop: 10 }}>
      <h3>Recorrido</h3>
      <SectionIntro title="¿Qué es esto?">
        Qué paradas visita este sentido y en qué orden (<code>stop_times.txt</code>), y el trazo por calles que sigue
        el vehículo (<code>shapes.txt</code>). Usa "📍 Agregar paradas" para lo más común — unir paradas ya creadas,
        se rutea solo por la red vial. "✏️ Dibujar" es para trazar la geometría a mano libre y no agrega paradas.
      </SectionIntro>
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
          <p className="hint">
            Haz clic en el mapa para agregar puntos de control del trazo ({draftShapePoints.length}). Esto dibuja la
            geometría de la calle, <strong>no agrega paradas</strong> — aunque hagas clic sobre una parada existente,
            solo cuenta como punto del trazo. Para seleccionar paradas usa "📍 Agregar paradas". Cada tramo entre dos
            puntos se rutea automáticamente por la red vial.
          </p>
          {draftShapePoints.length >= 2 && (
            <p className="hint" style={{ color: routedPreviewInfo?.routed ? 'var(--success)' : 'var(--warning)' }}>
              {routedPreviewInfo?.routed
                ? `✓ Ruteado por calles (${routedPreviewInfo.provider})`
                : routedPreviewPoints.length > 0
                  ? '⚠ Sin ruteo disponible — se guardará una línea recta entre puntos'
                  : 'Calculando ruta…'}
            </p>
          )}
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
          <p className="hint">
            Haz clic en las paradas del mapa, en el orden en que las visita el recorrido ({draftPatternStopIds.length} seleccionadas).
            Si haces clic donde no hay una parada, se crea una nueva ahí mismo y se suma al recorrido. El tramo entre
            cada par se rutea automáticamente por la red vial.
          </p>
          {draftPatternStopIds.length >= 2 && (
            <p className="hint" style={{ color: routedPreviewInfo?.routed ? 'var(--success)' : 'var(--warning)' }}>
              {routedPreviewInfo?.routed
                ? `✓ Ruteado por calles (${routedPreviewInfo.provider})`
                : routedPreviewPoints.length > 0
                  ? '⚠ Sin ruteo disponible — se guardarán líneas rectas entre paradas'
                  : 'Calculando ruta…'}
            </p>
          )}
          <div className="btn-row">
            <button className="btn secondary" onClick={clearDraftPatternStops}>Limpiar</button>
            <button className="btn" disabled={draftPatternStopIds.length < 2 || saveStopsAndRoute.isPending} onClick={() => saveStopsAndRoute.mutate()}>
              {saveStopsAndRoute.isPending ? 'Guardando…' : 'Guardar paradas y recorrido'}
            </button>
          </div>
        </div>
      )}

      <div className="panel-section" style={{ marginTop: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <h3 style={{ margin: 0 }}>Paradas del recorrido ({patternStopsQuery.data?.length || 0})</h3>
          {(patternStopsQuery.data?.length || 0) > 0 && (
            <button
              className="btn danger icon-btn"
              disabled={clearSavedPattern.isPending || removePatternStop.isPending}
              onClick={() => {
                if (
                  window.confirm(
                    '¿Vaciar el recorrido? Se eliminarán todas sus paradas y el trazo guardado, y tendrás que regenerar el horario. Esta acción no se puede deshacer.',
                  )
                ) {
                  clearSavedPattern.mutate();
                }
              }}
            >
              Vaciar
            </button>
          )}
        </div>
        {(removePatternStop.isError || clearSavedPattern.isError) && (
          <div className="notice ERROR">
            {((removePatternStop.error || clearSavedPattern.error) as any)?.message}
          </div>
        )}
        {(patternStopsQuery.data || []).map((ps, i) => (
          <div key={ps.id} className="list-item">
            <span>{String(i + 1).padStart(2, '0')} — {ps.stop.stopName}</span>
            <button
              className="btn danger icon-btn"
              disabled={removePatternStop.isPending || clearSavedPattern.isPending}
              title="Quitar del recorrido"
              onClick={() => {
                if (
                  window.confirm(
                    `¿Quitar "${ps.stop.stopName}" del recorrido? Se recalculará el trazo y tendrás que regenerar el horario.`,
                  )
                ) {
                  removePatternStop.mutate(ps.stop.id);
                }
              }}
            >
              ✕
            </button>
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
  const queryClient = useQueryClient();
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
      queryClient.invalidateQueries({ queryKey: ['trips', patternId] });
    } catch (e: any) {
      setResult('Error: ' + e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel-section" style={{ marginTop: 10 }}>
      <h3>Horario</h3>
      <SectionIntro title="¿Qué es esto?">
        Genera los viajes (<code>trips.txt</code>) y sus horas de paso en cada parada (<code>stop_times.txt</code>)
        para este recorrido, sobre un calendario de servicio ya creado. "Frecuencia" crea un solo viaje que se repite
        cada X minutos (<code>frequencies.txt</code>) — lo normal en una ruta urbana. "Horario explícito" crea un
        viaje por cada hora de salida que escribas, útil si el servicio no es regular.
      </SectionIntro>
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
      <TripsList patternId={patternId} />
    </div>
  );
}

// Lista de los trips ya generados para este pattern — sin esto, el único
// rastro de "Generar trips y stop_times" era el mensaje puntual que
// desaparecía al recargar o cambiar de pestaña.
function TripsList({ patternId }: { patternId: string }) {
  const queryClient = useQueryClient();
  const tripsQuery = useQuery({ queryKey: ['trips', patternId], queryFn: () => api.trips.list(patternId) });

  const removeTrip = useMutation({
    mutationFn: (id: string) => api.trips.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['trips', patternId] }),
  });

  const trips = tripsQuery.data || [];

  return (
    <div style={{ marginTop: 14 }}>
      <h3>Trips generados ({trips.length})</h3>
      {trips.length === 0 && <p className="hint">Todavía no hay trips para este recorrido.</p>}
      {trips.map((t) => (
        <div key={t.id} className="list-item" style={{ alignItems: 'flex-start' }}>
          <div>
            <div>
              <strong>{t.gtfsId}</strong> {t.tripHeadsign && `· ${t.tripHeadsign}`}
            </div>
            <div className="hint" style={{ margin: 0 }}>
              {t.serviceCalendarName} · {t.stopCount} paradas
              {t.frequencyBased
                ? t.frequencies.map((f, i) => (
                    <span key={i}>
                      {' '}
                      · {f.startTime}–{f.endTime} cada {Math.round(f.headwaySecs / 60)} min
                    </span>
                  ))
                : t.firstDeparture && ` · sale ${t.firstDeparture}, llega ${t.lastArrival}`}
            </div>
          </div>
          <button
            className="btn danger icon-btn"
            disabled={removeTrip.isPending}
            title="Eliminar trip"
            onClick={() => {
              if (window.confirm(`¿Eliminar el trip "${t.gtfsId}"? Esta acción no se puede deshacer.`)) {
                removeTrip.mutate(t.id);
              }
            }}
          >
            ✕
          </button>
        </div>
      ))}
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
      <SectionIntro title="¿Qué es esto?">
        <code>calendar.txt</code>: los patrones de servicio — qué días de la semana opera y en qué rango de fechas
        (p. ej. "Lunes a Viernes" o "Sábados y domingos"). Créalos aquí y luego asígnalos al generar el horario de
        cada recorrido, en la pestaña "Rutas" — un mismo recorrido puede tener horarios distintos según el calendario.
      </SectionIntro>
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
      <SectionIntro title="¿Qué es esto?">
        <code>fare_products.txt</code> (Fares V2, la forma vigente de definir tarifas en GTFS): el costo de un
        viaje. Esta pantalla solo cubre el caso simple — nombre, monto y moneda, aplicable a todo el feed. Asociar
        una tarifa a una ruta específica, a una categoría de pasajero o a un medio de pago (tarjeta, efectivo) es
        parte del estándar pero todavía no tiene interfaz aquí.
      </SectionIntro>
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
  const [busy, setBusy] = useState<'export' | 'validate' | 'download' | null>(null);
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

  async function runDownload() {
    setBusy('download');
    try {
      await api.gtfs.download(feedVersionId);
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
      <SectionIntro title="¿Qué es esto?">
        Empaqueta todo lo que armaste en un <code>gtfs.zip</code> y lo revisa contra las reglas del estándar.
        "Rápida" corre validaciones propias de esta plataforma; "Completa" usa el validador oficial de MobilityData
        — el mismo que usan Google Transit y otras plataformas de consumo. Corrige los errores (rojo) antes de
        publicar; las advertencias son buenas prácticas, no bloquean.
      </SectionIntro>
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
        <button className="btn secondary block" style={{ marginTop: 6 }} disabled={busy !== null} onClick={runDownload}>
          {busy === 'download' ? 'Descargando…' : '⬇ Descargar gtfs.zip'}
        </button>
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

// ---------------------------------------------------------------------------
// Panel de administración: usuarios registrados y su rol/permiso (solo ADMIN)
// ---------------------------------------------------------------------------
function AdminPanel({ currentUserId }: { currentUserId: string }) {
  const queryClient = useQueryClient();
  const usersQuery = useQuery({ queryKey: ['adminUsers'], queryFn: api.admin.users.list });

  const updateRole = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: AdminUser['role'] }) =>
      api.admin.users.updateRole(userId, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['adminUsers'] }),
  });

  return (
    <div>
      <SectionIntro title="¿Qué es esto?">
        No es parte del estándar GTFS — es la administración de la plataforma. Aquí ves quién está registrado y le
        cambias el rol. Por ahora solo ADMIN tiene un efecto real (administra usuarios y ve todos los feeds, no solo
        los propios); EDITOR y VIEWER quedan como etiqueta para uso futuro, todavía no restringen nada.
      </SectionIntro>
      <div className="panel-section">
        <h3>Usuarios registrados ({usersQuery.data?.length || 0})</h3>
        {usersQuery.isLoading && <div className="hint">Cargando…</div>}
        {updateRole.isError && <div className="notice ERROR">{(updateRole.error as any)?.message}</div>}
        {(usersQuery.data || []).map((u) => (
          <div key={u.id} className="admin-user-card">
            <div className="admin-user-name">
              {u.displayName} {u.id === currentUserId && <span className="hint">(tú)</span>}
            </div>
            <div className="admin-user-meta">{u.email}</div>
            {(u.institution || u.jobTitle) && (
              <div className="admin-user-meta">
                {u.jobTitle}
                {u.jobTitle && u.institution ? ' · ' : ''}
                {u.institution}
              </div>
            )}
            <div className="admin-user-meta">
              {u.feedCount} feed{u.feedCount === 1 ? '' : 's'} · registrado{' '}
              {new Date(u.createdAt).toLocaleDateString('es-MX')}
            </div>
            <div className="field" style={{ marginTop: 6, marginBottom: 0 }}>
              <label>Rol</label>
              <select
                value={u.role}
                disabled={u.id === currentUserId || updateRole.isPending}
                onChange={(e) => updateRole.mutate({ userId: u.id, role: e.target.value as AdminUser['role'] })}
              >
                <option value="ADMIN">ADMIN</option>
                <option value="EDITOR">EDITOR</option>
                <option value="VIEWER">VIEWER</option>
              </select>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
