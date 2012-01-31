package dk.dda.ddieditor.spss.stat.util;

import java.util.HashMap;
import java.util.Map;

import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticTypeCodedType;

public class SpssStatsToDdiLStatsMap {
	public static Map<String, CategoryStatisticTypeCodedType.Enum> categoryStatisticTypeMap = new HashMap<String, CategoryStatisticTypeCodedType.Enum>();
	public static Map<String, String> otherCategoryStatisticTypeMap = new HashMap<String, String>();
	static {
		categoryStatisticTypeMap.put("Frequency",
				CategoryStatisticTypeCodedType.FREQUENCY);
		categoryStatisticTypeMap.put("Percent",
				CategoryStatisticTypeCodedType.PERCENT);
		categoryStatisticTypeMap.put("Valid Percent",
				CategoryStatisticTypeCodedType.USE_OTHER);
		categoryStatisticTypeMap.put("Cumulative Percent",
				CategoryStatisticTypeCodedType.CUMULATIVE_PERCENT);
		
		otherCategoryStatisticTypeMap.put("Valid Percent", "ValidPercent");
	}
}
