package org.opendatafoundation.data.spss;

import java.io.File;

import org.junit.Test;
import org.opendatafoundation.data.Utils;
import org.w3c.dom.Document;

/*
* Copyright 2011 Danish Data Archive (http://www.dda.dk) 
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

public class SpssFileTest {
	String path = "resources/eksempler_manual.sav";
	File file = new File(path);
	String outFilePath = file.getName() + ".xml";

	@Test
	public void SPSSFile() throws Exception {
		SPSSFile spssFile = new SPSSFile(file);
		spssFile.loadMetadata();
		// spssFile.dumpDDI3();
		ExportOptions exportOptions = new ExportOptions();
		exportOptions.createCategories = true;
		Document doc = spssFile
				.getDDI3LogicalProduct(exportOptions, null, null);

		System.out.println(Utils.nodeToString(doc));
		System.out.println(outFilePath);
		Utils.writeXmlFile(doc, outFilePath);
	}
}
