#!/usr/bin/python3

from glob import glob
from re import findall, search, compile, sub, DOTALL

# Globals
URL_REGEX = compile("override val mainUrl(?:\:\s?String)?[^\"']+[\"'](https?://[a-zA-Z0-9\.-]+)[\"']")
NAME_REGEX = compile("class (.+?) ?: MainAPI\(\)")
START_MARKER = "<!--SITE LIST START-->"
END_MARKER = "<!--SITE LIST END-->"
GLOB = "app/src/main/java/com/lagradost/cloudstream3/*providers/*Provider.kt"
MAIN_API = "app/src/main/java/com/lagradost/cloudstream3/MainAPI.kt"
API_REGEX = compile("val (?:restrictedA|a)pis = arrayListOf\((.+?)\)(?=\n\n)", DOTALL)

sites = []
enabled_sites = []


with open(MAIN_API, "r", encoding="utf-8") as f:
    apis = findall(API_REGEX, f.read())
    for api_list in apis:
        for api in api_list.split("\n"):
            if not api.strip() or api.strip().startswith("/"):
                continue
            enabled_sites.append(api.strip().split("(")[0])

for path in glob(GLOB):
    with open(path, "r", encoding='utf-8') as file:
        try:
            site_text = file.read()
            name = findall(NAME_REGEX, site_text)
            if name:
                if name[0] not in enabled_sites:
                    continue
            url = search(URL_REGEX, site_text).groups()[0]
            sites.append(url)
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
        readme.write(
            "- [{0}]({1}) \n".format(sub("^https?://", "", site), site))

    readme.write(END_MARKER)
    readme.write(raw.split(END_MARKER)[-1])

    readme.truncate()
