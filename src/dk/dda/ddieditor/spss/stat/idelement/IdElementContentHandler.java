package dk.dda.ddieditor.spss.stat.idelement;

import java.util.HashMap;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

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
 * SAX Content Handler for DdiManager variable short list 
 */
public class IdElementContentHandler extends DefaultHandler {
	String idElm = "IdElement";
	String[] attNames = { "id", "version", "agency", "name", "repType", "repRef" };
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
									.getValue(uri, attNames[4]), attributes.getValue(uri, attNames[5])));
		}
	}
}
