package dk.dda.ddieditor.spss.stat.command;

import java.lang.reflect.InvocationTargetException;

import org.ddialliance.ddiftp.util.Translator;
import org.ddialliance.ddiftp.util.log.Log;
import org.ddialliance.ddiftp.util.log.LogFactory;
import org.ddialliance.ddiftp.util.log.LogType;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
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
			final SpssStatsImportRunnable longJob = new SpssStatsImportRunnable(
					statsWizard.selectedResource, statsWizard.inOxmlFile);
			try {
				PlatformUI.getWorkbench().getProgressService()
						.busyCursorWhile(new IRunnableWithProgress() {
							@Override
							public void run(IProgressMonitor monitor)
									throws InvocationTargetException,
									InterruptedException {
								monitor.beginTask(Translator
										.trans("spssstat.wizard.title"), 1);
								PlatformUI.getWorkbench().getDisplay()
										.asyncExec(longJob);
								monitor.worked(1);
							}
						});
			} catch (Exception e) {
				throw new ExecutionException(
						Translator.trans("spssstat.error"), e.getCause());
			}
		}
		return null;
	}
}
