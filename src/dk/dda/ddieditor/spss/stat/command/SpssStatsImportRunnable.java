package dk.dda.ddieditor.spss.stat.command;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Formatter;
import java.util.List;
import java.util.Map.Entry;

import javax.xml.parsers.SAXParserFactory;

import org.ddialliance.ddieditor.model.DdiManager;
import org.ddialliance.ddieditor.model.resource.DDIResourceType;
import org.ddialliance.ddieditor.persistenceaccess.PersistenceManager;
import org.ddialliance.ddieditor.persistenceaccess.filesystem.FilesystemManager;
import org.ddialliance.ddiftp.util.DDIFtpException;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.spss.xml.spss.oms.PivotTableDocument;

import dk.dda.ddieditor.spss.stat.idelement.IdElement;
import dk.dda.ddieditor.spss.stat.idelement.IdElementContentHandler;

public class SpssStatsImportRunnable implements Runnable {
	public DDIResourceType selectedResource = null;
	public String inOxmlFile = null;
	public String omsFreqQueryFunction;

	public SpssStatsImportRunnable(DDIResourceType selectedResource,
			String inOxmlFile) {
		super();
		this.selectedResource = selectedResource;
		this.inOxmlFile = inOxmlFile;

		StringBuilder q = new StringBuilder();
		q.append("declare namespace oms=\"http://xml.spss.com/spss/oms\";");
		q.append("declare namespace ddieditor= \"http://dda.dk/ddieditor\";");
		q.append("declare function ddieditor:getPivotTable($doc as xs:string, $type as xs:string, $varname as xs:string) as element()* {");
		q.append(" for $x in doc($doc)//oms:outputTree/oms:command/oms:heading/oms:pivotTable");
		q.append(" where $x/@subType=$type and $x/@varName=$varname");
		q.append(" return $x};");
		omsFreqQueryFunction = q.toString();
	}

	@Override
	public void run() {
		try {
			importStats();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void importStats() throws Exception {
		// query ddi vars varname, id version agency
		// String queryResult =
		// "<IdElement id=\"id\" version=\"version\" agency=\"agency\" name=\"name\"/>";
		PersistenceManager.getInstance().setWorkingResource(this.selectedResource.getOrgName());
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
		File file = new File(inOxmlFile);
		FilesystemManager.getInstance().addResource(file);
		PersistenceManager.getInstance().setWorkingResource(file.getName());

		// freq pivot table
		String pivotXml;
		for (Entry<String, IdElement> entry : contentHandler.result.entrySet()) {
			pivotXml = getPivotTableByVariableName(entry.getKey().toLowerCase());
			if (pivotXml.equals("")) {
				continue;
			}
			PivotTableDocument pivotTableDoc = PivotTableDocument.Factory
					.parse(pivotXml);
			System.out.println(pivotTableDoc.getPivotTable().getVarName());
			// create ddi
			// http://xmlbeans.apache.org/docs/2.0.0/guide/conSelectingXMLwithXQueryPathXPath.html
		}

		// store ddi

		// finalize
		PersistenceManager.getInstance().deleteResource(file.getName());
		PersistenceManager.getInstance().deleteStorage(
				PersistenceManager.getStorageId(file));
	}

	private String getPivotTableByVariableName(String variableName)
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
}
