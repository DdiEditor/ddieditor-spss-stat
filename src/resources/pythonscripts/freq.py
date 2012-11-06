
#import spss, spssaux
import SpssClient

import sys

if len(sys.argv)!=3:
  print "Du skal kalde scriptet med: "+sys.argv[0]+" input.sav output.xml"
  sys.exit(1)

inputfile = sys.argv[1]
outputfile = sys.argv[2]

command = r"""
   GET FILE='%s'.
   oms
      /destination
         format  = oxml
         outfile = "%s"
         viewer  = no .
   
   frequencies
      /variables  = all
      /statistics = all .
   omsend .
""" % (inputfile,outputfile)

# Se kommandoen der koeres:
#print command

def close_all():
  size = SpssClient.GetDataDocuments().Size()
  SpssClient.GetDataDocuments().GetItemAt(size-1).CloseDocument()

try:
  SpssClient.StartClient()
  SpssClient.RunSyntax(command)
except:
  print "Kunne ikke gennemfoere scriptet. Fejl:", sys.exc_info()[0]

close_all()

