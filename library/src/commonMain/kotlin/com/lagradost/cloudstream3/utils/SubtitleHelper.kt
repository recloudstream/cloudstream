package com.lagradost.cloudstream3.utils

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import com.lagradost.cloudstream3.mvvm.logError
import java.util.Locale
import kotlin.text.RegexOption.IGNORE_CASE

// For backward compatibility with already build plugins.
// The number of parameters and types in LanguageMetadata
// remains the same as when it was named Language639.
typealias Language639 = SubtitleHelper.LanguageMetadata
// (?_?) LanguageMetadata should be private, protected ??

object SubtitleHelper {
    /**
     * Represents one language with english name, native name and codes.
     * For language code IETF BCP 47 conformant tag shall be used as first choise!
     *
     * See locales on:
     * https://github.com/unicode-org/cldr-json/blob/main/cldr-json/cldr-core/availableLocales.json
     * https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
     * https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r2/core/res/res/values/locale_config.xml
     * https://iso639-3.sil.org/code_tables/639/data/all
    */
    data class LanguageMetadata(
        val languageName: String,
        val nativeName: String,
        val ISO_639_1: String,
        val open_subtitles: String, // inconsistent codes that do not conform ISO 639
        val ISO_639_2_B: String,    // ISO 639-2/T missing as it's a subset of ISO 639-3
        val ISO_639_3: String,      // ISO 639-6 missing as it's intended to differentiate specific dialects and variants
        val IETF_tag: String,       // Combine ISO 639-1, 639-3 and ISO 3166-1 (or numeric UN M49 / CLDR) for country / region
    ) {
        fun nameNextToFlagEmoji(): String {
            val flag =
                getFlagFromIso(this.IETF_tag) ?: if (VERSION.SDK_INT >= VERSION_CODES.P)
                "\ud83c\udde6\ud83c\udde6" else "\ud83c\udff3\ufe0f"
            val newLocale = Locale.forLanguageTag(this.IETF_tag)
            val sysLocalizedName = newLocale.getDisplayName(newLocale)

            return if (sysLocalizedName.equals(this.IETF_tag, ignoreCase = true))
                "$flag ${this.nativeName}"
            else
                "$flag $sysLocalizedName"
        }
    }

    /*fun createISO() {
        val url = "https://infogalactic.com/info/List_of_ISO_639-1_codes"
        val response = get(url).text
        val document = Jsoup.parse(response)
        val headers = document.select("table.wikitable > tbody > tr")

        var text = "listOf(\n"
        for (head in headers) {
            val tds = head.select("td")
            if (tds.size < 8) continue
            val name = tds[2].selectFirst("> a").text()
            val native = tds[3].text()
            val ISO_639_1 = tds[4].ownText().replace("+", "").replace(" ", "")
            val ISO_639_2_T = tds[5].ownText().replace("+", "").replace(" ", "")
            val ISO_639_2_B = tds[6].ownText().replace("+", "").replace(" ", "")
            val ISO_639_3 = tds[7].ownText().replace("+", "").replace(" ", "")
            val ISO_639_6 = tds[8].ownText().replace("+", "").replace(" ", "")

            val txtAdd =
                "Language(\"$name\", \"$native\", \"$ISO_639_1\", \"$ISO_639_2_T\", \"$ISO_639_2_B\", \"$ISO_639_3\", \"$ISO_639_6\"),\n"
            text += txtAdd
        }
        text += ")"
        println("ISO CREATED:\n$text")
    }*/

    /**
     * languageName -> [LanguageMetadata]
     * @param languageName english language name
     * @param halfMatch match with `contains()` in addition to `equals()`
    */
    fun getLanguageDataFromName(languageName: String?, halfMatch: Boolean?): LanguageMetadata? {
        if (languageName.isNullOrBlank() || languageName.length < 2) return null

        if (halfMatch ?: false) {
            for (lang in languages) 
                if (lang.languageName.equals(languageName, ignoreCase = true)
                    || lang.nativeName.equals(languageName, ignoreCase = true)
                    || lang.languageName.contains(languageName, ignoreCase = true)
                    || lang.nativeName.contains(languageName, ignoreCase = true))
                    return lang
        } else {
            for (lang in languages)
                if (lang.languageName.equals(languageName, ignoreCase = true)
                    || lang.nativeName.equals(languageName, ignoreCase = true))
                    return lang
        }
        return null
    }

    /**
     * languageName -> ISO_639_1
     * @param languageName english language name
     * @param halfMatch match with `contains()` in addition to `equals()`
    */
    fun fromLanguageToTwoLetters(languageName: String?, halfMatch: Boolean?): String? {
        return getLanguageDataFromName(languageName, halfMatch)?.ISO_639_1
    }

    /**
     * languageName -> ISO_639_3
     * @param languageName english language name
     * @param halfMatch match with `contains()` in addition to `equals()`
    */
    fun fromLanguageToThreeLetters(languageName: String?, halfMatch: Boolean?): String? {
        return getLanguageDataFromName(languageName, halfMatch)?.ISO_639_3
    }

    /**
     * languageName -> IETF_tag
     * @param languageName english language name
     * @param halfMatch match with `contains()` in addition to `equals()`
    */
    fun fromLanguageToTagIETF(languageName: String?, halfMatch: Boolean?): String? {
        return getLanguageDataFromName(languageName, halfMatch)?.IETF_tag
    }

    /**
     * languageCode -> [LanguageMetadata]
     * @param languageCode IETF BCP 47, ISO 639-1, ISO 639-2B/T, ISO 639-3, OpenSubtitles
     * @return [LanguageMetadata] or `null`
    */
    fun getLanguageDataFromCode(languageCode: String?): LanguageMetadata?  {
        if (languageCode.isNullOrBlank() || languageCode.length < 2) return null

        for (lang in languages) {
            if (lang.IETF_tag.equals(languageCode, ignoreCase = true)
                || lang.ISO_639_3.equals(languageCode, ignoreCase = true)
                || lang.ISO_639_2_B.equals(languageCode, ignoreCase = true)
                || lang.ISO_639_1.equals(languageCode, ignoreCase = true)
                || lang.open_subtitles.equals(languageCode, ignoreCase = true)) {
                return lang
            }
        }
        return null
    }

    @Deprecated(
        "Default language code changed to IETF BCP 47 tag",
        ReplaceWith("fromTagToLanguage(languageCode)"))
    fun fromTwoLettersToLanguage(languageCode: String?): String? {
        return getLanguageDataFromCode(languageCode)?.languageName
    }

    @Deprecated(
        "Default language code changed to IETF BCP 47 tag",
        ReplaceWith("fromTagToLanguage(languageCode)"))
    fun fromThreeLettersToLanguage(languageCode: String?): String? {
        return getLanguageDataFromCode(languageCode)?.languageName
    }

    fun fromTagToLanguage(languageCode: String?): String? {
        return getLanguageDataFromCode(languageCode)?.languageName
    }

    /**
     * languageCode -> open_subtitles inconsistent language tag
     * @param languageCode IETF BCP 47, ISO 639-1, ISO 639-2B/T, ISO 639-3, OpenSubtitles
    */
    fun fromCodeToOpenSubtitlesTag(languageCode: String?): String? {
        return getLanguageDataFromCode(languageCode)?.open_subtitles
    }

    /** open_subtitles -> IETF_tag */
    fun fromCodeToLangTagIETF(languageCode: String?): String? {
        return getLanguageDataFromCode(languageCode)?.IETF_tag
    }

    /**
     * Check for a well formed IETF BCP 47 conformant language tag
     *
     * See locales on:
     * https://github.com/unicode-org/cldr-json/blob/main/cldr-json/cldr-core/availableLocales.json
     * https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
     * https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r2/core/res/res/values/locale_config.xml
    */
    fun wellFormedTagIETF(langTagIETF: String?): Boolean {
        if (langTagIETF.isNullOrBlank() || langTagIETF.length < 2) return false

        // Written by Addison Phillips, <addison at amazon.com>
        // https://www.langtag.net/philips-regexp.html
        val langTagRegex = """
            +(^[xX]([\x2d]\p{Alnum}{1,8})*$)"
            +|(((^\p{Alpha}{2,8}(?=\x2d|$)){1}"
            +(([\x2d]\p{Alpha}{3})(?=\x2d|$)){0,3}"
            +([\x2d]\p{Alpha}{4}(?=\x2d|$))?"
            +([\x2d](\p{Alpha}{2}|\d{3})(?=\x2d|$))?"
            +([\x2d](\d\p{Alnum}{3}|\p{Alnum}{5,8})(?=\x2d|$))*)"
            +(([\x2d]([a-wyzA-WYZ](?=\x2d))([\x2d](\p{Alnum}{2,8})+)*))*"
            +([\x2d][xX]([\x2d]\p{Alnum}{1,8})*)?)$
            """.trimMargin("+").toRegex()
        return langTagIETF.matches(langTagRegex)
    }

    /**
     * Try to get a flag emoji form a language code
     * or two letters country code (ISO 3166-1-alfa-2)
    */
    fun getFlagFromIso(countryOrLang: String?): String? {
        if (countryOrLang.isNullOrBlank() || countryOrLang.length < 2) return null

        // 2 times a symbol between regional indicator "[A]" and "[Z]"
        val unicodeFlagRegex = Regex("[\uD83C\uDDE6-\uD83C\uDDFF]{2}")
        // language tags (en-US, es-419, pt-BR, zh-hant-TW) that includes country
        val countryRegex = Regex("(?<=-|_)(?<country>\\p{Alnum}{2,3})$", IGNORE_CASE)

        val country = countryRegex.find(countryOrLang)?.groups["country"]?.value

        val flagEmoji =
            getFlagFromCountry2(lang2country[countryOrLang.lowercase()]) ?:
            getFlagFromCountry2(country?.uppercase()) ?:
            getFlagFromCountry2(countryOrLang.uppercase()) ?:
            getFlagFromCountry2(lang2country[country?.lowercase()]) ?: ""

        if (countryOrLang.equals("qt", ignoreCase = true))
            return "\uD83E\uDD8D" // "mmmm... monke" -> gorilla [🦍]
        if (flagEmoji.matches(unicodeFlagRegex))
            return flagEmoji
        return null
    }

    // 2 letters country code (ISO 3166-1-alfa-2) -> flag emoji
    private fun getFlagFromCountry2(countryLetters: String?): String? {
        if (countryLetters.isNullOrBlank() || countryLetters.length != 2) return null

        val asciiOffset = 0x41    // uppercase "A"
        val flagOffset = 0x1F1E6  // regional indicator "[A]"
        val offset = flagOffset - asciiOffset

        val firstChar: Int = Character.codePointAt(countryLetters, 0) + offset
        val secondChar: Int = Character.codePointAt(countryLetters, 1) + offset

        return (String(Character.toChars(firstChar)) + String(Character.toChars(secondChar)))
    }

    // when (langTag = country) or (langTag contains country)
    // as in:
    //   "es" to "ES"
    //   "pt" to "PT"
    //   "fr" to "FR"
    //   "en-US" to "US"
    //   "pt-BR" to "BR"
    //   "zh-hant-TW" to "TW"
    // add to this list is useless as getFlagFromIso() already
    // handles it.
    private val lang2country = mapOf(
        "419" to "ES", // (?_?) Latin America -> ES or one Latin American country?
        "af" to "ZA",
        "agq" to "CM",
        "ajp" to "SY",
        "ak" to "GH",
        "am" to "ET",
        "apc" to "SY",
        "ar" to "AE",
        "ars" to "SA",
        "as" to "IN",
        "asa" to "TZ",
        "bas" to "CM",
        "be" to "BY",
        "bem" to "ZM",
        "bez" to "IT",
        "bm" to "ML",
        "bn" to "BD",
        "bo" to "CN",
        "br" to "FR",
        "brx" to "IN",
        "bs" to "BA",
        "ca" to "ES",
        "cgg" to "UG",
        "chr" to "US",
        "cs" to "CZ",
        "cy" to "GB",
        "da" to "DK",
        "dav" to "KE",
        "dje" to "NE",
        "dua" to "CM",
        "dyo" to "SN",
        "ebu" to "KE",
        "ee" to "GH",
        "el" to "GR",
        "en" to "GB",
        "et" to "EE",
        "eu" to "ES",
        "ewo" to "CM",
        "fa" to "IR",
        "ff" to "CN",
        "fil" to "PH",
        "ga" to "IE",
        "gl" to "ES",
        "gsw" to "CH",
        "gu" to "IN",
        "guz" to "KE",
        "gv" to "GB",
        "ha" to "NG",
        "haw" to "US",
        "he" to "IL",
        "hi" to "IN",
        "hy" to "AM",
        "ig" to "NG",
        "ii" to "CN",
        "in" to "ID", // "in" deprecate, use "id" instead. Keeping for exiting subtitles
        "ita" to "IT",
        "iw" to "IL", // "iw" deprecate, use "he" instead. Keeping for exiting subtitles
        "ja" to "JP",
        "jmc" to "TZ",
        "ka" to "GE",
        "kab" to "DZ",
        "kam" to "KE",
        "kde" to "TZ",
        "kea" to "CV",
        "khq" to "ML",
        "ki" to "KE",
        "kk" to "KZ",
        "kl" to "GL",
        "kln" to "KE",
        "km" to "KH",
        "kn" to "IN",
        "ko" to "KR",
        "kok" to "IN",
        "ksb" to "TZ",
        "ksf" to "CM",
        "kw" to "GB",
        "lag" to "TZ",
        "lat" to "LV",
        "lg" to "UG",
        "ln" to "CG",
        "lu" to "CD",
        "luo" to "KE",
        "luy" to "KE",
        "mas" to "TZ",
        "mer" to "KE",
        "mfe" to "MU",
        "mgh" to "MZ",
        "ml" to "IN",
        "mr" to "IN",
        "ms" to "MY",
        "mua" to "CM",
        "my" to "MM",
        "naq" to "NA",
        "nb" to "NO",
        "nd" to "ZW",
        "ne" to "NP",
        "nmg" to "CM",
        "nn" to "NO",
        "nus" to "SD",
        "nyn" to "UG",
        "om" to "ET",
        "or" to "IN",
        "pa" to "PK",
        "ps" to "AF",
        "rm" to "CH",
        "rn" to "BI",
        "rof" to "TZ",
        "rwk" to "TZ",
        "saq" to "KE",
        "sbp" to "TZ",
        "seh" to "MZ",
        "ses" to "ML",
        "sg" to "CF",
        "shi" to "MA",
        "si" to "LK",
        "sl" to "SI",
        "sn" to "ZW",
        "sq" to "AL",
        "sr" to "RS",
        "sv" to "SE",
        "sw" to "TZ",
        "swc" to "CD",
        "ta" to "IN",
        "te" to "IN",
        "teo" to "UG",
        "ti" to "ET",
        "tl" to "PH",
        "twq" to "NE",
        "tzm" to "MA",
        "uk" to "UA",
        "ur" to "PK",
        "vai" to "LR",
        "vi" to "VN",
        "vun" to "TZ",
        "xog" to "UG",
        "yav" to "CM",
        "yo" to "NG",
        "yue" to "CN",
        "zh-hans" to "CN", // (?_?) Chinese (simplified) -> CN ?
        "zh-hant" to "TW", // (?_?) Chinese (traditional) -> CN or TW or other ?
        "zh" to "CN",
        "zu" to "ZA",
    )

    val languages = listOf(
        // languageName, nativeName, ISO_639_1, open_subtitles, ISO_639_2_B, ISO_639_3, IETF_tag
        LanguageMetadata("Abkhaz","аҧсуа бызшәа, аҧсшәа","ab","ab","abk","abk","ab"),
        LanguageMetadata("Afar","Afaraf","aa","","aar","aar","aa"),
        LanguageMetadata("Afrikaans","Afrikaans","af","af","afr","afr","af"),
        LanguageMetadata("Akan","Akan","ak","","aka","aka","ak"),
        LanguageMetadata("Albanian","Shqip","sq","sq","","sqi","sq"),
        LanguageMetadata("Amharic","አማርኛ","am","am","amh","amh","am"),
        LanguageMetadata("Arabic (Levantine)","عربي شامي","","ar","","apc","apc"),
        LanguageMetadata("Arabic (Najdi)","عربي شامي","","ar","","ars","ars"),
        LanguageMetadata("Arabic","العربية","ar","ar","ara","ara","ar"),
        LanguageMetadata("Aragonese","aragonés","an","an","arg","arg","an"),
        LanguageMetadata("Armenian","Հայերեն","hy","hy","","hye","hy"),
        LanguageMetadata("Assamese","অসমীয়া","as","as","asm","asm","as"),
        LanguageMetadata("Asturian","asturianu","","at","","ast","ast"),
        LanguageMetadata("Avaric","авар мацӀ, магӀарул мацӀ","av","","ava","ava","av"),
        LanguageMetadata("Avestan","avesta","ae","","ave","ave","ae"),
        LanguageMetadata("Aymara","aymar aru","ay","","aym","aym","ay"),
        LanguageMetadata("Azerbaijani (South)","Azərbaycan (Cənubi)","","az-zb","","","azb"),
        LanguageMetadata("Azerbaijani","Azərbaycan","az","az-az","aze","aze","az"),
        LanguageMetadata("Bambara","bamanankan","bm","","bam","bam","bm"),
        LanguageMetadata("Bashkir","башҡорт теле","ba","","bak","bak","ba"),
        LanguageMetadata("Basque","euskara, euskera","eu","eu","","eus","eu"),
        LanguageMetadata("Belarusian","беларуская мова","be","be","bel","bel","be"),
        LanguageMetadata("Bengali","বাংলা","bn","bn","ben","ben","bn"),
        LanguageMetadata("Bihari","भोजपुरी","bh","","bih","bih","bh"),
        LanguageMetadata("Bislama","Bislama","bi","","bis","bis","bi"),
        LanguageMetadata("Bosnian","bosanski jezik","bs","bs","bos","bos","bs"),
        LanguageMetadata("Breton","brezhoneg","br","br","bre","bre","br"),
        LanguageMetadata("Bulgarian","български език","bg","bg","bul","bul","bg"),
        LanguageMetadata("Burmese","ဗမာစာ","my","my","","mya","my"),
        LanguageMetadata("Catalan","català","ca","ca","cat","cat","ca"),
        LanguageMetadata("Chamorro","Chamoru","ch","","cha","cha","ch"),
        LanguageMetadata("Chechen","нохчийн мотт","ce","","che","che","ce"),
        LanguageMetadata("Chichewa","chiCheŵa, chinyanja","ny","","nya","nya","ny"),
        LanguageMetadata("Chinese (Cantonese)","廣東話, 广东话","zh","zh-ca","","yue","yue"),
        LanguageMetadata("Chinese (simplified)","汉语","zh","zh-cn","","","zh-hans"),
        LanguageMetadata("Chinese (Taiwan)","正體中文(臺灣)","zh","zh-tw","","","zh-hant-tw"),
        LanguageMetadata("Chinese (traditional)","漢語","zh","zh-tw","","","zh-hant"),
        LanguageMetadata("Chinese","中文, 汉语, 漢語","zh","ze","chi","zho","zh"),
        LanguageMetadata("Chuvash","чӑваш чӗлхи","cv","","chv","chv","cv"),
        LanguageMetadata("Cornish","Kernewek","kw","","cor","cor","kw"),
        LanguageMetadata("Corsican","corsu, lingua corsa","co","","cos","cos","co"),
        LanguageMetadata("Cree","ᓀᐦᐃᔭᐍᐏᐣ","cr","","cre","cre","cr"),
        LanguageMetadata("Croatian","hrvatski jezik","hr","hr","hrv","hrv","hr"),
        LanguageMetadata("Czech","čeština, český jazyk","cs","cs","","ces","cs"),
        LanguageMetadata("Danish","dansk","da","da","dan","dan","da"),
        LanguageMetadata("Dari","دری","","pr","","prs","prs"),
        LanguageMetadata("Divehi","ދިވެހި","dv","","div","div","dv"),
        LanguageMetadata("Dutch","Nederlands, Vlaams","nl","nl","","nld","nl"),
        LanguageMetadata("Dzongkha","རྫོང་ཁ","dz","","dzo","dzo","dz"),
        LanguageMetadata("English","English","en","en","eng","eng","en"),
        LanguageMetadata("Esperanto","Esperanto","eo","eo","epo","epo","eo"),
        LanguageMetadata("Estonian","eesti, eesti keel","et","et","est","est","et"),
        LanguageMetadata("Ewe","Eʋegbe","ee","","ewe","ewe","ee"),
        LanguageMetadata("Extremaduran","Estremeñu","","ex","","ext","ext"),
        LanguageMetadata("Faroese","føroyskt","fo","","fao","fao","fo"),
        LanguageMetadata("Fijian","vosa Vakaviti","fj","","fij","fij","fj"),
        LanguageMetadata("Filipino","Wikang Filipino","","","fil","fil","fil"),
        LanguageMetadata("Finnish","suomi, suomen kieli","fi","fi","fin","fin","fi"),
        LanguageMetadata("French","français, langue française","fr","fr","","fra","fr"),
        LanguageMetadata("Fula","Fulfulde, Pulaar, Pular","ff","","ful","ful","ff"),
        LanguageMetadata("Galician","Galego","gl","gl","glg","glg","gl"),
        LanguageMetadata("Ganda","Luganda","lg","","lug","lug","lg"),
        LanguageMetadata("Georgian","ქართული","ka","ka","","kat","ka"),
        LanguageMetadata("German","Deutsch","de","de","","deu","de"),
        LanguageMetadata("Greek","ελληνικά","el","el","","ell","el"),
        LanguageMetadata("Guaraní","Avañe'ẽ","gn","","grn","grn","gn"),
        LanguageMetadata("Gujarati","ગુજરાતી","gu","","guj","guj","gu"),
        LanguageMetadata("Haitian","Kreyòl ayisyen","ht","","hat","hat","ht"),
        LanguageMetadata("Hausa","(Hausa) هَوُسَ","ha","","hau","hau","ha"),
        LanguageMetadata("Hebrew","עברית","he","he","heb","heb","he"),
        LanguageMetadata("Herero","Otjiherero","hz","","her","her","hz"),
        LanguageMetadata("Hindi","हिन्दी, हिंदी","hi","hi","hin","hin","hi"),
        LanguageMetadata("Hiri Motu","Hiri Motu","ho","","hmo","hmo","ho"),
        LanguageMetadata("Hungarian","Magyar","hu","hu","hun","hun","hu"),
        LanguageMetadata("Icelandic","Íslenska","is","is","","isl","is"),
        LanguageMetadata("Ido","Ido","io","","ido","ido","io"),
        LanguageMetadata("Igbo","Asụsụ Igbo","ig","ig","ibo","ibo","ig"),
        LanguageMetadata("Indonesian","Bahasa Indonesia","id","id","ind","ind","id"),
        LanguageMetadata("Interlingua","Interlingua","ia","ia","ina","ina","ia"),
        LanguageMetadata("Interlingue","Interlingue (originally Occidental)","ie","","ile","ile","ie"),
        LanguageMetadata("Inuktitut","ᐃᓄᒃᑎᑐᑦ","iu","","iku","iku","iu"),
        LanguageMetadata("Inupiaq","Iñupiaq, Iñupiatun","ik","","ipk","ipk","ik"),
        LanguageMetadata("Irish","Gaeilge","ga","ga","gle","gle","ga"),
        LanguageMetadata("Italian","italiano","it","it","ita","ita","it"),
        LanguageMetadata("Japanese","日本語 (にほんご)","ja","ja","jpn","jpn","ja"),
        LanguageMetadata("Javanese","ꦧꦱꦗꦮ","jv","","jav","jav","jv"),
        LanguageMetadata("Kalaallisut","kalaallisut, kalaallit oqaasii","kl","","kal","kal","kl"),
        LanguageMetadata("Kannada","ಕನ್ನಡ","kn","kn","kan","kan","kn"),
        LanguageMetadata("Kanuri","Kanuri","kr","","kau","kau","kr"),
        LanguageMetadata("Kashmiri","कश्मीरी, كشميري‎","ks","","kas","kas","ks"),
        LanguageMetadata("Kazakh","қазақ тілі","kk","kk","kaz","kaz","kk"),
        LanguageMetadata("Khmer","ខ្មែរ, ខេមរភាសា, ភាសាខ្មែរ","km","km","khm","khm","km"),
        LanguageMetadata("Kikuyu","Gĩkũyũ","ki","","kik","kik","ki"),
        LanguageMetadata("Kinyarwanda","Ikinyarwanda","rw","","kin","kin","rw"),
        LanguageMetadata("Kirundi","Ikirundi","rn","","run","run","rn"),
        LanguageMetadata("Komi","коми кыв","kv","","kom","kom","kv"),
        LanguageMetadata("Kongo","Kikongo","kg","","kon","kon","kg"),
        LanguageMetadata("Korean","한국어, 조선어","ko","ko","kor","kor","ko"),
        LanguageMetadata("Kurdish","Kurdî, كوردی‎","ku","ku","kur","kur","ku"),
        LanguageMetadata("Kwanyama","Kuanyama","kj","","kua","kua","kj"),
        LanguageMetadata("Kyrgyz","Кыргызча, Кыргыз тили","ky","","kir","kir","ky"),
        LanguageMetadata("Lao","ພາສາລາວ","lo","","lao","lao","lo"),
        LanguageMetadata("Latin","latine, lingua latina","la","","lat","lat","la"),
        LanguageMetadata("Latvian","latviešu valoda","lv","lv","lav","lav","lv"),
        LanguageMetadata("Limburgish","Limburgs","li","","lim","lim","li"),
        LanguageMetadata("Lingala","Lingála","ln","","lin","lin","ln"),
        LanguageMetadata("Lithuanian","lietuvių kalba","lt","lt","lit","lit","lt"),
        LanguageMetadata("Luba-Katanga","Tshiluba","lu","","lub","lub","lu"),
        LanguageMetadata("Luxembourgish","Lëtzebuergesch","lb","lb","ltz","ltz","lb"),
        LanguageMetadata("Macedonian","македонски","mk","mk","","mkd","mk"),
        LanguageMetadata("Malagasy","fiteny malagasy","mg","","mlg","mlg","mg"),
        LanguageMetadata("Malay","Bahasa Melayu, بهاس ملايو‎","ms","ms","","msa","ms"),
        LanguageMetadata("Malayalam","മലയാളം","ml","ml","mal","mal","ml"),
        LanguageMetadata("Maltese","Malti","mt","","mlt","mlt","mt"),
        LanguageMetadata("Manipuri","ꯃꯅꯤꯄꯨꯔꯤ, মণিপুরী","","ma","mni","mni","mni"),
        LanguageMetadata("Manx","Gaelg, Gailck","gv","","glv","glv","gv"),
        LanguageMetadata("Māori","te reo Māori","mi","","","mri","mi"),
        LanguageMetadata("Marathi","मराठी","mr","mr","mar","mar","mr"),
        LanguageMetadata("Marshallese","Kajin M̧ajeļ","mh","","mah","mah","mh"),
        LanguageMetadata("Mongolian","Монгол хэл","mn","mn","mon","mon","mn"),
        LanguageMetadata("Montenegrin","crnogorski, црногорски","","me","cnr","cnr","cnr"),
        LanguageMetadata("Nauru","Dorerin Naoero","na","","nau","nau","na"),
        LanguageMetadata("Navajo","Diné bizaad","nv","nv","nav","nav","nv"),
        LanguageMetadata("Ndonga","Owambo","ng","","ndo","ndo","ng"),
        LanguageMetadata("Nepali","नेपाली","ne","ne","nep","nep","ne"),
        LanguageMetadata("Northern Ndebele","isiNdebele","nd","","nde","nde","nd"),
        LanguageMetadata("Northern Sami","Davvisámegiella","se","se","sme","sme","se"),
        LanguageMetadata("Norwegian Bokmål","Norsk bokmål","nb","","nob","nob","nb"),
        LanguageMetadata("Norwegian Nynorsk","Norsk nynorsk","nn","","nno","nno","nn"),
        LanguageMetadata("Norwegian","Norsk","no","no","nor","nor","no"),
        LanguageMetadata("Nuosu","ꆈꌠ꒿ Nuosuhxop","ii","","iii","iii","ii"),
        LanguageMetadata("Occitan","occitan, lenga d'òc","oc","oc","oci","oci","oc"),
        LanguageMetadata("Ojibwe","ᐊᓂᔑᓈᐯᒧᐎᓐ","oj","","oji","oji","oj"),
        LanguageMetadata("Old Church Slavonic","ѩзыкъ словѣньскъ","cu","","chu","chu","cu"),
        LanguageMetadata("Oriya","ଓଡ଼ିଆ","or","or","ori","ori","or"),
        LanguageMetadata("Oromo","Afaan Oromoo","om","","orm","orm","om"),
        LanguageMetadata("Ossetian","ирон æвзаг","os","","oss","oss","os"),
        LanguageMetadata("Pali","पाऴि","pi","","pli","pli","pi"),
        LanguageMetadata("Panjabi","ਪੰਜਾਬੀ, پنجابی‎","pa","","pan","pan","pa"),
        LanguageMetadata("Pashto","پښتو","ps","ps","pus","pus","ps"),
        LanguageMetadata("Persian","فارسی","fa","fa","","fas","fa"),
        LanguageMetadata("Polish","Polski, polszczyzna","pl","pl","pol","pol","pl"),
        LanguageMetadata("Portuguese (Brazil)","Português (Brasil)","pt","pt-br","por","por","pt-br"),
        LanguageMetadata("Portuguese (Mozambique)","Português (Moçambique)","pt","pm","por","por","pt-mz"),
        LanguageMetadata("Portuguese","Português","pt","pt-pt","por","por","pt"),
        LanguageMetadata("Quechua","Runa Simi, Kichwa","qu","","que","que","qu"),
        LanguageMetadata("Reunion Creole French","Kréol Rénioné","","","rcf","rcf","rcf"),
        LanguageMetadata("Romanian","Română","ro","ro","","ron","ro"),
        LanguageMetadata("Romansh","rumantsch grischun","rm","","roh","roh","rm"),
        LanguageMetadata("Russian","Русский","ru","ru","rus","rus","ru"),
        LanguageMetadata("Samoan","gagana fa'a Samoa","sm","","smo","smo","sm"),
        LanguageMetadata("Sango","yângâ tî sängö","sg","","sag","sag","sg"),
        LanguageMetadata("Sanskrit","संस्कृतम्","sa","","san","san","sa"),
        LanguageMetadata("Santali","ᱥᱟᱱᱛᱟᱲᱤ","","sx","","sat","sat"),
        LanguageMetadata("Sardinian","sardu","sc","","srd","srd","sc"),
        LanguageMetadata("Scottish Gaelic","Gàidhlig","gd","gd","gla","gla","gd"),
        LanguageMetadata("Serbian","српски језик","sr","sr","srp","srp","sr"),
        LanguageMetadata("Shona","chiShona","sn","","sna","sna","sn"),
        LanguageMetadata("Sindhi","सिन्धी, سنڌي، سندھی‎","sd","sd","snd","snd","sd"),
        LanguageMetadata("Sinhalese","සිංහල","si","si","sin","sin","si"),
        LanguageMetadata("Slovak","slovenčina, slovenský jazyk","sk","sk","","slk","sk"),
        LanguageMetadata("Slovene","slovenski jezik, slovenščina","sl","sl","slv","slv","sl"),
        LanguageMetadata("Somali","Soomaaliga, af Soomaali","so","so","som","som","so"),
        LanguageMetadata("Southern Ndebele","isiNdebele","nr","","nbl","nbl","nr"),
        LanguageMetadata("Southern Sotho","Sesotho","st","","sot","sot","st"),
        LanguageMetadata("Spanish (Europe)","Español (Europa)","es","sp","spa","spa","es-es"),
        LanguageMetadata("Spanish (Latin America)","Español (Latinoamérica)","es","ea","spa","spa","es-419"),
        LanguageMetadata("Spanish","Español","es","es","spa","spa","es"),
        LanguageMetadata("Sundanese","Basa Sunda","su","","sun","sun","su"),
        LanguageMetadata("Swahili","Kiswahili","sw","sw","swa","swa","sw"),
        LanguageMetadata("Swati","SiSwati","ss","","ssw","ssw","ss"),
        LanguageMetadata("Swedish","Svenska","sv","sv","swe","swe","sv"),
        LanguageMetadata("Syriac","ܠܫܢܐ ܣܘܪܝܝܐ","","sy","","syc","syc"),
        LanguageMetadata("Tagalog","Wikang Tagalog, ᜆᜄᜎᜓᜄ᜔","tl","tl","","tlg","tl"),
        LanguageMetadata("Tahitian","Reo Tahiti","ty","","tah","tah","ty"),
        LanguageMetadata("Tajik","тоҷикӣ, toçikī, تاجیکی‎","tg","","tgk","tgk","tg"),
        LanguageMetadata("Tamil","தமிழ்","ta","ta","tam","tam","ta"),
        LanguageMetadata("Tatar","татар теле, tatar tele","tt","tt","tat","tat","tt"),
        LanguageMetadata("Telugu","తెలుగు","te","te","tel","tel","te"),
        LanguageMetadata("Tetum","Tetun","","tm-td","","tet","tet"),
        LanguageMetadata("Thai","ไทย","th","th","tha","tha","th"),
        LanguageMetadata("Tibetan Standard","བོད་ཡིག","bo","","","bod","bo"),
        LanguageMetadata("Tigrinya","ትግርኛ","ti","","tir","tir","ti"),
        LanguageMetadata("Toki Pona","toki pona","","tp","","tok","tok"),
        LanguageMetadata("Tonga","faka Tonga","to","","ton","ton","to"),
        LanguageMetadata("Tsonga","Xitsonga","ts","","tso","tso","ts"),
        LanguageMetadata("Tswana","Setswana","tn","","tsn","tsn","tn"),
        LanguageMetadata("Turkish","Türkçe","tr","tr","tur","tur","tr"),
        LanguageMetadata("Turkmen","Türkmen, Түркмен","tk","tk","tuk","tuk","tk"),
        LanguageMetadata("Twi","Twi","tw","","twi","twi","tw"),
        LanguageMetadata("Ukrainian","Українська","uk","uk","ukr","ukr","uk"),
        LanguageMetadata("Urdu","اردو","ur","ur","urd","urd","ur"),
        LanguageMetadata("Uyghur","ئۇيغۇرچە‎, Uyghurche","ug","","uig","uig","ug"),
        LanguageMetadata("Uzbek","Oʻzbek, Ўзбек, أۇزبېك‎","uz","uz","uzb","uzb","uz"),
        LanguageMetadata("Venda","Tshivenḓa","ve","","ven","ven","ve"),
        LanguageMetadata("Vietnamese","Tiếng Việt","vi","vi","vie","vie","vi"),
        LanguageMetadata("Volapük","Volapük","vo","","vol","vol","vo"),
        LanguageMetadata("Walloon","walon","wa","","wln","wln","wa"),
        LanguageMetadata("Welsh","Cymraeg","cy","cy","","cym","cy"),
        LanguageMetadata("Western Frisian","Frysk","fy","","fry","fry","fy"),
        LanguageMetadata("Wolof","Wollof","wo","","wol","wol","wo"),
        LanguageMetadata("Xhosa","isiXhosa","xh","","xho","xho","xh"),
        LanguageMetadata("Yiddish","ייִדיש","yi","","yid","yid","yi"),
        LanguageMetadata("Yoruba","Yorùbá","yo","","yor","yor","yo"),
        LanguageMetadata("Zhuang","Saɯ cueŋƅ, Saw cuengh","za","","zha","zha","za"),
        LanguageMetadata("Zulu","isiZulu","zu","","zul","zul","zu"),
    )
}
