package org.renci.epi.geography;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.GeometryFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory; 
import org.apache.log4j.Logger;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.type.GeometryType;
import org.opengis.geometry.Geometry;
import org.opengis.geometry.primitive.Point;

public class GeographyServiceImpl implements GeographyService {

    private static Log logger = LogFactory.getLog (GeographyServiceImpl.class); 

    public void getPolygons (String fileName) {

	File file = new File (fileName);

	try {

	    Map connect = new HashMap ();
	    connect.put ("url", file.toURL ());

	    DataStore dataStore = DataStoreFinder.getDataStore (connect);
	    String[] typeNames = dataStore.getTypeNames ();
	    String typeName = typeNames [0];

	    System.out.println ("Reading content " + typeName);

	    FeatureSource featureSource = dataStore.getFeatureSource (typeName);
	    FeatureCollection collection = featureSource.getFeatures ();
	    FeatureIterator iterator = collection.features ();

	    int q = 0; 

	    try {
		while (iterator.hasNext ()) {
		    Feature feature = iterator.next ();
		    //Geometry sourceGeometry = feature.getDefaultGeometryProperty ();

		    GeometryAttribute geometryAttribute = feature.getDefaultGeometryProperty (); 
		    GeometryType geometryType = geometryAttribute.getType ();

		    logger.debug ("feature: " + geometryType.getName ());

		    double [][] points = this.decodeMultiPolygon (geometryAttribute);
		    StringBuffer buffer = new StringBuffer (points.length * 20);
		    buffer.append ("polygon->\n");
		    for (int c = 0; c < points.length; c++) {
			buffer.
			    append ("(").
			    append (points [c] [0]).
			    append (",").
			    append (points [c] [1]).
			    append (") ");

		    }
		    logger.debug (buffer.toString ());

		    q++;
		    if (q > 10) {
			System.exit (0);
		    }
		    
		}
	    } finally {
		iterator.close ();
	    }

	} catch (Throwable e) {
	    e.printStackTrace ();
	}
    }

    private Point [] decodeMultiPolygon (GeometryAttribute geometryAttribute) {

	List<Point> points = new ArrayList<double []> ();

	MultiPolygon multiPolygon = (MultiPolygon)geometryAttribute.getValue ();

	for (int c = 0; c < multiPolygon.getNumGeometries (); c++) {

	    Polygon polygon = (Polygon)multiPolygon.getGeometryN (c);
	    LinearRing linearRing = (LinearRing)polygon.getExteriorRing ();
	    Coordinate [] coordinates = linearRing.getCoordinates ();

	    // Skipping last coordinate since JTD defines a shell as a LineString that start with same first and last coordinate
	    for (int j = 0; j < coordinates.length-1; j++) {
		Point point = GeometryFactory.createPoint (coordinates [j]);
		points.add (point);
	    }
	}
	return (Point [])points.toArray (new Point [points.size ()]);
    }

    public void mapEventsToPolygons (BufferedReader 

}