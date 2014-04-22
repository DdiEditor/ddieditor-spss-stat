package dk.dda.ddieditor.spss.stat.view;

import org.eclipse.ui.views.markers.MarkerField;
import org.eclipse.ui.views.markers.MarkerItem;

public class IdentifierMarkerField extends MarkerField {
	public static final String DDI_REFERENCE = "ddi_reference";

	@Override
	public String getValue(MarkerItem item) {
		return item.getAttributeValue(DDI_REFERENCE, "NA");
	}
}
