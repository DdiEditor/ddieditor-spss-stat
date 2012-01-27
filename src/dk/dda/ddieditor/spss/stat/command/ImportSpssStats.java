package dk.dda.ddieditor.spss.stat.command;

import org.ddialliance.ddiftp.util.log.Log;
import org.ddialliance.ddiftp.util.log.LogFactory;
import org.ddialliance.ddiftp.util.log.LogType;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.ui.PlatformUI;

import dk.dda.ddieditor.spss.stat.wizard.SpssStatsWizard;

public class ImportSpssStats extends org.eclipse.core.commands.AbstractHandler {
	private Log log = LogFactory.getLog(LogType.SYSTEM, ImportSpssStats.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// open dialog
		SpssStatsWizard statsWizard = new SpssStatsWizard();
		WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench()
				.getDisplay().getActiveShell(), statsWizard);

		int returnCode = dialog.open();
		if (returnCode != Window.CANCEL) {
			// import
			SpssStatsImportRunnable longJob = new SpssStatsImportRunnable(
					statsWizard.selectedResource, statsWizard.outOxmlFile);
			BusyIndicator.showWhile(PlatformUI.getWorkbench().getDisplay(),
					longJob);

			// refresh
			// ViewManager.getInstance().addViewsToRefresh(
			// new String[] { CodeSchemeEditor.ID,
			// CategorySchemeEditor.ID, VariableSchemeEditor.ID });
			// ViewManager.getInstance().refesh();
		}
		return null;
	}
}
