/**
 * Copyright (c) 2014, Oliver Kleine, Institute of Telematics, University of Luebeck
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source messageCode must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.uzl.itm.osm.adapter.osm2turtle;

import com.google.common.collect.HashBasedTable;
import com.grum.geocalc.Point;
import de.uzl.itm.jaxb4osm.tools.WayElementFilter;
import de.uzl.itm.osm.adapter.osm2geography.WaySection;
import de.uzl.itm.osm.adapter.osm2geography.OsmWays2WaySectionsAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * This adapter is to create RDF representations of OSM ways in the form of one turtle file per way section.
 *
 * @author Oliver Kleine
 */
public class OsmWays2TurtleAdapter extends OsmWays2WaySectionsAdapter {

    private static Logger LOG = LoggerFactory.getLogger(OsmWays2TurtleAdapter.class.getName());

    private static DecimalFormatSymbols DFS = DecimalFormatSymbols.getInstance();
    static{
        DFS.setDecimalSeparator('.');
    }
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.000", DFS);

    public static final String WAY_PREFIX =
            "@prefix geo: <http://www.opengis.net/ont/geosparql#> .\n" +
            "@prefix osm: <http://example.org/osm#> .\n" +
            "@prefix sf: <http://www.opengis.net/ont/sf#> .\n" +
            "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .";

    public static final String WAY_TEMPLATE =
            "osm:Way-%d a osm:Way ;\n\t" +
                "osm:hasName \"%s\"^^xsd:string ;\n\t" +
                "osm:inAreaWithPostalCode \"%s\"^^xsd:string ;\n\t" +
                "osm:inCity \"%s\"^^xsd:string ;\n\t" +
                "osm:inCountry \"%s\"^^xsd:string";


    private static String createWayInstance(long wayID, Map<Integer, String> metadata) throws Exception {
        return String.format(WAY_TEMPLATE, wayID, metadata.get(STREET_NAME), metadata.get(POSTAL_CODE),
            metadata.get(CITY), metadata.get(COUNTRY_CODE));
    }

    public static final String WAY_HAS_SECTION_TEMPLATE =
            "osm:hasPart osm:WaySection-%s";

    private static String createWayHasSection(long wayID, int sectionID){
        return " ;\n\t" + String.format(WAY_HAS_SECTION_TEMPLATE, wayID + "-" + sectionID)  ;
    }

    public static final String WAY_SECTION_TEMPLATE =
            "osm:WaySection-%s a osm:WaySection ;\n\t" +
                "osm:hasLengthInMeter \"%s\"^^xsd:double";

    private static String createSectionInstance(long wayID, int sectionID, double length){
        return String.format(WAY_SECTION_TEMPLATE, wayID + "-" + sectionID, DECIMAL_FORMAT.format(length));
    }

    public static final String SECTION_HAS_LANE_TEMPLATE =
            "osm:hasWaySectionLane osm:WaySectionLane-%s";

    private static String createSectionHasLanes(long wayID, int sectionID, int noOfLanes){
        String result = "";
        for(int i = 0; i < noOfLanes; i++){
            result += " ;\n\t" + String.format(SECTION_HAS_LANE_TEMPLATE, wayID + "-" + sectionID + "-" + (i+1));
        }
        return result;
    }

    public static final String SECTION_LANE_INSTANCE_TEMPLATE =
            "osm:WaySectionLane-%s a osm:WaySectionLane ;\n\t" +
                "osm:hasLengthInMeter \"%s\"^^xsd:double ; \n\t" +
                "osm:boundary _:boundary%s ;\n\t" +
                "osm:centerLine _:centerline%s .\n\n" +

            "_:boundary%s a sf:Polygon ;\n\t" +
                "geo:asWKT \"<http://www.opengis.net/def/crs/OGC/1.3/CRS84>Polygon((%s))\"^^geo:wktLiteral .\n\n" +

            "_:centerline%s a sf:LineString ;\n\t" +
                    "geo:asWKT \"<http://www.opengis.net/def/crs/OGC/1.3/CRS84>LineString(%s)\"^^geo:wktLiteral .";


    private static String createSectionLaneInstances(long wayID, int sectionID, double length,
            List<List<Point>> rawBoundaries, List<List<Point>> centerLines){

        if(rawBoundaries.size() != centerLines.size())
            throw new IllegalArgumentException("Boundary lists do not have the same size!");

        String result = "";

        for(int i = 0; i < rawBoundaries.size(); i++){
            String laneID = wayID + "-" + sectionID + "-" + (i+1);
            result += "\n\n" + createComment("Section Lane " + laneID);
            result += "\n\n" + String.format(SECTION_LANE_INSTANCE_TEMPLATE, laneID, DECIMAL_FORMAT.format(length),
                laneID, laneID, laneID, createCoordinatesList(rawBoundaries.get(i)) , laneID,
                    createCoordinatesList(centerLines.get(i)));
        }

        return result;
    }

    private static String createCoordinatesList(List<Point> points){
        StringBuilder polygon= new StringBuilder();
        Iterator<Point> pointIterator = points.iterator();
        while(pointIterator.hasNext()){
            Point point = pointIterator.next();
            polygon.append(point.getLongitude()).append(" ").append(point.getLatitude());

            if(pointIterator.hasNext()){
                polygon.append(", ");
            }
        }
        return polygon.toString();
    }


    private static String createComment(String comment){
        return "##################################################\n" +
                "# " + comment + "\n" +
                "##################################################";
    }

    public OsmWays2TurtleAdapter(File osmFile, WayElementFilter filter) throws Exception {
        super(osmFile, filter, true);
    }


    @Override
    public void initialize() throws Exception {
        super.initialize();
    }


    public void writeTurtleWayFiles(String directory) throws Exception {
        assureDirectoryExists(new File(directory));

        long start = System.currentTimeMillis();

        //serialize files
        for(Long wayID : this.getWaySections().rowKeySet()){
            File wayFile = new File(directory + "/way-" + wayID + ".ttl");
            assureFileExists(wayFile);

            String ttlResult = WAY_PREFIX + "\n\n" + createComment("Way " + wayID) + "\n\n";
            ttlResult += createWayInstance(wayID, this.getMetadata().get(wayID));

            HashBasedTable<String, String, String> ttlSections = HashBasedTable.create();

            for(Integer sectionID : this.getWaySections().row(wayID).keySet()){

                ttlResult += createWayHasSection(wayID, sectionID);


                WaySection waySection = this.getWaySections().get(wayID, sectionID);
                String ttlSection = createSectionInstance(wayID, sectionID, waySection.getLength());
                List<List<Point>> rawBoundaries = waySection.getLanePolygonCorners(false);
                //List<List<Point>> tapBoundaries = waySection.getLanePolygonCorners(true);
                List<List<Point>> laneCoordinates = waySection.getLaneCenterLines();

                ttlSection += createSectionHasLanes(wayID, sectionID, rawBoundaries.size());
                ttlSection += " .";

                String ttlLanes = createSectionLaneInstances(wayID, sectionID, waySection.getLength(),
                    rawBoundaries, laneCoordinates);

                ttlSections.put(wayID + "-" + sectionID, ttlSection, ttlLanes);
            }

            ttlResult += " . \n\n";

            for(String section : ttlSections.rowKeySet()){
                ttlResult += createComment("Way Section " + section);

                for(String ttlSection : ttlSections.row(section).keySet()){
                    ttlResult += "\n\n" + ttlSection + ttlSections.get(section, ttlSection) + "\n\n";
                }
            }

            BufferedWriter writer = new BufferedWriter(new FileWriter(wayFile));
            writer.write(ttlResult);
            writer.flush();
            writer.close();
        }

        long end = System.currentTimeMillis();
        LOG.info("{} files written to directory {} (duration: {} ms)", new Object[]{
            this.getWaySections().rowKeySet().size(), directory, end-start});
    }

    private void assureDirectoryExists(File directory){

        if(!directory.exists() && !directory.mkdirs()){
            String errorMessage = "Could not create directory \"" + directory.getAbsolutePath() + "\"!";
            LOG.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        if(!directory.isDirectory()){
            String errorMessage = "Given path \"" + directory + "\" is no directory!";
            LOG.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
    }


    private void assureFileExists(File file) throws Exception{

        if(file.exists() && !file.delete()){
            String errorMessage = "Could not delete existing file \"" + file.getAbsolutePath() + "\"!";
            LOG.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        if(!file.createNewFile()){
            String errorMessage = "Could not create file \"" + file.getAbsolutePath() + "\"!";
            LOG.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
    }


    public static void main(String[] args) throws Exception{

        String directory = "/home/olli/Dokumente/Dissertation/Experimente/OSM/HL";
        File osmFile = new File(directory, "map.osm");

        WayElementFilter wayFilter = WayElementFilter.STREETS;

        OsmWays2TurtleAdapter adapter = new OsmWays2TurtleAdapter(osmFile, wayFilter);
        adapter.initialize();
        adapter.writeTurtleWayFiles(directory + "/ttl");
    }
}
