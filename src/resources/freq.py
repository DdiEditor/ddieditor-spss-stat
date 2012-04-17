#! /usr/bin/env python


"""
# Lib\site-packages\DDI\__init__.py
DDI.write - exports metadata from SPSS files to DDI format.
Uses a XSLT stylesheet to transform metadata on the variable and on the category level
from SPSS OMS XML (1.2 since SPSS 14.0) to DDI (3.0).

StatsProgs2DDI, SPSSOMS12toDDI30.xslt - SPSS DDI Writer (beta version)
$LastChangedDate: 2007-10-18 16:35:14 +0200 (Do, 18 Okt 2007) $
http://db.zuma-mannheim.de/DDI/StatsProgs2DDI/StatsProgs2DDI.html
Copyright (c) GESIS 2007 Joachim Wackerow
joachim.wackerow@gesis.org

"""

# License: GNU Lesser General Public License
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

# $HeadURL: http://db2/svn/ddi/StatsProgs2DDI/SPSS_Python/2007-07/DDI/__init__.py $
# $LastChangedDate: 2007-10-18 16:35:14 +0200 (Do, 18 Okt 2007) $
# $LastChangedRevision: 152 $
# $LastChangedBy: wackerow $

def write ( outfile="None", variables="all", frequencies="None", statistics="None", version="3.0", viewer="no", debug="no" ):
    """

- Usage
    write ( outfile=STRING, variables=STRING [, frequencies=STRING, statistics=STRING, viewer=STRING, debug=STRING ] )

- Parameters
    outfile = STRING [ no default ]
    variables = STRING [ SPSS variable list, no default ]
    frequencies = STRING [ SPSS variable list, default:no frequencies ]
    statistics = STRING [ values as in SPSS frequencies/statistics, default:no statistics ]
    viewer =  STRING [ allowed values: 'no' | 'yes', default: 'no' ]
    debug =  STRING [ allowed values: 'no' | 'yes', default: 'no' ]

- Example in SPSS
    GET FILE =  'E:\\SPSSFiles\\ISSP2004_ZA3950_F1.sav' .

    * Please note:
      The "r" before the filename string for the outfile is IMPORTANT.
    BEGIN PROGRAM Python .
    import DDI
    DDI.write(
      outfile = r'E:\\temp\\test.xml',
      variables = 'v4 v5',
      frequencies = 'v4 v5',
      statistics = 'all'
    )
    END PROGRAM .

    """
    # configuration start
    stylesheetBasename = "SPSSOMS12toDDI30.xslt"
    XMLWorkspace = "DISPLAY"
    #
    SPSSCommand = r"""
oms
 /destination
  format = oxml
  xmlworkspace = '%(xmlworkspace)s'
  viewer = %(viewer)s
  .

display dictionary
  /variables = %(variables)s
  .

omsend .
""" %{ "xmlworkspace":XMLWorkspace, "viewer":viewer, "variables":variables }
    #
    SPSSCommandFrequencies = r"""
oms
 /destination
  format = oxml
  xmlworkspace = '%(xmlworkspace)s'
  viewer = %(viewer)s
  .

display dictionary
  /variables = %(variables)s
  .

frequencies
  /variables = %(variables)s
  .

omsend .
""" %{ "xmlworkspace":XMLWorkspace, "viewer":viewer, "variables":variables }
    #
    SPSSCommandFrequenciesStatistics = r"""
oms
 /destination
  format = oxml
  xmlworkspace = '%(xmlworkspace)s'
  viewer = %(viewer)s
  .

display dictionary
  /variables = %(variables)s
  .

frequencies
  /variables = %(variables)s
  /ntiles = 4
  /ntiles = 5
  /statistics = all
  .

omsend .
""" %{ "xmlworkspace":XMLWorkspace, "viewer":viewer, "variables":variables }
    # configuration end

    # prolog
    import os, Pyana, re, spss, StringIO, sys, time
2+2
    startingTime = time.time()
    stylesheetFilename = os.sep.join( [__path__[0], stylesheetBasename] )  # __path__ geht nur in __init__.py

    # run spss command
    if frequencies == "None":
        spss.Submit( SPSSCommand )
    else:
        if statistics == "None":
            spss.Submit( SPSSCommandFrequencies )
        else:
            spss.Submit( SPSSCommandFrequenciesStatistics )

    # prepare transformation of display output
    spss.StartProcedure( "writeDDI" ) # submit not possible inside procedure
    if debug == "yes":
        line = "SPSS-Python Integration Plug-in Version: " + spss.GetDefaultPlugInVersion()
        debugTextBlock = spss.TextBlock( "Debug", line )
        line = "SPSS Python DDI module: " + __file__
        debugTextBlock.append( line )
        line = "Stylesheet filename: " + stylesheetFilename
        debugTextBlock.append( line )
        handleList = spss.GetHandleList()
        for handle in handleList:
            if handle == "FREQ":
                line = "Handle 'FREQ' exists."
                debugTextBlock.append( line )

    if outfile == "None":
        textBlock = spss.TextBlock( "Usage", "Error: no output file specified!" )
        textBlock.append( str( write.__doc__ ), skip=2 )
        spss.EndProcedure()
        return

    SPSSOMSXMLAsUnicodeString = spss.GetXmlUtf16( XMLWorkspace )
    SPSSOMSXMLAsByteString = SPSSOMSXMLAsUnicodeString.encode( "utf-16" )
    # test stylesheetFilename
    stylesheetFile = open( stylesheetFilename, 'r' )
    stylesheetFile.close()
    # test outfile
    DDIFile = open( outfile, 'w' )
    DDIFile.close()
    # capture stdout
    stdoutDefault = sys.stdout
    stdoutCaptured = StringIO.StringIO()
    sys.stdout = stdoutCaptured

    # transformation of display output, default encoding is ascii (default)
    Pyana.transform2File( SPSSOMSXMLAsByteString, Pyana.URI( stylesheetFilename ), outfile )

    # set stdout to default
    sys.stdout = stdoutDefault
    stdoutString = str( stdoutCaptured.getvalue() ).strip()
    if debug == "yes":
        debugTextBlock.append( stdoutString )

    # epilog
    endingTime = time.time()
    p = re.compile( r"[^\d]+(\d*) variables processed.+", re.DOTALL )
    numberOfVariables = p.sub( r"\1", stdoutString )
    line = "DDI %s file written: %s" %( version, outfile )
    textBlock = spss.TextBlock( "Metadata transformation", line )
    line = numberOfVariables + " variables processed."
    textBlock.append( line )
    line = "Transforming metadata to DDI format took %s seconds." % round( ( endingTime - startingTime ), 2 )
    textBlock.append( line )
    # about
    line = "StatsProgs2DDI, SPSSOMS12toDDI30.xslt - SPSS DDI Writer (beta version)"
    textBlock = spss.TextBlock( "About", line )
    line = "  $LastChangedDate: 2007-10-18 16:35:14 +0200 (Do, 18 Okt 2007) $"
    textBlock.append( line )
    line = "  http://db.zuma-mannheim.de/DDI/StatsProgs2DDI/StatsProgs2DDI.html"
    textBlock.append( line )
    line = "  joachim.wackerow@gesis.org"
    textBlock.append( line )

    spss.DeleteXPathHandle( XMLWorkspace ) # important to release memory
    spss.EndProcedure()


