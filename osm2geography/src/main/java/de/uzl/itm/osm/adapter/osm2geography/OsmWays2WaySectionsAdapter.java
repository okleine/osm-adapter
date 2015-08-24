/**
 * Copyright (c) 2015, Oliver Kleine, Institute of Telematics, University of Luebeck
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
package de.uzl.itm.osm.adapter.osm2geography;


import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.grum.geocalc.DegreeCoordinate;
import com.grum.geocalc.Point;
import de.uzl.itm.jaxb4osm.jaxb.NodeElement;
import de.uzl.itm.jaxb4osm.jaxb.OsmElement;
import de.uzl.itm.jaxb4osm.jaxb.WayElement;
import de.uzl.itm.jaxb4osm.tools.OsmUnmarshaller;
import de.uzl.itm.jaxb4osm.tools.WayElementFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * This is the "intermediate" Adapter to convert data from OSM files to instances classes from the geocalc
 * framework. This is useful to make calculations, such as lengths or distances.
 *
 * @author Oliver Kleine
 */
public class OsmWays2WaySectionsAdapter {

    public static Logger LOG = LoggerFactory.getLogger(OsmWays2WaySectionsAdapter.class.getName());

    public static final int COUNTRY_CODE = 0;
    public static final int POSTAL_CODE = 1;
    public static final int CITY = 2;
    public static final int STREET_NAME = 3;
    
    public static final String UNKNOWN = "unknown";

    private File osmFile;
    private final WayElementFilter filter;
    private final boolean splitWays;

    private HashBasedTable <Long, Integer, WaySection> waySections = null;
    private Map<Long, Map<Integer, String>> metadata = null;

    /**
     *
     * @param osmFile the OSM (XML) file to be unmarshalled
     * @param filter the {@link de.uzl.itm.jaxb4osm.tools.WayElementFilter} to filter e.g. certain ways
     * @param splitWays <code>true</code> if each {@link de.uzl.itm.jaxb4osm.jaxb.WayElement} is to be
     *                  split at crossings or <code>false</code> otherwise
     * @throws Exception
     */
    public OsmWays2WaySectionsAdapter(File osmFile, WayElementFilter filter, boolean splitWays) throws Exception {
        this.osmFile = osmFile;
        this.filter = filter;
        this.splitWays = splitWays;
    }

    /**
     * Returns a map containing an OSM-ID based key and a {@link WaySection} as
     * values. The key consists of the ID of the corresponding OSM Way and a (consecutive) number.
     *
     * @return A map containing an OSM-ID based key and a {@link WaySection} as
     * values. The key consists of the ID of the corresponding OSM Way and a (consecutive) number.
     */
    public Table<Long, Integer, WaySection> getWaySections(){
        return this.waySections;
    }

    public Map<Long, Map<Integer, String>> getMetadata() throws Exception {
        if(this.metadata == null){
            this.initialize();
        }

        return this.metadata;
    }

    /**
     * Creates {@link de.uzl.itm.osm.adapter.osm2geography.WaySection}s and metadata
     */
    public void initialize() throws Exception {
        createWaySectionsAndMetadata();
    }

    private void createWaySectionsAndMetadata() throws Exception{
        try{
            long start = System.currentTimeMillis();

            FileInputStream fileInputStream = new FileInputStream(osmFile);
            OsmElement osmElement = OsmUnmarshaller.unmarshal(fileInputStream, filter, splitWays);

            this.waySections = HashBasedTable.create();
            this.metadata = new HashMap<>();

            for(WayElement wayElement : osmElement.getWayElements()){
                String country = wayElement.getTagValue(WayElement.TAG_COUNTRY);
                String postalCode = wayElement.getTagValue(WayElement.TAG_POSTAL_CODE);
                String city = wayElement.getTagValue(WayElement.TAG_CITY);
                String streetName = wayElement.getTagValue(WayElement.TAG_NAME);

                List<Point> points = new ArrayList<>();
                int segmentID = 0;

                for(int i = 0; i < wayElement.getNdElements().size(); i++){
                    long nodeID = wayElement.getNdElements().get(i).getReference();
                    points.add(toPoint(osmElement.getNodeElement(nodeID)));

                    if((splitWays && osmElement.getReferencingWayIDs(nodeID).size() > 1) ||
                            nodeID == wayElement.getLastNdElement().getReference()){

                        if(points.size() > 1){
                            this.waySections.put(wayElement.getID(), ++segmentID,
                                    new WaySection(points, wayElement.getTagValue("name"), wayElement.isOneWay()));

                            Map<Integer, String> tmp = new HashMap<>();
                            tmp.put(COUNTRY_CODE, country == null ? UNKNOWN : country);
                            tmp.put(POSTAL_CODE, postalCode == null ? UNKNOWN : postalCode);
                            tmp.put(CITY, city == null ? UNKNOWN : city);
                            tmp.put(STREET_NAME, streetName == null ? UNKNOWN : streetName);

                            this.metadata.put(wayElement.getID(), tmp);
                        }

                        points = new ArrayList<>();
                        points.add(toPoint(osmElement.getNodeElement(nodeID)));
                    }
                }
            }

            long duration = System.currentTimeMillis() - start;

            int lanes = 0;
            for(WaySection waySection : this.waySections.values()){
                if(waySection.isOneWay()) {
                    lanes += 1;
                }
                else{
                    lanes += 2;
                }
            }

            LOG.info("Created {} ways with {} sections with {} lanes (duration: {} ms).",
                    new Object[]{this.metadata.size(), this.waySections.size(), lanes, duration});
        }
        catch(UnsupportedEncodingException ex){
            System.err.println("This should never happen!" + ex.getMessage());
        }
    }

    private static Point toPoint(NodeElement nodeElement){
        return new Point(
                new DegreeCoordinate(nodeElement.getLatitude()), new DegreeCoordinate(nodeElement.getLongitude())
        );
    }
}
