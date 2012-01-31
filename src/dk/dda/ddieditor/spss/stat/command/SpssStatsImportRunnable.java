package dk.dda.ddieditor.spss.stat.command;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map.Entry;

import javax.xml.parsers.SAXParserFactory;

import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticType;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticTypeCodedDocument;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticTypeCodedType;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticsType;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.StatisticsDocument;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.StatisticsType;
import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.SummaryStatisticType;
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

		dFormat.setRoundingMode(RoundingMode.HALF_EVEN);
		dFormat.setMaximumFractionDigits(0);
	}

	@Override
	public void run() {
		try {
			
			file = new File(inOxmlFile);
			importStats();
			
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

		// store ddi
		for (VariableStatisticsType varStat : variableStatistics) {
			System.out.println(varStat); 
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

		IdentificationManager.getInstance().addReferenceInformation(
				varStatType.addNewVariableReference(),
				LightXmlObjectUtil.createLightXmlObject(null, null, entry
						.getValue().getId(), entry.getValue().getVersion(),
						"Variable"));

		CategoryStatisticsType catStatType = varStatType
				.addNewCategoryStatistics();
		
		//
		// category frequencies
		//
		CategoryDocument[] spssTopCategories = (CategoryDocument[]) spssPivotTableDoc
				.execQuery(declareNamspaces
						+ "let $category :=  for $x in $this//oms:group where $x/@text=\"-1\" return $x/oms:category return $category");
		for (int i = 0; i < spssTopCategories.length; i++) {
			// category value
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

		//
		// missing frequencies
		//
		
		//
		// summary statistics
		//
		SummaryStatisticType sumStatType = varStatType.addNewSummaryStatistic();

		// total responces
		// varStatType.setTotalResponses(new BigInteger(""));

		// sum percent

		// sum valid percent
		// valid total percent

		variableStatistics.add(varStatType);
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

		// insert into persistence storage
		// DdiManager.getInstance().
	}
}
