package org.renci.epi.population.insurance;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory; 
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Insurance status table.
 * 
 * Import and translate a probability map for estimating insurance status for a population.
 *
 */
public class InsuranceStatusTable {

    private String [] _header = null;
    private HashMap<String, double []> _table = new HashMap<String, double[]> ();
    private static Log logger = LogFactory.getLog (InsuranceStatusTable.class); 
    
    /**
     * Construct the table, loading the basic mapping of categories to probabiliites.
     */
    public InsuranceStatusTable () {
	InputStream stream = InsuranceStatusTable.class.getResourceAsStream ("PopulationInsuranceStatusTable.csv");
	BufferedReader reader = new BufferedReader (new InputStreamReader (stream));
	try {
	    char inputSeparator = ',';
	    CSVReader csvReader = new CSVReader (reader, inputSeparator);
	    initialize (csvReader);
	} catch (IOException e) {
	} finally {
	    IOUtils.closeQuietly (reader);
	}
    }

    /**
     * Initialize the model.
     */
    private void initialize (CSVReader reader) throws IOException {
	for (String [] line = reader.readNext (); line != null; line = reader.readNext ()) {
	    if (_header == null) {
		this.logger.info ("setting header " + StringUtils.join (line, ","));
		_header = line;
	    } else {
		//this.logger.info ("adding line " + line.length + " " + StringUtils.join (line, ","));
		
		List<String> keys = new ArrayList<String> (5);
		for (int c = 0; c < line.length; c++) {
		    String columnName = _header [c];
		    String text = line [c];		    
		    if (columnName.endsWith ("_CAT")) {
			//logger.info ("  --column: " + columnName);
			keys.add (text);
		    }
		}
		String key = StringUtils.join (keys, ".");
		//logger.info ("--key: " + key);

		try {
		    List<String> probabilityMask = new ArrayList<String> (10);
		    for (int c = 0; c < line.length; c++) {
			String columnName = _header [c];
			String text = line [c];
			if (columnName.startsWith ("CP")) {
			    probabilityMask.add (text);
			}
		    }
		    
		    String [] maskText = 
			(String [])probabilityMask.toArray (new String [probabilityMask.size ()]);

		    double [] mask = new double [maskText.length];
		    for (int c = 0; c < maskText.length; c++) {
			String text = maskText [c];
			if (".".equals (text)) {
			    text = "0.0";
			}
			double value = Double.parseDouble (text);
			mask [c] = value;
		    }
		    logger.debug ("--key: " + key + " => " + StringUtils.join (probabilityMask, ","));

		    _table.put (key, mask);
		} catch (NumberFormatException e) {
		    e.printStackTrace ();
		}
	    }
	}
    }

    /**
     * Get the category key into the table for a person.
     * Construct a 5-tuple based on categories.
     */
    private String getPersonKey (Person person) {
	StringBuffer buffer = new StringBuffer (9);
	buffer.
	    append (person.getAgeCat ()).
	    append ('.').
	    append (person.getHouseholdIncomeCat ()).
	    append ('.').
	    append (person.getHouseholdSizeCat ()).
	    append ('.').
	    append (person.getRaceCat ()).
	    append ('.').
	    append (person.getSexCat ());
	return buffer.toString ();
    }

    /**
     * Get the probability string.
     */
    private String getProbString (double [] mask) {
	StringBuffer buffer = new StringBuffer (100);
	for (int c = 0; c < mask.length; c++) {
	    buffer.
		append (mask [c]);
	    if (c < mask.length - 1) {
		buffer.append (',');
	    }
	}
	return buffer.toString ();
    }

    /**
     * Get the insurance status for a person.
     */
    public InsuranceStatus getInsuranceStatus (Person person) {	
	InsuranceStatus result = null;

	String key = this.getPersonKey (person);
	double random = person.getRandom ();

	if (key != null) {
	    logger.info ("-lookup(key): " + key);
	    double [] cumulativeProbabilities = _table.get (key);
	    if (cumulativeProbabilities != null) {
		logger.info ("-lookup(probabilities): " + this.getProbString (cumulativeProbabilities));
		for (int c = 0; c < cumulativeProbabilities.length; c++) {
		    double value = cumulativeProbabilities [c];

		    double prevValue = c > 0 ? 
			cumulativeProbabilities [c - 1] :
			0;
			
		    logger.info ("--lookup (random: " + random + ", value: " + value + ", prev: " + prevValue + ")");

		    if (random > prevValue && random < value) {

			InsuranceStatus [] values = InsuranceStatus.values();
			if (c < values.length) {
			    result = values[c];
			    break;
			} else {
			    logger.error ("person maps to category: " + c + " which is not a valid category.");
			}
		    }
		}
	    } else {
		logger.error ("unable to get probability mask for person with key: " + key);
	    }
	} else {
	    logger.error ("unable to get key for person: " + person);
	}
	return result;
    }

    /**

a. If AGE_CAT is 1 or 2, then health insurance type is defined as follows:
  1) No insurance 

  2) Insurance through a current or former employer or union only 
  3) Insurance purchased directly from an insurance company only 

  4) Medicaid (or Medical Assistance or any kind of government-assistance plan Jillian Brown – July 23, 2012
     for those with low incomes or disabilities) only 
  5) Medicare only / Medicaid and Medicare only/and having any other insurance  
     type(s) / Medicare and having any other insurance type(s), but not Medicaid 
  6) Medicaid and having any other insurance type(s), but not Medicare 
  7) Other public insurance (any combination or sole used of TRICARE or other 
     military health care, VA, Indian Health Services), but not Medicaid, 
     Medicare, insurance through a current or former employer or union, nor 
     insurance purchased directly from an insurance company 

  8) Other (any combination of insurance combinations not listed previously, but 
     not Medicaid nor Medicare) 

b. If AGE_CAT is 3 or 4 or 5, then health insurance type is defined as follows:
  1)   No insurance  

  2)   Insurance through a current or former employer or union only 
  3)   Insurance purchased directly from an insurance company only 

  4)   Medicaid (or Medical Assistance or any kind of government-assistance plan 
       for those with low incomes or disabilities) only 
  5)   Medicare only 
  6)   Medicaid and Medicare only/and having any other insurance type(s) 
  7)   Medicare and having any other insurance type(s), but not Medicaid 
  8)   Medicaid and having any other insurance type(s), but not Medicare 
  9)   Other public insurance (any combination or sole used of TRICARE or other 
       military health care, VA, Indian Health Services), but not Medicaid, 
       Medicare, insurance through a current or former employer or union, nor 
       insurance purchased directly from an insurance company 
  10) Other (any combination of insurance combinations not listed previously, but 
       not Medicaid nor Medicare) 
c. If AGE_CAT is 6, then health insurance type is defined as follows:
  1)   No insurance  

  2)   Insurance through a current or former employer or union only / Insurance 
       purchased directly from an insurance company only / Other public insurance 
       (any combination or sole used of TRICARE or other military health care, VA, 
       Indian Health Services), but not Medicaid, Medicare, insurance through a 
       current or former employer or union, nor insurance purchased directly from an 
       insurance company / Other (any combination of insurance combinations not 
       listed previously, but not Medicaid nor Medicare) 

  3)   Medicare only 
  4)   Medicaid and Medicare only/and having any other insurance type(s) 
  5)   Medicare and having any other insurance type(s), but not Medicaid 

*/
    public boolean hasNoInsurance (Person person, InsuranceStatus status) {
	return status == InsuranceStatus.CAT_ONE;
    }

    /**
     *
     * 	    
     *  - Under 2.a) categories 2 and 3 correspond to private insurance
     *  - Under 2.b) categories correspond to private insurance
     *  - Under 2.c) ... I can't tell. Category 2 corresponds to private insurance?
     * 	    
     */
    public boolean hasPrivateInsurance0 (Person person, InsuranceStatus status) {
	short ageCat = person.getAgeCat ();
	return
	    (
	     (ageCat == 1 || ageCat == 2) &&
	     (InsuranceStatus.CAT_TWO.equals (status) ||
	      InsuranceStatus.CAT_THREE.equals (status))
	     ) 

	    ||

	    (
	     (ageCat == 3 || ageCat == 4 || ageCat == 5) &&
	     (
	      InsuranceStatus.CAT_TWO.equals (status) ||
	      InsuranceStatus.CAT_THREE.equals (status)
	      )
	     );
    }

    public boolean hasPrivateInsurance (Person person, InsuranceStatus status) {
	short ageCat = person.getAgeCat ();
	return
	    (
	     (ageCat == 1 || ageCat == 2) &&
	     (InsuranceStatus.CAT_TWO.equals (status) ||
	      InsuranceStatus.CAT_THREE.equals (status) || 
	      InsuranceStatus.CAT_EIGHT.equals (status)
	      ) 
	     )

	    ||
	    
	    (
	     (ageCat == 3 || ageCat == 4 || ageCat == 5) &&
	     (
	      InsuranceStatus.CAT_TWO.equals (status) ||
	      InsuranceStatus.CAT_THREE.equals (status) ||
	      InsuranceStatus.CAT_TEN.equals (status)
	      )
	     )
	     
	    ||

	     (
	      (ageCat == 6) && InsuranceStatus.CAT_TWO.equals (status)
	      )
	    ;
    }
}

