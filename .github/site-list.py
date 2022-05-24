#!/usr/bin/python3

from glob import glob
from re import findall, compile, DOTALL
from json import dump, load
from typing import List, Dict

# Globals
JSON_PATH = "docs/providers.json"
GLOB_ANIME = "app/src/main/java/com/lagradost/cloudstream3/animeproviders/*Provider.kt"
GLOB_MOVIE = "app/src/main/java/com/lagradost/cloudstream3/movieproviders/*Provider.kt"
URL_REGEX = compile("override\sva[lr]\smainUrl[^\"']+[\"'](https?://[a-zA-Z0-9\.-/]+)[\"']")
FILENAME_REGEX = compile("([A-Za-z0-9]+)(?:.kt)$")
PROVIDER_CLASSNAME_REGEX = compile("(?<=class\s)([a-zA-Z]+)(?=\s:\sMainAPI\(\))")
NAME_REGEX = compile("override\sva[lr]\sname[^\"']+[\"']([a-zA-Z-.\s]+)")
LANG_REGEX = compile("override\sva[lr]\slang[^\"']+[\"']([a-zA-Z]+)")

old_sites: Dict[str, Dict] = load(open(JSON_PATH, "r", encoding="utf-8"))
sites: Dict[str, Dict] = {}

animelist = glob(GLOB_ANIME)
movielist = glob(GLOB_MOVIE)
allProvidersList = animelist + movielist

# parse all *Provider.kt files
for path in allProvidersList:
    with open(path, "r", encoding='utf-8') as file:
        try:
            site_text: str = file.read()
            filename: str = findall(FILENAME_REGEX, path)[0]
            name: str = [*findall(PROVIDER_CLASSNAME_REGEX, site_text), filename][0]
            provider_url: str = [*findall(URL_REGEX, site_text), ""][0]
            lang: str = [*findall(LANG_REGEX, site_text), "en"][0]

            if name in old_sites.keys():  # if already in previous list use old status and name
                sites[name] = {
                    "name": old_sites[name]['name'],
                    "url": provider_url if provider_url else old_sites[name]['url'],
                    "status": old_sites[name]['status'],
                    "language": lang
                }
            else: # if not in previous list add with new data
                display_name: str = [*findall(NAME_REGEX, site_text), name][0]
                if display_name.endswith("Provider"):
                    display_name = display_name[:-len("Provider")]
                sites[name] = {
                    "name": display_name,
                    "url": provider_url if provider_url else "",
                    "status": 1,
                    "language": lang
                }

        except Exception as ex:
            print("Error => {0}: {1}".format(path, ex))

# add sites from old_sites that are missing in new list
for name in old_sites.keys():
    if name not in sites.keys():
        sites[name] = {
            "name": old_sites[name]['name'],
            "url": old_sites[name]['url'],
            "status": old_sites[name]['status']
        }

dump(sites, open(JSON_PATH, "w+", encoding="utf-8"), indent=4, sort_keys=True)
