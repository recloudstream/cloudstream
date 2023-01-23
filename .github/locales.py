import re
import glob
import requests


SETTINGS_PATH = "app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsGeneral.kt"
START_MARKER = "/* begin language list */"
END_MARKER = "/* end language list */"
XML_NAME = "app/src/main/res/values-"
ISO_MAP_URL = "https://gist.githubusercontent.com/Josantonius/b455e315bc7f790d14b136d61d9ae469/raw"
INDENT = " "*4

iso_map = requests.get(ISO_MAP_URL, timeout=300).json()

# Load settings file
src = open(SETTINGS_PATH, "r", encoding='utf-8').read()
before_src, rest = src.split(START_MARKER)
rest, after_src = rest.split(END_MARKER)

# Load already added langs
languages = {}
for lang in re.finditer(r'Triple\("(.*)", "(.*)", "(.*)"\)', rest):
    flag, name, iso = lang.groups()
    languages[iso] = (flag, name)

# Add not yet added langs
for folder in glob.glob(f"{XML_NAME}*"):
    iso = folder[len(XML_NAME):]
    if iso not in languages.keys():
        languages[iso] = ("", iso_map.get(iso.lower(),iso))

# Create triples
triples = []
for iso in sorted(languages.keys()):
    flag, name = languages[iso]
    triples.append(f'{INDENT}Triple("{flag}", "{name}", "{iso}"),')

# Update settings file
open(SETTINGS_PATH, "w+",encoding='utf-8').write(
    before_src +
    START_MARKER +
    "\n" +
    "\n".join(triples) +
    "\n" +
    END_MARKER +
    after_src
)