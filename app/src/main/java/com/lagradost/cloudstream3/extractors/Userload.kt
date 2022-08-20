package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import org.mozilla.javascript.Context
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Scriptable
import java.util.*


open class Userload : ExtractorApi() {
    override var name = "Userload"
    override var mainUrl = "https://userload.co"
    override val requiresReferer = false

    private fun splitInput(input: String): List<String> {
        var counter = 0
        val array = ArrayList<String>()
        var buffer = ""
        for (c in input) {
            when (c) {
                '(' -> counter++
                ')' -> counter--
                else -> {}
            }
            buffer += c
            if (counter == 0) {
                if (buffer.isNotBlank() && buffer != "+")
                    array.add(buffer)
                buffer = ""
            }
        }
        return array
    }

    private fun evaluateMath(mathExpression : String): String {
        val rhino = Context.enter()
        rhino.initStandardObjects()
        rhino.optimizationLevel = -1
        val scope: Scriptable = rhino.initStandardObjects()
        return try {
            rhino.evaluateString(scope, "eval($mathExpression)", "JavaScript", 1, null).toString()
        }
        catch (e: EvaluatorException){
            ""
        }
    }

    private fun decodeVideoJs(text: String): List<String> {
        text.replace("""\s+|/\*.*?\*/""".toRegex(), "")
        val data = text.split("""+(ﾟДﾟ)[ﾟoﾟ]""")[1]
        val chars = data.split("""+ (ﾟДﾟ)[ﾟεﾟ]+""").drop(1)
        val newchars = chars.map { char ->
            char.replace("(oﾟｰﾟo)", "u")
                .replace("c", "0")
                .replace("(ﾟДﾟ)['0']", "c")
                .replace("ﾟΘﾟ", "1")
                .replace("!+[]", "1")
                .replace("-~", "1+")
                .replace("o", "3")
                .replace("_", "3")
                .replace("ﾟｰﾟ", "4")
                .replace("(+", "(")
        }

        val subchar = mutableListOf<String>()

        newchars.dropLast(1).forEach { v ->
            subchar.add(splitInput(v).map { evaluateMath(it).substringBefore(".") }.toString().filter { it.isDigit() })
        }
        var txtresult = ""
        subchar.forEach{
            txtresult = txtresult.plus(Char(it.toInt(8)))
        }
        val val1 = Regex(""""morocco="((.|\\n)*?)"&mycountry="""").find(txtresult)?.groups?.get(1)?.value.toString().drop(1).dropLast(1)
        val val2 = txtresult.substringAfter("""&mycountry="+""").substringBefore(")")

        return listOf(
            val1,
            val2
        )


    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {

        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()

        val response = app.get(url).text
        val jsToUnpack = Regex("ext/javascript\">eval((.|\\n)*?)</script>").find(response)?.groups?.get(1)?.value
        val unpacked = JsUnpacker(jsToUnpack).unpack()
        val videoJs = app.get("$mainUrl/api/assets/userload/js/videojs.js")
        val videoJsToDecode = videoJs.text
        val values = decodeVideoJs(videoJsToDecode)
        val morocco = unpacked!!.split(";").filter { it.contains(values[0]) }[0].split("=")[1].drop(1).dropLast(1)
        val mycountry = unpacked.split(";").filter { it.contains(values[1]) }[0].split("=")[1].drop(1).dropLast(1)
        val videoLinkPage = app.post("$mainUrl/api/request/", data = mapOf(
            "morocco" to morocco,
            "mycountry" to mycountry
        ))
        val videoLink = videoLinkPage.text
        val nameSource = app.get(url).document.head().selectFirst("title")!!.text()
        extractedLinksList.add(
            ExtractorLink(
                name,
                name,
                videoLink,
                mainUrl,
                getQualityFromName(nameSource),
            )
        )

        return extractedLinksList
    }
}