package mx.gtfsplatform.gtfs.kml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parser mínimo de KML (sección "importar paradas/rutas desde KML"): solo extrae lo que
 * necesitamos — Placemark > Point > coordinates (paradas) y Placemark > LineString >
 * coordinates, incluida dentro de MultiGeometry (rutas). No depende de ninguna librería
 * externa de KML; el formato de <coordinates> (lon,lat[,alt] separados por espacios) es
 * simple de leer directo con DOM. Sin namespace-awareness a propósito: casi ningún KML
 * real (Google Earth, Google My Maps, QGIS) usa prefijo de namespace en sus tags.
 */
public final class KmlParser {

    private KmlParser() {
    }

    public record KmlPoint(String name, double lat, double lon) {
    }

    public record KmlLine(String name, double[] lats, double[] lons) {
    }

    public static List<KmlPoint> parsePoints(InputStream in) throws Exception {
        Document doc = parseDocument(in);
        List<KmlPoint> points = new ArrayList<>();
        NodeList placemarks = doc.getElementsByTagName("Placemark");
        for (int i = 0; i < placemarks.getLength(); i++) {
            Element placemark = (Element) placemarks.item(i);
            Element pointEl = firstDescendantByTag(placemark, "Point");
            if (pointEl == null) {
                continue;
            }
            Element coordsEl = firstDescendantByTag(pointEl, "coordinates");
            if (coordsEl == null) {
                continue;
            }
            double[] lonLat = parseFirstCoordinate(coordsEl.getTextContent());
            if (lonLat == null) {
                continue;
            }
            points.add(new KmlPoint(textOfFirstDescendant(placemark, "name"), lonLat[1], lonLat[0]));
        }
        return points;
    }

    public static List<KmlLine> parseLines(InputStream in) throws Exception {
        Document doc = parseDocument(in);
        List<KmlLine> lines = new ArrayList<>();
        NodeList placemarks = doc.getElementsByTagName("Placemark");
        for (int i = 0; i < placemarks.getLength(); i++) {
            Element placemark = (Element) placemarks.item(i);
            String name = textOfFirstDescendant(placemark, "name");
            NodeList lineStrings = placemark.getElementsByTagName("LineString");
            for (int j = 0; j < lineStrings.getLength(); j++) {
                Element lineEl = (Element) lineStrings.item(j);
                Element coordsEl = firstDescendantByTag(lineEl, "coordinates");
                if (coordsEl == null) {
                    continue;
                }
                List<double[]> coords = parseCoordinateList(coordsEl.getTextContent());
                if (coords.size() < 2) {
                    continue;
                }
                double[] lats = new double[coords.size()];
                double[] lons = new double[coords.size()];
                for (int k = 0; k < coords.size(); k++) {
                    lons[k] = coords.get(k)[0];
                    lats[k] = coords.get(k)[1];
                }
                lines.add(new KmlLine(name, lats, lons));
            }
        }
        return lines;
    }

    private static Document parseDocument(InputStream in) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        // Un KML lo sube el usuario — mismas protecciones anti-XXE que cualquier XML no
        // confiable: sin DOCTYPE, sin resolver entidades externas.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(in);
    }

    private static Element firstDescendantByTag(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static String textOfFirstDescendant(Element parent, String tag) {
        Element el = firstDescendantByTag(parent, tag);
        return el == null ? null : el.getTextContent().trim();
    }

    private static double[] parseFirstCoordinate(String raw) {
        List<double[]> list = parseCoordinateList(raw);
        return list.isEmpty() ? null : list.get(0);
    }

    private static List<double[]> parseCoordinateList(String raw) {
        List<double[]> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String tuple : raw.trim().split("\\s+")) {
            if (tuple.isBlank()) {
                continue;
            }
            String[] parts = tuple.split(",");
            if (parts.length < 2) {
                continue;
            }
            try {
                double lon = Double.parseDouble(parts[0]);
                double lat = Double.parseDouble(parts[1]);
                result.add(new double[] {lon, lat});
            } catch (NumberFormatException ignored) {
                // Tupla corrupta: se ignora ese punto en vez de tirar todo el import.
            }
        }
        return result;
    }
}
