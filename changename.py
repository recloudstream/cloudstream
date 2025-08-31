
import re
import os

#File paths
pathBuildGradle: str = os.path.join(".", "app", "build.gradle.kts")
pathStringRes: str = os.path.join(".", "app", "src", "main", "res", "values", "strings.xml")

# Regex to find old string
findAppId: str = "(?<=applicationId = \")(.*?)(?=\")"
findAppName: str = "(?<=\"app_name\">)(.*?)(?=<)"

# Replace string
newAppPackage: str = "com.lagradost.cloudstream3xxx"
newAppName: str = "CloudStream XXX"

# Define functions
def replace_str_using_regex(path: str, regex: str, new_text: str):
    try:
        # Save current contents
        text: str = ""
        # Check file if exists
        print(f"Checking filepath => {path}")
        if os.path.exists(path):
            # Read contents
            with open(path, "r", encoding='utf-8') as file:
                print("Read file..")
                text: str = file.read()
                #print("Old text => {0}".format(text))
                file.close()
                print("Reading file closed!")
            
            # replace with new content
            with open(path, "w", encoding='utf-8') as file:
                print("Replacing file contents..")
                newText: str = re.sub(regex, new_text, text)
                #newText: str = text.replace("com.lagradost.cloudstream3", newAppPackage)
                #print("New text => {0}".format(newText))
                file.truncate(0)
                print("File cleared!")
                file.write(newText)
                print("Done writing!")
                file.close()
                print("File closed!")

    except Exception as ex:
        print("Error => {0}: {1}".format(path, ex))


if __name__ == '__main__':
    replace_str_using_regex(pathBuildGradle, findAppId, newAppPackage)
    replace_str_using_regex(pathStringRes, findAppName, newAppName)
