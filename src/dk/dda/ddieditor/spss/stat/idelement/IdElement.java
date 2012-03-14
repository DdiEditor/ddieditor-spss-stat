package dk.dda.ddieditor.spss.stat.idelement;

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
 * POJO for DdiManager variable short list
 */
public class IdElement {
	public enum RepresentationType {
		CODE("Code"), DATA_TIME("DateTime"), EXTERNAL_CATEGORY(
				"ExternalCategory"), NUMERIC("Numeric"), TEXT("Text");
		private String xmlRep;

		private RepresentationType(String xmlRep) {
			this.xmlRep = xmlRep;
		}

		public static RepresentationType getRepresentationType(String xmlRep) {
			for (int i = 0; i < RepresentationType.values().length; i++) {
				RepresentationType namespacePrefix = RepresentationType
						.values()[i];
				if (namespacePrefix.xmlRep.equals(xmlRep)) {
					return namespacePrefix;
				}
			}
			return null;
		}
	};

	String id, version, name, agency;
	RepresentationType representationType;

	public IdElement(String id, String version, String agency, String name,
			String repType) {
		super();
		this.id = id;
		this.version = version;
		this.name = name;
		this.agency = agency;
		this.representationType = RepresentationType
				.getRepresentationType(repType);
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

	public RepresentationType getRepresentationType() {
		return representationType;
	}

	public void setRepresentationType(RepresentationType representationType) {
		this.representationType = representationType;
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
