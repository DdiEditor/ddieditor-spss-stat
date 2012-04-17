package org.opendatafoundation.data.spss;

/*
 * Author(s): Pascal Heus (pheus@opendatafoundation.org)
 *  
 * This product has been developed with the financial and 
 * technical support of the UK Data Archive Data Exchange Tools 
 * project (http://www.data-archive.ac.uk/dext/) and the 
 * Open Data Foundation (http://www.opendatafoundation.org) 
 * 
 * Copyright 2007 University of Essex (http://www.esds.ac.uk) 
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *  
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library; if not, write to the 
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, 
 * Boston, MA  02110-1301  USA
 * The full text of the license is also available on the Internet at 
 * http://www.gnu.org/copyleft/lesser.html
 * 
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ddialliance.ddieditor.ui.model.ElementType;
import org.ddialliance.ddiftp.util.DDIFtpException;
import org.opendatafoundation.data.FileFormatInfo;
import org.opendatafoundation.data.Utils;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import dk.dda.ddieditor.spss.SPSSMeasure;

/**
 * Abstract base class for SPSS variable
 * 
 * @author Pascal Heus (pheus@opendatafoundation.org)
 */
public abstract class SPSSVariable {
	final static int LABEL_INFO_LENGHT = 40;
	SPSSFile spssFile; // < The SPSS file this variable belongs to
	public SPSSRecordType2 variableRecord; // < The SPSS type 2 record
											// describing this variable
	public SPSSRecordType3 valueLabelRecord; // < The optional SPSS type 3
												// record holding this variable
												// value labels

	static enum VariableType {
		NUMERIC, STRING
	}; // < The SPSS variable type enumeration

	VariableType type;

	/** The type of variable */

	static enum DDI3RepresentationType {
		TEXT, NUMERIC, DATETIME
	}; // < The SPSS variable type enumeration

	int variableNumber = 0; // < The variable number in the dataset (1-based
							// index, 0 means not set)
	String variableName = ""; // < The full variable name set from
								// SPSSRecordType2 or SPSSRecordType7Subtype13
	String variableShortName = ""; // < The short variable name (8 characters
									// max) set from SPSSRecordType2 or
									// SPSSRecordType7Subtype13

	int measure = -1; // < 1=nominal, 2=ordinal, 3=scale (copied from record
						// type 7 subtype 11) */
	int displayWidth = -1; // < display width (copied from record type 7 subtype
							// 11) */
	int alignment = -1; // < 0=left 1=right, 2=center (copied from record type 7
						// subtype 11) */

	/**
	 * The map of categories. Note that the key is always a string, even for
	 * numeric variables
	 */
	public Map<String, SPSSVariableCategory> misCategoryMap = new LinkedHashMap<String, SPSSVariableCategory>();
	// all categories incl. missing:
	public Map<String, SPSSVariableCategory> categoryMap = new LinkedHashMap<String, SPSSVariableCategory>();
	// only missing categories:
	public Map<String, SPSSVariableCategory> missingCategoryMap = new LinkedHashMap<String, SPSSVariableCategory>();
	public List<String> codeList = new ArrayList<String>();
	/**
	 * Map<variable-number, ddi3 id for variable>
	 */
	Map<Integer, String> variableIdMap = new HashMap<Integer, String>();

	/**
	 * Map<variable-number, ddi3 id for category scheme>
	 */
	Map<Integer, String> categorySchemeIdMap = new HashMap<Integer, String>();

	/**
	 * Map<ddi3 id for category scheme, category scheme label>
	 */
	Map<String, String> categorySchemeLabelMap = new HashMap<String, String>();

	/**
	 * Map<ddi3 id for category scheme, ',' seperated string of ddi3 id for
	 * categores in category scheme>
	 */
	Map<String, String> categoryIdMap = new HashMap<String, String>();

	/**
	 * Map<variable-number, ddi3 id for code scheme>
	 */
	Map<Integer, String> codeSchemeIdMap = new HashMap<Integer, String>();

	/**
	 * Constructor
	 * 
	 * @param file
	 *            the SPSSFile this variable belongs to
	 */
	public SPSSVariable(SPSSFile file) {
		this.spssFile = file;
	}

	/**
	 * Adds a category to the variable
	 */
	public abstract SPSSVariableCategory addCategory(boolean missing,
			byte[] byteValue, String label) throws SPSSFileException;

	/**
	 * @return A string containing the kind of measure
	 */
	public String getAlignmentLabel() {
		String label = "";
		switch (this.alignment) {
		case 0:
			label = "Left";
			break;
		case 1:
			label = "Center";
			break;
		case 2:
			label = "Right";
			break;
		}
		return (label);
	}

	/**
	 * Gets a category for this variable based on a byte[8] value
	 */
	public abstract SPSSVariableCategory getCategory(byte[] byteValue)
			throws SPSSFileException;

	/**
	 * Generates a DDI 2 <var> element for this variable based on the SPSS data
	 * format.
	 * 
	 * @param doc
	 *            the document wrapping this element
	 * @param offset
	 *            the variable offset for starting position in the file
	 * @return the genarated Element
	 * @throws SPSSFileException
	 */
	public Element getDDI2(Document doc, int offset) throws SPSSFileException {
		return (getDDI2(doc, new FileFormatInfo(), offset));
	}

	/**
	 * Generates a DDI 2 <var> element for this variable based on the specified
	 * data format.
	 * 
	 * @param doc
	 *            the document wrapping this element
	 * @param dataFormat
	 *            the SPSSFile.DataFormat this DDI is being generated for
	 * @param offset
	 *            the variable offset for starting position in the file
	 * 
	 * @return the generated Element
	 * @throws SPSSFileException
	 */
	public Element getDDI2(Document doc, FileFormatInfo dataFormat, int offset)
			throws SPSSFileException {
		Element elem;
		Element var = doc.createElementNS(SPSSFile.DDI2_NAMESPACE, "var");

		// decimals
		if (this.getDecimals() > 0)
			var.setAttribute("dcml", "" + this.getDecimals());

		// interval
		if (this.type == VariableType.NUMERIC) {
			switch (this.measure) {
			case 1: // nominal
			case 2: // ordinal
				var.setAttribute("intrvl", "discrete");
				break;
			case 3: // scale
				var.setAttribute("intrvl", "contin");
				break;
			}
		}

		// location
		elem = (Element) var.appendChild(doc.createElementNS(
				SPSSFile.DDI2_NAMESPACE, "location"));
		// location width
		elem.setAttribute("width", "" + this.getLength(dataFormat));

		// location StartPos
		elem.setAttribute("StartPos", "" + offset);
		// location EndPos
		elem.setAttribute("EndPos", "" + (offset + this.getLength(dataFormat)));

		// name
		var.setAttribute("name", this.getName());

		// label
		elem = (Element) var.appendChild(doc.createElementNS(
				SPSSFile.DDI2_NAMESPACE, "labl"));
		elem.setTextContent(this.getLabel());

		// categories
		if (!this.categoryMap.isEmpty()) {
			// iterate over categories
			Iterator catIterator = categoryMap.keySet().iterator();
			while (catIterator.hasNext()) {
				String key = (String) catIterator.next();
				SPSSVariableCategory cat = categoryMap.get(key);
				Element catgry = (Element) var.appendChild(doc.createElementNS(
						SPSSFile.DDI2_NAMESPACE, "catgry"));
				if (cat.isMissing)
					catgry.setAttribute("missing", "Y");
				// category value
				elem = (Element) catgry.appendChild(doc.createElementNS(
						SPSSFile.DDI2_NAMESPACE, "catValu"));
				elem.setTextContent(cat.strValue);
				// category label
				elem = (Element) catgry.appendChild(doc.createElementNS(
						SPSSFile.DDI2_NAMESPACE, "labl"));
				elem.setTextContent(cat.label);
			}
		}

		// format
		elem = (Element) var.appendChild(doc.createElementNS(
				SPSSFile.DDI2_NAMESPACE, "varFormat"));
		// format type
		if (this.type == VariableType.NUMERIC)
			elem.setAttribute("type", "numeric");
		else
			elem.setAttribute("type", "character");
		// format category
		// TODO: add format category
		// format schema
		elem.setAttribute("schema", "SPSS");
		// format name
		elem.setAttribute("formatname", getSPSSFormat());

		return (var);
	}

	/**
	 * Returns a default Category Scheme ID based on the file unique identifier.
	 * 
	 * @return a String containing the r:ID
	 * @throws DDIFtpException
	 */
	public String getDDI3DefaultCategorySchemeID() throws DDIFtpException {
		// return (spssFile.getUniqueID() + "_" +
		// spssFile.categorySchemeIDSuffix + "_V" + this.variableNumber);
		return spssFile.createId(ElementType.CATEGORY_SCHEME);
	}

	/**
	 * Returns a default Code Scheme ID based on the file unique identifier.
	 * 
	 * @return a String containing the r:ID
	 * @throws DDIFtpException
	 */
	public String getDDI3DefaultCodeSchemeID() throws DDIFtpException {
		// return (spssFile.getUniqueID() + "_" + spssFile.codeSchemeIDSuffix +
		// "_V" + this.variableNumber);
		return spssFile.createId(ElementType.CODE_SCHEME);
	}

	/**
	 * Generates a DDI3 Category Scheme for this variable using default ID
	 * 
	 * @param doc
	 * @return a org.w3c.dom.Element containing the scheme
	 * @throws DDIFtpException
	 */
	public Element createDDI3CategoryScheme(Document doc)
			throws DDIFtpException {
		return (createDDI3CategoryScheme(doc, null));
	}

	/**
	 * Generates a DDI3 Category Scheme for this variable
	 * 
	 * @param doc
	 * @param categorySchemeID
	 * @return
	 * @throws DDIFtpException
	 */
	private Element createDDI3CategoryScheme(Document doc,
			String categorySchemeID) throws DDIFtpException {
		Element scheme = null;
		Element elem;

		// only for variables with a value label set
		if (!this.categoryMap.isEmpty()) {
			// CategoryScheme
			scheme = doc.createElementNS(
					SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE, "CategoryScheme");
			if (categorySchemeID == null)
				categorySchemeID = getDDI3DefaultCategorySchemeID();
			Utils.setDDIMaintainableId(scheme, categorySchemeID);
			categorySchemeIdMap.put(this.variableNumber, categorySchemeID);

			// label
			Element schemeLabelElm = (Element) scheme
					.appendChild(doc.createElementNS(
							SPSSFile.DDI3_REUSABLE_NAMESPACE, "Label"));

			// iterate over categories
			int categoryNumber = 0;
			// Iterator catIterator =
			// valueLabelRecord.valueLabel.keySet().iterator();
			Iterator catIterator = categoryMap.keySet().iterator();

			StringBuilder schemeLabelStrB = new StringBuilder();
			StringBuilder categoryIds = new StringBuilder();
			boolean missingCreated = false;
			while (catIterator.hasNext()) {
				// id
				String id = spssFile.createId(ElementType.CATEGORY,
						categoryNumber);
				categoryIds.append(id);
				if (catIterator.hasNext()) {
					categoryIds.append(",");
				}

				String key = (String) catIterator.next();
				SPSSVariableCategory cat = categoryMap.get(key);
				if (cat.isMissing && cat.label == "") {
					if (!missingCreated) {
						// add a category for missing values without label
						// category element
						Element category = (Element) scheme
								.appendChild(doc
										.createElementNS(
												SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
												"Category"));
						Utils.setDDIVersionableId(category, id);

						category.setAttribute("missing", "true");
						missingCreated = true;
						// category label
						elem = (Element) category.appendChild(doc
								.createElementNS(
										SPSSFile.DDI3_REUSABLE_NAMESPACE,
										"Label"));
						elem.setTextContent("Missing");
						spssFile.setLangAttr(elem);
						schemeLabelStrB.append("Missing");
					}
				} else {
					categoryNumber++;
					// category element
					Element category = (Element) scheme.appendChild(doc
							.createElementNS(
									SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
									"Category"));

					Utils.setDDIVersionableId(category,
					// spssFile.variableCategoryPrefix + "_" + categoryNumber);
							id);
					// missing?
					if (cat.isMissing)
						category.setAttribute("missing", "true");
					// category label
					elem = (Element) category.appendChild(doc.createElementNS(
							SPSSFile.DDI3_REUSABLE_NAMESPACE, "Label"));
					elem.setTextContent(cat.label);
					schemeLabelStrB.append(cat.label);
					if (catIterator.hasNext()) {
						schemeLabelStrB.append(", ");
					}
				}
			}

			// category scheme label
			String schemelabelMin = schemeLabelStrB
					.substring(
							0,
							schemeLabelStrB.length() < LABEL_INFO_LENGHT ? schemeLabelStrB
									.length() : LABEL_INFO_LENGHT)
					+ "... , " + categoryNumber;
			schemeLabelElm.setTextContent(schemelabelMin);
			spssFile.setLangAttr(schemeLabelElm);
			categorySchemeLabelMap.put(categorySchemeID, schemelabelMin);

			// category ids
			categoryIdMap.put(categorySchemeID, categoryIds.toString());
		}
		return (scheme);
	}

	/**
	 * Generates a DDI3 Code Scheme for this variable using default category and
	 * code scheme identifiers
	 * 
	 * @param doc
	 * @param createCategoryReference
	 * @return a org.w3c.dom.Element containing the scheme
	 * @throws SPSSFileException
	 * @throws DOMException
	 * @throws DDIFtpException
	 */
	public Element createDDI3CodeScheme(Document doc,
			boolean createCategoryReference) throws DOMException,
			SPSSFileException, DDIFtpException {
		return (createDDI3CodeScheme(doc, null, null, createCategoryReference));
	}

	/**
	 * Generates a DDI3 Code Scheme for this variable
	 * 
	 * @param doc
	 * @param categorySchemeID
	 * @param codeSchemeID
	 * @return code scheme
	 * @throws SPSSFileException
	 * @throws DOMException
	 * @throws DDIFtpException
	 */
	private Element createDDI3CodeScheme(Document doc, String categorySchemeID,
			String codeSchemeID, boolean createCategoryReference)
			throws DOMException, SPSSFileException, DDIFtpException {
		Element scheme = null;
		Element elem;

		// only for variables with a value label set
		if (!this.categoryMap.isEmpty()) {
			scheme = doc.createElementNS(
					SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE, "CodeScheme");

			// id
			if (categorySchemeID == null) {
				categorySchemeID = categorySchemeIdMap.get(this.variableNumber);
			}
			if (codeSchemeID == null) {
				codeSchemeID = getDDI3DefaultCodeSchemeID();
			}
			Utils.setDDIMaintainableId(scheme, codeSchemeID);
			codeSchemeIdMap.put(this.variableNumber, codeSchemeID);

			// label
			elem = (Element) scheme.appendChild(doc.createElementNS(
					SPSSFile.DDI3_REUSABLE_NAMESPACE, "Label"));
			// TODO set magic character as an option ;- )
			elem.setTextContent("V" + this.variableNumber + "-CODS");

			// categorySchemeReference
			if (createCategoryReference) {
				Element categorySchemeReference = (Element) scheme
						.appendChild(doc.createElementNS(
								SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
								"CategorySchemeReference"));
				elem = (Element) categorySchemeReference
						.appendChild(doc.createElementNS(
								SPSSFile.DDI3_REUSABLE_NAMESPACE, "ID"));
				elem.setTextContent(categorySchemeID);

				// category refs
				String[] categoryIds = categoryIdMap.get(categorySchemeID)
						.split(",");

				// iterate over categories
				Iterator catIterator = categoryMap.keySet().iterator();
				int categoryNumber = 0;
				while (catIterator.hasNext()) {
					String key = (String) catIterator.next();
					SPSSVariableCategory cat = categoryMap.get(key);

					// Code element
					Element code = (Element) scheme.appendChild(doc
							.createElementNS(
									SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
									"Code"));

					// category reference
					Element categoryReference = (Element) code.appendChild(doc
							.createElementNS(
									SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
									"CategoryReference"));
					elem = (Element) categoryReference.appendChild(doc
							.createElementNS(SPSSFile.DDI3_REUSABLE_NAMESPACE,
									"ID"));
					elem.setTextContent(categoryIds[categoryNumber]);
					categoryNumber++;

					// value
					elem = (Element) code.appendChild(doc.createElementNS(
							SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE, "Value"));
					elem.setTextContent(cat.strValue);
					/*
					 * if(this.type==VariableType.NUMERIC) { // convert key into
					 * a numeric value and then into a trimmed string double
					 * value = SPSSUtils.byte8ToDouble(key);
					 * elem.setTextContent(((SPSSNumericVariable)
					 * this).valueToString(value).trim()); } else { // convert
					 * value-key to string elem.setTextContent(new String(key));
					 * }
					 */
				}
			} else {
				// iterate over categories - but do not store category reference
				Iterator catIterator = categoryMap.keySet().iterator();
				while (catIterator.hasNext()) {
					String key = (String) catIterator.next();
					SPSSVariableCategory cat = categoryMap.get(key);

					// Code element
					Element code = (Element) scheme.appendChild(doc
							.createElementNS(
									SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
									"Code"));

					// value
					elem = (Element) code.appendChild(doc.createElementNS(
							SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE, "Value"));
					elem.setTextContent(cat.strValue);
				}
			}
		}
		return (scheme);
	}

	/**
	 * Generates a DDI3 Data Item for this variable.
	 * 
	 * @param doc
	 * @param dataFormat
	 * @param offset
	 * @return a org.w3c.dom.Element containing the data item
	 * @throws SPSSFileException
	 * @throws DOMException
	 * @throws DDIFtpException
	 */
	public Element getDDI3DataItem(Document doc, FileFormatInfo dataFormat,
			int offset) throws DOMException, SPSSFileException {
		Element dataItem;
		Element elem;

		dataItem = doc.createElementNS(
				SPSSFile.DDI3_PHYSICAL_PRODUCT_NAMESPACE, "DataItem");

		// variable reference
		Element varReference = (Element) dataItem.appendChild(doc
				.createElementNS(SPSSFile.DDI3_PHYSICAL_PRODUCT_NAMESPACE,
						"VariableReference"));
		// TODO: DDI3: need scheme reference but propose to have a default
		// scheme reference in GrossRecordStructure to avoid unnecessary repeats
		elem = (Element) varReference.appendChild(doc.createElementNS(
				SPSSFile.DDI3_REUSABLE_NAMESPACE, "ID"));
		elem.setTextContent(variableIdMap.get(this.variableNumber));

		// physical location
		Element physicalLocation = (Element) dataItem.appendChild(doc
				.createElementNS(SPSSFile.DDI3_PHYSICAL_PRODUCT_NAMESPACE,
						"PhysicalLocation"));
		elem = (Element) physicalLocation.appendChild(doc.createElementNS(
				SPSSFile.DDI3_PHYSICAL_PRODUCT_NAMESPACE, "StorageFormat"));
		elem.setTextContent(this.getSPSSFormat());

		elem = (Element) physicalLocation.appendChild(doc.createElementNS(
				SPSSFile.DDI3_PHYSICAL_PRODUCT_NAMESPACE, "StartPosition"));
		elem.setTextContent("" + offset);
		elem = (Element) physicalLocation.appendChild(doc.createElementNS(
				SPSSFile.DDI3_PHYSICAL_PRODUCT_NAMESPACE, "Width"));
		elem.setTextContent("" + getLength(dataFormat));
		if (this.getDecimals() > 0) {
			elem = (Element) physicalLocation.appendChild(doc.createElementNS(
					SPSSFile.DDI3_PHYSICAL_PRODUCT_NAMESPACE,
					"DecimalPositions"));
			elem.setTextContent("" + this.getDecimals());
		}

		return (dataItem);
	}

	/**
	 * Generates a DDI3 Proprietary Data Item for this variable.
	 * 
	 * @param doc
	 * @return a org.w3c.dom.Element containing the proprietary data item
	 * @throws SPSSFileException
	 * @throws DOMException
	 */
	public Element getDDI3ProprietaryDataItem(Document doc)
			throws DOMException, SPSSFileException {
		Element dataItem;
		Element elem;

		dataItem = doc.createElementNS(
				SPSSFile.DDI3_PROPRIETARY_RECORD_NAMESPACE, "DataItem");

		// variable reference
		Element varReference = (Element) dataItem.appendChild(doc
				.createElementNS(SPSSFile.DDI3_PROPRIETARY_RECORD_NAMESPACE,
						"VariableReference"));
		elem = (Element) varReference.appendChild(doc.createElementNS(
				SPSSFile.DDI3_REUSABLE_NAMESPACE, "ID"));
		elem.setTextContent(variableIdMap.get(this.variableNumber));

		elem = (Element) dataItem.appendChild(doc.createElementNS(
				SPSSFile.DDI3_PROPRIETARY_RECORD_NAMESPACE,
				"ProprietaryDataType"));
		elem.setTextContent(variableRecord.variableTypeCode == 0 ? "numeric"
				: "string");

		elem = (Element) dataItem.appendChild(doc.createElementNS(
				SPSSFile.DDI3_PROPRIETARY_RECORD_NAMESPACE,
				"ProprietaryOutputFormat"));
		elem.setTextContent(this.getSPSSFormat());

		Element proprietaryInfo = (Element) dataItem.appendChild(doc
				.createElementNS(SPSSFile.DDI3_REUSABLE_NAMESPACE,
						"ProprietaryInfo"));

		elem = (Element) proprietaryInfo.appendChild(doc.createElementNS(
				SPSSFile.DDI3_REUSABLE_NAMESPACE, "ProprietaryProperty"));
		elem.setAttribute("name", "Width");
		elem.setTextContent("" + this.variableRecord.writeFormatWidth);

		elem = (Element) proprietaryInfo.appendChild(doc.createElementNS(
				SPSSFile.DDI3_REUSABLE_NAMESPACE, "ProprietaryProperty"));
		elem.setAttribute("name", "Decimals");
		elem.setTextContent("" + this.getDecimals());

		if (variableRecord.missingValueFormatCode != 0) {
			elem = (Element) proprietaryInfo.appendChild(doc.createElementNS(
					SPSSFile.DDI3_REUSABLE_NAMESPACE, "ProprietaryProperty"));
			elem.setAttribute("name", "MissingFormatCode");
			elem.setTextContent("" + variableRecord.missingValueFormatCode);
			for (int i = 0; i < Math.abs(variableRecord.missingValueFormatCode); i++) {
				elem = (Element) proprietaryInfo.appendChild(doc
						.createElementNS(SPSSFile.DDI3_REUSABLE_NAMESPACE,
								"ProprietaryProperty"));
				elem.setAttribute("name", "MissingValue" + i);
				if (variableRecord.variableTypeCode == 0)
					elem.setTextContent(""
							+ SPSSUtils
									.byte8ToDouble(variableRecord.missingValue[i]));
				else
					elem.setTextContent(SPSSUtils
							.byte8ToString(variableRecord.missingValue[i]));
			}
		}
		if (this.displayWidth != -1) {
			elem = (Element) proprietaryInfo.appendChild(doc.createElementNS(
					SPSSFile.DDI3_REUSABLE_NAMESPACE, "ProprietaryProperty"));
			elem.setAttribute("name", "DisplayWidth");
			elem.setTextContent("" + this.displayWidth);
		}
		if (this.alignment != -1) {
			elem = (Element) proprietaryInfo.appendChild(doc.createElementNS(
					SPSSFile.DDI3_REUSABLE_NAMESPACE, "ProprietaryProperty"));
			elem.setAttribute("name", "Alignment");
			elem.setTextContent("" + this.getAlignmentLabel());
		}
		if (this.measure != -1) {
			elem = (Element) proprietaryInfo.appendChild(doc.createElementNS(
					SPSSFile.DDI3_REUSABLE_NAMESPACE, "ProprietaryProperty"));
			elem.setAttribute("name", "Measure");
			elem.setTextContent("" + this.getMeasureLabel());
		}

		return (dataItem);
	}

	/**
	 * Generates a DDI3 Variable element for this variable
	 * 
	 * @param doc
	 * @return a org.w3c.dom.Element containing the Variable
	 * @throws DDIFtpException
	 */
	public Element createDDI3Variable(Document doc) throws DDIFtpException {
		return (createDDI3Variable(doc, null));
	}

	/**
	 * Create a DDI3 Variable element for this variable
	 * 
	 * @param doc
	 * @param codeSchemeReferenceID
	 * @return a org.w3c.dom.Element containing the Variable
	 * @throws DDIFtpException
	 */
	private Element createDDI3Variable(Document doc,
			String codeSchemeReferenceID) throws DDIFtpException {
		Element var = null;
		Element elem;

		var = doc.createElementNS(SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
				"Variable");
		variableIdMap.put(variableNumber,
				spssFile.createId(ElementType.VARIABLE));
		Utils.setDDIVersionableId(var, variableIdMap.get(variableNumber));

		// variable name
		elem = (Element) var.appendChild(doc.createElementNS(
				SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE, "VariableName"));
		elem.setTextContent(this.getName());

		// variable label
		elem = (Element) var.appendChild(doc.createElementNS(
				SPSSFile.DDI3_REUSABLE_NAMESPACE, "Label"));
		elem.setAttribute("type", "label");
		elem.setAttribute("maxLength", "120");
		elem.setTextContent(this.getLabel());

		// variable label (short name)
		// elem = (Element) var.appendChild(doc.createElementNS(
		// SPSSFile.DDI3_REUSABLE_NAMESPACE, "Label"));
		// elem.setAttribute("type", "name");
		// elem.setAttribute("maxLength", "8");
		// elem.setTextContent(this.getShortName());

		// representation
		if (hasValueLabels() || getDDI3RepresentationType() != null) {
			Element representation = (Element) var.appendChild(doc
					.createElementNS(SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
							"Representation"));

			// measurementUnit
			if (variableRecord.writeFormatType == 4) {
				representation.setAttribute("measurementUnit", "$");
			}

			// code list representation
			if (hasValueLabels()) {
				Element codeRepresentation = (Element) representation
						.appendChild(doc.createElementNS(
								SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
								"CodeRepresentation"));
				setMeasure(codeRepresentation);
				setMissingValue(codeRepresentation);
				Element codeSchemeReference = (Element) codeRepresentation
						.appendChild(doc.createElementNS(
								SPSSFile.DDI3_REUSABLE_NAMESPACE,
								"CodeSchemeReference"));
				elem = (Element) codeSchemeReference
						.appendChild(doc.createElementNS(
								SPSSFile.DDI3_REUSABLE_NAMESPACE, "ID"));
				if (codeSchemeReferenceID == null)
					codeSchemeReferenceID = codeSchemeIdMap
							.get(this.variableNumber);
				elem.setTextContent(codeSchemeReferenceID);
			} else {
				String dataType = getDDI3DataType();
				if (getDDI3RepresentationType() == DDI3RepresentationType.NUMERIC) {
					// numeric representation - type
					elem = (Element) representation.appendChild(doc
							.createElementNS(
									SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
									"NumericRepresentation"));
					setMeasure(elem);
					if (dataType != null)
						elem.setAttribute("type", dataType);
					// - decimal position
					elem.setAttribute("decimalPositions",
							"" + this.getDecimals());
					setMissingValue(elem);
					// TODO: DDI3: add @format attribute to schema
					// elem.setAttribute("format", this.getSPSSFormat());
				}
				if (getDDI3RepresentationType() == DDI3RepresentationType.DATETIME) {
					// datetime representation
					elem = (Element) representation.appendChild(doc
							.createElementNS(
									SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
									"DateTimeRepresentation"));
					setMeasure(elem);
					if (dataType != null)
						elem.setAttribute("type", dataType);
					elem.setAttribute("format", this.getSPSSFormat());
					setMissingValue(elem);
				}
				if (getDDI3RepresentationType() == DDI3RepresentationType.TEXT) {
					// string representation
					elem = (Element) representation.appendChild(doc
							.createElementNS(
									SPSSFile.DDI3_LOGICAL_PRODUCT_NAMESPACE,
									"TextRepresentation"));
					setMeasure(elem);
					elem.setAttribute("maxLength", "" + this.getLength());
					setMissingValue(elem);
				}
			}
		}
		return (var);
	}

	private void setMeasure(Element elem) {
		SPSSMeasure measureEnum = SPSSMeasure.intToSpssMeasure(this.measure);
		if (measureEnum == null) {
			measureEnum = SPSSMeasure.ORDINAL;
		}
		elem.setAttribute("classificationLevel", measureEnum.classificationLevel());
	}
	
	private void setMissingValue(Element elem) {
		// - missing values
		Iterator catIterator = missingCategoryMap.keySet()
				.iterator();
		StringBuilder missing = new StringBuilder();
		while (catIterator.hasNext()) {
			String key = (String) catIterator.next();
			SPSSVariableCategory cat = missingCategoryMap.get(key);
			missing.append(cat.strValue);
			if (catIterator.hasNext()) {
				missing.append(" ");
			}
		}
		if (missing.length() > 0) {
			elem.setAttribute("missingValue", missing.toString());
		}	
	}

	/**
	 * Gets the DDI data type for this variable that matches the controlled
	 * vocabulary of the representation @type attribute as closely as possible.
	 * 
	 * @return A string containing the XML data type
	 */
	public DDI3RepresentationType getDDI3RepresentationType() {
		DDI3RepresentationType type = null;

		switch (this.variableRecord.writeFormatType) {
		case 0:
		case 1:
		case 2:
		case 26:
		case 27:
			type = DDI3RepresentationType.TEXT;
			break;
		case 3:
		case 4:
		case 5:
		case 31:
		case 32:
		case 33:
		case 34:
		case 35:
		case 36:
		case 37:
		case 17:
			type = DDI3RepresentationType.NUMERIC;
			break;
		case 20:
		case 23:
		case 24:
		case 28:
		case 29:
		case 30:
		case 21:
		case 22:
		case 25:
		case 38:
		case 39:
			type = DDI3RepresentationType.DATETIME;
			break;
		}
		return (type);

	}

	String unsurpportedInDDI3 = "Unsurported in DDI-3";

	/**
	 * Gets the DDI data type for this variable that matches the controlled
	 * vocabulary of the representation @type attribute as closely as possible.
	 * 
	 * @return A string containing the XML data type
	 */
	public String getDDI3DataType() {
		String typeStr = null;
		switch (this.variableRecord.writeFormatType) {
		case 0:
		case 1:
		case 2:
		case 3:
		case 4:
		case 5:
			// TODO: 6,7,8,9,10,11,12,15,16
		case 17:
			typeStr = "Double";
			break;
		case 20:
			typeStr = "Date";
			break;
		case 21:
			typeStr = "Time";
			break;
		case 22:
			typeStr = "DateTime";
			break;
		case 23:
			typeStr = "Date";
			break;
		case 24:
			typeStr = "Date";
			break;
		case 25:
			typeStr = "DateTime";
			break;
		case 26:
			typeStr = "Day";
			break;
		case 27:
			typeStr = "Month";
			break;
		case 28:
			typeStr = "MonthDay";
			break;
		case 29:
			typeStr = unsurpportedInDDI3;
			break;
		case 30:
			typeStr = unsurpportedInDDI3;
			break;
		case 31:
		case 32:
		case 33:
		case 34:
		case 35:
		case 36:
		case 37:
			if (this.getDecimals() > 0)
				typeStr = "Decimal";
			else
				typeStr = "BigInteger"; // TODO: check length and return smaller
										// types like Integer, Short, etc.
			break;
		case 38:
			typeStr = "Date";
			break;
		case 39:
			typeStr = "Date";
			break;
		default:
			typeStr = null;
		}
		return (typeStr);
	}

	/**
	 * Retrieves the SPSS write format number of decimals.
	 * 
	 * @return the length
	 */
	public int getDecimals() {
		return (this.variableRecord.writeFormatDecimals);
	}

	/**
	 * @return A string containing the variable name (empty if no label is
	 *         available)
	 */
	public String getLabel() {
		return (variableRecord.label);
	}

	/**
	 * Retrieves the SPSS write format width. This is the same as the getWidth
	 * method
	 * 
	 * @return the variable length
	 */
	public int getLength() {
		return (getLength(new FileFormatInfo(FileFormatInfo.Format.SPSS)));
	}

	/**
	 * Computes the variable length in the specific format
	 * 
	 * @return the variable length
	 */
	public int getLength(FileFormatInfo format) {
		// TODO: compute generic ascii length
		return (this.variableRecord.writeFormatWidth);
	}

	/**
	 * @return A string containing the kind of measure
	 */
	public String getMeasureLabel() {
		String label = "";
		switch (this.measure) {
		case 1:
			label = "Nominal";
			break;
		case 2:
			label = "Ordinal";
			break;
		case 3:
			label = "Scale";
			break;
		}
		return (label);
	}

	/**
	 * @return A string containing the variable name
	 */
	public String getName() {
		return (this.variableName);
	}

	/**
	 * @return A string representing variable in SPSS syntax
	 */
	public abstract String getSPSSFormat();

	/**
	 * @return A string containing the variable short name (max 8 characters)
	 */
	public String getShortName() {
		return (this.variableName);
	}

	/**
	 * @param recordNumber
	 * @return the value of the observation in a string
	 * @throws SPSSFileException
	 */

	public abstract String getValueAsString(int recordNumber,
			FileFormatInfo dataFormat) throws SPSSFileException;

	/**
	 * Determines if a variable is associated with a set of value labels
	 * 
	 * @return boolean true if a SPSSRecordtype3 exists for this variable
	 */
	public boolean hasValueLabels() {
		return (!this.categoryMap.isEmpty());
	}

	/**
	 * Determines if a variable is a date without time information
	 * 
	 * @return boolean true if this is a date
	 */
	public boolean isDate() {
		if (this.valueLabelRecord == null)
			return (false);
		else
			return (true);
	}

	/**
	 * Determines if a numeric value is a missing value "code" (not a SYSMISS)
	 * 
	 * @return boolean true if this value is found in the missing value code
	 *         list
	 */
	public boolean isMissingValueCode(double value) {
		boolean rc = false;
		if (this.variableRecord.missingValueFormatCode > 0) {
			// 1-3 --> discreet missing value codes
			for (int i = 0; i < this.variableRecord.missingValueFormatCode; i++) {
				if (value == SPSSUtils
						.byte8ToDouble(this.variableRecord.missingValue[i])) {
					rc = true;
					break;
				}
			}
		} else if (this.variableRecord.missingValueFormatCode <= -2) {
			// -2 --> range of missing value codes
			if (value >= SPSSUtils
					.byte8ToDouble(this.variableRecord.missingValue[0])
					&& value <= SPSSUtils
							.byte8ToDouble(this.variableRecord.missingValue[1]))
				rc = true;
			else if (this.variableRecord.missingValueFormatCode == -3) {
				// -3 --> an extra discrete value is also specified
				if (value == SPSSUtils
						.byte8ToDouble(this.variableRecord.missingValue[2]))
					rc = true;
			}
		}
		return (rc);
	}

	/**
	 * Determines if a String value is a missing value "code" (not a SYSMISS)
	 * 
	 * @return boolean true if this value is found in the missing value code
	 *         list
	 */
	public boolean isMissingValueCode(String str) {
		boolean rc = false;
		if (this.variableRecord.missingValueFormatCode > 0) {
			// 1-3 --> discreet missing value codes
			for (int i = 0; i < this.variableRecord.missingValueFormatCode; i++) {
				if (str.compareToIgnoreCase(SPSSUtils
						.byte8ToString(this.variableRecord.missingValue[i])) == 0) {
					rc = true;
					break;
				}
			}
		}
		// NOTE: missing value range is not allowed for string variables
		return (rc);
	}

}
