#!/usr/bin/python3

from glob import glob
from re import search, compile

# Globals
URL_REGEX = compile("override val mainUrl(?:\:\s?String)?[^\"']+[\"'](https?://[a-zA-Z0-9\.-]+)[\"']")
START_MARKER = "<!--SITE LIST START-->"
END_MARKER = "<!--SITE LIST END-->"
GLOB = "app/src/main/java/com/lagradost/cloudstream3/*providers/*Provider.kt"

sites = []

for path in glob(GLOB):
    with open(path, "r", encoding='utf-8') as file:
        try:
            sites.append(search(URL_REGEX, file.read()).groups()[0])
        except Exception as ex:
            print("{0}: {1}".format(path, ex))
            
with open("README.md", "r+", encoding='utf-8') as readme:
    raw = readme.read()
    if START_MARKER not in raw or END_MARKER not in raw:
        raise RuntimeError("Missing start and end markers")
    readme.seek(0)
    
    readme.write(raw.split(START_MARKER)[0])
    readme.write(START_MARKER+"\n")
    
    for site in sites:
        readme.write("- [{0}]({0}) \n".format(site))
    
    readme.write(END_MARKER+"\n")
    readme.write(raw.split(END_MARKER)[-1])
    
    readme.truncate()