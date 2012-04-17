package org.opendatafoundation.data.spss;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * 
 * @author ddajvj Based on:
 *         http://www.gnu.org/software/pspp/pspp-dev/html_node/Very
 *         -Long-String-Record.html
 */
public class SPSSRecordType7Subtype14 extends SPSSAbstractRecordType {
	// type 7
	int recordTypeCode;
	int recordSubtypeCode;
	int size;
	int count;
	Map<String, String> nameMap;

	// subtype 14
	String variableNameLengthStr;

	public void read(SPSSFile is) throws IOException, SPSSFileException {
		// position in file
		fileLocation = is.getFilePointer();

		// record type
		recordTypeCode = is.readSPSSInt();
		if (recordTypeCode != 7)
			throw new SPSSFileException(
					"Error reading record type 7 subtype 11: bad record type ["
							+ recordTypeCode + "]. Expecting Record Type 7.");

		// subtype
		recordSubtypeCode = is.readSPSSInt();
		if (recordSubtypeCode != 14)
			throw new SPSSFileException(
					"Error reading record type 7 subtype 14: bad subrecord type ["
							+ recordSubtypeCode
							+ "]. Expecting Record Subtype 14.");

		// data elements
		size = is.readSPSSInt();
		if (size != 1)
			throw new SPSSFileException(
					"Error reading record type 7 subtype 11: bad data element length ["
							+ size + "]. Expecting 1.");
		count = is.readSPSSInt();

		// read the long names String
		variableNameLengthStr = is.readSPSSString(count);

		// load names (separated by tabs)
		nameMap = new LinkedHashMap<String, String>();
		StringTokenizer st1 = new StringTokenizer(variableNameLengthStr, "\t");
		while (st1.hasMoreTokens()) {
			StringTokenizer st2 = new StringTokenizer(st1.nextToken(), "=");
			if (st2.countTokens() >= 2) {
				String key = st2.nextToken();
				String length = st2.nextToken();
				nameMap.put(key,
						length.substring(0, length.length() - 1));
			}
		}
	}

	public String toString() {
		String str = "";
		str += "\nRECORD TYPE 7 SUBTYPE 14 - LONG STRING VARIABLES";
		str += "\nLocation        		: " + fileLocation;
		str += "\nRecord Type     		: " + recordTypeCode;
		str += "\nRecord Subtype  		: " + recordSubtypeCode;
		str += "\nCount   		  		: " + count;
		str += "\nSize            		: " + size;
		str += "\nVariableName-Length   : ";// + variableNameLengthStr;
		Iterator it = nameMap.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry entry = (Map.Entry) it.next();
			str += "\n" + (entry.getKey() + " = " + entry.getValue());
		}
		return (str);
	}
}
