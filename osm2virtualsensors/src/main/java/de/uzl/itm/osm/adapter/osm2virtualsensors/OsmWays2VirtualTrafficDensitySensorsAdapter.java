package de.uzl.itm.osm.adapter.osm2virtualsensors;

import de.uzl.itm.jaxb4osm.tools.WayElementFilter;
import de.uzl.itm.osm.adapter.osm2geography.WaySection;
import de.uzl.itm.osm.adapter.osm2geography.OsmWays2WaySectionsAdapter;
import de.uzl.itm.ssp.jaxb4vs.jaxb.*;
import de.uzl.itm.ssp.jaxb4vs.tools.VirtualSensorsMarshaller;
import org.apache.log4j.xml.DOMConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.net.URL;
import java.util.Map;

/**
 * Created by olli on 11.07.14.
 */
public class OsmWays2VirtualTrafficDensitySensorsAdapter extends OsmWays2WaySectionsAdapter {

    private static Logger LOG = LoggerFactory.getLogger(
            OsmWays2VirtualTrafficDensitySensorsAdapter.class.getName()
    );



    private static final String VS_ONTOLOGY =
            "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" +
            "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" +
            "@prefix vs: <http://example.org/vs#> .\n" +
            "@prefix ssn: <http://purl.oclc.org/NET/ssnx/ssn#> .\n\n" +

            "vs:VirtualTrafficDensitySensor a rdfs:Class ;\n\t" +
                "rdfs:subClassOf ssn:Sensor .\n\n" +

            "vs:trafficDensity a rdfs:Class ;\n\t" +
                "rdfs:subClassOf ssn:Property .";



    public static void writeOntologyFile(String directory) throws Exception{
        assureDirectoryExists(directory);
        File file = assureFileExists(directory, "vs-ontology.ttl");

        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(VS_ONTOLOGY);
        writer.flush();
        writer.close();
    }
    

    private static final String QUERY_TEMPLATE =
        "PREFIX veh: <http://example.org/vehicles#>\n" +
        "PREFIX osm: <http://example.org/osm#>\n" +
        "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n" +
        "PREFIX geof: <http://www.opengis.net/def/function/geosparql/>\n" +
        "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n\n" +

        "SELECT (xsd:string(IF(COUNT(?veh) = 0, \"low\", IF(COUNT(?veh) / (?length / 40) < 1, \"low\",\n\t" +
            "IF(COUNT(?veh) / (?length / 40) > 2, \"high\", \"medium\")))) AS ?val) WHERE {\n\t" +
            "  ?veh a veh:Vehicle .\n\t" +
            "  ?veh veh:hasLocation ?loc .\n\t" +
            "  ?loc geo:asWKT ?point .\n\t" +
            "  osm:WaySectionLane-%s osm:boundary ?bound .\n\t" +
            "  osm:WaySectionLane-%s osm:hasLengthInMeter ?length .\n\t" +
            "  ?bound geo:asWKT ?polygon\n\t" +
            "  FILTER (geof:sfWithin(?point, ?polygon))\n" +
        "} GROUP BY ?length";

//    private static final String QUERY_TEMPLATE =
//            "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n" +
//            "PREFIX geof: <http://www.opengis.net/def/function/geosparql/>\n" +
//            "PREFIX osm: <http://example.org/osm#>\n" +
//            "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
//            "PREFIX veh: <http://example.org/vehicles#>\n\n" +
//
//            "SELECT (xsd:int((COUNT(?veh) / (?length / 40))) AS ?val) WHERE {\n\t" +
//                "?veh a veh:Vehicle .\n\t" +
//                "?veh veh:hasLocation ?loc .\n\t" +
//                "?loc geo:asWKT ?point .\n\t" +
//                "osm:WaySectionLane-%s osm:boundary ?bound .\n\t" +
//                "?bound geo:asWKT ?polygon . \n\t" +
//                "osm:WaySectionLane-%s osm:hasLengthInMeter ?length .\n\t" +
//                "FILTER (geof:sfWithin(?point, ?polygon))\n" +
//            "} GROUP BY ?length";

//    private static final String VIRTUAL_SENSOR_NAMESPACE =
//            "http://example.org/virtual-sensors#";

    private static final String VIRTUAL_TRAFFIC_DENSITY_SENSOR =
            "VirtualTrafficDensitySensor";

    private static final String VIRTUAL_TRAFFIC_DENSITY_SENSOR_TYPE =
            "http://example.org/vs#" + VIRTUAL_TRAFFIC_DENSITY_SENSOR;

    private static final String SENSOR_NAME_TEMPLATE =
            VIRTUAL_TRAFFIC_DENSITY_SENSOR + "-%s";

    private static final String LANE_SECTION_NAME_TEMPLATE =
            "http://example.org/osm#WaySectionLane-%s";

    private JAXBVirtualSensorsList virtualSensors;

    public OsmWays2VirtualTrafficDensitySensorsAdapter(File osmFile, WayElementFilter filter, boolean splitWays)
            throws Exception {
        super(osmFile, filter, splitWays);
    }

    public void initialize() throws Exception{
        //Create Way Sections
        super.initialize();

        long start = System.currentTimeMillis();

        //Create Virtual Sensors
        this.virtualSensors = new JAXBVirtualSensorsList();
        for(Long wayID : this.getWaySections().rowKeySet()){
            for(Map.Entry<Integer, WaySection> entry : this.getWaySections().row(wayID).entrySet()){
                String sectionID = wayID + "-" + entry.getKey();

                //add virtual sensor (left lane or one way)
                virtualSensors.getVirtualSensors().add(createVirtualSensor(sectionID + "-1"));

                //add virtual sensor for right lane (if existing)
                if(!entry.getValue().isOneWay()){
                    virtualSensors.getVirtualSensors().add(createVirtualSensor(sectionID + "-2"));
                }
            }
        }

        long duration = System.currentTimeMillis() - start;

        LOG.info("Created {} virtual sensors (duration: {} ms).", virtualSensors.getVirtualSensors().size(), duration);
    }


    private static JAXBVirtualSensor createVirtualSensor(String laneSectionID) {

        JAXBVirtualSensor virtualSensor = new JAXBVirtualSensor();
        virtualSensor.setSensorName(String.format(SENSOR_NAME_TEMPLATE, laneSectionID));
        virtualSensor.setSensorType(VIRTUAL_TRAFFIC_DENSITY_SENSOR_TYPE);
        virtualSensor.setFeatureOfInterest(String.format(LANE_SECTION_NAME_TEMPLATE, laneSectionID));
        virtualSensor.setObservedProperty("http://example.org/osm#trafficDensity");

        virtualSensor.setSparqlQuery(String.format(QUERY_TEMPLATE, laneSectionID, laneSectionID));

        return virtualSensor;
    }


    public JAXBVirtualSensorsList getVirtualSensors(){
        return this.virtualSensors;
    }

    private static void assureDirectoryExists(String directory){
        File dir = new File(directory);
        if(!dir.exists() && !dir.mkdirs()){
            throw new IllegalArgumentException("Directory \"" + directory + "\" could not be created!");
        }
    }

    private static File assureFileExists(String directory, String fileName) throws Exception{
        File result = new File(directory, fileName);
        if(result.exists() && !result.delete()){
            String message = "Existing file \"" + result.getAbsolutePath() + "\" could not be deleted!";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        if(!result.createNewFile()){
            String message = "File \"" + result.getAbsolutePath() + "\" could not be deleted!";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        return result;
    }

    public void writeVirtualTrafficDensitySensorsXMLFile(String directory) throws Exception {
        assureDirectoryExists(directory);
        File file = assureFileExists(directory, "virtual-traffic-density-sensors.xml");
        VirtualSensorsMarshaller.marshal(this.getVirtualSensors(), new FileOutputStream(file));
    }

    public static void configureDefaultLogging() throws Exception{
        System.out.println("Use default logging configuration, i.e. INFO level...\n");
        URL url = OsmWays2VirtualTrafficDensitySensorsAdapter.class.getClassLoader().getResource("log4j.xml");
        System.out.println("Use config file " + url);
        DOMConfigurator.configure(url);
    }


    public static void main(String[] args) throws Exception {

        configureDefaultLogging();

        String directory = "/home/olli/Dokumente/Dissertation/Experimente/OSM/HL";

        File osmFile = new File(directory, "map.osm");
        WayElementFilter wayFilter = WayElementFilter.HIGHWAYS;
        OsmWays2VirtualTrafficDensitySensorsAdapter adapter = new OsmWays2VirtualTrafficDensitySensorsAdapter(
                osmFile, wayFilter, true
        );

        adapter.initialize();

        adapter.writeVirtualTrafficDensitySensorsXMLFile(directory);
        adapter.writeOntologyFile(directory);
    }

}
