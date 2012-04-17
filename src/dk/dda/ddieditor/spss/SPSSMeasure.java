package dk.dda.ddieditor.spss;

/**
 * SPSS measure int to DDI-L string expresion
 */
public enum SPSSMeasure {
	// < 1=nominal, 2=ordinal, 3=scale (copied from record type 7 subtype 11) 
	NOMINAL(1, "Nominal"), ORDINAL(2, "Ordinal"), SCALE(3, "Continuous");
	
	private int type;
	private String typeTxt;
	private SPSSMeasure(int type, String typeTxt) {
		this.type = type;
		this.typeTxt = typeTxt;
	}
	
	public static SPSSMeasure intToSpssMeasure(int spssInt) {
		for (int i = 0; i < values().length; i++) {
			if (values()[i].type==spssInt) {
				return values()[i]; 
			}
		}
		return null;
	}
	
	public String classificationLevel() {
		return typeTxt;
	}
}
