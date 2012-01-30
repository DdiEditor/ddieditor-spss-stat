package dk.dda.ddieditor.spss.stat.idelement;


public class IdElement {
	public enum RepresentationType {
		CODE("Code"), DATA_TIME("DateTime"), EXTERNAL_CATEGORY(
				"ExternalCategory"), NUMERIC("Numeric"), TEXT("TEXT");
		private String xmlRep;

		private RepresentationType(String xmlRep) {
			this.xmlRep = xmlRep;
		}
		
		public static RepresentationType getRepresentationType(String xmlRep) {
			for (int i = 0; i < RepresentationType.values().length; i++) {
				RepresentationType namespacePrefix = RepresentationType.values()[i];
				if (namespacePrefix.xmlRep.equals(xmlRep)) {
					return namespacePrefix;
				}
			}
			return null;
		}
	};

	String id, version, name, agency;
	RepresentationType representationType;
	
	public IdElement(String id, String version, String agency, String name, String repType) {
		super();
		this.id = id;
		this.version = version;
		this.name = name;
		this.agency = agency;
		this.representationType = RepresentationType.getRepresentationType(repType);
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAgency() {
		return agency;
	}

	public void setAgency(String agency) {
		this.agency = agency;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("id: ");
		sb.append(id);
		sb.append(", version: ");
		sb.append(version);
		sb.append(", agency: ");
		sb.append(agency);
		sb.append(", name: ");
		sb.append(name);
		sb.append(", repType: ");
		sb.append(representationType);

		return sb.toString();
	}
}
