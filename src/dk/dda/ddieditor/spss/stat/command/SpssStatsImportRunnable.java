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
import org.ddialliance.ddieditor.logic.identification.IdentificationManager;
import org.ddialliance.ddieditor.model.DdiManager;
import org.ddialliance.ddieditor.model.resource.DDIResourceType;
import org.ddialliance.ddieditor.persistenceaccess.PersistenceManager;
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

public class SpssStatsImportRunnable implements Runnable {
	public DDIResourceType selectedResource = null;
	public String inOxmlFile = null;

	File file;
	String declareNamspaces = "declare namespace oms='http://xml.spss.com/spss/oms';"
			+ "declare namespace ddieditor= 'http://dda.dk/ddieditor';";
	String omsFreqQueryFunction;
	String omsLocalCategoryFunction;

	List<VariableStatisticsType> variableStatistics = new ArrayList<VariableStatisticsType>();

	NumberFormat dFormat = NumberFormat.getInstance();

	public SpssStatsImportRunnable(DDIResourceType selectedResource,
			String inOxmlFile) {
		super();
		this.selectedResource = selectedResource;
		this.inOxmlFile = inOxmlFile;

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
		q.append("let $category :=  for $x in $this//oms:group where $x/@text=$group_text return $x/oms:category return $category");
		q.append("};");
		omsLocalCategoryFunction = q.toString();

		dFormat.setRoundingMode(RoundingMode.HALF_EVEN);
		dFormat.setMaximumFractionDigits(0);
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
			try {
				PersistenceManager.getInstance().deleteResource(file.getName());
				PersistenceManager.getInstance().deleteStorage(
						PersistenceManager.getStorageId(file));
			} catch (DDIFtpException e) {
				// do nothing
			}
		}
	}

	public void importStats() throws Exception {
		// query ddi vars varname, id version agency
		// String queryResult =
		// "<IdElement id=\"id\" version=\"version\" agency=\"agency\" name=\"name\"/>";
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
					.equals(IdElement.RepresentationType.CODE)) {
				createCodeStatistics(entry);
			}
		}
	}

	public void createCodeStatistics(Entry<String, IdElement> entry)
			throws DDIFtpException, Exception {
		String spssPivotTableXml = getSpssPivotTableByVariableName(entry
				.getKey().toLowerCase());
		if (spssPivotTableXml.equals("")) {
			return;
		}
		PivotTableDocument spssPivotTableDoc = PivotTableDocument.Factory
				.parse(spssPivotTableXml);

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

		// total responces
		// varStatType.setTotalResponses(new BigInteger(""));

		// sum percent

		// sum valid percent
		// valid total percent

		variableStatistics.add(varStatType);
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
			// category value
			CategoryStatisticsType catStatType = varStatType
					.addNewCategoryStatistics();
			catStatType.setCategoryValue(dFormat.format(spssTopCategories[i]
					.getCategory().getNumber()));

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
				CategoryStatisticType cat = catStatType
						.addNewCategoryStatistic();

				// type
				CodeValueType codeValue = cat.addNewCategoryStatisticType();
				CategoryStatisticTypeCodedType catStatCodeType = (CategoryStatisticTypeCodedType) codeValue
						.substitute(CategoryStatisticTypeCodedDocument.type
								.getDocumentElementName(),
								CategoryStatisticTypeCodedType.type);
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
				cat.setValue(new BigDecimal(number));

				// weight
				cat.setWeighted(false);
			}
		}
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
			if (spssCategory.getText().equals("Percent")) {
				SummaryStatisticType sumStat = varStatType
						.addNewSummaryStatistic();
				SummaryStatisticTypeCodedType summaryStatCode = substituteSummaryStatisticType(sumStat
						.addNewSummaryStatisticType());
				summaryStatCode.set(SummaryStatisticTypeCodedType.USE_OTHER);
				summaryStatCode.setOtherValue("ValidTotalPercent");

				String number = dFormat.format(spssCategory.getCell()
						.getNumber());
				sumStat.setValue(new BigDecimal(number));
			}

			if (spssCategory.getText().equals("Valid Percent")) {
				SummaryStatisticType sumStat = varStatType
						.addNewSummaryStatistic();
				SummaryStatisticTypeCodedType summaryStatCode = substituteSummaryStatisticType(sumStat
						.addNewSummaryStatisticType());
				summaryStatCode.set(SummaryStatisticTypeCodedType.USE_OTHER);
				summaryStatCode.setOtherValue("SumValidPerent");

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
			if (spssCategory.getText().equals("Percent")) {
				SummaryStatisticType sumStat = varStatType
						.addNewSummaryStatistic();
				SummaryStatisticTypeCodedType summaryStatCode = substituteSummaryStatisticType(sumStat
						.addNewSummaryStatisticType());
				summaryStatCode.set(SummaryStatisticTypeCodedType.USE_OTHER);
				summaryStatCode.setOtherValue("SumPercent");

				String number = dFormat.format(spssCategory.getCell()
						.getNumber());
				sumStat.setValue(new BigDecimal(number));
			}

			if (spssCategory.getText().equals("Valid Percent")) {
				SummaryStatisticType sumStat = varStatType
						.addNewSummaryStatistic();
				SummaryStatisticTypeCodedType summaryStatCode = substituteSummaryStatisticType(sumStat
						.addNewSummaryStatisticType());
				summaryStatCode.set(SummaryStatisticTypeCodedType.USE_OTHER);
				summaryStatCode.setOtherValue("SumValidPerent");

				String number = dFormat.format(spssCategory.getCell()
						.getNumber());
				sumStat.setValue(new BigDecimal(number));
			}

			if (spssCategory.getText().equals("Frequency")) {
				String number = spssCategory.getCell().getText();
				varStatType.setTotalResponses(new BigInteger(number));
			}
		}
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

	private void storeDdi() {
		StatisticsDocument statsDoc = StatisticsDocument.Factory.newInstance();
		StatisticsType statsType = statsDoc.addNewStatistics();
		statsType.setVariableStatisticsArray(variableStatistics
				.toArray(new VariableStatisticsType[] {}));

		System.out.println(statsDoc);
		// insert into persistence storage
		// DdiManager.getInstance().
	}
}
