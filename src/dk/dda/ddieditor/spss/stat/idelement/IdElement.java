package dk.dda.ddieditor.spss.stat.idelement;

public class IdElement {
	String id, version, name, agency;

	public IdElement(String id, String version, String agency, String name) {
		super();
		this.id = id;
		this.version = version;
		this.name = name;
		this.agency = agency;
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
		
		return sb.toString();
	}
}
