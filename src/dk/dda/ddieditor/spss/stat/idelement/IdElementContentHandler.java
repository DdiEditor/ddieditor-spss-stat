package dk.dda.ddieditor.spss.stat.idelement;

import java.util.HashMap;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class IdElementContentHandler extends DefaultHandler {
	String idElm = "IdElement";
	String[] attNames = { "id", "version", "agency", "name", "repType" };
	public HashMap<String, IdElement> result = new HashMap<String, IdElement>();

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		super.startElement(uri, localName, qName, attributes);
		if (qName.equals(idElm)) {
			result.put(
					attributes.getValue(uri, attNames[3]),
					new IdElement(attributes.getValue(uri, attNames[0]),
							attributes.getValue(uri, attNames[1]), attributes
									.getValue(uri, attNames[2]), attributes
									.getValue(uri, attNames[3]), attributes
									.getValue(uri, attNames[4])));
		}
	}
}
