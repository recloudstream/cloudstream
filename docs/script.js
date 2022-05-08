const count = document.getElementById("count")
const mainContainer = document.getElementById("siteList")
const regionNamesInEnglish = new Intl.DisplayNames(['en'], { type: 'language' });
fetch("providers.json" + "?v=" + Date.now())
    .then(r => r.json())
    .then(function (data) {
        count.innerHTML = Object.keys(data).length;
        for (var key in data) {
            if (data.hasOwnProperty(key)) {
                var value = data[key];
                if (value.url == "NONE") { continue; }

                var _status = value.status
                var _lang = value.language

                var node = document.createElement("tr");
                node.classList.add("row");

                var _a = document.createElement("a");
                _a.setAttribute('href', value.url);
                _a.innerHTML = value.name

                var _langEl = document.createElement("sup");
                var _langName = "Unknown";
                try {
                    _langName = regionNamesInEnglish.of(_lang);
                } catch (langerr) {
                    _langName = "Unknown";
                    console.log("Error on parsing language code => " + langerr);
                }
                _langEl.innerHTML = " " + _langName
                _a.appendChild(_langEl);

                var _statusText = "Unknown";
                var _buttonText = "yellow";
                switch (_status) {
                    case 0:
                        _statusText = "Unavailable";
                        _buttonText = "red";
                        break;
                    case 1:
                        _statusText = "Available";
                        _buttonText = "green";

                        break;
                    case 2:
                        _statusText = "Slow";
                        _buttonText = "yellow";
                        break;
                    case 3:
                        _statusText = "Beta";
                        _buttonText = "blue";
                        break;
                }
                _a.classList.add(_buttonText + "Button");
                _a.classList.add("indicator");
                _a.classList.add("button");
                node.appendChild(_a);
                mainContainer.appendChild(node);
            }
        }
    })
