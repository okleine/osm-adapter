package de.uzl.itm.osm.adapter.osm2virtualsensors;

import de.uzl.itm.jaxb4osm.tools.WayElementFilter;
import de.uzl.itm.osm.adapter.osm2geography.WaySection;
import de.uzl.itm.osm.adapter.osm2geography.OsmWays2WaySectionsAdapter;
import de.uzl.itm.ssp.jaxb4vs.jaxb.VirtualSensor;
import de.uzl.itm.ssp.jaxb4vs.jaxb.VirtualSensorList;
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

//    public static final String GRAPH_NAME_TEMPLATE = "http://example.org/virtual-sensors/instances#%s";
//
//    private static final StringBuilder STATEMENT_TEMPLATE = new StringBuilder(
//            "@prefix rs: <http://example.org/ways#> .\n" +
//                    "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" +
//                    "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" +
//                    "@prefix vs: <http://example.org/virtual-sensors/instances#> .\n" +
//                    "@prefix ssn: <http://purl.oclc.org/NET/ssnx/ssn#> .\n\n" +
//
//                    "<http://example.org/virtual-sensors#VirtualTrafficDensitySensor-%s>\n\t" +
//                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n\t\t" +
//                    "<http://example.org/virtual-sensors#VirtualTrafficDensitySensor>\n\n" +
//
//                    "vs:5837598-4-1-VirtualTrafficDensitySensor-Observation a ssn:Observation ;\n\t" +
//                    "ssn:featureOfInterest rs:%s ;\n\t" +
//                    "ssn:observedProperty vs:%s-Sensor-VirtualProperty ;\n\t" +
//                    "ssn:observedBy vs:%s-Sensor ;\n\t" +
//                    "ssn:observedResult vs:%s-VirtualTrafficDensitySensorOutput .\n\n" +
//
//                    "rs:%s a ssn:FeatureOfInterest ;\n\t" +
//                    "vs:virtualProperty vs:%s-Sensor-VirtualProperty .\n\n" +
//
//                    "vs:%s-%s-VirtualTrafficDensitySensorProperty a vs:VirtualProperty .\n\n" +
//
//                    "vs:%s-VirtualTrafficDensitySensorOutput a vs:VirtualSensorOutput ;\n\t" +
//                    "ssn:hasValue 0 ."
//    );


    private static final String VS_ONTOLOGY =
            "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" +
            "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" +
            "@prefix vs: <http://example.org/virtual-sensors#> .\n" +
            "@prefix ssn: <http://purl.oclc.org/NET/ssnx/ssn#> .\n\n" +

            "vs:VirtualSensor a rdfs:Class ;\n\t" +
                "rdfs:subClassOf ssn:Sensor .\n\n" +

            "vs:VirtualSensorOutput a rdfs:Class ;\n\t" +
                "rdfs:subClassOf ssn:SensorOutput .\n\n" +

            "vs:hasVirtualProperty a rdf:Property ;\n\t" +
                "rdfs:subPropertyOf ssn:hasProperty .\n\n" +

            "vs:VirtualProperty a rdfs:Class ;\n\t" +
                "rdfs:subClassOf ssn:Property .\n\n" +

            "vs:VirtualTrafficDensitySensor a rdfs:Class ;\n\t" +
                "rdfs:subClassOf vs:VirtualSensor .";


    public void writeOntologyFile(String directory) throws Exception{
        assureDirectoryExists(directory);
        File file = assureFileExists(directory, "vs-ontology.ttl");

        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(VS_ONTOLOGY);
        writer.flush();
        writer.close();
    }


    private static final String QUERY_TEMPLATE =
            "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n" +
            "PREFIX geof: <http://www.opengis.net/def/function/geosparql/>\n" +
            "PREFIX osm: <http://example.org/osm#>\n" +
            "PREFIX veh: <http://example.org/vehicles#>\n\n" +

            "SELECT ((COUNT(?val) / (?length / 40)) AS ?aggVal) WHERE {\n\t" +
                "?val a veh:Vehicle .\n\t" +
                "?val veh:hasLocation ?loc .\n\t" +
                "?loc geo:asWKT ?point .\n\t" +
                "osm:WaySectionLane-%s osm:boundary ?bound .\n\t" +
                "?bound geo:asWKT ?polygon . \n\t" +
                "osm:WaySectionLane-%s osm:hasLengthInMeter ?length .\n\t" +
                "FILTER (geof:sfWithin(?point, ?polygon))\n" +
            "} GROUP BY ?length";

//    private static final String VIRTUAL_SENSOR_NAMESPACE =
//            "http://example.org/virtual-sensors#";

    private static final String VIRTUAL_TRAFFIC_DENSITY_SENSOR =
            "VirtualTrafficDensitySensor";

    private static final String VIRTUAL_TRAFFIC_DENSITY_SENSOR_TYPE =
            VIRTUAL_TRAFFIC_DENSITY_SENSOR;

    private static final String SENSOR_NAME_TEMPLATE =
            VIRTUAL_TRAFFIC_DENSITY_SENSOR_TYPE + "-%s";

    private static final String LANE_SECTION_NAME_TEMPLATE =
            "http://example.org/osm#WaySectionLane-%s";

    private VirtualSensorList virtualSensors;

    public OsmWays2VirtualTrafficDensitySensorsAdapter(File osmFile, WayElementFilter filter, boolean splitWays)
            throws Exception {
        super(osmFile, filter, splitWays);
    }

    public void initialize() throws Exception{
        //Create Way Sections
        super.initialize();

        long start = System.currentTimeMillis();

        //Create Virtual Sensors
        this.virtualSensors = new VirtualSensorList();
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


    private static VirtualSensor createVirtualSensor(String laneSectionID) {

        VirtualSensor virtualSensor = new VirtualSensor();
        virtualSensor.setName(String.format(SENSOR_NAME_TEMPLATE, laneSectionID));
        virtualSensor.setType(VIRTUAL_TRAFFIC_DENSITY_SENSOR_TYPE);
        virtualSensor.setFoi(String.format(LANE_SECTION_NAME_TEMPLATE, laneSectionID));

        virtualSensor.setQuery(String.format(QUERY_TEMPLATE, laneSectionID, laneSectionID));

        return virtualSensor;
    }


    public VirtualSensorList getVirtualSensors(){
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

        String directory = "/home/olli/Dokumente/Dissertation/Experimente/OSM/HL2015";

        File osmFile = new File(directory, "map.osm");
        WayElementFilter wayFilter = WayElementFilter.STREETS;
        OsmWays2VirtualTrafficDensitySensorsAdapter adapter = new OsmWays2VirtualTrafficDensitySensorsAdapter(
                osmFile, wayFilter, true
        );

        adapter.initialize();

        adapter.writeVirtualTrafficDensitySensorsXMLFile(directory);
        adapter.writeOntologyFile(directory);
    }

}
