import { useEffect, useRef } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useAppStore } from '../store/useAppStore';
import type { Stop, PatternStop } from '../api/client';

interface MapViewProps {
  tileUrl: string;
  attribution: string;
  stops: Stop[];
  patternStops: PatternStop[];
  savedShapePoints: { lat: number; lon: number }[];
  routedPreviewPoints?: { lat: number; lon: number }[];
  onMapClick: (lat: number, lon: number) => void;
  onStopClick: (stopId: string) => void;
  focusPoint?: { lat: number; lon: number } | null;
}

const HERMOSILLO_CENTER: [number, number] = [-110.9559, 29.0729];

export function MapView({
  tileUrl,
  attribution,
  stops,
  patternStops,
  savedShapePoints,
  routedPreviewPoints = [],
  onMapClick,
  onStopClick,
  focusPoint,
}: MapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  // map.isStyleLoaded() puede devolver false transitoriamente (no solo antes de la
  // primera carga) mientras el estilo procesa otras actualizaciones — usarlo como
  // condición para decidir entre "actualizar ya" o "esperar a 'load'" es incorrecto,
  // porque el evento 'load' del mapa se dispara UNA sola vez en toda su vida. Si el
  // gate caía en el branch else después de la carga inicial, esa actualización se
  // perdía para siempre (bug real: el recorrido ruteado nunca se pintaba). Esta bandera
  // propia sí refleja "¿ya pasó la carga inicial?" de forma estable.
  const styleReadyRef = useRef(false);
  const draftShapePoints = useAppStore((s) => s.draftShapePoints);
  const mapTool = useAppStore((s) => s.mapTool);

  const onMapClickRef = useRef(onMapClick);
  onMapClickRef.current = onMapClick;
  const onStopClickRef = useRef(onStopClick);
  onStopClickRef.current = onStopClick;
  const mapToolRef = useRef(mapTool);
  mapToolRef.current = mapTool;

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: {
        version: 8,
        sources: {
          osm: {
            type: 'raster',
            tiles: [tileUrl],
            tileSize: 256,
            attribution,
          },
        },
        layers: [{ id: 'osm-tiles', type: 'raster', source: 'osm' }],
      },
      center: HERMOSILLO_CENTER,
      zoom: 13,
    });
    map.addControl(new maplibregl.NavigationControl(), 'top-right');

    map.on('load', () => {
      map.addSource('stops', { type: 'geojson', data: emptyFC() });
      map.addLayer({
        id: 'stops-circle',
        type: 'circle',
        source: 'stops',
        paint: {
          'circle-radius': 7,
          'circle-color': ['case', ['get', 'selected'], '#e53935', '#1e88e5'],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff',
        },
      });
      map.addSource('shape-saved', { type: 'geojson', data: emptyFC() });
      map.addLayer({
        id: 'shape-saved-line',
        type: 'line',
        source: 'shape-saved',
        paint: { 'line-color': '#1e88e5', 'line-width': 4 },
      });

      map.addSource('routed-preview', { type: 'geojson', data: emptyFC() });
      map.addLayer({
        id: 'routed-preview-line',
        type: 'line',
        source: 'routed-preview',
        paint: { 'line-color': '#1b7f4a', 'line-width': 4 },
      });

      map.addSource('shape-draft', { type: 'geojson', data: emptyFC() });
      map.addLayer({
        id: 'shape-draft-line',
        type: 'line',
        source: 'shape-draft',
        paint: { 'line-color': '#e53935', 'line-width': 3, 'line-dasharray': [2, 2] },
      });
      map.addLayer({
        id: 'shape-draft-points',
        type: 'circle',
        source: 'shape-draft',
        filter: ['==', ['geometry-type'], 'Point'],
        paint: { 'circle-radius': 5, 'circle-color': '#e53935' },
      });

      map.on('click', (e) => {
        const features = map.queryRenderedFeatures(e.point, { layers: ['stops-circle'] });
        if (features.length > 0 && mapToolRef.current === 'add-pattern-stop') {
          const stopId = features[0].properties?.stopId;
          if (stopId) onStopClickRef.current(stopId);
          return;
        }
        if (
          mapToolRef.current === 'add-stop' ||
          mapToolRef.current === 'draw-shape' ||
          mapToolRef.current === 'add-pattern-stop'
        ) {
          onMapClickRef.current(e.lngLat.lat, e.lngLat.lng);
        }
      });

      const popup = new maplibregl.Popup({ closeButton: false, closeOnClick: false, offset: 10 });
      map.on('mouseenter', 'stops-circle', (e) => {
        map.getCanvas().style.cursor = 'pointer';
        const f = e.features?.[0];
        if (f && f.geometry.type === 'Point') {
          const coords = (f.geometry as any).coordinates.slice();
          popup.setLngLat(coords).setText(f.properties?.label || '').addTo(map);
        }
      });
      map.on('mouseleave', 'stops-circle', () => {
        map.getCanvas().style.cursor = '';
        popup.remove();
      });

      styleReadyRef.current = true;
    });

    mapRef.current = map;

    const resizeObserver = new ResizeObserver(() => map.resize());
    resizeObserver.observe(containerRef.current);
    requestAnimationFrame(() => map.resize());

    return () => {
      resizeObserver.disconnect();
      map.remove();
      mapRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const update = () => {
      const source = map.getSource('stops') as maplibregl.GeoJSONSource | undefined;
      if (!source) return;
      const selectedIds = new Set(patternStops.map((ps) => ps.stop.id));
      source.setData({
        type: 'FeatureCollection',
        features: stops.map((s) => ({
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [s.stopLon, s.stopLat] },
          properties: {
            stopId: s.id,
            label: s.stopName,
            selected: selectedIds.has(s.id),
          },
        })),
      });
    };
    if (styleReadyRef.current) update();
    else map.once('load', update);
  }, [stops, patternStops]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const update = () => {
      const source = map.getSource('shape-saved') as maplibregl.GeoJSONSource | undefined;
      if (!source || savedShapePoints.length < 2) {
        source?.setData(emptyFC());
        return;
      }
      source.setData({
        type: 'Feature',
        geometry: { type: 'LineString', coordinates: savedShapePoints.map((p) => [p.lon, p.lat]) },
        properties: {},
      });
    };
    if (styleReadyRef.current) update();
    else map.once('load', update);
  }, [savedShapePoints]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const update = () => {
      const source = map.getSource('routed-preview') as maplibregl.GeoJSONSource | undefined;
      if (!source || routedPreviewPoints.length < 2) {
        source?.setData(emptyFC());
        return;
      }
      source.setData({
        type: 'Feature',
        geometry: { type: 'LineString', coordinates: routedPreviewPoints.map((p) => [p.lon, p.lat]) },
        properties: {},
      });
    };
    if (styleReadyRef.current) update();
    else map.once('load', update);
  }, [routedPreviewPoints]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const update = () => {
      const source = map.getSource('shape-draft') as maplibregl.GeoJSONSource | undefined;
      if (!source) return;
      const features: any[] = draftShapePoints.map((p) => ({
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [p.lon, p.lat] },
        properties: {},
      }));
      if (draftShapePoints.length >= 2) {
        features.push({
          type: 'Feature',
          geometry: { type: 'LineString', coordinates: draftShapePoints.map((p) => [p.lon, p.lat]) },
          properties: {},
        });
      }
      source.setData({ type: 'FeatureCollection', features });
    };
    if (styleReadyRef.current) update();
    else map.once('load', update);
  }, [draftShapePoints]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !focusPoint) return;
    map.flyTo({ center: [focusPoint.lon, focusPoint.lat], zoom: 16 });
  }, [focusPoint]);

  return <div ref={containerRef} style={{ position: 'absolute', inset: 0 }} />;
}

function emptyFC(): any {
  return { type: 'FeatureCollection', features: [] };
}
