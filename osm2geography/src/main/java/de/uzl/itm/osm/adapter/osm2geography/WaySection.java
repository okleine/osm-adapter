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

import com.grum.geocalc.DegreeCoordinate;
import com.grum.geocalc.EarthCalc;
import com.grum.geocalc.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A {@link WaySection} is defined by an ordered collection of {@link com.grum.geocalc.Point}s. A section can be
 * considered the resulting multiline created by connecting the given points consecutively, i.e in the given order.
 *
 * @author Oliver Kleine
 */
public class WaySection {

    private static Logger log = LoggerFactory.getLogger(WaySection.class.getName());

    private String name;
    private boolean oneWay;
    private List<Point> points;

    /**
     * Creates a new instance of {@link WaySection}.
     *
     * @param points the {@link com.grum.geocalc.Point}s, i.e. this path passes
     * @param oneWay <code>true</code> if this path is a one-way path, <code>false</code> otherwise.
     */
    public WaySection(List<Point> points, String name, boolean oneWay){
        this.name = name == null ? "no-name" : name;
        this.oneWay = oneWay;
        this.points = points;
    }


    /**
     * Returns the name of this path
     * @return the name of this path
     */
    public String getName(){
        return this.name;
    }


    /**
     * Returns a {@link java.util.List} containing the points, i.e. the coordinates, this path passes
     *
     * <b>Note:</b> Changes to the returned list change the path!
     *
     * @return a {@link java.util.List} containing the points, i.e. the coordinates, this path passes
     */
    public List<Point> getPoints() {
        return points;
    }


    /**
     * Returns the first {@link com.grum.geocalc.Point} of this path
     *
     * <br>Note:</br>This is a shortcut for <code>this.getPoints().get(0)</code>
     *
     * @return the first {@link com.grum.geocalc.Point} of this path
     */
    public Point getBegin(){
        return this.getPoints().get(0);
    }

    /**
     * Returns the last {@link com.grum.geocalc.Point} of this path
     *
     * <br>Note:</br>This is a shortcut for <code>this.getPoints().get(this.getPoints().size() - 1)</code>
     *
     * @return the last {@link com.grum.geocalc.Point} of this path
     */
    public Point getEnd(){
        return this.getPoints().get(this.getPoints().size() - 1);
    }

    /**
     * Returns <code>true</code> if this way was defined to be a one-way path and <code>false</code> otherwise
     * @return <code>true</code> if this way was defined to be a one-way path and <code>false</code> otherwise
     */
    public boolean isOneWay() {
        return oneWay;
    }


    /**
     * Returns the length of this {@link WaySection} in meter
     * @return the length of this {@link WaySection} in meter
     */
    public double getLength(){
        double result = 0;

        Iterator<Point> pointIterator = this.points.iterator();
        Point segmentStart = pointIterator.next();
        while(pointIterator.hasNext()){
            Point segmentEnd = pointIterator.next();
            result += EarthCalc.getDistance(segmentStart, segmentEnd);
            segmentStart = segmentEnd;
        }

        return result;
    }


    /**
     * Returns a {@link java.util.List} with either 1 (if this is a one-way path) or 2 (if this is not a one-way path)
     * sub-lists. Each sublists contains the {@link com.grum.geocalc.Point}s of a multi-line that represents the
     * center of a lane. For 2 sub-lists the first sub-list refers to the left lane of the path whereas the second
     * sub-list refers to the right lane.
     *
     * Left and right refer to the direction of the path. The direction is given by the order of its points.
     *
     * @return A {@link java.util.List} with either 1 (if this is a one-way path) or 2 (if this is not a one-way path)
     * sub-lists.
     */
    public List<List<Point>> getLaneCenterLines(){
        List<List<Point>> result = new ArrayList<>();

        if(this.isOneWay()){
            result.add(this.getPoints());
            return result;
        }

        double distance = 1.5;

        List<Point> leftLane = new ArrayList<>();
        List<Point> rightLane = new ArrayList<>();

        for(int i = 0; i < this.getPoints().size() - 1; i++){
            Point segmentStart = this.getPoints().get(i);
            Point segmentEnd = this.getPoints().get(i+1);
            double segmentBearing = EarthCalc.getBearing(segmentStart, segmentEnd);

            leftLane.add(EarthCalc.pointRadialDistance(segmentStart, (segmentBearing + 270) % 360, distance));
            rightLane.add(EarthCalc.pointRadialDistance(segmentStart, (segmentBearing + 90) % 360, distance));

            leftLane.add(EarthCalc.pointRadialDistance(segmentEnd, (segmentBearing + 270) % 360, distance));
            rightLane.add(EarthCalc.pointRadialDistance(segmentEnd, (segmentBearing + 90) % 360, distance));

            if(i > 0){
                Point leftIntersection = getLineIntersection(leftLane.subList(leftLane.size() - 4, leftLane.size()));
                leftLane.remove(leftLane.size() - 3);
                leftLane.remove(leftLane.size() - 2);
                leftLane.add(leftLane.size() - 1, leftIntersection);

                Point rightIntersection = getLineIntersection(rightLane.subList(rightLane.size() - 4, rightLane.size()));
                rightLane.remove(rightLane.size() - 3);
                rightLane.remove(rightLane.size() - 2);
                rightLane.add(rightLane.size() - 1, rightIntersection);
            }
        }

        result.add(leftLane);
        result.add(rightLane);

        return result;
    }

    /**
     * Returns a {@link java.util.List} with either 1 (if this is a one-way path) or 2 (if this is not a one-way path)
     * sub-lists. Each sublists contains the corner {@link com.grum.geocalc.Point}s of a polygon. For 2 sub-lists the
     * first polygon refers to the left side of the path whereas the second polygon refers to the right side.
     *
     * Left and right refer to the direction of the path. The direction is given by the order of its points.
     *
     * @return A {@link java.util.List} with either 1 (if this is a one-way path) or 2 (if this is not a one-way path)
     * sub-lists.
     */
    public List<List<Point>> getLanePolygonCorners(boolean taper){
        List<List<Point>> result = new ArrayList<>();
        double width;
        if(taper){
            width = this.isOneWay() ? 0.8 : 1.6;
        }
        else{
            width = this.isOneWay() ? 2 : 4;
        }

        List<Point> leftPoints = new ArrayList<>();
        List<Point> rightPoints = new ArrayList<>();

        for(int i = 0; i < this.getPoints().size() - 1; i++){
            Point segmentStart = this.getPoints().get(i);
            Point segmentEnd = this.getPoints().get(i+1);
            double segmentBearing = EarthCalc.getBearing(segmentStart, segmentEnd);

            if(taper && (i == 0 || i == this.getPoints().size() - 2)){
                double tapering = Math.min(EarthCalc.getDistance(segmentStart, segmentEnd) / 2, 4);

                if(i == 0)
                    segmentStart = EarthCalc.pointRadialDistance(segmentStart, segmentBearing, tapering);

                if(i == this.getPoints().size() -2)
                    segmentEnd = EarthCalc.pointRadialDistance(segmentEnd, segmentBearing, (-1) * tapering);
            }

            leftPoints.add(EarthCalc.pointRadialDistance(segmentStart, (segmentBearing + 270) % 360, width));
            rightPoints.add(EarthCalc.pointRadialDistance(segmentStart, (segmentBearing + 90) % 360, width));

            leftPoints.add(EarthCalc.pointRadialDistance(segmentEnd, (segmentBearing + 270) % 360, width));
            rightPoints.add(EarthCalc.pointRadialDistance(segmentEnd, (segmentBearing + 90) % 360, width));

            if(i > 0){
                Point leftIntersection = getLineIntersection(leftPoints.subList(leftPoints.size() - 4, leftPoints.size()));
                leftPoints.remove(leftPoints.size() - 3);
                leftPoints.remove(leftPoints.size() - 2);
                leftPoints.add(leftPoints.size() - 1, leftIntersection);

                Point rightIntersection = getLineIntersection(rightPoints.subList(rightPoints.size() - 4, rightPoints.size()));
                rightPoints.remove(rightPoints.size() - 3);
                rightPoints.remove(rightPoints.size() - 2);
                rightPoints.add(rightPoints.size() - 1, rightIntersection);
            }
        }

        //Create only one polygon if it's a one-way path
        if(this.isOneWay()){
            List<Point> polygon = new ArrayList<>(rightPoints);

            if(taper){
                polygon.add(0, this.getBegin());
                polygon.add(this.getEnd());
            }

            ListIterator<Point> pointIterator = leftPoints.listIterator(leftPoints.size());
            while(pointIterator.hasPrevious()){
                polygon.add(pointIterator.previous());
            }

            if(!polygon.get(0).equals(polygon.get(polygon.size() - 1)))
                polygon.add(polygon.get(0));

            result.add(polygon);
        }

        //Create 2 polygons if it's not a one-way path (i.e. one polygon for each side of the path)
        else{
            List<Point> polygon1 = new ArrayList<Point>(this.getPoints());
            ListIterator<Point> pointIterator = leftPoints.listIterator(leftPoints.size());
            while(pointIterator.hasPrevious()){
                polygon1.add(pointIterator.previous());
            }

            //Close the shape...
            if(!polygon1.get(0).equals(polygon1.get(polygon1.size() - 1)))
                polygon1.add(polygon1.get(0));

            result.add(polygon1);

            List<Point> polygon2 = new ArrayList<Point>(this.getPoints());
            pointIterator = rightPoints.listIterator(rightPoints.size());
            while(pointIterator.hasPrevious()){
                polygon2.add(pointIterator.previous());
            }

            //Close the shape...
            if(!polygon2.get(0).equals(polygon2.get(polygon2.size() - 1)))
                polygon2.add(polygon2.get(0));

            result.add(polygon2);
        }

        return result;
    }


    private static Point getLineIntersection(List<Point> points){
        return getLineIntersection(points.get(0), points.get(1), points.get(2), points.get(3));    
    }


    private static Point getLineIntersection(Point point1, Point point2, Point point3, Point point4){

        if(EarthCalc.getDistance(point2, point3) < 0.5)
            return point2;

        double x1 = point1.getLatitude();
        double y1 = point1.getLongitude();
        double x2 = point2.getLatitude();
        double y2 = point2.getLongitude();

        double x3 = point3.getLatitude();
        double y3 = point3.getLongitude();
        double x4 = point4.getLatitude();
        double y4 = point4.getLongitude();

        double d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (d == 0)
            return null;

        double xi = ((x3 - x4) * (x1 * y2 - y1 * x2) - (x1 - x2) * (x3 * y4 - y3 * x4)) / d;
        double yi = ((y3 - y4) * (x1 * y2 - y1 * x2) - (y1 - y2) * (x3 * y4 - y3 * x4)) / d;

        return new Point(new DegreeCoordinate(xi), new DegreeCoordinate(yi));
    }
}
