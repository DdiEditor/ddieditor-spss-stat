package dk.dda.ddieditor.spss.stat.wizard;

import java.io.File;
import java.util.List;

import org.ddialliance.ddieditor.model.resource.DDIResourceType;
import org.ddialliance.ddieditor.persistenceaccess.PersistenceManager;
import org.ddialliance.ddieditor.ui.editor.Editor;
import org.ddialliance.ddieditor.ui.preference.PreferenceUtil;
import org.ddialliance.ddiftp.util.DDIFtpException;
import org.ddialliance.ddiftp.util.Translator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.PlatformUI;

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
 * RCP Wizard for SPSS Statistics import
 */
public class SpssStatsWizard extends Wizard {

	private List<DDIResourceType> resources = null;

	public DDIResourceType selectedResource = null;
	public String inOxmlFile = null;
	public boolean incrementalLoad = false;
	public boolean ignoreInconsistency = false;
	public boolean charSetValidation = false;

	@Override
	public boolean performFinish() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public void addPages() {
		SelectPage rangePage = new SelectPage();
		addPage(rangePage);
	}

	class SelectPage extends WizardPage {
		public static final String PAGE_NAME = "select";

		public SelectPage() {
			super(PAGE_NAME, Translator.trans("spssstat.wizard.title"), null);
		}

		void pageComplete() {
			if (inOxmlFile != null && selectedResource != null) {
				setPageComplete(true);
			}
		}

		@Override
		public void createControl(Composite parent) {
			final Editor editor = new Editor();
			Group group = editor.createGroup(parent,
					Translator.trans("spssstat.wizard.title"));

			// spss file
			editor.createLabel(group,
					Translator.trans("spssstat.filechooser.title"));
			final Text pathText = editor.createText(group, "");
			pathText.addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent e) {
					// on a CR - check if file exist and read it
					if (e.keyCode == SWT.CR) {
						inOxmlFile = readFile(pathText);
					}
				}
			});
			pathText.addTraverseListener(new TraverseListener() {
				public void keyTraversed(TraverseEvent e) {
					// on a TAB - check if file exist and read it
					switch (e.detail) {
					case SWT.TRAVERSE_TAB_NEXT:
					case SWT.TRAVERSE_TAB_PREVIOUS: {
						inOxmlFile = readFile(pathText);
						if (inOxmlFile == null) {
							e.doit = false;
						}
					}
					}
				}
			});
			Button pathBrowse = editor.createButton(group,
					Translator.trans("spss.filechooser.browse"));
			pathBrowse.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					FileDialog fileChooser = new FileDialog(PlatformUI
							.getWorkbench().getDisplay().getActiveShell());
					fileChooser.setText(Translator
							.trans("spssstat.filechooser.title"));
					fileChooser.setFilterExtensions(new String[] { "*.xml",
							"*.*" });
					fileChooser.setFilterNames(new String[] {
							Translator.trans("spssstat.filternames.spssoxml"),
							Translator.trans("spssstat.filternames.anyfile") });

					PreferenceUtil.setPathFilter(fileChooser);
					inOxmlFile = fileChooser.open();
					PreferenceUtil.setLastBrowsedPath(inOxmlFile);

					pathText.setText(inOxmlFile);
					pageComplete();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
					// do nothing
				}
			});

			// loaded resources
			try {
				resources = PersistenceManager.getInstance().getResources();
			} catch (DDIFtpException e) {
				MessageDialog.openError(PlatformUI.getWorkbench().getDisplay()
						.getActiveShell(), Translator.trans("ErrorTitle"),
						e.getMessage());
			}

			String[] options = new String[resources.size()];
			int count = 0;
			for (DDIResourceType resource : resources) {
				options[count] = resource.getOrgName();
				count++;
			}
			editor.createLabel(group, Translator.trans("spss.resource.select"));
			Combo combo = editor.createCombo(group, options);
			if (options.length == 1) {
				combo.select(0);
				selectedResource = resources.get(0);
			} else {
				combo.addSelectionListener(new SelectionListener() {
					@Override
					public void widgetSelected(SelectionEvent event) {
						Combo c = (Combo) event.getSource();
						selectedResource = resources.get(c.getSelectionIndex());
						pageComplete();
					}

					@Override
					public void widgetDefaultSelected(SelectionEvent event) {
						// do nothing
					}
				});
			}
			
			// Full or incremental load of Statistics
			Button loadTypeButton = editor.createCheckBox(group, "", Translator
					.trans("spssstat.option.incrementalload",
							pathText.getText()));
			loadTypeButton.setSelection(false);
			loadTypeButton.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					incrementalLoad = ((Button) e.widget).getSelection();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
					// do nothing
				}
			});

			// SPSS metadata inconsistency check
			// Button inconsistencyButton = editor.createCheckBox(group, "",
			// Translator
			// .trans("spssstat.option.ignoreinconsistency",
			// pathText.getText()));
			// inconsistencyButton.setSelection(false);
			// inconsistencyButton.addSelectionListener(new SelectionListener()
			// {
			// @Override
			// public void widgetSelected(SelectionEvent e) {
			// ignoreInconsistency = ((Button) e.widget).getSelection();
			// }
			//
			// @Override
			// public void widgetDefaultSelected(SelectionEvent e) {
			// // do nothing
			// }
			// });

			// character set validation check
			Button charValidationButton = editor.createCheckBox(group, "", Translator
					.trans("spssstat.option.charsetvalidation",
							pathText.getText()));
			charValidationButton.setSelection(false);
			charValidationButton.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					charSetValidation = ((Button) e.widget).getSelection();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
					// do nothing
				}
			});

			// finalize
			setControl(group);
			setPageComplete(false);
		}

		private String readFile(Text pathText) {
			if (!new File(pathText.getText()).exists()) {
				MessageDialog
						.openError(PlatformUI.getWorkbench().getDisplay()
								.getActiveShell(), Translator
								.trans("ErrorTitle"),
								Translator.trans("spss.filenotfound.message",
										pathText.getText()));
				setPageComplete(false);
				return null;
			}
			setPageComplete(true);
			return pathText.getText();
		}
	}
}
