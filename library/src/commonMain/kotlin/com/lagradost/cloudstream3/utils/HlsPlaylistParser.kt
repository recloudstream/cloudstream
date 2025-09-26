/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 NOTE: This is a simplified and more portable kotlin media3 hls parser. 
 */
package com.lagradost.cloudstream3.utils

import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Suppress("unused")
object HlsPlaylistParser {
    private const val LOG_TAG: String = "HlsPlaylistParser"
    private const val PLAYLIST_HEADER: String = "#EXTM3U"
    private const val TAG_PREFIX: String = "#EXT"
    private const val TAG_VERSION: String = "#EXT-X-VERSION"
    private const val TAG_PLAYLIST_TYPE: String = "#EXT-X-PLAYLIST-TYPE"
    private const val TAG_DEFINE: String = "#EXT-X-DEFINE"
    private const val TAG_SERVER_CONTROL: String = "#EXT-X-SERVER-CONTROL"
    private const val TAG_STREAM_INF: String = "#EXT-X-STREAM-INF"
    private const val TAG_PART_INF: String = "#EXT-X-PART-INF"
    private const val TAG_PART: String = "#EXT-X-PART"
    private const val TAG_I_FRAME_STREAM_INF: String = "#EXT-X-I-FRAME-STREAM-INF"
    private const val TAG_IFRAME: String = "#EXT-X-I-FRAMES-ONLY"
    private const val TAG_MEDIA: String = "#EXT-X-MEDIA"
    private const val TAG_TARGET_DURATION: String = "#EXT-X-TARGETDURATION"
    private const val TAG_DISCONTINUITY: String = "#EXT-X-DISCONTINUITY"
    private const val TAG_DISCONTINUITY_SEQUENCE: String = "#EXT-X-DISCONTINUITY-SEQUENCE"
    private const val TAG_PROGRAM_DATE_TIME: String = "#EXT-X-PROGRAM-DATE-TIME"
    private const val TAG_INIT_SEGMENT: String = "#EXT-X-MAP"
    private const val TAG_INDEPENDENT_SEGMENTS: String = "#EXT-X-INDEPENDENT-SEGMENTS"
    private const val TAG_MEDIA_DURATION: String = "#EXTINF"
    private const val TAG_MEDIA_SEQUENCE: String = "#EXT-X-MEDIA-SEQUENCE"
    private const val TAG_START: String = "#EXT-X-START"
    private const val TAG_ENDLIST: String = "#EXT-X-ENDLIST"
    private const val TAG_KEY: String = "#EXT-X-KEY"
    private const val TAG_SESSION_KEY: String = "#EXT-X-SESSION-KEY"
    private const val TAG_BYTERANGE: String = "#EXT-X-BYTERANGE"
    private const val TAG_GAP: String = "#EXT-X-GAP"
    private const val TAG_SKIP: String = "#EXT-X-SKIP"
    private const val TAG_PRELOAD_HINT: String = "#EXT-X-PRELOAD-HINT"
    private const val TAG_RENDITION_REPORT: String = "#EXT-X-RENDITION-REPORT"
    private const val TAG_DATERANGE: String = "#EXT-X-DATERANGE"
    private const val TYPE_AUDIO: String = "AUDIO"
    private const val TYPE_VIDEO: String = "VIDEO"
    private const val TYPE_SUBTITLES: String = "SUBTITLES"
    private const val TYPE_CLOSED_CAPTIONS: String = "CLOSED-CAPTIONS"
    private const val TYPE_PART: String = "PART"
    private const val TYPE_MAP: String = "MAP"
    private const val METHOD_NONE: String = "NONE"
    private const val METHOD_AES_128: String = "AES-128"
    private const val METHOD_SAMPLE_AES: String = "SAMPLE-AES"

    // Replaced by METHOD_SAMPLE_AES_CTR. Keep for backward compatibility.
    private const val METHOD_SAMPLE_AES_CENC: String = "SAMPLE-AES-CENC"
    private const val METHOD_SAMPLE_AES_CTR: String = "SAMPLE-AES-CTR"
    private const val KEYFORMAT_PLAYREADY: String = "com.microsoft.playready"
    private const val KEYFORMAT_IDENTITY: String = "identity"
    private const val KEYFORMAT_WIDEVINE_PSSH_BINARY: String =
        "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"
    private const val KEYFORMAT_WIDEVINE_PSSH_JSON: String = "com.widevine"
    private const val BOOLEAN_TRUE: String = "YES"
    private const val BOOLEAN_FALSE: String = "NO"
    private const val ATTR_CLOSED_CAPTIONS_NONE: String = "CLOSED-CAPTIONS=NONE"
    private val REGEX_AVERAGE_BANDWIDTH: Regex = Regex("AVERAGE-BANDWIDTH=(\\d+)\\b")
    private val REGEX_VIDEO: Regex = Regex("VIDEO=\"(.+?)\"")
    private val REGEX_AUDIO: Regex = Regex("AUDIO=\"(.+?)\"")
    private val REGEX_SUBTITLES: Regex = Regex("SUBTITLES=\"(.+?)\"")
    private val REGEX_CLOSED_CAPTIONS: Regex = Regex("CLOSED-CAPTIONS=\"(.+?)\"")
    private val REGEX_BANDWIDTH: Regex = Regex("[^-]BANDWIDTH=(\\d+)\\b")
    private val REGEX_CHANNELS: Regex = Regex("CHANNELS=\"(.+?)\"")
    private val REGEX_VIDEO_RANGE: Regex = Regex("VIDEO-RANGE=(SDR|PQ|HLG)")
    private val REGEX_CODECS: Regex = Regex("CODECS=\"(.+?)\"")
    private val REGEX_SUPPLEMENTAL_CODECS: Regex = Regex("SUPPLEMENTAL-CODECS=\"(.+?)\"")
    private val REGEX_RESOLUTION: Regex = Regex("RESOLUTION=(\\d+x\\d+)")
    private val REGEX_FRAME_RATE: Regex = Regex("FRAME-RATE=([\\d\\.]+)\\b")
    private val REGEX_TARGET_DURATION: Regex = Regex("$TAG_TARGET_DURATION:(\\d+)\\b")
    private val REGEX_ATTR_DURATION: Regex = Regex("DURATION=([\\d\\.]+)\\b")
    private val REGEX_ATTR_DURATION_PREFIXED: Regex = Regex("[:,]DURATION=([\\d\\.]+)\\b")
    private val REGEX_PART_TARGET_DURATION: Regex = Regex("PART-TARGET=([\\d\\.]+)\\b")
    private val REGEX_VERSION: Regex = Regex("$TAG_VERSION:(\\d+)\\b")
    private val REGEX_PLAYLIST_TYPE: Regex = Regex("$TAG_PLAYLIST_TYPE:(.+)\\b")
    private val REGEX_CAN_SKIP_UNTIL: Regex = Regex("CAN-SKIP-UNTIL=([\\d\\.]+)\\b")
    private val REGEX_CAN_SKIP_DATE_RANGES: Regex = compileBooleanAttrPattern("CAN-SKIP-DATERANGES")
    private val REGEX_SKIPPED_SEGMENTS: Regex = Regex("SKIPPED-SEGMENTS=(\\d+)\\b")
    private val REGEX_HOLD_BACK: Regex = Regex("[:|,]HOLD-BACK=([\\d\\.]+)\\b")
    private val REGEX_PART_HOLD_BACK: Regex = Regex("PART-HOLD-BACK=([\\d\\.]+)\\b")
    private val REGEX_CAN_BLOCK_RELOAD: Regex = compileBooleanAttrPattern("CAN-BLOCK-RELOAD")
    private val REGEX_MEDIA_SEQUENCE: Regex = Regex("$TAG_MEDIA_SEQUENCE:(\\d+)\\b")
    private val REGEX_MEDIA_DURATION: Regex = Regex("$TAG_MEDIA_DURATION:([\\d\\.]+)\\b")
    private val REGEX_MEDIA_TITLE: Regex = Regex("$TAG_MEDIA_DURATION:[\\d\\.]+\\b,(.+)")
    private val REGEX_LAST_MSN: Regex = Regex("LAST-MSN" + "=(\\d+)\\b")
    private val REGEX_LAST_PART: Regex = Regex("LAST-PART" + "=(\\d+)\\b")
    private val REGEX_TIME_OFFSET: Regex = Regex("TIME-OFFSET=(-?[\\d\\.]+)\\b")
    private val REGEX_BYTERANGE: Regex = Regex("$TAG_BYTERANGE:(\\d+(?:@\\d+)?)\\b")
    private val REGEX_ATTR_BYTERANGE: Regex = Regex("BYTERANGE=\"(\\d+(?:@\\d+)?)\\b\"")
    private val REGEX_BYTERANGE_START: Regex = Regex("BYTERANGE-START=(\\d+)\\b")
    private val REGEX_BYTERANGE_LENGTH: Regex = Regex("BYTERANGE-LENGTH=(\\d+)\\b")
    private val REGEX_METHOD: Regex = Regex(
        ("METHOD=("
                + METHOD_NONE
                + "|"
                + METHOD_AES_128
                + "|"
                + METHOD_SAMPLE_AES
                + "|"
                + METHOD_SAMPLE_AES_CENC
                + "|"
                + METHOD_SAMPLE_AES_CTR
                + ")"
                + "\\s*(?:,|$)")
    )
    private val REGEX_KEYFORMAT: Regex = Regex("KEYFORMAT=\"(.+?)\"")
    private val REGEX_KEYFORMATVERSIONS: Regex = Regex("KEYFORMATVERSIONS=\"(.+?)\"")
    private val REGEX_URI: Regex = Regex("URI=\"(.+?)\"")
    private val REGEX_IV: Regex = Regex("IV=([^,.*]+)")
    private val REGEX_TYPE: Regex = Regex(
        ("TYPE=("
                + TYPE_AUDIO
                + "|"
                + TYPE_VIDEO
                + "|"
                + TYPE_SUBTITLES
                + "|"
                + TYPE_CLOSED_CAPTIONS
                + ")")
    )
    private val REGEX_PRELOAD_HINT_TYPE: Regex = Regex("TYPE=($TYPE_PART|$TYPE_MAP)")
    private val REGEX_LANGUAGE: Regex = Regex("LANGUAGE=\"(.+?)\"")
    private val REGEX_NAME: Regex = Regex("NAME=\"(.+?)\"")
    private val REGEX_GROUP_ID: Regex = Regex("GROUP-ID=\"(.+?)\"")
    private val REGEX_CHARACTERISTICS: Regex = Regex("CHARACTERISTICS=\"(.+?)\"")
    private val REGEX_INSTREAM_ID: Regex = Regex("INSTREAM-ID=\"((?:CC|SERVICE)\\d+)\"")
    private val REGEX_AUTOSELECT: Regex = compileBooleanAttrPattern("AUTOSELECT")
    private val REGEX_DEFAULT: Regex = compileBooleanAttrPattern("DEFAULT")
    private val REGEX_FORCED: Regex = compileBooleanAttrPattern("FORCED")
    private val REGEX_INDEPENDENT: Regex = compileBooleanAttrPattern("INDEPENDENT")
    private val REGEX_GAP: Regex = compileBooleanAttrPattern("GAP")
    private val REGEX_PRECISE: Regex = compileBooleanAttrPattern("PRECISE")
    private val REGEX_VALUE: Regex = Regex("VALUE=\"(.+?)\"")
    private val REGEX_IMPORT: Regex = Regex("IMPORT=\"(.+?)\"")
    private val REGEX_ID: Regex = Regex("[:,]ID=\"(.+?)\"")
    private val REGEX_CLASS: Regex = Regex("CLASS=\"(.+?)\"")
    private val REGEX_START_DATE: Regex = Regex("START-DATE=\"(.+?)\"")
    private val REGEX_CUE: Regex = Regex("CUE=\"(.+?)\"")
    private val REGEX_END_DATE: Regex = Regex("END-DATE=\"(.+?)\"")
    private val REGEX_PLANNED_DURATION: Regex = Regex("PLANNED-DURATION=([\\d.]+)\\b")
    private val REGEX_END_ON_NEXT: Regex = compileBooleanAttrPattern("END-ON-NEXT")
    private val REGEX_ASSET_URI: Regex = Regex("X-ASSET-URI=\"(.+?)\"")
    private val REGEX_ASSET_LIST_URI: Regex = Regex("X-ASSET-LIST=\"(.+?)\"")
    private val REGEX_RESUME_OFFSET: Regex = Regex("X-RESUME-OFFSET=(-?[\\d.]+)\\b")
    private val REGEX_PLAYOUT_LIMIT: Regex = Regex("X-PLAYOUT-LIMIT=([\\d.]+)\\b")
    private val REGEX_SNAP: Regex = Regex("X-SNAP=\"(.+?)\"")
    private val REGEX_RESTRICT: Regex = Regex("X-RESTRICT=\"(.+?)\"")
    private val REGEX_VARIABLE_REFERENCE: Regex = Regex("\\{\\$([a-zA-Z0-9\\-_]+)\\}")
    private val REGEX_CLIENT_DEFINED_ATTRIBUTE_PREFIX: Regex = Regex("\\b(X-[A-Z0-9-]+)=")

    private fun compileBooleanAttrPattern(attribute: String): Regex {
        return Regex("$attribute=($BOOLEAN_FALSE|$BOOLEAN_TRUE)")
    }

    @Throws(ParserException::class)
    private fun parseIntAttr(line: String, pattern: Regex): Int {
        return parseStringAttr(line, pattern, emptyMap()).toInt()
    }

    private fun parseOptionalIntAttr(line: String, pattern: Regex, defaultValue: Int): Int =
        pattern.find(line)?.groupValues?.get(1)?.toInt() ?: defaultValue

    data class SchemeData(
        /**
         * The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is universal (i.e.
         * applies to all schemes).
         */
        val uuid: UUID,
        /** The URL of the server to which license requests should be made. May be null if unknown. */
        val licenseServerUrl: String? = null,
        /** The mimeType of {@link #data}. */
        val mimeType: String,
        /** The initialization data. May be null for scheme support checks only. */
        val data: ByteArray
    )

    object Util {
        /**
         * Splits the string at the first occurrence of the delimiter `regex`. If the delimiter does
         * not match, returns an array with one element which is the input string. If the delimiter does
         * match, returns an array with the portion of the string before the delimiter and the rest of the
         * string.
         *
         * @param value The string.
         * @param regex A delimiting regular expression.
         * @return The string split by the first occurrence of the delimiter.
         */
        fun splitAtFirst(value: String, regex: String): Array<String> {
            return value.split(regex.toRegex(), limit = 2).toTypedArray()
        }

        fun split(value: String, regex: String): Array<String> {
            return value.split(regex.toRegex()).toTypedArray()
        }

        fun splitCodecs(codecs: String?): Array<String> {
            if (codecs.isNullOrEmpty()) {
                return arrayOf()
            }
            return split(codecs.trim { it <= ' ' }, "(\\s*,\\s*)")
        }

        fun getCodecsOfType(
            codecs: String?,
            /**@TrackType*/
            trackType: Int
        ): String? {
            val codecArray: Array<String> = splitCodecs(codecs)
            if (codecArray.isEmpty()) {
                return null
            }
            val builder = java.lang.StringBuilder()
            for (codec in codecArray) {
                if (trackType == MimeTypes.getTrackTypeOfCodec(codec)) {
                    if (builder.isNotEmpty()) {
                        builder.append(",")
                    }
                    builder.append(codec)
                }
            }
            return if (builder.isNotEmpty()) builder.toString() else null
        }

        /**
         * Returns a copy of `codecs` without the codecs whose track type matches `trackType`.
         *
         * @param codecs A codec sequence string, as defined in RFC 6381.
         * @param trackType The [track type][C.TrackType].
         * @return A copy of `codecs` without the codecs whose track type matches `trackType`.
         * If this ends up empty, or `codecs` is null, returns null.
         */
        fun getCodecsWithoutType(
            codecs: String?,
            /** @TrackType  */
            trackType: Int
        ): String? {
            val codecArray = splitCodecs(codecs)
            if (codecArray.isEmpty()) {
                return null
            }
            val builder = java.lang.StringBuilder()
            for (codec in codecArray) {
                if (trackType != MimeTypes.getTrackTypeOfCodec(codec)) {
                    if (builder.isNotEmpty()) {
                        builder.append(",")
                    }
                    builder.append(codec)
                }
            }
            return if (builder.isNotEmpty()) builder.toString() else null
        }
    }

    object UriUtil {
        fun resolveToUri(baseUri: String?, referenceUri: String?): URI {
            return URI.create(resolve(baseUri, referenceUri))
        }


        /** The length of arrays returned by [.getUriIndices].  */
        private
        const val INDEX_COUNT: Int = 4

        /**
         * An index into an array returned by [.getUriIndices].
         *
         *
         * The value at this position in the array is the index of the ':' after the scheme. Equals -1
         * if the URI is a relative reference (no scheme). The hier-part starts at (schemeColon + 1),
         * including when the URI has no scheme.
         */
        private
        const val SCHEME_COLON: Int = 0

        /**
         * An index into an array returned by [.getUriIndices].
         *
         *
         * The value at this position in the array is the index of the path part. Equals (schemeColon +
         * 1) if no authority part, (schemeColon + 3) if the authority part consists of just "//", and
         * (query) if no path part. The characters starting at this index can be "//" only if the
         * authority part is non-empty (in this case the double-slash means the first segment is empty).
         */
        private
        const val PATH: Int = 1

        /**
         * An index into an array returned by [.getUriIndices].
         *
         *
         * The value at this position in the array is the index of the query part, including the '?'
         * before the query. Equals fragment if no query part, and (fragment - 1) if the query part is a
         * single '?' with no data.
         */
        private
        const val QUERY: Int = 2

        /**
         * An index into an array returned by [.getUriIndices].
         *
         *
         * The value at this position in the array is the index of the fragment part, including the '#'
         * before the fragment. Equal to the length of the URI if no fragment part, and (length - 1) if
         * the fragment part is a single '#' with no data.
         */
        private
        const val FRAGMENT: Int = 3

        /**
         * Performs relative resolution of a `referenceUri` with respect to a `baseUri`.
         *
         *
         * The resolution is performed as specified by RFC-3986.
         *
         * @param baseUri The base URI.
         * @param referenceUri The reference URI to resolve.
         */
        private fun resolve(baseUri: String?, referenceUri: String?): String {
            var baseUri = baseUri
            var referenceUri = referenceUri
            val uri = StringBuilder()

            // Map null onto empty string, to make the following logic simpler.
            baseUri = baseUri ?: ""
            referenceUri = referenceUri ?: ""

            val refIndices: IntArray = getUriIndices(referenceUri)
            if (refIndices[SCHEME_COLON] != -1) {
                // The reference is absolute. The target Uri is the reference.
                uri.append(referenceUri)
                removeDotSegments(uri, refIndices[PATH], refIndices[QUERY])
                return uri.toString()
            }

            val baseIndices: IntArray = getUriIndices(baseUri)
            if (refIndices[FRAGMENT] == 0) {
                // The reference is empty or contains just the fragment part, then the target Uri is the
                // concatenation of the base Uri without its fragment, and the reference.
                return uri.append(baseUri, 0, baseIndices[FRAGMENT]).append(referenceUri).toString()
            }

            if (refIndices[QUERY] == 0) {
                // The reference starts with the query part. The target is the base up to (but excluding) the
                // query, plus the reference.
                return uri.append(baseUri, 0, baseIndices[QUERY]).append(referenceUri).toString()
            }

            if (refIndices[PATH] != 0) {
                // The reference has authority. The target is the base scheme plus the reference.
                val baseLimit = baseIndices[SCHEME_COLON] + 1
                uri.append(baseUri, 0, baseLimit).append(referenceUri)
                return removeDotSegments(
                    uri,
                    baseLimit + refIndices[PATH],
                    baseLimit + refIndices[QUERY]
                )
            }

            if (referenceUri[refIndices[PATH]] == '/') {
                // The reference path is rooted. The target is the base scheme and authority (if any), plus
                // the reference.
                uri.append(baseUri, 0, baseIndices[PATH]).append(referenceUri)
                return removeDotSegments(
                    uri,
                    baseIndices[PATH],
                    baseIndices[PATH] + refIndices[QUERY]
                )
            }

            // The target Uri is the concatenation of the base Uri up to (but excluding) the last segment,
            // and the reference. This can be split into 2 cases:
            if (baseIndices[SCHEME_COLON] + 2 < baseIndices[PATH]
                && baseIndices[PATH] == baseIndices[QUERY]
            ) {
                // Case 1: The base hier-part is just the authority, with an empty path. An additional '/' is
                // needed after the authority, before appending the reference.
                uri.append(baseUri, 0, baseIndices[PATH]).append('/').append(referenceUri)
                return removeDotSegments(
                    uri,
                    baseIndices[PATH],
                    baseIndices[PATH] + refIndices[QUERY] + 1
                )
            } else {
                // Case 2: Otherwise, find the last '/' in the base hier-part and append the reference after
                // it. If base hier-part has no '/', it could only mean that it is completely empty or
                // contains only one segment, in which case the whole hier-part is excluded and the reference
                // is appended right after the base scheme colon without an added '/'.
                val lastSlashIndex = baseUri.lastIndexOf('/', baseIndices[QUERY] - 1)
                val baseLimit = if (lastSlashIndex == -1) baseIndices[PATH] else lastSlashIndex + 1
                uri.append(baseUri, 0, baseLimit).append(referenceUri)
                return removeDotSegments(uri, baseIndices[PATH], baseLimit + refIndices[QUERY])
            }
        }

        /**
         * Removes dot segments from the path of a URI.
         *
         * @param uri A [StringBuilder] containing the URI.
         * @param offset The index of the start of the path in `uri`.
         * @param limit The limit (exclusive) of the path in `uri`.
         */
        private fun removeDotSegments(
            uri: java.lang.StringBuilder,
            offset: Int,
            limit: Int
        ): String {
            var offset = offset
            var limit = limit
            if (offset >= limit) {
                // Nothing to do.
                return uri.toString()
            }
            if (uri[offset] == '/') {
                // If the path starts with a /, always retain it.
                offset++
            }
            // The first character of the current path segment.
            var segmentStart = offset
            var i = offset
            while (i <= limit) {
                val nextSegmentStart = if (i == limit) {
                    i
                } else if (uri[i] == '/') {
                    i + 1
                } else {
                    i++
                    continue
                }
                // We've encountered the end of a segment or the end of the path. If the final segment was
                // "." or "..", remove the appropriate segments of the path.
                if (i == segmentStart + 1 && uri[segmentStart] == '.') {
                    // Given "abc/def/./ghi", remove "./" to get "abc/def/ghi".
                    uri.delete(segmentStart, nextSegmentStart)
                    limit -= nextSegmentStart - segmentStart
                    i = segmentStart
                } else if (i == segmentStart + 2 && uri[segmentStart] == '.' && uri[segmentStart + 1] == '.') {
                    // Given "abc/def/../ghi", remove "def/../" to get "abc/ghi".
                    val prevSegmentStart = uri.lastIndexOf("/", segmentStart - 2) + 1
                    val removeFrom = if (prevSegmentStart > offset) prevSegmentStart else offset
                    uri.delete(removeFrom, nextSegmentStart)
                    limit -= nextSegmentStart - removeFrom
                    segmentStart = prevSegmentStart
                    i = prevSegmentStart
                } else {
                    i++
                    segmentStart = i
                }
            }
            return uri.toString()
        }

        /**
         * Calculates indices of the constituent components of a URI.
         *
         * @param uriString The URI as a string.
         * @return The corresponding indices.
         */
        private fun getUriIndices(uriString: String?): IntArray {
            val indices = IntArray(INDEX_COUNT)
            if (uriString.isNullOrEmpty()) {
                indices[SCHEME_COLON] = -1
                return indices
            }

            // Determine outer structure from right to left.
            // Uri = scheme ":" hier-part [ "?" query ] [ "#" fragment ]
            val length = uriString.length
            var fragmentIndex = uriString.indexOf('#')
            if (fragmentIndex == -1) {
                fragmentIndex = length
            }
            var queryIndex = uriString.indexOf('?')
            if (queryIndex == -1 || queryIndex > fragmentIndex) {
                // '#' before '?': '?' is within the fragment.
                queryIndex = fragmentIndex
            }
            // Slashes are allowed only in hier-part so any colon after the first slash is part of the
            // hier-part, not the scheme colon separator.
            var schemeIndexLimit = uriString.indexOf('/')
            if (schemeIndexLimit == -1 || schemeIndexLimit > queryIndex) {
                schemeIndexLimit = queryIndex
            }
            var schemeIndex = uriString.indexOf(':')
            if (schemeIndex > schemeIndexLimit) {
                // '/' before ':'
                schemeIndex = -1
            }

            // Determine hier-part structure: hier-part = "//" authority path / path
            // This block can also cope with schemeIndex == -1.
            val hasAuthority =
                schemeIndex + 2 < queryIndex && uriString[schemeIndex + 1] == '/' && uriString[schemeIndex + 2] == '/'
            var pathIndex: Int
            if (hasAuthority) {
                pathIndex = uriString.indexOf('/', schemeIndex + 3) // find first '/' after "://"
                if (pathIndex == -1 || pathIndex > queryIndex) {
                    pathIndex = queryIndex
                }
            } else {
                pathIndex = schemeIndex + 1
            }

            indices[SCHEME_COLON] = schemeIndex
            indices[PATH] = pathIndex
            indices[QUERY] = queryIndex
            indices[FRAGMENT] = fragmentIndex
            return indices
        }
    }

    object C {
        /**
         * UUID for the ClearKey DRM scheme.
         *
         *
         * ClearKey is supported on Android devices running Android 5.0 (API Level 21) and up.
         */
        val CLEARKEY_UUID = UUID(-0x1d8e62a7567a4c37L, 0x781AB030AF78D30EL)

        /**
         * UUID for the Widevine DRM scheme.
         *
         *
         * Widevine is supported on Android devices running Android 4.3 (API Level 18) and up.
         */
        val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

        /**
         * UUID for the PlayReady DRM scheme.
         *
         *
         * PlayReady is supported on all AndroidTV devices. Note that most other Android devices do not
         * provide PlayReady support.
         */
        val PLAYREADY_UUID = UUID(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)


        /** "cenc" scheme type name as defined in ISO/IEC 23001-7:2016.  */
        const val CENC_TYPE_cenc: String = "cenc"

        /** "cbc1" scheme type name as defined in ISO/IEC 23001-7:2016.  */
        const val CENC_TYPE_cbc1: String = "cbc1"

        /** "cens" scheme type name as defined in ISO/IEC 23001-7:2016.  */
        const val CENC_TYPE_cens: String = "cens"

        /** "cbcs" scheme type name as defined in ISO/IEC 23001-7:2016.  */
        const val CENC_TYPE_cbcs: String = "cbcs"


        // LINT.IfChange(role_flags)
        /** Indicates a main track.  */
        const val ROLE_FLAG_MAIN: Int = 1

        /**
         * Indicates an alternate track. For example a video track recorded from an different view point
         * than the main track(s).
         */
        const val ROLE_FLAG_ALTERNATE: Int = 1 shl 1

        /**
         * Indicates a supplementary track, meaning the track has lower importance than the main track(s).
         * For example a video track that provides a visual accompaniment to a main audio track.
         */
        const val ROLE_FLAG_SUPPLEMENTARY: Int = 1 shl 2

        /** Indicates the track contains commentary, for example from the director.  */
        const val ROLE_FLAG_COMMENTARY: Int = 1 shl 3

        /**
         * Indicates the track is in a different language from the original, for example dubbed audio or
         * translated captions.
         */
        const val ROLE_FLAG_DUB: Int = 1 shl 4

        /** Indicates the track contains information about a current emergency.  */
        const val ROLE_FLAG_EMERGENCY: Int = 1 shl 5

        /**
         * Indicates the track contains captions. This flag may be set on video tracks to indicate the
         * presence of burned in captions.
         */
        const val ROLE_FLAG_CAPTION: Int = 1 shl 6

        /**
         * Indicates the track contains subtitles. This flag may be set on video tracks to indicate the
         * presence of burned in subtitles.
         */
        const val ROLE_FLAG_SUBTITLE: Int = 1 shl 7

        /** Indicates the track contains a visual sign-language interpretation of an audio track.  */
        const val ROLE_FLAG_SIGN: Int = 1 shl 8

        /** Indicates the track contains an audio or textual description of a video track.  */
        const val ROLE_FLAG_DESCRIBES_VIDEO: Int = 1 shl 9

        /** Indicates the track contains a textual description of music and sound.  */
        const val ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND: Int = 1 shl 10

        /** Indicates the track is designed for improved intelligibility of dialogue.  */
        const val ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY: Int = 1 shl 11

        /** Indicates the track contains a transcription of spoken dialog.  */
        const val ROLE_FLAG_TRANSCRIBES_DIALOG: Int = 1 shl 12

        /** Indicates the track contains a text that has been edited for ease of reading.  */
        const val ROLE_FLAG_EASY_TO_READ: Int = 1 shl 13

        /** Indicates the track is intended for trick play.  */
        const val ROLE_FLAG_TRICK_PLAY: Int = 1 shl 14


        /** A type constant for a fake or empty track.  */
        const val TRACK_TYPE_NONE: Int = -2

        /** A type constant for tracks of unknown type.  */
        const val TRACK_TYPE_UNKNOWN: Int = -1

        /** A type constant for tracks of some default type, where the type itself is unknown.  */
        const val TRACK_TYPE_DEFAULT: Int = 0

        /** A type constant for audio tracks.  */
        const val TRACK_TYPE_AUDIO: Int = 1

        /** A type constant for video tracks.  */
        const val TRACK_TYPE_VIDEO: Int = 2

        /** A type constant for text tracks.  */
        const val TRACK_TYPE_TEXT: Int = 3

        /** A type constant for image tracks.  */
        const val TRACK_TYPE_IMAGE: Int = 4

        /** A type constant for metadata tracks.  */
        const val TRACK_TYPE_METADATA: Int = 5

        /** A type constant for camera motion tracks.  */
        const val TRACK_TYPE_CAMERA_MOTION: Int = 6


        // LINT.IfChange(selection_flags)
        /** Indicates that the track should be selected if user preferences do not state otherwise.  */
        const val SELECTION_FLAG_DEFAULT: Int = 1

        /**
         * Indicates that the track should be selected if its language matches the language of the
         * selected audio track and user preferences do not state otherwise. Only applies to text tracks.
         *
         *
         * Tracks with this flag generally provide translation for elements that don't match the
         * declared language of the selected audio track (e.g. speech in an alien language). See [Netflix's summary](https://partnerhelp.netflixstudios.com/hc/en-us/articles/217558918)
         * for more info.
         */
        const val SELECTION_FLAG_FORCED: Int = 1 shl 1 // 2

        /**
         * Indicates that the player may choose to play the track in absence of an explicit user
         * preference.
         */
        const val SELECTION_FLAG_AUTOSELECT: Int = 1 shl 2 // 4
    }


    object MimeTypes {
        const val BASE_TYPE_VIDEO: String = "video"
        const val BASE_TYPE_AUDIO: String = "audio"
        const val BASE_TYPE_TEXT: String = "text"
        const val BASE_TYPE_IMAGE: String = "image"
        const val BASE_TYPE_APPLICATION: String = "application"

        // video/ MIME types
        const val VIDEO_MP4: String = "$BASE_TYPE_VIDEO/mp4"
        const val VIDEO_MATROSKA: String = "$BASE_TYPE_VIDEO/x-matroska"
        const val VIDEO_WEBM: String = "$BASE_TYPE_VIDEO/webm"
        const val VIDEO_H263: String = "$BASE_TYPE_VIDEO/3gpp"
        const val VIDEO_H264: String = "$BASE_TYPE_VIDEO/avc"
        const val VIDEO_APV: String = "$BASE_TYPE_VIDEO/apv"
        const val VIDEO_H265: String = "$BASE_TYPE_VIDEO/hevc"
        const val VIDEO_VP8: String = "$BASE_TYPE_VIDEO/x-vnd.on2.vp8"
        const val VIDEO_VP9: String = "$BASE_TYPE_VIDEO/x-vnd.on2.vp9"
        const val VIDEO_AV1: String = "$BASE_TYPE_VIDEO/av01"
        const val VIDEO_MP2T: String = "$BASE_TYPE_VIDEO/mp2t"
        const val VIDEO_MP4V: String = "$BASE_TYPE_VIDEO/mp4v-es"
        const val VIDEO_MPEG: String = "$BASE_TYPE_VIDEO/mpeg"
        const val VIDEO_PS: String = "$BASE_TYPE_VIDEO/mp2p"
        const val VIDEO_MPEG2: String = "$BASE_TYPE_VIDEO/mpeg2"
        const val VIDEO_VC1: String = "$BASE_TYPE_VIDEO/wvc1"
        const val VIDEO_DIVX: String = "$BASE_TYPE_VIDEO/divx"
        const val VIDEO_FLV: String = "$BASE_TYPE_VIDEO/x-flv"
        const val VIDEO_DOLBY_VISION: String = "$BASE_TYPE_VIDEO/dolby-vision"
        const val VIDEO_OGG: String = "$BASE_TYPE_VIDEO/ogg"
        const val VIDEO_AVI: String = "$BASE_TYPE_VIDEO/x-msvideo"
        const val VIDEO_MJPEG: String = "$BASE_TYPE_VIDEO/mjpeg"
        const val VIDEO_MP42: String = "$BASE_TYPE_VIDEO/mp42"
        const val VIDEO_MP43: String = "$BASE_TYPE_VIDEO/mp43"
        const val VIDEO_MV_HEVC: String = "$BASE_TYPE_VIDEO/mv-hevc"
        const val VIDEO_RAW: String = "$BASE_TYPE_VIDEO/raw"
        const val VIDEO_UNKNOWN: String = "$BASE_TYPE_VIDEO/x-unknown"


        // audio/ MIME types
        const val AUDIO_MP4: String = "$BASE_TYPE_AUDIO/mp4"
        const val AUDIO_AAC: String = "$BASE_TYPE_AUDIO/mp4a-latm"
        const val AUDIO_MATROSKA: String = "$BASE_TYPE_AUDIO/x-matroska"
        const val AUDIO_WEBM: String = "$BASE_TYPE_AUDIO/webm"
        const val AUDIO_MPEG: String = "$BASE_TYPE_AUDIO/mpeg"
        const val AUDIO_MPEG_L1: String = "$BASE_TYPE_AUDIO/mpeg-L1"
        const val AUDIO_MPEG_L2: String = "$BASE_TYPE_AUDIO/mpeg-L2"
        const val AUDIO_MPEGH_MHA1: String = "$BASE_TYPE_AUDIO/mha1"
        const val AUDIO_MPEGH_MHM1: String = "$BASE_TYPE_AUDIO/mhm1"
        const val AUDIO_RAW: String = "$BASE_TYPE_AUDIO/raw"
        const val AUDIO_ALAW: String = "$BASE_TYPE_AUDIO/g711-alaw"
        const val AUDIO_MLAW: String = "$BASE_TYPE_AUDIO/g711-mlaw"
        const val AUDIO_AC3: String = "$BASE_TYPE_AUDIO/ac3"
        const val AUDIO_E_AC3: String = "$BASE_TYPE_AUDIO/eac3"
        const val AUDIO_E_AC3_JOC: String = "$BASE_TYPE_AUDIO/eac3-joc"
        const val AUDIO_AC4: String = "$BASE_TYPE_AUDIO/ac4"
        const val AUDIO_TRUEHD: String = "$BASE_TYPE_AUDIO/true-hd"
        const val AUDIO_DTS: String = "$BASE_TYPE_AUDIO/vnd.dts"
        const val AUDIO_DTS_HD: String = "$BASE_TYPE_AUDIO/vnd.dts.hd"
        const val AUDIO_DTS_EXPRESS: String = "$BASE_TYPE_AUDIO/vnd.dts.hd;profile=lbr"
        const val AUDIO_DTS_X: String = "$BASE_TYPE_AUDIO/vnd.dts.uhd;profile=p2"
        const val AUDIO_VORBIS: String = "$BASE_TYPE_AUDIO/vorbis"
        const val AUDIO_OPUS: String = "$BASE_TYPE_AUDIO/opus"
        const val AUDIO_AMR: String = "$BASE_TYPE_AUDIO/amr"
        const val AUDIO_AMR_NB: String = "$BASE_TYPE_AUDIO/3gpp"
        const val AUDIO_AMR_WB: String = "$BASE_TYPE_AUDIO/amr-wb"
        const val AUDIO_FLAC: String = "$BASE_TYPE_AUDIO/flac"
        const val AUDIO_ALAC: String = "$BASE_TYPE_AUDIO/alac"
        const val AUDIO_MSGSM: String = "$BASE_TYPE_AUDIO/gsm"
        const val AUDIO_OGG: String = "$BASE_TYPE_AUDIO/ogg"
        const val AUDIO_WAV: String = "$BASE_TYPE_AUDIO/wav"
        const val AUDIO_MIDI: String = "$BASE_TYPE_AUDIO/midi"
        const val AUDIO_IAMF: String = "$BASE_TYPE_AUDIO/iamf"

        const val AUDIO_EXOPLAYER_MIDI: String = "$BASE_TYPE_AUDIO/x-exoplayer-midi"

        const val AUDIO_UNKNOWN: String = "$BASE_TYPE_AUDIO/x-unknown"


        // text/ MIME types
        const val TEXT_VTT: String = "$BASE_TYPE_TEXT/vtt"
        const val TEXT_SSA: String = "$BASE_TYPE_TEXT/x-ssa"
        const val TEXT_UNKNOWN: String = "$BASE_TYPE_TEXT/x-unknown"


        // application/ MIME types
        const val APPLICATION_MP4: String = "$BASE_TYPE_APPLICATION/mp4"
        const val APPLICATION_WEBM: String = "$BASE_TYPE_APPLICATION/webm"

        const val APPLICATION_MATROSKA: String = "$BASE_TYPE_APPLICATION/x-matroska"

        const val APPLICATION_MPD: String = "$BASE_TYPE_APPLICATION/dash+xml"
        const val APPLICATION_M3U8: String = "$BASE_TYPE_APPLICATION/x-mpegURL"
        const val APPLICATION_SS: String = "$BASE_TYPE_APPLICATION/vnd.ms-sstr+xml"
        const val APPLICATION_ID3: String = "$BASE_TYPE_APPLICATION/id3"
        const val APPLICATION_CEA608: String = "$BASE_TYPE_APPLICATION/cea-608"
        const val APPLICATION_CEA708: String = "$BASE_TYPE_APPLICATION/cea-708"
        const val APPLICATION_SUBRIP: String = "$BASE_TYPE_APPLICATION/x-subrip"
        const val APPLICATION_TTML: String = "$BASE_TYPE_APPLICATION/ttml+xml"
        const val APPLICATION_TX3G: String = "$BASE_TYPE_APPLICATION/x-quicktime-tx3g"
        const val APPLICATION_MP4VTT: String = "$BASE_TYPE_APPLICATION/x-mp4-vtt"
        const val APPLICATION_MP4CEA608: String = "$BASE_TYPE_APPLICATION/x-mp4-cea-608"


        @Deprecated(
            """RawCC is a Google-internal subtitle format that isn't supported by this version of
        Media3. There is no replacement for this value."""
        )
        const val APPLICATION_RAWCC: String = "$BASE_TYPE_APPLICATION/x-rawcc"

        const val APPLICATION_VOBSUB: String = "$BASE_TYPE_APPLICATION/vobsub"
        const val APPLICATION_PGS: String = "$BASE_TYPE_APPLICATION/pgs"
        const val APPLICATION_SCTE35: String = "$BASE_TYPE_APPLICATION/x-scte35"
        const val APPLICATION_SDP: String = "$BASE_TYPE_APPLICATION/sdp"

        const val APPLICATION_CAMERA_MOTION: String = "$BASE_TYPE_APPLICATION/x-camera-motion"

        const val APPLICATION_DEPTH_METADATA: String = "$BASE_TYPE_APPLICATION/x-depth-metadata"

        const val APPLICATION_EMSG: String = "$BASE_TYPE_APPLICATION/x-emsg"
        const val APPLICATION_DVBSUBS: String = "$BASE_TYPE_APPLICATION/dvbsubs"
        const val APPLICATION_EXIF: String = "$BASE_TYPE_APPLICATION/x-exif"
        const val APPLICATION_ICY: String = "$BASE_TYPE_APPLICATION/x-icy"
        const val APPLICATION_AIT: String = "$BASE_TYPE_APPLICATION/vnd.dvb.ait"
        const val APPLICATION_RTSP: String = "$BASE_TYPE_APPLICATION/x-rtsp"

        const val APPLICATION_MEDIA3_CUES: String = "$BASE_TYPE_APPLICATION/x-media3-cues"

        /** MIME type for an image URI loaded from an external image management framework.  */
        const val APPLICATION_EXTERNALLY_LOADED_IMAGE: String = "$BASE_TYPE_APPLICATION/x-image-uri"


        // image/ MIME types
        const val IMAGE_JPEG: String = "$BASE_TYPE_IMAGE/jpeg"
        const val IMAGE_JPEG_R: String = "$BASE_TYPE_IMAGE/jpeg_r"
        const val IMAGE_PNG: String = "$BASE_TYPE_IMAGE/png"
        const val IMAGE_HEIF: String = "$BASE_TYPE_IMAGE/heif"
        const val IMAGE_HEIC: String = "$BASE_TYPE_IMAGE/heic"
        const val IMAGE_AVIF: String = "$BASE_TYPE_IMAGE/avif"
        const val IMAGE_BMP: String = "$BASE_TYPE_IMAGE/bmp"
        const val IMAGE_WEBP: String = "$BASE_TYPE_IMAGE/webp"
        const val IMAGE_RAW: String = "$BASE_TYPE_IMAGE/raw"

        /**
         * A non-standard codec string for E-AC3-JOC. Use of this constant allows for disambiguation
         * between regular E-AC3 ("ec-3") and E-AC3-JOC ("ec+3") streams from the codec string alone. The
         * standard is to use "ec-3" for both, as per the [MP4RA
 * registered codec types](https://mp4ra.org/#/codecs).
         */
        const val CODEC_E_AC3_JOC: String = "ec+3"

        data class Mp4aObjectType(val objectTypeIndication: Int, val audioObjectTypeIndication: Int)

        private
        val MP4A_RFC_6381_CODEC_PATTERN: Regex =
            Regex("^mp4a\\.([a-zA-Z0-9]{2})(?:\\.([0-9]{1,2}))?$")

        /**
         * Returns whether the given `codecs` and `supplementalCodecs` correspond to a valid
         * Dolby Vision codec.
         *
         * @param codecs An RFC 6381 codecs string for the base codec. may be null.
         * @param supplementalCodecs An optional RFC 6381 codecs string for supplemental codecs.
         * @return Whether the given `codecs` and `supplementalCodecs` correspond to a valid
         * Dolby Vision codec.
         */
        fun isDolbyVisionCodec(
            codecs: String?, supplementalCodecs: String?
        ): Boolean {
            if (codecs == null) {
                return false
            }
            if (codecs.startsWith("dvhe") || codecs.startsWith("dvh1")) {
                // profile 5
                return true
            }
            if (supplementalCodecs == null) {
                return false
            }
            // profiles 8, 9 and 10
            return (supplementalCodecs.startsWith("dvhe") && codecs.startsWith("hev1"))
                    || (supplementalCodecs.startsWith("dvh1") && codecs.startsWith("hvc1"))
                    || (supplementalCodecs.startsWith("dvav") && codecs.startsWith("avc3"))
                    || (supplementalCodecs.startsWith("dva1") && codecs.startsWith("avc1"))
                    || (supplementalCodecs.startsWith("dav1") && codecs.startsWith("av01"))
        }

        /**
         * Returns the [Mp4aObjectType] of an RFC 6381 MP4 audio codec string.
         *
         *
         * Per https://mp4ra.org/#/object_types and https://tools.ietf.org/html/rfc6381#section-3.3, an
         * MP4 codec string has the form:
         *
         * <pre>
         * ~~~~~~~~~~~~~~ Object Type Indication (OTI) byte in hex
         * mp4a.[a-zA-Z0-9]{2}(.[0-9]{1,2})?
         * ~~~~~~~~~~ audio OTI, decimal. Only for certain OTI.
        </pre> *
         *
         * For example, mp4a.40.2 has an OTI of 0x40 and an audio OTI of 2.
         *
         * @param codec An RFC 6381 MP4 audio codec string.
         * @return The [Mp4aObjectType], or `null` if the input was invalid.
         */
        fun getObjectTypeFromMp4aRFC6381CodecString(codec: String?): Mp4aObjectType? {
            val groups = MP4A_RFC_6381_CODEC_PATTERN.find(codec ?: return null)?.groupValues
            if (groups.isNullOrEmpty()) {
                return null
            }
            val objectTypeIndicationHex: String = groups[1]
            val audioObjectTypeIndicationDec: String? = groups.getOrNull(2)
            val objectTypeIndication: Int
            var audioObjectTypeIndication = 0
            try {
                objectTypeIndication = objectTypeIndicationHex.toInt(16)
                if (audioObjectTypeIndicationDec != null) {
                    audioObjectTypeIndication = audioObjectTypeIndicationDec.toInt()
                }
            } catch (e: NumberFormatException) {
                return null
            }
            return Mp4aObjectType(objectTypeIndication, audioObjectTypeIndication)
        }

        fun getMimeTypeFromMp4ObjectType(objectType: Int): String? {
            return when (objectType) {
                0x20 -> VIDEO_MP4V
                0x21 -> VIDEO_H264
                0x23 -> VIDEO_H265
                0x60, 0x61, 0x62, 0x63, 0x64, 0x65 -> VIDEO_MPEG2
                0x6A -> VIDEO_MPEG
                0x69, 0x6B -> AUDIO_MPEG
                0x6C -> MimeTypes.IMAGE_JPEG
                0xA3 -> VIDEO_VC1
                0xB1 -> VIDEO_VP9
                0x40, 0x66, 0x67, 0x68 -> AUDIO_AAC
                0xA5 -> AUDIO_AC3
                0xA6 -> AUDIO_E_AC3
                0xA9, 0xAC -> AUDIO_DTS
                0xAA, 0xAB -> AUDIO_DTS_HD
                0xAD -> AUDIO_OPUS
                0xAE -> AUDIO_AC4
                0xDD -> AUDIO_VORBIS
                else -> null
            }
        }

        fun getMediaMimeType(codecOrNull: String?): String? {
            var codec = codecOrNull ?: return null
            codec = codec.trim { it <= ' ' }.lowercase()
            if (codec.startsWith("avc1") || codec.startsWith("avc3")) {
                return MimeTypes.VIDEO_H264
            } else if (codec.startsWith("hev1") || codec.startsWith("hvc1")) {
                return MimeTypes.VIDEO_H265
            } else if (codec.startsWith("dvav")
                || codec.startsWith("dva1")
                || codec.startsWith("dvhe")
                || codec.startsWith("dvh1")
            ) {
                return MimeTypes.VIDEO_DOLBY_VISION
            } else if (codec.startsWith("av01")) {
                return MimeTypes.VIDEO_AV1
            } else if (codec.startsWith("vp9") || codec.startsWith("vp09")) {
                return MimeTypes.VIDEO_VP9
            } else if (codec.startsWith("vp8") || codec.startsWith("vp08")) {
                return MimeTypes.VIDEO_VP8
            } else if (codec.startsWith("mp4a")) {
                var mimeType: String? = null
                if (codec.startsWith("mp4a.")) {
                    val objectType: Mp4aObjectType? = getObjectTypeFromMp4aRFC6381CodecString(codec)
                    if (objectType != null) {
                        mimeType = getMimeTypeFromMp4ObjectType(objectType.objectTypeIndication)
                    }
                }
                return mimeType ?: MimeTypes.AUDIO_AAC
            } else if (codec.startsWith("mha1")) {
                return MimeTypes.AUDIO_MPEGH_MHA1
            } else if (codec.startsWith("mhm1")) {
                return MimeTypes.AUDIO_MPEGH_MHM1
            } else if (codec.startsWith("ac-3") || codec.startsWith("dac3")) {
                return MimeTypes.AUDIO_AC3
            } else if (codec.startsWith("ec-3") || codec.startsWith("dec3")) {
                return AUDIO_E_AC3
            } else if (codec.startsWith(CODEC_E_AC3_JOC)) {
                return AUDIO_E_AC3_JOC
            } else if (codec.startsWith("ac-4") || codec.startsWith("dac4")) {
                return MimeTypes.AUDIO_AC4
            } else if (codec.startsWith("dtsc")) {
                return MimeTypes.AUDIO_DTS
            } else if (codec.startsWith("dtse")) {
                return MimeTypes.AUDIO_DTS_EXPRESS
            } else if (codec.startsWith("dtsh") || codec.startsWith("dtsl")) {
                return MimeTypes.AUDIO_DTS_HD
            } else if (codec.startsWith("dtsx")) {
                return MimeTypes.AUDIO_DTS_X
            } else if (codec.startsWith("opus")) {
                return MimeTypes.AUDIO_OPUS
            } else if (codec.startsWith("vorbis")) {
                return MimeTypes.AUDIO_VORBIS
            } else if (codec.startsWith("flac")) {
                return MimeTypes.AUDIO_FLAC
            } else if (codec.startsWith("stpp")) {
                return MimeTypes.APPLICATION_TTML
            } else if (codec.startsWith("wvtt")) {
                return TEXT_VTT
            } else if (codec.contains("cea708")) {
                return APPLICATION_CEA708
            } else if (codec.contains("eia608") || codec.contains("cea608")) {
                return APPLICATION_CEA608
            } else {
                return null //getCustomMimeTypeForCodec(codec)
            }
        }

        /**
         * Returns the top-level type of `mimeType`, or null if `mimeType` is null or does not
         * contain a forward slash character (`'/'`).
         */
        private fun getTopLevelType(mimeType: String?): String? {
            if (mimeType == null) {
                return null
            }
            val indexOfSlash = mimeType.indexOf('/')
            if (indexOfSlash == -1) {
                return null
            }
            return mimeType.substring(0, indexOfSlash)
        }

        fun isAudio(mimeType: String?): Boolean = BASE_TYPE_AUDIO == getTopLevelType(mimeType)
        fun isVideo(mimeType: String?): Boolean = BASE_TYPE_VIDEO == getTopLevelType(mimeType)
        fun isText(mimeType: String?): Boolean = BASE_TYPE_TEXT == getTopLevelType(mimeType)
                || APPLICATION_MEDIA3_CUES == mimeType
                || APPLICATION_CEA608 == mimeType
                || APPLICATION_CEA708 == mimeType
                || APPLICATION_MP4CEA608 == mimeType
                || APPLICATION_SUBRIP == mimeType
                || APPLICATION_TTML == mimeType
                || APPLICATION_TX3G == mimeType
                || APPLICATION_MP4VTT == mimeType
                || APPLICATION_RAWCC == mimeType
                || APPLICATION_VOBSUB == mimeType
                || APPLICATION_PGS == mimeType
                || APPLICATION_DVBSUBS == mimeType;

        fun isImage(mimeType: String?): Boolean = BASE_TYPE_IMAGE == getTopLevelType(mimeType)
                || APPLICATION_EXTERNALLY_LOADED_IMAGE == mimeType;


        fun getTrackType(mimeType: String?): Int {
            return if (mimeType.isNullOrEmpty()) {
                C.TRACK_TYPE_UNKNOWN
            } else if (isAudio(mimeType)) {
                C.TRACK_TYPE_AUDIO
            } else if (isVideo(mimeType)) {
                C.TRACK_TYPE_VIDEO
            } else if (isText(mimeType)) {
                C.TRACK_TYPE_TEXT
            } else if (isImage(mimeType)) {
                C.TRACK_TYPE_IMAGE
            } else if (APPLICATION_ID3 == mimeType
                || APPLICATION_EMSG == mimeType
                || APPLICATION_SCTE35 == mimeType
                || APPLICATION_ICY == mimeType
                || APPLICATION_AIT == mimeType
            ) {
                C.TRACK_TYPE_METADATA
            } else if (APPLICATION_CAMERA_MOTION == mimeType) {
                C.TRACK_TYPE_CAMERA_MOTION
            } else {
                C.TRACK_TYPE_UNKNOWN
                //getTrackTypeForCustomMimeType(mimeType)
            }
        }

        /*@TrackType*/
        fun getTrackTypeOfCodec(codec: String?): Int {
            return getTrackType(getMediaMimeType(codec))
        }
    }

    object Mp4Box {
        /** Size of a full box header, in bytes.  */
        const val FULL_HEADER_SIZE: Int = 12
        const val TYPE_pssh: Int = 0x70737368
    }

    object PsshAtomUtil {
        fun buildPsshAtom(
            systemId: UUID, keyIds: Array<UUID>?, data: ByteArray?
        ): ByteArray {
            val dataLength = data?.size ?: 0
            var psshBoxLength: Int =
                Mp4Box.FULL_HEADER_SIZE + 16 /* SystemId */ + 4 /* DataSize */ + dataLength
            if (keyIds != null) {
                psshBoxLength += 4 /* KID_count */ + (keyIds.size * 16) /* KIDs */
            }
            val psshBox: ByteBuffer = ByteBuffer.allocate(psshBoxLength)
            psshBox.putInt(psshBoxLength)
            psshBox.putInt(Mp4Box.TYPE_pssh)
            psshBox.putInt(if (keyIds != null) 0x01000000 else 0 /* version=(buildV1Atom ? 1 : 0), flags=0 */)
            psshBox.putLong(systemId.mostSignificantBits)
            psshBox.putLong(systemId.leastSignificantBits)
            if (keyIds != null) {
                psshBox.putInt(keyIds.size)
                for (keyId in keyIds) {
                    psshBox.putLong(keyId.mostSignificantBits)
                    psshBox.putLong(keyId.leastSignificantBits)
                }
            }
            if (data != null && data.size != 0) {
                psshBox.putInt(data.size)
                psshBox.put(data)
            } else {
                psshBox.putInt(0)
            }
            return psshBox.array()
        }
    }

    data class ParserException(
        override val message: String?,
        override val cause: Throwable?,
        /**
         * Whether the parsing error was caused by a bitstream not following the expected format. May be
         * false when a parser encounters a legal condition which it does not support.
         */
        val contentIsMalformed: Boolean,
        /** The [data type][DataType] of the parsed bitstream.  */
        val dataType: Int
    ) : IOException(message, cause) {
        /** A data type constant for a manifest file.  */
        companion object {
            const val DATA_TYPE_MANIFEST: Int = 4
            fun createForMalformedManifest(
                message: String?,
                cause: Throwable?
            ): ParserException {
                return ParserException(
                    message,
                    cause, /* contentIsMalformed= */
                    true,
                    DATA_TYPE_MANIFEST
                )
            }
        }
    }

    data class DrmInitData(
        val schemeType: String,
        val schemeDatas: Array<SchemeData>,
    )

    @Throws(ParserException::class)
    private fun parseStringAttr(
        line: String, pattern: Regex, variableDefinitions: Map<String, String>
    ): String = parseOptionalStringAttr(line, pattern, variableDefinitions)
        ?: throw ParserException.createForMalformedManifest(
            "Couldn't match $pattern in $line",  /* cause= */null
        )


    /**@PolyNull*/
    private fun parseOptionalStringAttr(
        line: String,
        pattern: Regex,
        /**@PolyNull*/
        defaultValue: String?,
        variableDefinitions: Map<String, String>?
    ): String? {
        val value: String? = pattern.find(line)?.groupValues?.get(1) ?: defaultValue
        return if (variableDefinitions.isNullOrEmpty() || value == null)
            value
        else
            replaceVariableReferences(value, variableDefinitions)
    }

    private fun replaceVariableReferences(
        string: String, variableDefinitions: Map<String, String>?
    ): String =
        string.replace(REGEX_VARIABLE_REFERENCE) { matchResult ->
            variableDefinitions?.get(matchResult.groupValues[1]) ?: matchResult.value
        }


    private fun parseOptionalStringAttr(
        line: String, pattern: Regex, variableDefinitions: Map<String, String>?
    ): String? {
        return parseOptionalStringAttr(line, pattern, null, variableDefinitions)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Throws(ParserException::class)
    private fun parseDrmSchemeData(
        line: String, keyFormat: String, variableDefinitions: Map<String, String>
    ): SchemeData? {
        val keyFormatVersions =
            parseOptionalStringAttr(line, REGEX_KEYFORMATVERSIONS, "1", variableDefinitions)
        if (KEYFORMAT_WIDEVINE_PSSH_BINARY == keyFormat) {
            val uriString = parseStringAttr(line, REGEX_URI, variableDefinitions)
            return SchemeData(
                uuid = WIDEVINE_UUID,
                mimeType = MimeTypes.VIDEO_MP4,
                data = Base64.Default.decode(uriString.substring(uriString.indexOf(',')))
            )
        } else if (KEYFORMAT_WIDEVINE_PSSH_JSON == keyFormat) {
            return SchemeData(
                uuid = C.WIDEVINE_UUID,
                mimeType = "hls",
                data = line.toByteArray(charset = Charsets.UTF_8)
            )
        } else if (KEYFORMAT_PLAYREADY == keyFormat && "1" == keyFormatVersions) {
            val uriString = parseStringAttr(line, REGEX_URI, variableDefinitions)
            val data: ByteArray =
                Base64.Default.decode(uriString.substring(uriString.indexOf(',')))
            val psshData: ByteArray =
                PsshAtomUtil.buildPsshAtom(
                    systemId = C.PLAYREADY_UUID,
                    keyIds = null,
                    data = data
                )
            return SchemeData(
                uuid = C.PLAYREADY_UUID,
                mimeType = MimeTypes.VIDEO_MP4,
                data = psshData
            )
        }
        return null
    }

    private fun parseEncryptionScheme(method: String): String {
        return if (METHOD_SAMPLE_AES_CENC == method || METHOD_SAMPLE_AES_CTR == method)
            C.CENC_TYPE_cenc
        else
            C.CENC_TYPE_cbcs
    }


    //@SelectionFlags
    private fun parseSelectionFlags(line: String): Int {
        var flags = 0
        if (parseOptionalBooleanAttribute(line, REGEX_DEFAULT, false)) {
            flags = flags or C.SELECTION_FLAG_DEFAULT
        }
        if (parseOptionalBooleanAttribute(line, REGEX_FORCED, false)) {
            flags = flags or C.SELECTION_FLAG_FORCED
        }
        if (parseOptionalBooleanAttribute(line, REGEX_AUTOSELECT, false)) {
            flags = flags or C.SELECTION_FLAG_AUTOSELECT
        }
        return flags
    }

    //@RoleFlags
    private fun parseRoleFlags(
        line: String, variableDefinitions: Map<String, String>
    ): Int {
        val concatenatedCharacteristics =
            parseOptionalStringAttr(line, REGEX_CHARACTERISTICS, variableDefinitions)
        if (concatenatedCharacteristics.isNullOrEmpty()) {
            return 0
        }
        val characteristics = Util.split(concatenatedCharacteristics!!, ",")
        //@RoleFlags
        var roleFlags = 0
        if (characteristics.contains("public.accessibility.describes-video")) {
            roleFlags = roleFlags or C.ROLE_FLAG_DESCRIBES_VIDEO
        }
        if (characteristics.contains("public.accessibility.transcribes-spoken-dialog")) {
            roleFlags = roleFlags or C.ROLE_FLAG_TRANSCRIBES_DIALOG
        }
        if (characteristics.contains("public.accessibility.describes-music-and-sound")) {
            roleFlags = roleFlags or C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND
        }
        if (characteristics.contains("public.easy-to-read")) {
            roleFlags = roleFlags or C.ROLE_FLAG_EASY_TO_READ
        }
        return roleFlags
    }

    private fun parseOptionalBooleanAttribute(
        line: String, pattern: Regex, defaultValue: Boolean
    ): Boolean {
        val matcher = pattern.find(line)?.groupValues?.get(1)
        return if (matcher != null) {
            matcher == BOOLEAN_TRUE
        } else {
            defaultValue
        }
    }

    data class Variant(
        val url: URI,
        val format: Format,
        val videoGroupId: String?,
        val audioGroupId: String?,
        val subtitleGroupId: String?,
        val captionGroupId: String?,
    ) {
        /** This is unfortunately impossible to do 100%, given that audio detection is hard without TS inspection, 
         * however this is a generous safety abstraction */
        fun isPlayableStandalone(playlist: HlsMultivariantPlaylist): Boolean =
            mustContainAudio(playlist) && !isTrickPlay()

        /**
         * https://datatracker.ietf.org/doc/html/rfc8216#section-4.3.3.6
         * > The EXT-X-I-FRAMES-ONLY tag indicates that each Media Segment in the
         * > Playlist describes a single I-frame.  I-frames are encoded video
         * > frames whose encoding does not depend on any other frame.  I-frame
         * > Playlists can be used for trick play, such as fast forward, rapid
         * > reverse, and scrubbing.
         */
        fun isTrickPlay(): Boolean = (format.roleFlags and C.ROLE_FLAG_TRICK_PLAY != 0)

        /**
        https://datatracker.ietf.org/doc/html/rfc6381:
        > When the 'codecs' parameter is used, it MUST contain all codecs
        > indicated by the content present in the body part.  The 'codecs'
        > parameter MUST NOT include any codecs that are not indicated by any
        > media elements in the body part.

        This means that codecs cant be used
        "|| format.codecs?.split(",")?.any { MimeTypes.isAudio(MimeTypes.getMediaMimeType(it)) } == true"
        They may be used for harsher restriction on "audioGroupId == null", but codecs is optional

        https://datatracker.ietf.org/doc/html/rfc8216
        > Since the EXT-X-STREAM-INF tag has no AUDIO attribute, all video
        > Renditions would be required to contain the audio.

        However it may still contain audio with the AUDIO attribute, therefore we also check the audio with that groupId:

        > If the media type is VIDEO or AUDIO, a missing URI attribute
        > indicates that the media data for this Rendition is included in the
        > Media Playlist of any EXT-X-STREAM-INF tag referencing this EXT-
        > X-MEDIA tag.

        But this is not foolproof, because TS segments needs to be investigated to be sure as I do not see any
        way to detect this from the m3u8 playlist
         */
        fun mustContainAudio(playlist: HlsMultivariantPlaylist): Boolean =
            audioGroupId == null || (playlist.audios.firstOrNull { it.groupId == audioGroupId }?.url == null)
    }

    data class Rendition(
        /** The rendition's url, or null if the tag does not have a URI attribute. */
        val url: URI?,

        /** Format information associated with this rendition. */
        val format: Format,

        /** The group to which this rendition belongs. */
        val groupId: String,

        /** The name of the rendition. */
        val name: String
    )

    data class HlsMultivariantPlaylist(
        /** The base uri. Used to resolve relative paths. */
        val baseUri: String,

        /** The list of tags in the playlist. */
        val tags: List<String>,

        /** All of the media playlist URLs referenced by the playlist. */
        //val mediaPlaylistUrls: List<URI>,

        /** The variants declared by the playlist. */
        val variants: List<Variant>,

        /** The video renditions declared by the playlist. */
        val videos: List<Rendition>,

        /** The audio renditions declared by the playlist. */
        val audios: List<Rendition>,

        /** The subtitle renditions declared by the playlist. */
        val subtitles: List<Rendition>,

        /** The closed caption renditions declared by the playlist. */
        val closedCaptions: List<Rendition>,


        /**
         * The format of the audio muxed in the variants. May be null if the playlist does not declare any
         * muxed audio.
         */
        val muxedAudioFormat: Format? = null,

        /**
         * The format of the closed captions declared by the playlist. May be empty if the playlist
         * explicitly declares no captions are available, or null if the playlist does not declare any
         * captions information.
         */
        val muxedCaptionFormats: List<Format>?,

        /** Contains variable definitions, as defined by the #EXT-X-DEFINE tag. */
        val variableDefinitions: Map<String, String>,

        /** DRM initialization data derived from #EXT-X-SESSION-KEY tags. */
        val sessionKeyDrmInitData: List<DrmInitData>,

        /**
         * Whether the media is formed of independent segments, as defined by the
         * #EXT-X-INDEPENDENT-SEGMENTS tag.
         */
        val hasIndependentSegments: Boolean,
    )

    data class VariantInfo(
        /**
         * The average bitrate as declared by the AVERAGE-BANDWIDTH attribute of the EXT-X-STREAM-INF
         * tag, or {@link Format#NO_VALUE} if the attribute is not declared.
         */
        val averageBitrate: Int = Format.NO_VALUE,

        /** The peak bitrate as declared by the BANDWIDTH attribute of the EXT-X-STREAM-INF tag. */
        val peakBitrate: Int,

        /**
         * The VIDEO value as defined in the EXT-X-STREAM-INF tag, or null if the VIDEO attribute is not
         * present.
         */
        val videoGroupId: String? = null,

        /**
         * The AUDIO value as defined in the EXT-X-STREAM-INF tag, or null if the AUDIO attribute is not
         * present.
         */
        val audioGroupId: String? = null,

        /**
         * The SUBTITLES value as defined in the EXT-X-STREAM-INF tag, or null if the SUBTITLES
         * attribute is not present.
         */
        val subtitleGroupId: String? = null,

        /**
         * The CLOSED-CAPTIONS value as defined in the EXT-X-STREAM-INF tag, or null if the
         * CLOSED-CAPTIONS attribute is not present.
         */
        val captionGroupId: String? = null,
    )

    data class Format(

        /** An identifier for the format, or null if unknown or not applicable. */
        val id: String? = null,

        /**
         * The default human readable label, or null if unknown or not applicable.
         *
         * <p>If non-null, the same label will be part of {@link #labels} too. If null, {@link #labels}
         * will be empty.
         */
        val label: String? = null,

        /**
         * The human readable list of labels, or an empty list if unknown or not applicable.
         *
         * <p>If non-empty, the default {@link #label} will be part of this list. If empty, the default
         * {@link #label} will be null.
         */
        //val labels: List<Label> = emptyList(),

        /**
         * The language as an IETF BCP 47 conformant tag, or null if unknown or not applicable.
         * Check [com.lagradost.cloudstream3.utils.SubtitleHelper].
         *
         * See locales on:
         * https://github.com/unicode-org/cldr-json/blob/main/cldr-json/cldr-core/availableLocales.json
         * https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
         * https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r2/core/res/res/values/locale_config.xml
         * https://iso639-3.sil.org/code_tables/639/data/all
        */
        val language: String? = null,

        /** Track selection flags. */
        //  @C.SelectionFlags
        val selectionFlags: Int = 0,

        /** Track role flags. */
        // @C.RoleFlags
        val roleFlags: Int = 0,

        /** The auxiliary track type. */
        // @C.AuxiliaryTrackType
        //val auxiliaryTrackType: Int,

        /**
         * The average bitrate in bits per second, or {@link #NO_VALUE} if unknown or not applicable. The
         * way in which this field is populated depends on the type of media to which the format
         * corresponds:
         *
         * <ul>
         *   <li>DASH representations: Always {@link Format#NO_VALUE}.
         *   <li>HLS variants: The {@code AVERAGE-BANDWIDTH} attribute defined on the corresponding {@code
         *       EXT-X-STREAM-INF} tag in the multivariant playlist, or {@link Format#NO_VALUE} if not
         *       present.
         *   <li>SmoothStreaming track elements: The {@code Bitrate} attribute defined on the
         *       corresponding {@code TrackElement} in the manifest, or {@link Format#NO_VALUE} if not
         *       present.
         *   <li>Progressive container formats: Often {@link Format#NO_VALUE}, but may be populated with
         *       the average bitrate of the container if known.
         *   <li>Sample formats: Often {@link Format#NO_VALUE}, but may be populated with the average
         *       bitrate of the stream of samples with type {@link #sampleMimeType} if known. Note that if
         *       {@link #sampleMimeType} is a compressed format (e.g., {@link MimeTypes#AUDIO_AAC}), then
         *       this bitrate is for the stream of still compressed samples.
         * </ul>
         */
        val averageBitrate: Int = NO_VALUE,

        /**
         * The peak bitrate in bits per second, or {@link #NO_VALUE} if unknown or not applicable. The way
         * in which this field is populated depends on the type of media to which the format corresponds:
         *
         * <ul>
         *   <li>DASH representations: The {@code @bandwidth} attribute of the corresponding {@code
         *       Representation} element in the manifest.
         *   <li>HLS variants: The {@code BANDWIDTH} attribute defined on the corresponding {@code
         *       EXT-X-STREAM-INF} tag.
         *   <li>SmoothStreaming track elements: Always {@link Format#NO_VALUE}.
         *   <li>Progressive container formats: Often {@link Format#NO_VALUE}, but may be populated with
         *       the peak bitrate of the container if known.
         *   <li>Sample formats: Often {@link Format#NO_VALUE}, but may be populated with the peak bitrate
         *       of the stream of samples with type {@link #sampleMimeType} if known. Note that if {@link
         *       #sampleMimeType} is a compressed format (e.g., {@link MimeTypes#AUDIO_AAC}), then this
         *       bitrate is for the stream of still compressed samples.
         * </ul>
         */
        val peakBitrate: Int = NO_VALUE,

        /**
         * The bitrate in bits per second. This is the peak bitrate if known, or else the average bitrate
         * if known, or else {@link Format#NO_VALUE}. Equivalent to: {@code peakBitrate != NO_VALUE ?
         * peakBitrate : averageBitrate}.
         */
        val bitrate: Int = NO_VALUE,

        /** Codecs of the format as described in RFC 6381, or null if unknown or not applicable. */
        val codecs: String? = null,

        /** Metadata, or null if unknown or not applicable. */
        //val metadata: Metadata? = null,

        /**
         * An extra opaque object that can be added to the {@link Format} to provide additional
         * information that can be passed through the player.
         *
         * <p>This value is not included in serialized {@link Bundle} instances of this class that are
         * used to transfer data to other processes.
         */
        //@UnstableApi @Nullable public final Object customData;

        // Container specific.

        /** The MIME type of the container, or null if unknown or not applicable. */
        val containerMimeType: String? = null,

        // Sample specific.

        /** The sample MIME type, or null if unknown or not applicable. */
        val sampleMimeType: String? = null,

        /**
         * The maximum size of a buffer of data (typically one sample), or {@link #NO_VALUE} if unknown or
         * not applicable.
         */
        //@UnstableApi public final int maxInputSize;

        /**
         * The maximum number of samples that must be stored to correctly re-order samples from decode
         * order to presentation order.
         */
        //@UnstableApi public final int maxNumReorderSamples;

        /**
         * Initialization data that must be provided to the decoder. Will not be null, but may be empty if
         * initialization data is not required.
         */
        //@UnstableApi public final List<byte[]> initializationData;

        /** DRM initialization data if the stream is protected, or null otherwise. */
        //@UnstableApi @Nullable public final DrmInitData drmInitData;

        /**
         * For samples that contain subsamples, this is an offset that should be added to subsample
         * timestamps. A value of {@link #OFFSET_SAMPLE_RELATIVE} indicates that subsample timestamps are
         * relative to the timestamps of their parent samples.
         */
        //@UnstableApi public final long subsampleOffsetUs;

        /**
         * Indicates whether the stream contains preroll samples.
         *
         * <p>When this field is set to {@code true}, it means that the stream includes decode-only
         * samples that occur before the intended playback start position. These samples are necessary for
         * decoding but are not meant to be rendered and should be skipped after decoding.
         */
        //@UnstableApi public final boolean hasPrerollSamples;

        // Video specific.

        /** The width of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable. */
        val width: Int = NO_VALUE,

        /** The height of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable. */
        val height: Int = NO_VALUE,

        /** The frame rate in frames per second, or {@link #NO_VALUE} if unknown or not applicable. */
        val frameRate: Float = NO_VALUE.toFloat(),

        /**
         * The clockwise rotation that should be applied to the video for it to be rendered in the correct
         * orientation, or 0 if unknown or not applicable. Only 0, 90, 180 and 270 are supported.
         */
        val rotationDegrees: Int = 0,

        /** The width to height ratio of pixels in the video, or 1.0 if unknown or not applicable. */
        val pixelWidthHeightRatio: Float = 1.0f,

        /** The projection data for 360/VR video, or null if not applicable. */
        //@UnstableApi @Nullable public final byte[] projectionData;

        /**
         * The stereo layout for 360/3D/VR video, or {@link #NO_VALUE} if not applicable. Valid stereo
         * modes are {@link C#STEREO_MODE_MONO}, {@link C#STEREO_MODE_TOP_BOTTOM}, {@link
         * C#STEREO_MODE_LEFT_RIGHT}, {@link C#STEREO_MODE_STEREO_MESH}.
         */
        //@UnstableApi public final @C.StereoMode int stereoMode;

        /** The color metadata associated with the video, or null if not applicable. */
        //@UnstableApi @Nullable public final ColorInfo colorInfo;

        /**
         * The maximum number of temporal scalable sub-layers in the video bitstream, or {@link #NO_VALUE}
         * if not applicable.
         */
        //@UnstableApi public final int maxSubLayers;

        // Audio specific.

        /** The number of audio channels, or {@link #NO_VALUE} if unknown or not applicable. */
        val channelCount: Int = NO_VALUE,

        /** The audio sampling rate in Hz, or {@link #NO_VALUE} if unknown or not applicable. */
        //public final int sampleRate;

        /**
         * The {@link C.PcmEncoding} for PCM or losslessly compressed audio. Set to {@link #NO_VALUE} for
         * other media types.
         */
        //@UnstableApi public final @C.PcmEncoding int pcmEncoding;

        /**
         * The number of frames to trim from the start of the decoded audio stream, or 0 if not
         * applicable.
         */
        //@UnstableApi public final int encoderDelay;

        /**
         * The number of frames to trim from the end of the decoded audio stream, or 0 if not applicable.
         */
        //@UnstableApi public final int encoderPadding;

        // Text specific.

        /** The Accessibility channel, or {@link #NO_VALUE} if not known or applicable. */
        val accessibilityChannel: Int = NO_VALUE,

        /**
         * The replacement behavior that should be followed when handling consecutive samples in a
         * {@linkplain C#TRACK_TYPE_TEXT text track} of type {@link MimeTypes#APPLICATION_MEDIA3_CUES}.
         */
        //@UnstableApi public final @CueReplacementBehavior int cueReplacementBehavior;

        // Image specific.

        /**
         * The number of horizontal tiles in an image, or {@link #NO_VALUE} if not known or applicable.
         */
        //@UnstableApi public final int tileCountHorizontal;

        /** The number of vertical tiles in an image, or {@link #NO_VALUE} if not known or applicable. */
        //@UnstableApi public final int tileCountVertical;

        // Provided by source.

        /**
         * The type of crypto that must be used to decode samples associated with this format, or {@link
         * C#CRYPTO_TYPE_NONE} if the content is not encrypted. Cannot be {@link C#CRYPTO_TYPE_NONE} if
         * {@link #drmInitData} is non-null, but may be {@link C#CRYPTO_TYPE_UNSUPPORTED} to indicate that
         * the samples are encrypted using an unsupported crypto type.
         */
        //@UnstableApi public final @C.CryptoType int cryptoType;
    ) {
        companion object {
            const val NO_VALUE: Int = -1
        }
    }

    private fun getVariantWithVideoGroup(variants: ArrayList<Variant>, groupId: String): Variant? =
        variants.firstOrNull { variant -> groupId == variant.videoGroupId }

    private fun getVariantWithAudioGroup(variants: ArrayList<Variant>, groupId: String): Variant? =
        variants.firstOrNull { variant -> groupId == variant.audioGroupId }

    private fun getVariantWithSubtitleGroup(
        variants: ArrayList<Variant>,
        groupId: String
    ): Variant? = variants.firstOrNull { variant -> groupId == variant.subtitleGroupId }

    private fun isDolbyVisionFormat(
        videoRange: String?,
        codecs: String?,
        supplementalCodecs: String?,
        supplementalProfiles: String?
    ): Boolean {
        if (!MimeTypes.isDolbyVisionCodec(codecs, supplementalCodecs)) {
            return false
        }
        if (supplementalCodecs == null) {
            // Dolby Vision profile 5 that doesn't define supplemental codecs.
            return true
        }
        if (videoRange == null || supplementalProfiles == null) {
            // Video range and supplemental profiles need to be defined for a full validity check.
            return false
        }
        if ((videoRange == "PQ" && supplementalProfiles != "db1p")
            || (videoRange == "SDR" && supplementalProfiles != "db2g")
            || (videoRange == "HLG" && !supplementalProfiles.startsWith("db4"))
        ) { // db4g or db4h
            return false
        }
        return true
    }


    fun parse(baseUri: String, text: String): HlsMultivariantPlaylist? {
        val lines = text.lines()
        if (lines.any { it.startsWith(TAG_STREAM_INF) }) {
            return parseMultivariantPlaylist(lines.iterator(), baseUri)
        }
        return null
    }

    @Throws(IOException::class)
    private fun parseMultivariantPlaylist(
        iterator: Iterator<String>, baseUri: String
    ): HlsMultivariantPlaylist {
        val urlToVariantInfos: HashMap<URI, ArrayList<VariantInfo>?> =
            HashMap<URI, ArrayList<VariantInfo>?>()
        val variableDefinitions = HashMap<String, String>()
        val variants: ArrayList<Variant> = ArrayList<Variant>()
        val videos: ArrayList<Rendition> = ArrayList<Rendition>()
        val audios: ArrayList<Rendition> = ArrayList<Rendition>()
        val subtitles: ArrayList<Rendition> = ArrayList<Rendition>()
        val closedCaptions: ArrayList<Rendition> = ArrayList<Rendition>()
        val mediaTags = ArrayList<String>()
        val sessionKeyDrmInitData: ArrayList<DrmInitData> = ArrayList<DrmInitData>()
        val tags = ArrayList<String>()
        var muxedAudioFormat: Format? = null
        var muxedCaptionFormats: ArrayList<Format> = arrayListOf()
        var noClosedCaptions = false
        var hasIndependentSegmentsTag = false

        var line: String
        while (iterator.hasNext()) {
            line = iterator.next()

            if (line.startsWith(TAG_PREFIX)) {
                // We expose all tags through the playlist.
                tags.add(line)
            }
            val isIFrameOnlyVariant = line.startsWith(TAG_I_FRAME_STREAM_INF)

            if (line.startsWith(TAG_DEFINE)) {
                variableDefinitions[parseStringAttr(line, REGEX_NAME, variableDefinitions)] =
                    parseStringAttr(line, REGEX_VALUE, variableDefinitions)
            } else if (line == TAG_INDEPENDENT_SEGMENTS) {
                hasIndependentSegmentsTag = true
            } else if (line.startsWith(TAG_MEDIA)) {
                // Media tags are parsed at the end to include codec information from #EXT-X-STREAM-INF
                // tags.
                mediaTags.add(line)
            } else if (line.startsWith(TAG_SESSION_KEY)) {
                val keyFormat: String =
                    parseOptionalStringAttr(
                        line,
                        REGEX_KEYFORMAT,
                        KEYFORMAT_IDENTITY,
                        variableDefinitions
                    )!!
                val schemeData: SchemeData? =
                    parseDrmSchemeData(line, keyFormat, variableDefinitions)
                if (schemeData != null) {
                    val method: String =
                        parseStringAttr(line, REGEX_METHOD, variableDefinitions)
                    val scheme: String = parseEncryptionScheme(method)
                    sessionKeyDrmInitData.add(DrmInitData(scheme, arrayOf(schemeData)))
                }
            } else if (line.startsWith(TAG_STREAM_INF) || isIFrameOnlyVariant) {
                noClosedCaptions = noClosedCaptions or line.contains(ATTR_CLOSED_CAPTIONS_NONE)
                val roleFlags = if (isIFrameOnlyVariant) C.ROLE_FLAG_TRICK_PLAY else 0
                val peakBitrate: Int = parseIntAttr(line, REGEX_BANDWIDTH)
                val averageBitrate: Int =
                    parseOptionalIntAttr(line, REGEX_AVERAGE_BANDWIDTH, -1)
                val videoRange: String? =
                    parseOptionalStringAttr(line, REGEX_VIDEO_RANGE, variableDefinitions)
                var codecs: String? =
                    parseOptionalStringAttr(line, REGEX_CODECS, variableDefinitions)
                val supplementalCodecsStrings: String? =
                    parseOptionalStringAttr(
                        line,
                        REGEX_SUPPLEMENTAL_CODECS,
                        variableDefinitions
                    )
                var supplementalCodecs: String? = null
                var supplementalProfiles: String? = null // i.e. Compatibility brand
                if (supplementalCodecsStrings != null) {
                    val supplementalCodecsString: Array<String> =
                        Util.splitAtFirst(supplementalCodecsStrings, ",")
                    // TODO: Support more than one element
                    val codecsAndProfiles: Array<String> = Util.split(
                        supplementalCodecsString[0], "/"
                    )
                    supplementalCodecs = codecsAndProfiles[0]
                    if (codecsAndProfiles.size > 1) {
                        supplementalProfiles = codecsAndProfiles[1]
                    }
                }
                var videoCodecs: String? = Util.getCodecsOfType(codecs, C.TRACK_TYPE_VIDEO)
                if (isDolbyVisionFormat(
                        videoRange, videoCodecs, supplementalCodecs, supplementalProfiles
                    )
                ) {
                    videoCodecs = supplementalCodecs ?: videoCodecs
                    val nonVideoCodecs: String? =
                        Util.getCodecsWithoutType(codecs, C.TRACK_TYPE_VIDEO)
                    codecs =
                        if (nonVideoCodecs != null) "$videoCodecs,$nonVideoCodecs" else videoCodecs
                }

                val resolutionString: String? =
                    parseOptionalStringAttr(line, REGEX_RESOLUTION, variableDefinitions)
                var width: Int
                var height: Int
                if (resolutionString != null) {
                    val widthAndHeight: Array<String> = Util.split(resolutionString, "x")
                    width = widthAndHeight[0].toInt()
                    height = widthAndHeight[1].toInt()
                    if (width <= 0 || height <= 0) {
                        // Resolution string is invalid.
                        width = Format.NO_VALUE
                        height = Format.NO_VALUE
                    }
                } else {
                    width = Format.NO_VALUE
                    height = Format.NO_VALUE
                }
                var frameRate: Float = Format.NO_VALUE.toFloat()
                val frameRateString: String? =
                    parseOptionalStringAttr(line, REGEX_FRAME_RATE, variableDefinitions)
                if (frameRateString != null) {
                    frameRate = frameRateString.toFloat()
                }
                val videoGroupId: String? =
                    parseOptionalStringAttr(line, REGEX_VIDEO, variableDefinitions)
                val audioGroupId: String? =
                    parseOptionalStringAttr(line, REGEX_AUDIO, variableDefinitions)
                val subtitlesGroupId: String? =
                    parseOptionalStringAttr(line, REGEX_SUBTITLES, variableDefinitions)
                val closedCaptionsGroupId: String? =
                    parseOptionalStringAttr(line, REGEX_CLOSED_CAPTIONS, variableDefinitions)
                val uri: URI
                if (isIFrameOnlyVariant) {
                    uri =
                        UriUtil.resolveToUri(
                            baseUri,
                            parseStringAttr(line, REGEX_URI, variableDefinitions)
                        )
                } else if (!iterator.hasNext()) {
                    throw ParserException.createForMalformedManifest(
                        "#EXT-X-STREAM-INF must be followed by another line",  /* cause= */null
                    )
                } else {
                    // The following line contains #EXT-X-STREAM-INF's URI.
                    line = replaceVariableReferences(iterator.next(), variableDefinitions)
                    uri = UriUtil.resolveToUri(baseUri, line)
                }

                val variant =
                    Variant(
                        url = uri,
                        format = Format(
                            id = variants.size.toString(),
                            containerMimeType = MimeTypes.APPLICATION_M3U8,
                            codecs = codecs,
                            averageBitrate = averageBitrate,
                            peakBitrate = peakBitrate,
                            width = width,
                            height = height,
                            frameRate = frameRate,
                            roleFlags = roleFlags,
                        ),
                        videoGroupId = videoGroupId,
                        audioGroupId = audioGroupId,
                        subtitleGroupId = subtitlesGroupId,
                        captionGroupId = closedCaptionsGroupId
                    )
                variants.add(variant)
                var variantInfosForUrl: ArrayList<VariantInfo>? = urlToVariantInfos[uri]
                if (variantInfosForUrl == null) {
                    variantInfosForUrl = ArrayList()
                    urlToVariantInfos[uri] = variantInfosForUrl
                }
                variantInfosForUrl.add(
                    VariantInfo(
                        averageBitrate,
                        peakBitrate,
                        videoGroupId,
                        audioGroupId,
                        subtitlesGroupId,
                        closedCaptionsGroupId
                    )
                )
            }
        }

        // TODO: Don't deduplicate variants by URL.
        val deduplicatedVariants = variants.distinctBy { it.url }
        /*val deduplicatedVariants: ArrayList<Variant> = ArrayList<Variant>()
        val urlsInDeduplicatedVariants = HashSet<URI>()
        for (i in variants.indices) {
            val variant: Variant = variants[i]
            if (urlsInDeduplicatedVariants.add(variant.url)) {
                Assertions.checkState(variant.format.metadata == null)
                val hlsMetadataEntry: HlsTrackMetadataEntry =
                    HlsTrackMetadataEntry( /* groupId= */
                        null,  /* name= */
                        null,
                        checkNotNull(urlToVariantInfos[variant.url])
                    )
                val metadata = Metadata(hlsMetadataEntry)
                val format: Format = variant.format.buildUpon().setMetadata(metadata).build()
                deduplicatedVariants.add(variant.copyWithFormat(format))
            }
        }*/

        for (i in mediaTags.indices) {
            line = mediaTags[i]
            val groupId: String = parseStringAttr(line, REGEX_GROUP_ID, variableDefinitions)
            val name: String = parseStringAttr(line, REGEX_NAME, variableDefinitions)
            var formatBuilder = Format(
                id = "$groupId:$name",
                roleFlags = parseRoleFlags(line, variableDefinitions),
                selectionFlags = parseSelectionFlags(line),
                label = name,
                language = parseOptionalStringAttr(
                    line,
                    REGEX_LANGUAGE,
                    variableDefinitions
                ),
                containerMimeType = MimeTypes.APPLICATION_M3U8,
            )

            val referenceUri: String? =
                parseOptionalStringAttr(line, REGEX_URI, variableDefinitions)
            val uri: URI? =
                if (referenceUri == null) null else UriUtil.resolveToUri(baseUri, referenceUri)
            //val metadata =
            //    Metadata(HlsTrackMetadataEntry(groupId, name, emptyList<T>()))
            when (parseStringAttr(line, REGEX_TYPE, variableDefinitions)) {
                TYPE_VIDEO -> {
                    val variant: Variant? = getVariantWithVideoGroup(variants, groupId)
                    if (variant != null) {
                        val variantFormat: Format = variant.format
                        formatBuilder = formatBuilder.copy(
                            height = variantFormat.height,
                            width = variantFormat.width,
                            frameRate = variantFormat.frameRate,
                            codecs = Util.getCodecsOfType(variantFormat.codecs, C.TRACK_TYPE_VIDEO)
                        )
                    }
                    if (uri == null) {
                        // TODO: Remove this case and add a Rendition with a null uri to videos.
                    } else {
                        //formatBuilder.setMetadata(metadata)
                        videos.add(Rendition(url = uri, format = formatBuilder, groupId, name))
                    }
                }

                TYPE_AUDIO -> {
                    var sampleMimeType: String? = null
                    val variant = getVariantWithAudioGroup(variants, groupId)
                    if (variant != null) {
                        val codecs: String? =
                            Util.getCodecsOfType(variant.format.codecs, C.TRACK_TYPE_AUDIO)
                        formatBuilder = formatBuilder.copy(codecs = codecs)
                        sampleMimeType = MimeTypes.getMediaMimeType(codecs)
                    }
                    val channelsString: String? =
                        parseOptionalStringAttr(line, REGEX_CHANNELS, variableDefinitions)
                    if (channelsString != null) {
                        val channelCount: Int =
                            Util.splitAtFirst(channelsString, "/")[0].toInt()
                        formatBuilder = formatBuilder.copy(channelCount = channelCount)
                        if (MimeTypes.AUDIO_E_AC3 == sampleMimeType && channelsString.endsWith(
                                "/JOC"
                            )
                        ) {
                            sampleMimeType = MimeTypes.AUDIO_E_AC3_JOC
                            formatBuilder = formatBuilder.copy(codecs = MimeTypes.CODEC_E_AC3_JOC)
                        }
                    }
                    val format = formatBuilder.copy(sampleMimeType = sampleMimeType)
                    if (uri != null) {
                        //formatBuilder.setMetadata(metadata)
                        audios.add(Rendition(uri, format, groupId, name))
                    } else if (variant != null) {
                        // TODO: Remove muxedAudioFormat and add a Rendition with a null uri to audios.
                        muxedAudioFormat = format
                    }
                }

                TYPE_SUBTITLES -> {
                    var sampleMimeType: String? = null
                    val variant = getVariantWithSubtitleGroup(variants, groupId)
                    if (variant != null) {
                        val codecs: String? =
                            Util.getCodecsOfType(variant.format.codecs, C.TRACK_TYPE_TEXT)
                        formatBuilder = formatBuilder.copy(
                            codecs = codecs,
                        )
                        sampleMimeType = MimeTypes.getMediaMimeType(codecs)
                    }
                    if (sampleMimeType == null) {
                        sampleMimeType = MimeTypes.TEXT_VTT
                    }
                    if (uri != null) {
                        subtitles.add(
                            Rendition(
                                uri,
                                formatBuilder.copy(sampleMimeType = sampleMimeType),
                                groupId,
                                name
                            )
                        )
                    } else {
                        /*Log.w(
                            LOG_TAG,
                            "EXT-X-MEDIA tag with missing mandatory URI attribute: skipping"
                        )*/
                    }
                }

                TYPE_CLOSED_CAPTIONS -> {
                    val instreamId: String =
                        parseStringAttr(line, REGEX_INSTREAM_ID, variableDefinitions)
                    val accessibilityChannel: Int
                    val sampleMimeType: String
                    if (instreamId.startsWith("CC")) {
                        sampleMimeType = MimeTypes.APPLICATION_CEA608
                        accessibilityChannel = instreamId.substring(2).toInt()
                    } else  /* starts with SERVICE */ {
                        sampleMimeType = MimeTypes.APPLICATION_CEA708
                        accessibilityChannel = instreamId.substring(7).toInt()
                    }
                    muxedCaptionFormats.add(
                        formatBuilder.copy(
                            sampleMimeType = sampleMimeType,
                            accessibilityChannel = accessibilityChannel
                        )
                    )
                }

                else -> {}
            }
        }

        if (noClosedCaptions) {
            muxedCaptionFormats = arrayListOf()
        }

        return HlsMultivariantPlaylist(
            baseUri = baseUri,
            tags = tags,
            variants = deduplicatedVariants,
            videos = videos,
            audios = audios,
            subtitles = subtitles,
            closedCaptions = closedCaptions,
            muxedAudioFormat = muxedAudioFormat,
            muxedCaptionFormats = muxedCaptionFormats,
            hasIndependentSegments = hasIndependentSegmentsTag,
            variableDefinitions = variableDefinitions,
            sessionKeyDrmInitData = sessionKeyDrmInitData
        )
    }
}