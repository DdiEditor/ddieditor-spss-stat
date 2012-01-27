package dk.dda.ddieditor.spss.stat.command;

import java.io.File;

import org.ddialliance.ddieditor.model.resource.DDIResourceType;
import org.ddialliance.ddieditor.persistenceaccess.PersistenceManager;
import org.ddialliance.ddieditor.persistenceaccess.filesystem.FilesystemManager;

public class SpssStatsImportRunnable implements Runnable {
	public DDIResourceType selectedResource = null;
	public String inOxmlFile = null;

	public SpssStatsImportRunnable(DDIResourceType selectedResource,
			String inOxmlFile) {
		super();
		this.selectedResource = selectedResource;
		this.inOxmlFile = inOxmlFile;
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
		// import into dbxml
		File file = new File(inOxmlFile);
		FilesystemManager.getInstance()
				.addResource(file);

		// set working resource
		PersistenceManager.getInstance()
				.setWorkingResource(file.getName());
		
		// query for stats line
		
		// query ddi vars ref resolution		
		
		// create ddi
				
	}
}
