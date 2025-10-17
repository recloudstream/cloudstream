package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.Prerelease
import java.util.Locale

// If you find a way to use SettingsGeneral getCurrentLocale()
// instead of this function do it.
fun getCurrentLocale(): String {
    return Locale.getDefault().toLanguageTag()
}

@Suppress(
    "unused",
    "MemberVisibilityCanBePrivate"
)
object SubtitleHelper {
    @Deprecated(
        "Default language code changed to IETF BCP 47 tag",
        ReplaceWith("LanguageMetadata(languageName, nativeName, ISO_639_1.ifBlank { ISO_639_2_B }), ISO_639_1, ISO_639_2_B, ISO_639_3, ISO_639_1"))
    data class Language639(
        val languageName: String,
        val nativeName: String,
        val ISO_639_1: String,
        val ISO_639_2_T: String,
        val ISO_639_2_B: String,
        val ISO_639_3: String,
        val ISO_639_6: String,
    )

    /**
     * Represents one language with english name, native name and codes.
     * IETF BCP 47 conformant tag shall be used as the first choice for language code!
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
        val IETF_tag: String,       // Combine ISO 639-1, 639-3 and ISO 3166-1 (or numeric UN M49 / CLDR) for country / region
        val ISO_639_1: String,
        val ISO_639_2_B: String,    // ISO 639-2/T missing as it's a subset of ISO 639-3
        val ISO_639_3: String,      // ISO 639-6 missing as it's intended to differentiate specific dialects and variants
        val openSubtitles: String, // inconsistent codes that do not conform ISO 639
    ) {
        fun localizedName(localizedTo: String? = null): String {
            // Use system locale to localize language name
            val localeOfLangCode = Locale.forLanguageTag(this.IETF_tag)
            val localeOfLocalizeTo = Locale.forLanguageTag(localizedTo ?: getCurrentLocale())
            val sysLocalizedName = localeOfLangCode.getDisplayName(localeOfLocalizeTo)

            val langCodeWithCountry = "${localeOfLangCode.language} (" // ${localeOfLangCode.country})"
            val failedToLocalize =
                sysLocalizedName.equals(this.IETF_tag, ignoreCase = true) ||
                sysLocalizedName.contains(langCodeWithCountry, ignoreCase = true)

            return if (failedToLocalize)
                // fallback to native language name
                this.nativeName
            else
                sysLocalizedName
        }

        fun nameNextToFlagEmoji(localizedTo: String? = null): String {
            // fallback to [A][A] -> [?] question mak flag
            val flag = getFlagFromIso(this.IETF_tag) ?: "\ud83c\udde6\ud83c\udde6"

            return "$flag\u00a0${localizedName(localizedTo)}" // \u00a0 non-breaking space
        }
    }

    /**
     * Language name (english or native) -> [LanguageMetadata]
     * @param languageName language name
     * @param halfMatch match with `contains()` instead of `equals()`
    */
    private fun getLanguageDataFromName(languageName: String?, halfMatch: Boolean? = false): LanguageMetadata? {
        if (languageName.isNullOrBlank() || languageName.length < 2) return null
        // Workaround to avoid junk like "English (original audio)" or "Spanish 123"
        // or "اَلْعَرَبِيَّةُ (Original Audio) 1" or "English (hindi sub)"…
        val garbage = Regex(
            "\\([^)]*(?:dub|sub|original|audio|code)[^)]*\\)|" + // junk words in parenthesis
            "[\\u064B-\\u065B]|" + // arabic diacritics
            "\\d|" +  // numbers
            "[^\\p{L}\\p{Mn}\\p{Mc}\\p{Me} ()]" // non-letter (from any language)
        )
        val lowLangName = languageName.lowercase().replace(garbage, "").trim()
        val index =
            indexMapLanguageName[lowLangName] ?:
            indexMapNativeName[lowLangName] ?: -1
        val langMetadata = languages.getOrNull(index)

        if (halfMatch == true && langMetadata == null) {
            for (lang in languages)
                if (lang.languageName.contains(lowLangName, ignoreCase = true) ||
                    lang.nativeName.contains(lowLangName, ignoreCase = true))
                    return lang
        }
        return langMetadata
    }

    // @Deprecated(
    //     "Default language code changed to IETF BCP 47 tag",
    //     ReplaceWith("fromLanguageToTagIETF(input, looseCheck)"))
    /**
     * Language name (english or native) -> ISO_639_1
     * @param input language name
     * @param looseCheck match with `contains()` instead of `equals()`
    */
    fun fromLanguageToTwoLetters(input: String, looseCheck: Boolean): String? {
        return getLanguageDataFromName(input, looseCheck)?.ISO_639_1
    }


    // @Deprecated(
    //     "Default language code changed to IETF BCP 47 tag",
    //     ReplaceWith("fromLanguageToTagIETF(input)"))
    /**
     * Language name (english or native) -> ISO_639_3
    */
    fun fromLanguageToThreeLetters(input: String): String? {
        return getLanguageDataFromName(input)?.ISO_639_3
    }

    /**
     * Language name -> IETF_tag
     * @param languageName language name
     * @param halfMatch match with `contains()` instead of `equals()`
    */
    fun fromLanguageToTagIETF(languageName: String?, halfMatch: Boolean? = false): String? {
        return getLanguageDataFromName(languageName, halfMatch)?.IETF_tag
    }

    /**
     * Language code -> [LanguageMetadata]
     * @param languageCode IETF BCP 47, ISO 639-1, ISO 639-2B/T, ISO 639-3, OpenSubtitles
     * @return [LanguageMetadata] or `null`
    */
    private fun getLanguageDataFromCode(languageCode: String?): LanguageMetadata?  {
        if (languageCode.isNullOrBlank() || languageCode.length < 2) return null

        val lowLangCode = languageCode.lowercase().trim()
        val index =
            indexMapIETF_tag[lowLangCode] ?:
            indexMapISO_639_1[lowLangCode] ?:
            indexMapISO_639_3[lowLangCode] ?:
            indexMapISO_639_2_B[lowLangCode] ?:
            indexMapOpenSubtitles[lowLangCode] ?: -1

        return languages.getOrNull(index)
    }

    // @Deprecated(
    //     "Default language code changed to IETF BCP 47 tag",
    //     ReplaceWith("fromTagToLanguageName(input)"))
    /**
     * Language code -> language english name
    */
    fun fromTwoLettersToLanguage(input: String): String? {
        return getLanguageDataFromCode(input)?.languageName
    }

    // @Deprecated(
    //     "Default language code changed to IETF BCP 47 tag",
    //     ReplaceWith("fromTagToLanguageName(input)"))
    /**
     * Language code -> language english name
    */
    fun fromThreeLettersToLanguage(input: String): String? {
        return getLanguageDataFromCode(input)?.languageName
    }

    /**
     * Language code -> language name (if not found, fallback to native language name)
     * @param languageCode IETF BCP 47, ISO 639-1, ISO 639-2B/T, ISO 639-3, OpenSubtitles
     * @param localizedTo IETF BCP 47 tag to localize the language name to. Default: app current language
    */
    fun fromTagToLanguageName(languageCode: String?, localizedTo: String? = null): String? {
        return getLanguageDataFromCode(languageCode)?.localizedName(localizedTo)
    }

    /**
     * Language code -> language english name
     * @param languageCode IETF BCP 47, ISO 639-1, ISO 639-2B/T, ISO 639-3, OpenSubtitles
    */
    fun fromTagToEnglishLanguageName(languageCode: String?): String? {
        return getLanguageDataFromCode(languageCode)?.languageName
    }

    /**
     * Language code -> openSubtitles inconsistent language tag
     * @param languageCode IETF BCP 47, ISO 639-1, ISO 639-2B/T, ISO 639-3, OpenSubtitles
    */
    fun fromCodeToOpenSubtitlesTag(languageCode: String?): String? {
        return getLanguageDataFromCode(languageCode)?.openSubtitles
    }

    /** openSubtitles -> IETF_tag */
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
    fun isWellFormedTagIETF(langTagIETF: String?): Boolean {
        if (langTagIETF.isNullOrBlank() || langTagIETF.length < 2) return false

        // Written by Addison Phillips, <Addison at amazon.com>
        // https://www.langtag.net/philips-regexp.html
        val langTagRegex = """
            +(^[xX](\x2d\p{Alnum}{1,8})*$)
            +|(((^\p{Alpha}{2,8}(?=\x2d|$)){1}
            +((\x2d\p{Alpha}{3})(?=\x2d|$)){0,3}
            +(\x2d\p{Alpha}{4}(?=\x2d|$))?
            +(\x2d(\p{Alpha}{2}|\d{3})(?=\x2d|$))?
            +(\x2d(\d\p{Alnum}{3}|\p{Alnum}{5,8})(?=\x2d|$))*)
            +((\x2d([a-wyzA-WYZ](?=\x2d))(\x2d(\p{Alnum}{2,8})+)*))*
            +(\x2d[xX](\x2d\p{Alnum}{1,8})*)?)$
            """.trimMargin("+").toRegex()
        return langTagIETF.matches(langTagRegex)
    }

    /**
     * Try to get a flag emoji form a language code
     * or two letters country code (ISO 3166-1-alfa-2)
    */
    fun getFlagFromIso(inp: String?): String? {
        if (inp.isNullOrBlank() || inp.length < 2) return null

        // 2 times a symbol between regional indicator "[A]" and "[Z]"
        val unicodeFlagRegex = Regex("[\uD83C\uDDE6-\uD83C\uDDFF]{2}")
        // language tags (en-US, es-419, pt-BR, zh-hant-TW) that includes country
        val countryRegex = Regex("[-_](\\p{Alnum}{2,3})$", RegexOption.IGNORE_CASE)

        val country = countryRegex.find(inp)?.groupValues?.get(1)

        val flagEmoji =
            getFlagFromCountry2Letters(lang2country[inp.lowercase()]) ?:
            getFlagFromCountry2Letters(country?.uppercase()) ?:
            getFlagFromCountry2Letters(inp.uppercase()) ?:
            getFlagFromCountry2Letters(lang2country[country?.lowercase()]) ?: ""

        if (inp.equals("qt", ignoreCase = true))
            return "\uD83E\uDD8D" // "mmmm... monke" -> gorilla [🦍]
        if (flagEmoji.matches(unicodeFlagRegex))
            return flagEmoji
        return null
    }

    /**
     * Get a flag emoji next to the language name
     * @param languageCode IETF BCP 47, ISO 639-1, ISO 639-2B/T, ISO 639-3, OpenSubtitles
     * @param localizedTo IETF BCP 47 tag to localize the language name to. Default: app current language
    */
    fun getNameNextToFlagEmoji(languageCode: String?, localizedTo: String? = null): String? {
        return getLanguageDataFromCode(languageCode)?.nameNextToFlagEmoji(localizedTo)
    }

    // 2 letters country code (ISO 3166-1-alfa-2) -> flag emoji
    private fun getFlagFromCountry2Letters(countryLetters: String?): String? {
        if (countryLetters.isNullOrBlank() || countryLetters.length != 2) return null

        val asciiOffset = 0x41    // uppercase "A"
        val flagOffset = 0x1F1E6  // regional indicator "[A]"
        val offset = flagOffset - asciiOffset

        val firstChar: Int = Character.codePointAt(countryLetters, 0) + offset
        val secondChar: Int = Character.codePointAt(countryLetters, 1) + offset

        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    // when (langTag = country) or (langTag contains country)
    // as in:
    //   "es" to "ES"
    //   "pt" to "PT"
    //   "fr" to "FR"
    //   "en-US" to "US"
    //   "pt-BR" to "BR"
    //   "zh-hant-TW" to "TW"
    // add to this list is useless as getFlagFromIso() already
    // handles it.
    // Adding here is still an option to overwrite a flag like in:
    //   "am" to "ET" => Ethiopia flag for Amharic instead of Armenia flag
    // For country / region see
    // https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
    // https://en.wikipedia.org/wiki/UN_M49
    private val lang2country = mapOf(
        "419" to "ES", // (?_?) Latin America Spanish -> ES or a L.A. country?
        "aa" to "ET",
        "af" to "ZA",
        "agq" to "CM",
        "ajp" to "SY",
        "ak" to "GH",
        "am" to "ET",
        "an" to "ES",
        "apc" to "SY",
        "ar" to "AE",
        "arg" to "ES",
        "ars" to "SA",
        "as" to "IN",
        "asa" to "TZ",
        "av" to "RU", // (?_?) Avaric -> RU
        "ay" to "BO", // (?_?) Aymara -> BO
        "azb" to "AZ",
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
        "ckb" to "IQ", // (?_?) Central Kurdish -> IQ or IR
        "cnr" to "ME",
        "cs" to "CZ",
        "cy" to "GB",
        "da" to "DK",
        "dav" to "KE",
        "dje" to "NE",
        "dua" to "CM",
        "dyo" to "SN",
        "dz" to "BT",
        "ea" to "ES",
        "ebu" to "KE",
        "ee" to "GH",
        "el" to "GR",
        "en" to "GB",
        "et" to "EE",
        "eu" to "ES",
        "ewo" to "CM",
        "ex" to "ES",
        "ext" to "ES",
        "fa" to "IR",
        "ff" to "CN",
        "fil" to "PH",
        "fj" to "FJ",
        "ga" to "IE",
        "gd" to "GB",
        "gl" to "ES",
        "gn" to "PY", // (?_?) Guarani -> PY
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
        "jv" to "ID",
        "jvn" to "ID",
        "ka" to "GE",
        "kab" to "DZ",
        "kam" to "KE",
        "kde" to "TZ",
        "kea" to "CV",
        "kg" to "CD", // (?_?) Kongo -> CD or CG or AO
        "khq" to "ML",
        "ki" to "KE",
        "kk" to "KZ",
        "kl" to "GL",
        "kln" to "KE",
        "km" to "KH",
        "kn" to "IN",
        "ko" to "KR",
        "kok" to "IN",
        "kr" to "TD", // (?_?) Kanuri -> TD or NE
        "ks" to "IN", // (?_?) Kashmiri -> IN or PK
        "ksb" to "TZ",
        "ksf" to "CM",
        "ku" to "IQ",
        "kw" to "GB",
        "ky" to "KG",
        "la" to "IT",
        "lag" to "TZ",
        "lat" to "IT",
        "lat" to "LV",
        "lb" to "LU",
        "lg" to "UG",
        "ln" to "CG",
        "lo" to "LA",
        "ltz" to "LU",
        "lu" to "CD",
        "luo" to "KE",
        "luy" to "KE",
        "ma" to "IN",
        "mas" to "TZ",
        "mer" to "KE",
        "mfe" to "MU",
        "mgh" to "MZ",
        "ml" to "IN",
        "mni" to "IN",
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
        "nr" to "ZA", // (?_?) Southern Ndebele -> ZA or ZW
        "nus" to "SD",
        "nv" to "US",
        "ny" to "MW", // (?_?) Nyanja -> MW
        "nyn" to "UG",
        "oc" to "ES",
        "oci" to "ES",
        "om" to "ET",
        "or" to "IN",
        "pa" to "IN",
        "pa" to "PK",
        "pan" to "IN",
        "pb" to "BR",
        "pm" to "MZ",
        "por" to "PT",
        "pr" to "AF",
        "prs" to "AF",
        "ps" to "AF",
        "qu" to "PE", // (?_?) Quechua -> PE or EC or BO or CO or CL or AR
        "que" to "PE", // (?_?) Quechua -> PE or EC or BO or CO or CL or AR
        "rm" to "CH",
        "rn" to "BI",
        "rof" to "TZ",
        "rwk" to "TZ",
        "sa" to "IN",
        "san" to "IN",
        "saq" to "KE",
        "sat" to "IN",
        "sbp" to "TZ",
        "sd" to "PK", // (?_?) Sindhi -> PK or IN
        "sdn" to "PK", // (?_?) Sindhi -> PK or IN
        "se" to "NO",
        "seh" to "MZ",
        "ses" to "ML",
        "sg" to "CF",
        "shi" to "MA",
        "si" to "LK",
        "sl" to "SI",
        "sm" to "WS",
        "smo" to "WS",
        "sn" to "ZW",
        "sp" to "ES",
        "sq" to "AL",
        "sr" to "RS",
        "st" to "LS", // (?_?) Southern Sotho -> LS or ZA
        "su" to "ID",
        "sv" to "SE",
        "sw" to "TZ",
        "swc" to "CD",
        "ta" to "IN",
        "tat" to "RU",
        "tdt" to "TL",
        "te" to "IN",
        "teo" to "UG",
        "tg" to "TJ",
        "tgk" to "TJ",
        "ti" to "ET",
        "tk" to "TM",
        "tl" to "PH",
        "tm-td" to "TL", // (?_?) Tetun -> TL
        "tn" to "ZA", // (?_?) Tswana -> ZA or BW
        "ts" to "ZA",
        "tso" to "ZA",
        "tt" to "RU",
        "tuk" to "TM",
        "twq" to "NE",
        "tzm" to "MA",
        "uk" to "UA",
        "ur" to "PK",
        "vai" to "LR",
        "vi" to "VN",
        "vun" to "TZ",
        "wo" to "SN",
        "xh" to "ZA",
        "xho" to "ZA",
        "xog" to "UG",
        "yav" to "CM",
        "yo" to "NG",
        "yue" to "CN",
        "za" to "CN",
        "zh-hans" to "CN", // (?_?) Chinese (simplified) -> CN ?
        "zh-hant" to "TW", // (?_?) Chinese (traditional) -> CN or TW or other ?
        "zh" to "CN",
        "zha" to "CN",
        "zu" to "ZA",
    )

    @Suppress("SpellCheckingInspection")
    val languages = listOf(
        // languageName, nativeName, IETF_tag, ISO_639_1, ISO_639_2_B, ISO_639_3, openSubtitles
        LanguageMetadata("Afar","Afaraf","aa","aa","aar","aar",""),
        LanguageMetadata("Afrikaans","Afrikaans","af","af","afr","afr","af"),
        LanguageMetadata("Akan","Akan","ak","ak","aka","aka",""),
        LanguageMetadata("Albanian","Shqip","sq","sq","","sqi","sq"),
        LanguageMetadata("Amharic","አማርኛ","am","am","amh","amh","am"),
        LanguageMetadata("Arabic","العربية","ar","ar","ara","ara","ar"),
        LanguageMetadata("Arabic (Levantine)","عربي شامي","apc","","ajp","apc","ar"), // "ajp" is deprecated, keeping for compatibility
        LanguageMetadata("Arabic (Najdi)","عربي شامي","ars","","","ars","ar"),
        LanguageMetadata("Aragonese","aragonés","an","an","arg","arg","an"),
        LanguageMetadata("Armenian","Հայերեն","hy","hy","","hye","hy"),
        LanguageMetadata("Assamese","অসমীয়া","as","as","asm","asm","as"),
        LanguageMetadata("Avaric","авар мацӀ, магӀарул мацӀ","av","av","ava","ava",""),
        LanguageMetadata("Aymara","aymar aru","ay","ay","aym","aym",""),
        LanguageMetadata("Azerbaijani","Azərbaycan","az","az","aze","aze","az-az"),
        LanguageMetadata("Azerbaijani (South)","Azərbaycan (Cənubi)","azb","","","azb","az-zb"),
        LanguageMetadata("Bambara","bamanankan","bm","bm","bam","bam",""),
        LanguageMetadata("Basque","euskara, euskera","eu","eu","","eus","eu"),
        LanguageMetadata("Belarusian","беларуская мова","be","be","bel","bel","be"),
        LanguageMetadata("Bengali","বাংলা","bn","bn","ben","ben","bn"),
        LanguageMetadata("Bosnian","bosanski jezik","bs","bs","bos","bos","bs"),
        LanguageMetadata("Breton","brezhoneg","br","br","bre","bre","br"),
        LanguageMetadata("Bulgarian","български език","bg","bg","bul","bul","bg"),
        LanguageMetadata("Burmese","ဗမာစာ","my","my","","mya","my"),
        LanguageMetadata("Catalan","català","ca","ca","cat","cat","ca"),
        LanguageMetadata("Chichewa","chiCheŵa, chinyanja","ny","ny","nya","nya",""),
        LanguageMetadata("Chinese","中文, 汉语, 漢語","zh","zh","chi","zho","ze"),
        LanguageMetadata("Chinese (Cantonese)","廣東話, 广东话","yue","","","yue","zh-ca"),
        LanguageMetadata("Chinese (simplified)","汉语","zh-hans","","","","zh-cn"),
        LanguageMetadata("Chinese (Taiwan)","正體中文(臺灣)","zh-hant-tw","","","","zh-tw"),
        LanguageMetadata("Chinese (traditional)","漢語","zh-hant","","","","zh-tw"),
        LanguageMetadata("Croatian","hrvatski jezik","hr","hr","hrv","hrv","hr"),
        LanguageMetadata("Czech","čeština, český jazyk","cs","cs","","ces","cs"),
        LanguageMetadata("Danish","dansk","da","da","dan","dan","da"),
        LanguageMetadata("Dari","دری","prs","","","prs","pr"),
        LanguageMetadata("Dutch","Nederlands, Vlaams","nl","nl","","nld","nl"),
        LanguageMetadata("Dzongkha","རྫོང་ཁ","dz","dz","dzo","dzo",""),
        LanguageMetadata("English","English","en","en","eng","eng","en"),
        LanguageMetadata("Esperanto","Esperanto","eo","eo","epo","epo","eo"),
        LanguageMetadata("Estonian","eesti, eesti keel","et","et","est","est","et"),
        LanguageMetadata("Ewe","Eʋegbe","ee","ee","ewe","ewe",""),
        LanguageMetadata("Extremaduran","Estremeñu","ext","","","ext","ex"),
        LanguageMetadata("Faroese","føroyskt","fo","fo","fao","fao",""),
        LanguageMetadata("Fijian","vosa Vakaviti","fj","fj","fij","fij",""),
        LanguageMetadata("Filipino","Wikang Filipino","fil","","fil","fil",""),
        LanguageMetadata("Finnish","suomi, suomen kieli","fi","fi","fin","fin","fi"),
        LanguageMetadata("French","Français","fr","fr","","fra","fr"),
        LanguageMetadata("Fula","Fulfulde, Pulaar, Pular","ff","ff","ful","ful",""),
        LanguageMetadata("Galician","Galego","gl","gl","glg","glg","gl"),
        LanguageMetadata("Ganda","Luganda","lg","lg","lug","lug",""),
        LanguageMetadata("Georgian","ქართული","ka","ka","","kat","ka"),
        LanguageMetadata("German","Deutsch","de","de","","deu","de"),
        LanguageMetadata("Greek","ελληνικά","el","el","","ell","el"),
        LanguageMetadata("Guarani","Avañe'ẽ","gn","gn","grn","gug",""),
        LanguageMetadata("Gujarati","ગુજરાતી","gu","gu","guj","guj",""),
        LanguageMetadata("Haitian","Kreyòl ayisyen","ht","ht","hat","hat",""),
        LanguageMetadata("Hausa","(Hausa) هَوُسَ","ha","ha","hau","hau",""),
        LanguageMetadata("Hebrew","עברית","he","iw","heb","heb","he"), // "iw" is deprecated, keeping for compatibility
        LanguageMetadata("Hindi","हिन्दी, हिंदी","hi","hi","hin","hin","hi"),
        LanguageMetadata("Hungarian","Magyar","hu","hu","hun","hun","hu"),
        LanguageMetadata("Icelandic","Íslenska","is","is","","isl","is"),
        LanguageMetadata("Ido","Ido","io","io","ido","ido",""),
        LanguageMetadata("Igbo","Asụsụ Igbo","ig","ig","ibo","ibo","ig"),
        LanguageMetadata("Indonesian","Bahasa Indonesia","id","in","ind","ind","id"), // "in" is deprecated, keeping for compatibility
        LanguageMetadata("Interlingua","Interlingua","ia","ia","ina","ina","ia"),
        LanguageMetadata("Interlingue","Interlingue (originally Occidental)","ie","ie","ile","ile",""),
        LanguageMetadata("Irish","Gaeilge","ga","ga","gle","gle","ga"),
        LanguageMetadata("Italian","italiano","it","it","ita","ita","it"),
        LanguageMetadata("Japanese","日本語 (にほんご)","ja","ja","jpn","jpn","ja"),
        LanguageMetadata("Javanese","ꦧꦱꦗꦮ","jv","jv","jav","jvn",""),
        LanguageMetadata("Kalaallisut","kalaallisut, kalaallit oqaasii","kl","kl","kal","kal",""),
        LanguageMetadata("Kannada","ಕನ್ನಡ","kn","kn","kan","kan","kn"),
        LanguageMetadata("Kanuri","Kanuri","kr","kr","kau","kau",""),
        LanguageMetadata("Kashmiri","कश्मीरी, كشميري‎","ks","ks","kas","kas",""),
        LanguageMetadata("Kazakh","қазақ тілі","kk","kk","kaz","kaz","kk"),
        LanguageMetadata("Khmer","ខ្មែរ, ខេមរភាសា, ភាសាខ្មែរ","km","km","khm","khm","km"),
        LanguageMetadata("Kikuyu","Gĩkũyũ","ki","ki","kik","kik",""),
        LanguageMetadata("Kinyarwanda","Ikinyarwanda","rw","rw","kin","kin",""),
        LanguageMetadata("Kirundi","Ikirundi","rn","rn","run","run",""),
        LanguageMetadata("Kongo","Kikongo","kg","kg","kon","kon",""),
        LanguageMetadata("Korean","한국어, 조선어","ko","ko","kor","kor","ko"),
        LanguageMetadata("Kurdish","Kurdî, كوردی‎","ku","ku","kur","kur","ku"),
        LanguageMetadata("Kyrgyz","Кыргызча, Кыргыз тили","ky","ky","kir","kir",""),
        LanguageMetadata("Lao","ພາສາລາວ","lo","lo","lao","lao",""),
        LanguageMetadata("Latin","Latine","la","la","lat","lat",""),
        LanguageMetadata("Latvian","latviešu valoda","lv","lv","lav","lav","lv"),
        LanguageMetadata("Lingala","Lingála","ln","ln","lin","lin",""),
        LanguageMetadata("Lithuanian","lietuvių kalba","lt","lt","lit","lit","lt"),
        LanguageMetadata("Luba-Katanga","Tshiluba","lu","lu","lub","lub",""),
        LanguageMetadata("Luxembourgish","Lëtzebuergesch","lb","lb","ltz","ltz","lb"),
        LanguageMetadata("Macedonian","македонски","mk","mk","","mkd","mk"),
        LanguageMetadata("Malagasy","fiteny malagasy","mg","mg","mlg","mlg",""),
        LanguageMetadata("Malay","Bahasa Melayu, بهاس ملايو‎","ms","ms","","msa","ms"),
        LanguageMetadata("Malayalam","മലയാളം","ml","ml","mal","mal","ml"),
        LanguageMetadata("Maltese","Malti","mt","mt","mlt","mlt",""),
        LanguageMetadata("Manx","Gaelg, Gailck","gv","gv","glv","glv",""),
        LanguageMetadata("Marathi","मराठी","mr","mr","mar","mar","mr"),
        LanguageMetadata("Marshallese","Kajin M̧ajeļ","mh","mh","mah","mah",""),
        LanguageMetadata("Meitei","ꯃꯅꯤꯄꯨꯔꯤ, মণিপুরী","mni","","mni","mni","ma"),
        LanguageMetadata("Mexican Spanish", "Español mexicano", "es-MX", "mx","","",""), // iso639_1 is not mx but, some extension use it as such
        LanguageMetadata("Mongolian","Монгол хэл","mn","mn","mon","mon","mn"),
        LanguageMetadata("Montenegrin","crnogorski, црногорски","cnr","","cnr","cnr","me"),
        LanguageMetadata("Navajo","Diné bizaad","nv","nv","nav","nav","nv"),
        LanguageMetadata("Nepali","नेपाली","ne","ne","nep","nep","ne"),
        LanguageMetadata("Northern Ndebele","isiNdebele","nd","nd","nde","nde",""),
        LanguageMetadata("Northern Sami","Davvisámegiella","se","se","sme","sme","se"),
        LanguageMetadata("Norwegian","Norsk","no","no","nor","nor","no"),
        LanguageMetadata("Norwegian Bokmål","Norsk bokmål","nb","nb","nob","nob",""),
        LanguageMetadata("Norwegian Nynorsk","Norsk nynorsk","nn","nn","nno","nno",""),
        LanguageMetadata("Nuosu","ꆈꌠ꒿ Nuosuhxop","ii","ii","iii","iii",""),
        LanguageMetadata("Occitan","occitan, lenga d'òc","oc","oc","oci","oci","oc"),
        LanguageMetadata("Oriya","ଓଡ଼ିଆ","or","or","ori","ori","or"),
        LanguageMetadata("Oromo","Afaan Oromoo","om","om","orm","orm",""),
        LanguageMetadata("Panjabi","ਪੰਜਾਬੀ, پنجابی‎","pa","pa","pan","pan",""),
        LanguageMetadata("Pashto","پښتو","ps","ps","pus","pus","ps"),
        LanguageMetadata("Persian (Farsi)","فارسی","fa","fa","","fas","fa"),
        LanguageMetadata("Polish","Polski, polszczyzna","pl","pl","pol","pol","pl"),
        LanguageMetadata("Portuguese","Português","pt","pt","por","por","pt-pt"),
        LanguageMetadata("Portuguese (Brazil)","Português (Brasil)","pt-br","","","","pt-br"),
        LanguageMetadata("Portuguese (Mozambique)","Português (Moçambique)","pt-mz","","","","pm"),
        LanguageMetadata("Quechua","Runa Simi, Kichwa","qu","qu","que","que",""),
        LanguageMetadata("Romanian","Română","ro","ro","","ron","ro"),
        LanguageMetadata("Romansh","rumantsch grischun","rm","rm","roh","roh",""),
        LanguageMetadata("Russian","Русский","ru","ru","rus","rus","ru"),
        LanguageMetadata("Samoan","gagana fa'a Samoa","sm","sm","smo","smo",""),
        LanguageMetadata("Sango","yângâ tî sängö","sg","sg","sag","sag",""),
        LanguageMetadata("Sanskrit","संस्कृतम्","sa","sa","san","san",""),
        LanguageMetadata("Santali","ᱥᱟᱱᱛᱟᱲᱤ","sat","","","sat","sx"),
        LanguageMetadata("Scottish Gaelic","Gàidhlig","gd","gd","gla","gla","gd"),
        LanguageMetadata("Serbian","српски језик","sr","sr","srp","srp","sr"),
        LanguageMetadata("Shona","chiShona","sn","sn","sna","sna",""),
        LanguageMetadata("Sindhi","सिन्धी, سنڌي، سندھی‎","sd","sd","snd","snd","sd"),
        LanguageMetadata("Sinhala","සිංහල","si","si","sin","sin","si"),
        LanguageMetadata("Slovak","slovenčina, slovenský jazyk","sk","sk","","slk","sk"),
        LanguageMetadata("Slovenian","slovenski jezik, slovenščina","sl","sl","slv","slv","sl"),
        LanguageMetadata("Somali","Soomaaliga, af Soomaali","so","so","som","som","so"),
        LanguageMetadata("Sotho","Sesotho","st","st","sot","sot",""),
        LanguageMetadata("Southern Ndebele","isiNdebele","nr","nr","nbl","nbl",""),
        LanguageMetadata("Spanish","Español","es","es","spa","spa","es"),
        LanguageMetadata("Spanish (Europe)","Español (Europa)","es-es","","","","sp"),
        LanguageMetadata("Spanish (Latin America)","Español (Latinoamérica)","es-419","","","","ea"),
        LanguageMetadata("Sundanese","Basa Sunda","su","su","sun","sun",""),
        LanguageMetadata("Swahili","Kiswahili","sw","sw","swa","swa","sw"),
        LanguageMetadata("Swedish","Svenska","sv","sv","swe","swe","sv"),
        LanguageMetadata("Tagalog","Wikang Tagalog, ᜆᜄᜎᜓᜄ᜔","tl","tl","","tlg","tl"),
        LanguageMetadata("Tajik","тоҷикӣ, toçikī, تاجیکی‎","tg","tg","tgk","tgk",""),
        LanguageMetadata("Tamil","தமிழ்","ta","ta","tam","tam","ta"),
        LanguageMetadata("Tatar","татар теле, tatar tele","tt","tt","tat","tat","tt"),
        LanguageMetadata("Telugu","తెలుగు","te","te","tel","tel","te"),
        LanguageMetadata("Tetum","Tetun","tdt","","","tdt","tm-td"),
        LanguageMetadata("Thai","ไทย","th","th","tha","tha","th"),
        LanguageMetadata("Tibetan Standard","བོད་ཡིག","bo","bo","","bod",""),
        LanguageMetadata("Tigrinya","ትግርኛ","ti","ti","tir","tir",""),
        LanguageMetadata("Toki Pona","toki pona","tok","","","tok","tp"),
        LanguageMetadata("Tonga","faka Tonga","to","to","ton","ton",""),
        LanguageMetadata("Tsonga","Xitsonga","ts","ts","tso","tso",""),
        LanguageMetadata("Tswana","Setswana","tn","tn","tsn","tsn",""),
        LanguageMetadata("Turkish","Türkçe","tr","tr","tur","tur","tr"),
        LanguageMetadata("Turkmen","Türkmen, Түркмен","tk","tk","tuk","tuk","tk"),
        LanguageMetadata("Ukrainian","Українська","uk","uk","ukr","ukr","uk"),
        LanguageMetadata("Urdu","اردو","ur","ur","urd","urd","ur"),
        LanguageMetadata("Uzbek","Oʻzbek, Ўзбек, أۇزبېك‎","uz","uz","uzb","uzb","uz"),
        LanguageMetadata("Vietnamese","Tiếng Việt","vi","vi","vie","vie","vi"),
        LanguageMetadata("Welsh","Cymraeg","cy","cy","","cym","cy"),
        LanguageMetadata("Wolof","Wollof","wo","wo","wol","wol",""),
        LanguageMetadata("Xhosa","isiXhosa","xh","xh","xho","xho",""),
        LanguageMetadata("Yoruba","Yorùbá","yo","yo","yor","yor",""),
        LanguageMetadata("Zhuang","Saɯ cueŋƅ, Saw cuengh","za","za","zha","zha",""),
        LanguageMetadata("Zulu","isiZulu","zu","zu","zul","zul",""),
    )

    val indexMapLanguageName = languages.withIndex().associate { (i, lang) -> lang.languageName.lowercase() to i}
    val indexMapNativeName = languages.withIndex().associate { (i, lang) -> lang.nativeName.lowercase() to i}
    val indexMapIETF_tag = languages.withIndex().associate { (i, lang) -> lang.IETF_tag.lowercase() to i}
    val indexMapISO_639_1 = languages.withIndex().associate { (i, lang) -> lang.ISO_639_1.lowercase() to i}
    val indexMapISO_639_2_B = languages.withIndex().associate { (i, lang) -> lang.ISO_639_2_B.lowercase() to i}
    val indexMapISO_639_3 = languages.withIndex().associate { (i, lang) -> lang.ISO_639_3.lowercase() to i}
    val indexMapOpenSubtitles = languages.withIndex().associate { (i, lang) -> lang.openSubtitles.lowercase() to i}
}
