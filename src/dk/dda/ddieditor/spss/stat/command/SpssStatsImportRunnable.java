package dk.dda.ddieditor.spss.stat.command;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.xml.parsers.SAXParserFactory;

import org.apache.xmlbeans.XmlObject;
import org.ddialliance.ddi3.xml.xmlbeans.dataset.VariableReferenceDocument;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticDocument;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticType;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticTypeCodedDocument;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticTypeCodedType;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticsType;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.StatisticsDocument;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.StatisticsType;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.SummaryStatisticType;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.SummaryStatisticTypeCodedDocument;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.SummaryStatisticTypeCodedType;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.VariableStatisticsDocument;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.VariableStatisticsType;
import org.ddialliance.ddi3.xml.xmlbeans.reusable.CodeValueType;
import org.ddialliance.ddi3.xml.xmlbeans.reusable.IDType;
import org.ddialliance.ddieditor.logic.identification.IdentificationManager;
import org.ddialliance.ddieditor.model.DdiManager;
import org.ddialliance.ddieditor.model.lightxmlobject.LightXmlObjectListDocument;
import org.ddialliance.ddieditor.model.lightxmlobject.LightXmlObjectType;
import org.ddialliance.ddieditor.model.resource.DDIResourceType;
import org.ddialliance.ddieditor.persistenceaccess.ParamatizedXquery;
import org.ddialliance.ddieditor.persistenceaccess.PersistenceManager;
import org.ddialliance.ddieditor.persistenceaccess.XQueryInsertKeyword;
import org.ddialliance.ddieditor.persistenceaccess.filesystem.FilesystemManager;
import org.ddialliance.ddieditor.ui.editor.Editor;
import org.ddialliance.ddieditor.util.DdiEditorConfig;
import org.ddialliance.ddieditor.util.LightXmlObjectUtil;
import org.ddialliance.ddiftp.util.DDIFtpException;
import org.ddialliance.ddiftp.util.Translator;
import org.ddialliance.ddiftp.util.log.Log;
import org.ddialliance.ddiftp.util.log.LogFactory;
import org.ddialliance.ddiftp.util.log.LogType;
import org.eclipse.ui.internal.decorators.LightweightDecoratorManager;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.spss.xml.spss.oms.CategoryDocument;
import com.spss.xml.spss.oms.CategoryDocument.Category;
import com.spss.xml.spss.oms.PivotTableDocument;

import dk.dda.ddieditor.spss.stat.idelement.IdElement;
import dk.dda.ddieditor.spss.stat.idelement.IdElementContentHandler;
import dk.dda.ddieditor.spss.stat.util.SpssStatsToDdiLStatsMap;

/*
 * Copyright 2012 Danish Data Archive (http://www.dda.dk) 
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either Version 3 of the License, or 
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
 */

/**
 * SPSS Statistics DdiEditor thread implementation, workflow:<br>
 * 1. Store and SPSS OXMl in DBXMl<br>
 * 2. Transform SPSS OXMl to DDI-L<br>
 * 3. Store DDI-L on selected resource<br>
 */
public class SpssStatsImportRunnable implements Runnable {
	private Log log = LogFactory.getLog(LogType.SYSTEM,
			SpssStatsImportRunnable.class);
	private Log errorLog = LogFactory.getLog(LogType.EXCEPTION,
			SpssStatsImportRunnable.class);

	int doHouseKeeping = 0;
	public DDIResourceType selectedResource = null;
	public String inOxmlFile = null;
	public boolean incrementalLoad = false;

	File file;
	String spssNamespace = "";
	// String declareNamspaces =
	// "declare namespace oms='http://xml.spss.com/spss/oms';"
	// + "declare namespace ddieditor= 'http://dda.dk/ddieditor';";
	String declareNamspaces = "";
	String omsFreqQueryFunction;
	String omsLocalCategoryFunction;
	String omsStatisticsCategoryFunction;
	String omsStatisticsCategoryValidMissingFunction;
	Map<String, XmlObject> varStatisticsMap = new HashMap<String, XmlObject>();

	List<VariableStatisticsDocument> variableStatistics = new ArrayList<VariableStatisticsDocument>();
	StatisticsType statsType;

	NumberFormat numberFormat = NumberFormat
			.getInstance(new Locale("en", "US"));

	public SpssStatsImportRunnable(DDIResourceType selectedResource,
			String inOxmlFile, boolean incrementalLoad) {
		super();
		doHouseKeeping = DdiEditorConfig
				.getInt(DdiEditorConfig.DO_HOUSE_KEEPING_COUNT);

		numberFormat.setRoundingMode(RoundingMode.HALF_EVEN);
		numberFormat.setGroupingUsed(false);

		this.selectedResource = selectedResource;
		this.inOxmlFile = inOxmlFile;
		this.incrementalLoad = incrementalLoad;

		// spss namespace to change from spss version
		// the change is from 21 and onwards
		// Note: import of com.ibm.software.analytics.spss.xml.oms is currently
		// NOT configurable
		spssNamespace = DdiEditorConfig
				.get(DdiEditorConfig.SPPS_OMS_XML_NAMESPACE);
		declareNamspaces = "declare namespace oms='" + spssNamespace + "';"
				+ "declare namespace ddieditor= 'http://dda.dk/ddieditor';";

		// code representation
		StringBuilder q = new StringBuilder();
		q.append(declareNamspaces);
		q.append("declare function ddieditor:getPivotTable($doc as xs:string, $type as xs:string, $varname as xs:string) as element()* {");
		q.append(" for $x in doc($doc)//oms:outputTree/oms:command/oms:heading/oms:pivotTable");
		q.append(" where $x/@subType=$type and $x/@varName=$varname");
		q.append(" return $x};");
		omsFreqQueryFunction = q.toString();

		q.delete(0, q.length());
		q.append(declareNamspaces);
		q.append("declare function ddieditor:get_category($group_text) as element()* {");
		q.append("let $category := for $x in $this//oms:group where $x/@text=$group_text return $x/oms:category return $category");
		q.append("};");
		omsLocalCategoryFunction = q.toString();

		// numeric representation
		q.delete(0, q.length());
		q.append(declareNamspaces);
		q.append("declare function ddieditor:getPivotTable($doc as xs:string, $type as xs:string, $varname as xs:string) as element()* {");
		q.append(" let $category := for $x in doc($doc)//oms:outputTree/oms:command/oms:pivotTable[@subType='Statistics']/oms:dimension/oms:category");
		q.append(" where $x/@text=$type return $x");
		q.append(" for $y in $category/oms:dimension/oms:category where $y/@varName=$varname");
		q.append(" return $y};");
		omsStatisticsCategoryFunction = q.toString();

		//
		q.delete(0, q.length());
		q.append(declareNamspaces);
		q.append("declare function ddieditor:getPivotTable($doc as xs:string, $type as xs:string, $varname as xs:string) as element()* {");
		q.append(" let $category := for $x in doc($doc)//oms:outputTree/oms:command/oms:pivotTable[@subType='Statistics']/oms:dimension/oms:group/oms:category");
		q.append(" where $x/@text=$type return $x");
		q.append(" for $y in $category/oms:dimension/oms:category where $y/@varName=$varname");
		q.append(" return $y};");
		omsStatisticsCategoryValidMissingFunction = q.toString();
	}

	@Override
	public void run() {
		try {
			PersistenceManager.getInstance().getPersistenceStorage()
					.setReuseTransaction(false);
			importStats();
			storeDdi();
		} catch (Exception e) {
			Editor.showError(e, null);
		} finally {
			try {
				PersistenceManager.getInstance().getPersistenceStorage()
						.setReuseTransaction(true);
			} catch (DDIFtpException e) {
				// do nothing
			}
			cleanUp();
		}
	}
	
	public void importStats() throws Exception {
		// stat file
		file = new File(inOxmlFile);

		// query ddi vars
		PersistenceManager.getInstance().setWorkingResource(
				this.selectedResource.getOrgName());
		String queryResult = DdiManager.getInstance().getVariableShort();

		// map up result by varname
		SAXParserFactory spf = SAXParserFactory.newInstance();
		XMLReader xmlReader = spf.newSAXParser().getXMLReader();

		IdElementContentHandler contentHandler = new IdElementContentHandler();
		xmlReader.setContentHandler(contentHandler);
		InputSource is = new InputSource(new ByteArrayInputStream(
				queryResult.getBytes()));
		// dak
		System.out.println("Parse VariableShort(1)");
        Logger logger = Logger.getLogger("ddieditor");  
        FileHandler fh;         
        try {  
          fh = new FileHandler("C:/ddieditor/log/ddieditor.log");  
//            fh = new FileHandler("/home/dak/ddieditor.log");  
            logger.addHandler(fh);  
            //logger.setLevel(Level.ALL);  
            SimpleFormatter formatter = new SimpleFormatter();  
            fh.setFormatter(formatter);
        } catch (SecurityException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
        logger.info(queryResult); 
        try {
    		xmlReader.parse(is);
        } catch (Exception e) {
			// TODO: handle exception
        	logger.info("Xerces exception:"+e.getMessage());
		}
		logger.info("Parse VariableShort(2)");
		// dak
		
		// free resources
		is = null;
		queryResult = null;

		// import oxml into dbxml
		FilesystemManager.getInstance().addResource(file);
		PersistenceManager.getInstance().setWorkingResource(file.getName());

		// freq pivot table
		for (Entry<String, IdElement> entry : contentHandler.result.entrySet()) {
			if (entry.getValue().getRepresentationType() == null) { // guard
				throw new DDIFtpException(Translator.trans(
						"spssstat.error.noreptypedef", new Object[] {
								entry.getValue().getName() == null ? "''"
										: entry.getValue().getName(),
								entry.getValue().getId() }), new Throwable());
			}

			if (entry.getValue().getRepresentationType()
					.equals(IdElement.RepresentationType.CODE)
					|| entry.getValue().getRepresentationType()
							.equals(IdElement.RepresentationType.NUMERIC)) {
				createCodeStatistics(entry);
			}
		}
	}

	private void createCodeStatistics(Entry<String, IdElement> entry)
			throws DDIFtpException, Exception {
		// init ddi
		VariableStatisticsDocument varStatDoc = VariableStatisticsDocument.Factory
				.newInstance();
		VariableStatisticsType varStatType = varStatDoc
				.addNewVariableStatistics();

		// var ref
		IdentificationManager.getInstance().addReferenceInformation(
				varStatType.addNewVariableReference(),
				LightXmlObjectUtil.createLightXmlObject(null, null, entry
						.getValue().getId(), entry.getValue().getVersion(),
						"Variable"));

		if (log.isDebugEnabled()) {
			log.debug("Variable: " + entry.getKey() + " - "
					+ entry.getValue().getId());
		}

		// create code statistics
		if (entry.getValue().getRepresentationType()
				.equals(IdElement.RepresentationType.CODE)) {
			createCodeRepresentation(varStatType, entry);
		}

		// create code statistics
		else if (entry.getValue().getRepresentationType()
				.equals(IdElement.RepresentationType.NUMERIC)) {
			createNumericRepresentation(varStatType, entry);
		}

		// guard
		else {
			return;
		}

		// add
		variableStatistics.add(varStatDoc);
	}

	private void createNumericRepresentation(
			VariableStatisticsType varStatType, Entry<String, IdElement> entry)
			throws Exception {
		// valid cases
		createNumericValidMissingSummaryStatistic(varStatType, entry,
				SummaryStatisticTypeCodedType.VALID_CASES, "Valid");

		// invalid cases
		createNumericValidMissingSummaryStatistic(varStatType, entry,
				SummaryStatisticTypeCodedType.INVALID_CASES, "Missing");

		// min
		createNumericSummaryStatistic(varStatType, entry,
				SummaryStatisticTypeCodedType.MINIMUM, "Minimum");

		// max
		createNumericSummaryStatistic(varStatType, entry,
				SummaryStatisticTypeCodedType.MAXIMUM, "Maximum");

		// mean
		createNumericSummaryStatistic(varStatType, entry,
				SummaryStatisticTypeCodedType.MEAN, "Mean");

		// median
		createNumericSummaryStatistic(varStatType, entry,
				SummaryStatisticTypeCodedType.MEDIAN, "Median");

		// mode
		createNumericSummaryStatistic(varStatType, entry,
				SummaryStatisticTypeCodedType.MODE, "Mode");

		// std diviation
		createNumericSummaryStatistic(varStatType, entry,
				SummaryStatisticTypeCodedType.STANDARD_DEVIATION,
				"Std. Deviation");
	}

	private void createNumericSummaryStatistic(
			VariableStatisticsType varStatType, Entry<String, IdElement> entry,
			SummaryStatisticTypeCodedType.Enum type, String spssType)
			throws Exception {
		String spssStatisticType = getSpssStatisticsCategoryByVariableName(
				spssType, entry.getKey());
		if (spssStatisticType != null && spssStatisticType.length() != 0) {
			try {
				CategoryDocument cat = CategoryDocument.Factory
						.parse(spssStatisticType);
				String number = numberFormat.format(cat.getCategory().getCell()
						.getNumber());
				createSummaryStatistic(varStatType, type,
						new BigDecimal(number));
			} catch (Exception e) {
				errorLog.error(entry.getKey(), e);
			}
		}
	}

	private void createNumericValidMissingSummaryStatistic(
			VariableStatisticsType varStatType, Entry<String, IdElement> entry,
			SummaryStatisticTypeCodedType.Enum type, String spssType)
			throws Exception {
		String spssStatisticType = getSpssStatisticsValidMissingCategoryByVariableName(
				spssType, entry.getKey());
		if (spssStatisticType != null && spssStatisticType.length() != 0) {
			try {
				CategoryDocument cat = CategoryDocument.Factory
						.parse(spssStatisticType);
				String number = numberFormat.format(cat.getCategory().getCell()
						.getNumber());
				createSummaryStatistic(varStatType, type,
						new BigDecimal(number));
			} catch (Exception e) {
				errorLog.error(entry.getKey(), e);
			}
		}
	}

	private void createCodeRepresentation(VariableStatisticsType varStatType,
			Entry<String, IdElement> entry) throws Exception {

		String spssPivotTableXml = getSpssFrequencyPivotTableByVariableName(entry
				.getKey());
		if (spssPivotTableXml.equals("")) {
			return;
		}
		PivotTableDocument spssPivotTableDoc = PivotTableDocument.Factory
				.parse(spssPivotTableXml);

		//
		// category frequencies
		//
		createCategoryStatisticsCodes(varStatType, spssPivotTableDoc, "-1");

		//
		// missing frequencies
		//
		createCategoryStatisticsCodes(varStatType, spssPivotTableDoc, "Missing");

		//
		// summary statistics
		//
		createValidSummaryStatistic(varStatType, spssPivotTableDoc, "Valid");
		createTotalSummaryStatistic(varStatType, spssPivotTableDoc);
	}

	private void createCategoryStatisticsCodes(
			VariableStatisticsType varStatType, XmlObject spssPivotTableDoc,
			String groupText) throws Exception {
		// top spss categories
		CategoryDocument[] spssTopCategories = null;
		XmlObject[] test = spssPivotTableDoc.execQuery(omsLocalCategoryFunction
				+ " ddieditor:get_category('" + groupText + "')");
		if (test.length == 0) { // guard
			return;
		}
		spssTopCategories = (CategoryDocument[]) test;

		// spss value labels
		for (int i = 0; i < spssTopCategories.length; i++) {
			// weed out missing and missing total
			if (spssTopCategories[i].getCategory().getText() != null
					&& (spssTopCategories[i].getCategory().getText()
							.equals("Total") || spssTopCategories[i]
							.getCategory().getText().equals("System"))) {
				continue;
			}

			// category value
			String value = numberFormat.format(spssTopCategories[i]
					.getCategory().getNumber());
			CategoryStatisticsType catStatType = varStatType
					.addNewCategoryStatistics();
			catStatType.setCategoryValue(value);

			// missing
			boolean isMissing = groupText.equals("Missing");
			CategoryStatisticType[] cats;
			if (isMissing) {
				cats = new CategoryStatisticType[2];
			} else {
				cats = new CategoryStatisticType[3];
			}

			for (Category spssCategory : spssTopCategories[i].getCategory()
					.getDimension().getCategoryList()) {
				// guard check spss cat type
				if (!SpssStatsToDdiLStatsMap.categoryStatisticTypeMap
						.containsKey(spssCategory.getText()) ||
				// weed out cumulative percent
						SpssStatsToDdiLStatsMap.categoryStatisticTypeMap
								.get(spssCategory.getText())
								.equals(CategoryStatisticTypeCodedType.CUMULATIVE_PERCENT)) {
					continue;
				}

				CategoryStatisticDocument catDoc = CategoryStatisticDocument.Factory
						.newInstance();
				catDoc.addNewCategoryStatistic();

				// type
				CategoryStatisticTypeCodedType catStatCodeType = createCategoryStatisticTypeCoded(catDoc
						.getCategoryStatistic());
				catStatCodeType
						.set(SpssStatsToDdiLStatsMap.categoryStatisticTypeMap
								.get(spssCategory.getText()));

				// valid percent
				if (CategoryStatisticTypeCodedType.Enum.forString(
						catStatCodeType.getStringValue()).equals(
						CategoryStatisticTypeCodedType.USE_OTHER)) {
					catStatCodeType
							.setOtherValue(SpssStatsToDdiLStatsMap.otherCategoryStatisticTypeMap
									.get(spssCategory.getText()));
				}

				// value
				String number;
				if (SpssStatsToDdiLStatsMap.categoryStatisticTypeMap.get(
						spssCategory.getText()).equals(
						CategoryStatisticTypeCodedType.FREQUENCY)) {
					number = spssCategory.getCell().getText();
				} else {
					number = numberFormat.format(spssCategory.getCell()
							.getNumber());
				}
				try {
					catDoc.getCategoryStatistic().setValue(
							new BigDecimal(number));
				} catch (Exception e) {
					if (log.isDebugEnabled()) {
						log.debug("Number format exception for value: "
								+ number);

					}
				}

				// weight
				catDoc.getCategoryStatistic().setWeighted(false);

				// order
				// Percent
				if (catStatCodeType.enumValue().equals(
						CategoryStatisticTypeCodedType.PERCENT)) {
					cats[0] = catDoc.getCategoryStatistic();
				}
				// Valid Percent
				if (catStatCodeType.enumValue().equals(
						CategoryStatisticTypeCodedType.USE_OTHER)) {
					cats[1] = catDoc.getCategoryStatistic();
				}
				// Frequency
				if (catStatCodeType.enumValue().equals(
						CategoryStatisticTypeCodedType.FREQUENCY)) {
					int position = 2;
					if (isMissing) {
						position = 1;
					}
					cats[position] = catDoc.getCategoryStatistic();
				}
			}

			if (cats[0] == null && cats[1] == null) { // guard
				continue;
			} else {
				catStatType.setCategoryStatisticArray(cats);
			}
		}
	}

	private CategoryStatisticTypeCodedType createCategoryStatisticTypeCoded(
			CategoryStatisticType cat) {
		CategoryStatisticTypeCodedType catStatCodeType = (CategoryStatisticTypeCodedType) cat
				.addNewCategoryStatisticType()
				.substitute(
						CategoryStatisticTypeCodedDocument.type
								.getDocumentElementName(),
						CategoryStatisticTypeCodedType.type);

		catStatCodeType.setCodeListAgencyName("DDI");
		catStatCodeType.setCodeListID("Category Statistic Type");
		catStatCodeType.setCodeListVersionID("1.0");
		return catStatCodeType;
	}

	private void createValidSummaryStatistic(
			VariableStatisticsType varStatType, XmlObject spssPivotTableDoc,
			String groupText) throws Exception {
		// top spps categories
		CategoryDocument[] spssTopCategories = null;
		XmlObject[] test = spssPivotTableDoc.execQuery(omsLocalCategoryFunction
				+ " ddieditor:get_category('" + groupText + "')");
		if (test.length == 0) { // guard
			return;
		}
		spssTopCategories = (CategoryDocument[]) test;

		for (Category spssCategory : spssTopCategories[0].getCategory()
				.getDimension().getCategoryList()) {
			// svar procent
			if (spssCategory.getText().equals("Percent")) {
				SummaryStatisticType sumStat = createSummaryStatistic(varStatType);
				SummaryStatisticTypeCodedType summaryStatCode = substituteSummaryStatisticType(sumStat
						.getSummaryStatisticType());
				summaryStatCode.set(SummaryStatisticTypeCodedType.USE_OTHER);
				summaryStatCode.setOtherValue("ValidPercent");

				String number = numberFormat.format(spssCategory.getCell()
						.getNumber());
				sumStat.setValue(new BigDecimal(number));
			}

			// total md%
			if (spssCategory.getText().equals("Valid Percent")) {
				SummaryStatisticType sumStat = createSummaryStatistic(varStatType);
				SummaryStatisticTypeCodedType summaryStatCode = substituteSummaryStatisticType(sumStat
						.getSummaryStatisticType());
				summaryStatCode.set(SummaryStatisticTypeCodedType.USE_OTHER);
				summaryStatCode.setOtherValue("ValidTotalPercent");

				String number = numberFormat.format(spssCategory.getCell()
						.getNumber());
				sumStat.setValue(new BigDecimal(number));
			}
		}
	}

	private void createTotalSummaryStatistic(
			VariableStatisticsType varStatType,
			PivotTableDocument spssPivotTableDoc) {
		Category spssTopCategory = null;

		// variable without missing codes
		if (spssPivotTableDoc.getPivotTable().getDimension().getCategoryList()
				.isEmpty()) {
			// top spss categories
			CategoryDocument[] spssTopCategories = new CategoryDocument[] {};
			Object[] queryResult = spssPivotTableDoc
					.execQuery(omsLocalCategoryFunction
							+ " ddieditor:get_category('Valid')");
			if (queryResult.length != 0) {
				spssTopCategories = (CategoryDocument[]) queryResult;
			}
			if (spssTopCategories.length == 0) { // guard
				return;
			}
			spssTopCategory = spssTopCategories[0].getCategory();
		}
		// variable with missing codes
		else {
			spssTopCategory = spssPivotTableDoc.getPivotTable().getDimension()
					.getCategoryList().get(0);
		}

		for (Category spssCategory : spssTopCategory.getDimension()
				.getCategoryList()) {

			// total %
			if (spssCategory.getText().equals("Percent")) {
				SummaryStatisticType sumStat = createSummaryStatistic(varStatType);
				SummaryStatisticTypeCodedType summaryStatCode = substituteSummaryStatisticType(sumStat
						.getSummaryStatisticType());
				summaryStatCode.set(SummaryStatisticTypeCodedType.USE_OTHER);
				summaryStatCode.setOtherValue("Percent");

				String number = numberFormat.format(spssCategory.getCell()
						.getNumber());
				sumStat.setValue(new BigDecimal(number));
			}

			// total responses
			if (spssCategory.getText().equals("Frequency")) {
				String number = spssCategory.getCell().getText();
				varStatType.setTotalResponses(new BigInteger(number));
			}
		}
	}

	private SummaryStatisticType createSummaryStatistic(
			VariableStatisticsType varStatType,
			SummaryStatisticTypeCodedType.Enum type, BigDecimal value) {
		SummaryStatisticType sumStat = createSummaryStatistic(varStatType);
		SummaryStatisticTypeCodedType summaryStatCode = substituteSummaryStatisticType(sumStat
				.getSummaryStatisticType());
		summaryStatCode.set(type);

		sumStat.setValue(value);
		return sumStat;
	}

	private SummaryStatisticType createSummaryStatistic(
			VariableStatisticsType varStatType) {
		SummaryStatisticType result = varStatType.addNewSummaryStatistic();
		result.setWeighted(false);

		SummaryStatisticTypeCodedType sumStatTypeCode = (SummaryStatisticTypeCodedType) result
				.addNewSummaryStatisticType()
				.substitute(
						SummaryStatisticTypeCodedDocument.type
								.getDocumentElementName(),
						SummaryStatisticTypeCodedType.type);
		sumStatTypeCode.setCodeListAgencyName("DDI");
		sumStatTypeCode.setCodeListID("Summary Statistic Type");
		sumStatTypeCode.setCodeListName("SummaryStatisticType");
		sumStatTypeCode.setCodeListVersionID("1.0");
		sumStatTypeCode
				.setCodeListSchemeURN("http://docs.oasis-open.org/codelist/ns/genericode/1.0/");
		sumStatTypeCode.setCodeListURN("urn:ddi-cv:SummaryStatisticType:1.0");

		return result;
	}

	private SummaryStatisticTypeCodedType substituteSummaryStatisticType(
			CodeValueType codeValue) {
		SummaryStatisticTypeCodedType result = (SummaryStatisticTypeCodedType) codeValue
				.substitute(SummaryStatisticTypeCodedDocument.type
						.getDocumentElementName(),
						SummaryStatisticTypeCodedType.type);
		return result;
	}

	private String getSpssFrequencyPivotTableByVariableName(String variableName)
			throws DDIFtpException, Exception {
		String result = getSpssFrequencyPivotTableByVariableNameImpl(variableName);
		if (result.equals("")) {
			return getSpssFrequencyPivotTableByVariableNameImpl(variableName
					.toLowerCase());
		} else {
			return result;
		}
	}

	String getSpssFrequencyPivotTableByVariableNameImpl(String variableName)
			throws DDIFtpException, Exception {
		String doc = PersistenceManager.getInstance().getResourcePath();

		Formatter formatter = new Formatter();
		formatter.format(
				"ddieditor:getPivotTable(\"%1$s\", \"Frequencies\", \"%2$s\")",
				doc.substring(5, doc.length() - 2), variableName);

		List<String> result = PersistenceManager.getInstance()
				.getPersistenceStorage()
				.query(omsFreqQueryFunction + formatter.toString());

		formatter.close();
		return result.isEmpty() ? "" : result.get(0);
	}

	private String getSpssStatisticsCategoryByVariableName(String statsType,
			String variableName) throws DDIFtpException, Exception {
		String result = getSpssStatisticsCategoryByVariableNameImpl(statsType,
				variableName);
		if (result.equals("")) {
			return getSpssStatisticsCategoryByVariableNameImpl(statsType,
					variableName.toLowerCase());
		} else {
			return result;
		}
	}

	String getSpssStatisticsCategoryByVariableNameImpl(String statsType,
			String variableName) throws DDIFtpException, Exception {
		String doc = PersistenceManager.getInstance().getResourcePath();

		Formatter formatter = new Formatter();
		formatter.format("ddieditor:getPivotTable(\"%1$s\", \"" + statsType
				+ "\", \"%2$s\")", doc.substring(5, doc.length() - 2),
				variableName);

		List<String> result = PersistenceManager.getInstance()
				.getPersistenceStorage()
				.query(omsStatisticsCategoryFunction + formatter.toString());

		formatter.close();
		return result.isEmpty() ? "" : result.get(0);
	}

	private String getSpssStatisticsValidMissingCategoryByVariableName(
			String statsType, String variableName) throws DDIFtpException,
			Exception {
		String result = getSpssStatisticsValidMissingCategoryByVariableNameImpl(
				statsType, variableName);
		if (result.equals("")) {
			return getSpssStatisticsValidMissingCategoryByVariableNameImpl(
					statsType, variableName.toLowerCase());
		} else {
			return result;
		}
	}

	String getSpssStatisticsValidMissingCategoryByVariableNameImpl(
			String statsType, String variableName) throws DDIFtpException,
			Exception {
		String doc = PersistenceManager.getInstance().getResourcePath();

		Formatter formatter = new Formatter();
		formatter.format("ddieditor:getPivotTable(\"%1$s\", \"" + statsType
				+ "\", \"%2$s\")", doc.substring(5, doc.length() - 2),
				variableName);

		List<String> result = PersistenceManager
				.getInstance()
				.getPersistenceStorage()
				.query(omsStatisticsCategoryValidMissingFunction
						+ formatter.toString());

		formatter.close();
		return result.isEmpty() ? "" : result.get(0);
	}

	public void storeDdi() throws Exception {
		PersistenceManager.getInstance().setWorkingResource(
				selectedResource.getOrgName());

		String query = null;
		try {
			query = DdiManager
					.getInstance()
					.getDdi3NamespaceHelper()
					.addFullyQualifiedNamespaceDeclarationToElements(
							"PhysicalInstance/Statistics/VariableStatistics");
		} catch (DDIFtpException e) {
			e.printStackTrace();
		}

		if (!incrementalLoad) {
			// full load - delete all old variable statistics
			try {
				PersistenceManager.getInstance().delete(
						PersistenceManager.getInstance().getResourcePath()
								+ "/" + query);
			} catch (Exception e) {
				// do nothing if Statistics is empty
			}
		} else {
			// incremental load
			// get existing list of VariableStatistics by Variable Reference
			StatisticsDocument stats = DdiManager.getInstance()
					.getStatisticsVariableReference();
			for (VariableStatisticsType varStatictics : stats.getStatistics()
					.getVariableStatisticsList()) {
				IDType id = varStatictics.getVariableReference().getIDList()
						.get(0);
				varStatisticsMap.put(id.getStringValue(), varStatictics);
			}
		}

		// look up physical instance (with Statistics)
		// - Statistics with or without VariableStatistics
		LightXmlObjectListDocument lightXmlObjectListDoc = DdiManager
				.getInstance()
				.getPhysicalInstancesLight(null, null, null, null);
		if (lightXmlObjectListDoc.getLightXmlObjectList()
				.getLightXmlObjectList().isEmpty()) { // guard
			throw new DDIFtpException("No Physical Instance found",
					new Throwable());
		}
		LightXmlObjectType lightXmlObject = lightXmlObjectListDoc
				.getLightXmlObjectList().getLightXmlObjectList().get(0);

		// store VariableStatistics
		int count = 0;
		for (VariableStatisticsDocument varStat : variableStatistics) {
			// only store if statistics available
			if (varStat.getVariableStatistics().getSummaryStatisticList().size() > 0) {
				String id = varStat.getVariableStatistics()
						.getVariableReference().getIDList().get(0)
						.getStringValue();

				// delete if incremental load
				if (varStatisticsMap.get(id) != null) {
					deleteVariableStatistics(id);
				}

				if (count == doHouseKeeping) {
					// clean logs
					PersistenceManager.getInstance().getPersistenceStorage()
							.houseKeeping();
					count = 0;
				} else {
					count++;
				}

				storeVariableStatistics(varStat);

			}
		}

		// final house keeping
		PersistenceManager.getInstance().getPersistenceStorage().houseKeeping();
	}

	private void deleteVariableStatistics(String varID) throws Exception {

		StringBuffer q = new StringBuffer();
		q.append("for $variableStatistics in ");
		q.append(PersistenceManager.getInstance().getResourcePath() + "/");
		q.append(DdiManager
				.getInstance()
				.getDdi3NamespaceHelper()
				.addFullyQualifiedNamespaceDeclarationToElements(
						"VariableStatistics")
				+ " ");
		q.append("for $variableReference in $variableStatistics/");
		q.append(DdiManager
				.getInstance()
				.getDdi3NamespaceHelper()
				.addFullyQualifiedNamespaceDeclarationToElements(
						"physicalinstance__VariableReference")
				+ " ");
		q.append("where $variableReference/");
		q.append(DdiManager.getInstance().getDdi3NamespaceHelper()
				.addFullyQualifiedNamespaceDeclarationToElements("ID"));
		q.append(" = '" + varID + "' ");
		q.append("return ( delete node $variableStatistics )");

		String location = q.toString();

		PersistenceManager.getInstance().delete(location);
	}

	private void storeVariableStatistics(VariableStatisticsDocument varStat)
			throws Exception {
		String query = null;
		try {
			query = DdiManager
					.getInstance()
					.getDdi3NamespaceHelper()
					.addFullyQualifiedNamespaceDeclarationToElements(
							"PhysicalInstance/Statistics");
		} catch (DDIFtpException e) {
			e.printStackTrace();
		}

		PersistenceManager.getInstance().insert(
				DdiManager
						.getInstance()
						.getDdi3NamespaceHelper()
						.substitutePrefixesFromElements(
								varStat.xmlText(DdiManager.getInstance()
										.getXmlOptions())),
				XQueryInsertKeyword.AS_LAST_NODE,
				PersistenceManager.getInstance().getResourcePath() + "/"
						+ query);
	}

	public void cleanUp() {
		// delete oxml from storage
		try {
			PersistenceManager.getInstance().setWorkingResource(file.getName());
			PersistenceManager.getInstance().deleteResource(file.getName());
			PersistenceManager.getInstance().deleteStorage(
					PersistenceManager.getStorageId(file));
		} catch (DDIFtpException e) {
			// do nothing
		}

		// reset selected resource as working resource
		try {
			PersistenceManager.getInstance().setWorkingResource(
					this.selectedResource.getOrgName());
		} catch (Exception e2) {
			// do nothing
		}
	}
}