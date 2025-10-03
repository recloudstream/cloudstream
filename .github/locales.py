import re
import glob
import requests
import os
import lxml.etree as ET  # builtin library doesn't preserve comments


SETTINGS_PATH = "app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsGeneral.kt"
START_MARKER = "/* begin language list */"
END_MARKER = "/* end language list */"
XML_NAME = "app/src/main/res/values-b+"
ISO_MAP_URL = "https://raw.githubusercontent.com/haliaeetus/iso-639/master/data/iso_639-1.min.json"
INDENT = " "*4

iso_map = requests.get(ISO_MAP_URL, timeout=300).json()

# Load settings file
src = open(SETTINGS_PATH, "r", encoding='utf-8').read()
before_src, rest = src.split(START_MARKER)
rest, after_src = rest.split(END_MARKER)

# Load already added langs
languages = {}
for lang in re.finditer(r'Pair\("(.*)", "(.*)"\)', rest):
    name, iso = lang.groups()
    languages[iso] = name

# Add not yet added langs
for folder in glob.glob(f"{XML_NAME}*"):
    iso = folder[len(XML_NAME):].replace("+", "-")
    if iso not in languages.keys():
        entry = iso_map.get(iso.lower(), {'nativeName':iso}) # fallback to iso code if not found
        languages[iso] = entry['nativeName'].split(',')[0] # first name if there are multiple

# Create pairs
pairs = []
for iso in sorted(languages, key=lambda iso: languages[iso].lower()): # sort by language name
    name = languages[iso]
    pairs.append(f'{INDENT}Pair("{name}", "{iso}"),')

# Update settings file
open(SETTINGS_PATH, "w+",encoding='utf-8').write(
    before_src +
    START_MARKER +
    "\n" +
    "\n".join(pairs) +
    "\n" +
    END_MARKER +
    after_src
)

# Go through each values.xml file and fix escaped \@string
for file in glob.glob(f"{XML_NAME}*/strings.xml"):
    try:
        tree = ET.parse(file)
        for child in tree.getroot():
            if not child.text:
                continue
            if child.text.startswith("\\@string/"):
                print(f"[{file}] fixing {child.attrib['name']}")
                child.text = child.text.replace("\\@string/", "@string/")
        with open(file, 'wb') as fp:
            fp.write(b'<?xml version="1.0" encoding="utf-8"?>\n')
            tree.write(fp, encoding="utf-8", method="xml", pretty_print=True, xml_declaration=False)
            # Remove trailing new line to be consistent with weblate
            fp.seek(-1, os.SEEK_END)
            fp.truncate()
    except ET.ParseError as ex:
        print(f"[{file}] {ex}")
