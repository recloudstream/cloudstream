#!/usr/bin/python3

from glob import glob
from re import findall, compile, DOTALL
from json import dump, load
from typing import List, Dict

# Globals
URL_REGEX = compile(
    "override\sva[lr]\smainUrl[^\"']+[\"'](https?://[a-zA-Z0-9\.-]+)[\"']")
NAME_REGEX = compile("([A-Za-z0-9]+)(?:.kt)$")
JSON_PATH = "docs/providers.json"
GLOB = "app/src/main/java/com/lagradost/cloudstream3/*providers/*Provider.kt"

old_sites: Dict[str, Dict] = load(open(JSON_PATH, "r", encoding="utf-8"))
sites: Dict[str, Dict] = {}

# parse all *Provider.kt files
for path in glob(GLOB):
    with open(path, "r", encoding='utf-8') as file:
        try:
            site_text: str = file.read()
            name: str = findall(NAME_REGEX, path)[0]
            provider_url: str = [*findall(URL_REGEX, site_text), ""][0]

            if name in old_sites.keys():  # if already in previous list use old status and name
                sites[name] = {
                    "name": old_sites[name]['name'],
                    "url": provider_url if provider_url else old_sites[name]['url'],
                    "status": old_sites[name]['status']
                }
            else: # if not in previous list add with new data
                display_name = name
                if display_name.endswith("Provider"):
                    display_name = display_name[:-len("Provider")]
                sites[name] = {
                    "name": display_name,
                    "url": provider_url if provider_url else "",
                    "status": 1
                }

        except Exception as ex:
            print("{0}: {1}".format(path, ex))
            
# add sites from old_sites that are missing in new list
for name in old_sites.keys():
    if name not in sites.keys():
        sites[name] = {
            "name": old_sites[name]['name'],
            "url": old_sites[name]['url'],
            "status": old_sites[name]['status']
        }

dump(sites, open(JSON_PATH, "w+", encoding="utf-8"), indent=4, sort_keys=True)
