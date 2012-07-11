package dk.dda.ddieditor.spss.stat.command;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map.Entry;

import javax.xml.parsers.SAXParserFactory;

import org.apache.xmlbeans.XmlObject;
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
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.SummaryStatisticTypeCodedType.Enum;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.VariableStatisticsDocument;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.VariableStatisticsType;
import org.ddialliance.ddi3.xml.xmlbeans.reusable.CodeValueType;
import org.ddialliance.ddieditor.logic.identification.IdentificationManager;
import org.ddialliance.ddieditor.model.DdiManager;
import org.ddialliance.ddieditor.model.lightxmlobject.LightXmlObjectListDocument;
import org.ddialliance.ddieditor.model.lightxmlobject.LightXmlObjectType;
import org.ddialliance.ddieditor.model.resource.DDIResourceType;
import org.ddialliance.ddieditor.persistenceaccess.PersistenceManager;
import org.ddialliance.ddieditor.persistenceaccess.XQueryInsertKeyword;
import org.ddialliance.ddieditor.persistenceaccess.filesystem.FilesystemManager;
import org.ddialliance.ddieditor.ui.editor.Editor;
import org.ddialliance.ddieditor.util.LightXmlObjectUtil;
import org.ddialliance.ddiftp.util.DDIFtpException;
import org.ddialliance.ddiftp.util.Translator;
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
 * SPSS Statistics DdiEditor Import thread implementation.<br><br> 
 * Workflow:<br>
 * 1. Store and SPSS OXML in DBXMl<br>
 * 2. Transform SPSS OXML to DDI-L<br>
 * 3. Store DDI-L on selected resource<br>
 */
public class SpssStatsImportRunnable implements Runnable {
	public DDIResourceType selectedResource = null;
	public String inOxmlFile = null;

	File file;
	String declareNamspaces = "declare namespace oms='http://xml.spss.com/spss/oms';"
			+ "declare namespace ddieditor= 'http://dda.dk/ddieditor';";
	String omsFreqQueryFunction;
	String omsLocalCategoryFunction;
	String omsStatisticsCategoryFunction;
	String query;

	List<VariableStatisticsDocument> variableStatistics = new ArrayList<VariableStatisticsDocument>();

	NumberFormat dFormat = NumberFormat.getInstance();

	public SpssStatsImportRunnable(DDIResourceType selectedResource,
			String inOxmlFile) {
		super();
		this.selectedResource = selectedResource;
		this.inOxmlFile = inOxmlFile;

		StringBuilder q = new StringBuilder();

		// omsFreqQueryFunction
		q.append(declareNamspaces);
		q.append("declare function ddieditor:getPivotTable($doc as xs:string, $type as xs:string, $varname as xs:string) as element()* {");
		q.append(" for $x in doc($doc)//oms:outputTree/oms:command/oms:heading/oms:pivotTable");
		q.append(" where $x/@subType=$type and $x/@varName=$varname");
		q.append(" return $x};");
		omsFreqQueryFunction = q.toString();

		// omsLocalCategoryFunction
		q.delete(0, q.length());
		q.append(declareNamspaces);
		q.append("declare function ddieditor:get_category($group_text) as element()* {");
		q.append("let $category :=  for $x in $this//oms:group where $x/@text=$group_text return $x/oms:category return $category");
		q.append("};");
		omsLocalCategoryFunction = q.toString();

		// omsStatisticsCategoryFunction
		q.delete(0, q.length());
		q.append(declareNamspaces);
		q.append("declare function ddieditor:getCategories($doc as xs:string, $type as xs:string, $varname as xs:string) as element()* {");
		q.append(" let $table := for $x in doc($doc)//oms:pivotTable where $x/@subType='Statistics' return $x");
		q.append(" let $cat := for $j in $table//oms:category where $j/@text=$type return $j");
		q.append(" for $y in $cat//oms:category where $y/@varName=$varname return $y");
		q.append("};");
		omsStatisticsCategoryFunction = q.toString();

		try {
			query = DdiManager
					.getInstance()
					.getDdi3NamespaceHelper()
					.addFullyQualifiedNamespaceDeclarationToElements(
							"PhysicalInstance/Statistics");
		} catch (DDIFtpException e) {
			e.printStackTrace();
		}

		dFormat.setRoundingMode(RoundingMode.HALF_EVEN);
		dFormat.setMaximumFractionDigits(-1);
		dFormat.setGroupingUsed(false);
	}

	@Override
	public void run() {
		try {
			file = new File(inOxmlFile);
			importStats();
			storeDdi();
		} catch (Exception e) {
			Editor.showError(e, null);
		} finally {
			// delete oxml from storage
			try {
				PersistenceManager.getInstance().setWorkingResource(
						file.getName());
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

	public void importStats() throws Exception {
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
		xmlReader.parse(is);

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
					| entry.getValue().getRepresentationType()
							.equals(IdElement.RepresentationType.NUMERIC)) {
				createCodeStatistics(entry);
			}
		}
	}

	public void createCodeStatistics(Entry<String, IdElement> entry)
			throws DDIFtpException, Exception {
		String spssPivotTableXml = getSpssPivotTableByVariableName(entry
				.getKey());
		if (spssPivotTableXml.equals("")) {
			return;
		}
		PivotTableDocument spssPivotTableDoc = PivotTableDocument.Factory
				.parse(spssPivotTableXml);

		// numeric var -min, max
		String min = null, max = null, xmlTmp = null;
		if (entry.getValue().getRepresentationType()
				.equals(IdElement.RepresentationType.NUMERIC)) {
			// min
			xmlTmp = getSpssStatisticsByVariableNameAndtype(entry.getKey(),
					"Minimum");
			if (xmlTmp != null) {
				CategoryDocument doc = CategoryDocument.Factory.parse(xmlTmp);
				if (doc.getCategory().getCell() != null) {
					min = dFormat.format(doc.getCategory().getCell()
							.getNumber());
				}
			}

			// max
			xmlTmp = getSpssStatisticsByVariableNameAndtype(entry.getKey(),
					"Maximum");
			if (xmlTmp != null) {
				CategoryDocument doc = CategoryDocument.Factory.parse(xmlTmp);
				if (doc.getCategory().getCell() != null) {
					max = dFormat.format(doc.getCategory().getCell()
							.getNumber());
				}
			}

			if (max == null && min == null) {
				return;
			}
		}

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

		//
		// numeric min - max
		//
		if (max != null || min != null) {
			if (min != null) {
				createNumericStatisticsCodes(varStatType,
						SummaryStatisticTypeCodedType.MINIMUM, min);
			}
			if (max != null) {
				createNumericStatisticsCodes(varStatType,
						SummaryStatisticTypeCodedType.MAXIMUM, max);
			}
		} else {
			//
			// category frequencies
			//
			createCategoryStatisticsCodes(varStatType, spssPivotTableDoc, "-1");

			//
			// category missing frequencies
			//
			createCategoryStatisticsCodes(varStatType, spssPivotTableDoc,
					"Missing");

			//
			// summary statistics
			//
			createValidSummaryStatistic(varStatType, spssPivotTableDoc, "Valid");
			createTotalSummaryStatistic(varStatType, spssPivotTableDoc);
		}

		// add
		variableStatistics.add(varStatDoc);
	}

	private void createCategoryStatisticsCodes(
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

		// spss value labels
		for (int i = 0; i < spssTopCategories.length; i++) {
			// weed out missing total
			if (spssTopCategories[i].getCategory().getText() != null
					&& spssTopCategories[i].getCategory().getText()
							.equals("Total")) {
				continue;
			}

			// category value
			CategoryStatisticsType catStatType = varStatType
					.addNewCategoryStatistics();
			catStatType.setCategoryValue(Long.toString(Math
					.round(spssTopCategories[i].getCategory().getNumber())));

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
					number = dFormat.format(spssCategory.getCell().getNumber());
				}
				catDoc.getCategoryStatistic().setValue(new BigDecimal(number));

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

				String number = dFormat.format(spssCategory.getCell()
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

				String number = dFormat.format(spssCategory.getCell()
						.getNumber());
				sumStat.setValue(new BigDecimal(number));
			}
		}
	}

	private void createTotalSummaryStatistic(
			VariableStatisticsType varStatType,
			PivotTableDocument spssPivotTableDoc) {
		if (spssPivotTableDoc.getPivotTable().getDimension().getCategoryList()
				.isEmpty()) { // guard
			return;
		}

		Category spssTopCategory = spssPivotTableDoc.getPivotTable()
				.getDimension().getCategoryList().get(0);
		for (Category spssCategory : spssTopCategory.getDimension()
				.getCategoryList()) {

			// total %
			if (spssCategory.getText().equals("Percent")) {
				SummaryStatisticType sumStat = createSummaryStatistic(varStatType);
				SummaryStatisticTypeCodedType summaryStatCode = substituteSummaryStatisticType(sumStat
						.getSummaryStatisticType());
				summaryStatCode.set(SummaryStatisticTypeCodedType.USE_OTHER);
				summaryStatCode.setOtherValue("Percent");

				String number = dFormat.format(spssCategory.getCell()
						.getNumber());
				sumStat.setValue(new BigDecimal(number));
			}

			// total responces
			if (spssCategory.getText().equals("Frequency")) {
				String number = spssCategory.getCell().getText();
				varStatType.setTotalResponses(new BigInteger(number));
			}
		}
	}

	private SummaryStatisticType createNumericStatisticsCodes(
			VariableStatisticsType varStatType, Enum enumType, String value) {
		SummaryStatisticType sumStat = createSummaryStatistic(varStatType);
		SummaryStatisticTypeCodedType summaryStatCode = substituteSummaryStatisticType(sumStat
				.getSummaryStatisticType());

		// type
		summaryStatCode.set(enumType);

		// value
		sumStat.setValue(new BigDecimal(value));

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
		sumStatTypeCode.setCodeListVersionID("1.0");

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

	private String getSpssPivotTableByVariableName(String variableName)
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

	private String getSpssStatisticsByVariableNameAndtype(String variableName,
			String type) throws DDIFtpException, Exception {
		String doc = PersistenceManager.getInstance().getResourcePath();

		Formatter formatter = new Formatter();
		formatter.format("ddieditor:getCategories(\"%1$s\",\"%2$s\",\"%3$s\")",
				doc.substring(5, doc.length() - 2), type, variableName);

		List<String> result = PersistenceManager.getInstance()
				.getPersistenceStorage()
				.query(omsStatisticsCategoryFunction + formatter.toString());
		formatter.close();

		return result.isEmpty() ? "" : result.get(0);
	}

	private void storeDdi() throws Exception {
		PersistenceManager.getInstance().setWorkingResource(
				selectedResource.getOrgName());

		// delete old stat
		try {
			PersistenceManager.getInstance().delete(
					PersistenceManager.getInstance().getResourcePath() + "/"
							+ query);
		} catch (Exception e) {
			// do nothing if stats is null
		}

		// new stat
		StatisticsDocument statsDoc = StatisticsDocument.Factory.newInstance();
		StatisticsType statsType = statsDoc.addNewStatistics();

		// look up physical instance
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

		// store stat
		DdiManager.getInstance().createElement(statsDoc,
				lightXmlObject.getId(),
				lightXmlObject.getVersion(),
				"PhysicalInstance",
				// parent sub-elements
				new String[] { "UserID", "VersionRationale",
						"VersionResponsibility", "PhysicalInstanceModuleName",
						"Citation", "Fingerprint", "Coverage", "OtherMaterial",
						"Note", "RecordLayoutReference",
						"DataFileIdentification", "GrossFileStructure",
						"ProprietaryInfo" },
				// stop elements
				new String[] { "ByteOrder" },
				// jump elements
				new String[] { "Statistics" });

		// store var stat
		for (VariableStatisticsDocument varStat : variableStatistics) {
			storeVariableStatistics(varStat);
		}

		// persistence house keeping
		PersistenceManager.getInstance().getPersistenceStorage().houseKeeping();
	}

	private void storeVariableStatistics(VariableStatisticsDocument varStat)
			throws Exception {
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
}
