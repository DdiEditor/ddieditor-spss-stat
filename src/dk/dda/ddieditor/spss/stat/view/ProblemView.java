package dk.dda.ddieditor.spss.stat.view;

import java.lang.reflect.Field;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.internal.views.markers.ExtendedMarkersView;
import org.eclipse.ui.internal.views.markers.MarkerContentGenerator;
import org.eclipse.ui.views.markers.MarkerSupportView;

public class ProblemView extends MarkerSupportView {

	static final String contentGeneratorId = "dk.dda.ddieditor.spss.stat.view.problemMarkerContentGenerator";
	public static final String ID = "dk.dda.ddieditor.spss.view.stat.ProblemView";
	public static final String MARKER_ID = "dk.dda.ddieditor.spss.stat.view.marker";

	public ProblemView(String contentGeneratorId) {
		super(ProblemView.contentGeneratorId);
	}

	public ProblemView() {
		super(ProblemView.contentGeneratorId);
	}

	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		if (memento == null) {
			return;
		}

		// hack to set marker limit to 99999
		try {
			Field f = ExtendedMarkersView.class.getDeclaredField("generator");
			f.setAccessible(true);
			MarkerContentGenerator generator = (MarkerContentGenerator) f
					.get(ExtendedMarkersView.class.cast(this));
			generator.setMarkerLimits(99999);
			generator.setMarkerLimitsEnabled(false);
		} catch (Exception e) {
			// TODO
			e.printStackTrace();
		}
	}
}
