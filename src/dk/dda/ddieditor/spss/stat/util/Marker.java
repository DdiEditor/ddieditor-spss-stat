package dk.dda.ddieditor.spss.stat.util;

import org.ddialliance.ddiftp.util.DDIFtpException;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import dk.dda.ddieditor.spss.stat.osgi.Activator;
import dk.dda.ddieditor.spss.stat.view.IdentifierMarkerField;
import dk.dda.ddieditor.spss.stat.view.ProblemView;
import dk.dda.ddieditor.spss.stat.view.StateMarkerField;
import dk.dda.ddieditor.spss.stat.view.TypeMarkerField;

public class Marker {

	public static void createMarker(boolean corrected, String elementName,
			String identifier, String msg) throws DDIFtpException {
		try {
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IResource resource = workspace.getRoot();

			IMarker marker = (IMarker) resource
					.createMarker(ProblemView.MARKER_ID);
			if (corrected) {
				marker.setAttribute(StateMarkerField.DDI_STATE, "Corrected");
			} else {
				marker.setAttribute(StateMarkerField.DDI_STATE, "Error");
			}
			if (elementName != null) {
				marker.setAttribute(TypeMarkerField.DDI_TYPE, elementName);
			}
			if (identifier != null) {
				marker.setAttribute(IdentifierMarkerField.DDI_REFERENCE,
						identifier);
			}
			marker.setAttribute(IMarker.SOURCE_ID, Activator.PLUGIN_ID);
			marker.setAttribute(IMarker.MESSAGE, msg);
		} catch (CoreException e) {
			throw new DDIFtpException(e.getMessage(), e);
		}
	}

	public static void cleanMarkers() throws Exception {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IMarker[] markers = root.findMarkers(ProblemView.MARKER_ID, false,
				IResource.DEPTH_ZERO);
		for (int i = 0; i < markers.length; i++) {
			String message = (String) markers[i]
					.getAttribute(IMarker.SOURCE_ID);
			if (message != null && message.equals(Activator.PLUGIN_ID)) {
				markers[i].delete();
			}
		}
	}
}
