package dk.dda.ddieditor.spss.stat.util;

import java.util.HashMap;
import java.util.Map;

import org.ddialliance.ddi3.xml.xmlbeans.physicalinstance.CategoryStatisticTypeCodedType;

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
 * Comparision Map between SPPS and DDI-L measures
 */
public class SpssStatsToDdiLStatsMap {
	public static Map<String, CategoryStatisticTypeCodedType.Enum> categoryStatisticTypeMap = new HashMap<String, CategoryStatisticTypeCodedType.Enum>();
	public static Map<String, String> otherCategoryStatisticTypeMap = new HashMap<String, String>();
	static {
		// category statistic type
		categoryStatisticTypeMap.put("Frequency",
				CategoryStatisticTypeCodedType.FREQUENCY);
		categoryStatisticTypeMap.put("Percent",
				CategoryStatisticTypeCodedType.PERCENT);
		categoryStatisticTypeMap.put("Valid Percent",
				CategoryStatisticTypeCodedType.USE_OTHER);
		categoryStatisticTypeMap.put("Cumulative Percent",
				CategoryStatisticTypeCodedType.CUMULATIVE_PERCENT);
		
		// other category statistic type
		otherCategoryStatisticTypeMap.put("Valid Percent", "ValidPercent");
	}
}
