package com.lagradost.cloudstream3.ui.player

import android.net.Uri
import androidx.annotation.GuardedBy
import androidx.media3.common.FileTypes
import androidx.media3.common.Format
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.amr.AmrExtractor
import androidx.media3.extractor.avi.AviExtractor
import androidx.media3.extractor.avif.AvifExtractor
import androidx.media3.extractor.bmp.BmpExtractor
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.flv.FlvExtractor
import androidx.media3.extractor.heif.HeifExtractor
import androidx.media3.extractor.jpeg.JpegExtractor
import androidx.media3.extractor.mkv.UpdatedMatroskaExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.png.PngExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.Ac3Extractor
import androidx.media3.extractor.ts.Ac4Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.PsExtractor
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.wav.WavExtractor
import androidx.media3.extractor.webp.WebpExtractor
import com.google.common.collect.ImmutableList
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean


/**
 * An [ExtractorsFactory] that provides an array of extractors for the following formats:
 *
 *
 *  * MP4, including M4A ([Mp4Extractor])
 *  * fMP4 ([FragmentedMp4Extractor])
 *  * Matroska and WebM ([UpdatedMatroskaExtractor])
 *  * Ogg Vorbis/FLAC ([OggExtractor]
 *  * MP3 ([Mp3Extractor])
 *  * AAC ([AdtsExtractor])
 *  * MPEG TS ([TsExtractor])
 *  * MPEG PS ([PsExtractor])
 *  * FLV ([FlvExtractor])
 *  * WAV ([WavExtractor])
 *  * AC3 ([Ac3Extractor])
 *  * AC4 ([Ac4Extractor])
 *  * AMR ([AmrExtractor])
 *  * FLAC
 *
 *  * If available, the FLAC extension's `androidx.media3.decoder.flac.FlacExtractor`
 * is used.
 *  * Otherwise, the core [FlacExtractor] is used. Note that Android devices do not
 * generally include a FLAC decoder before API 27. This can be worked around by using
 * the FLAC extension or the FFmpeg extension.
 *
 *  * JPEG ([JpegExtractor])
 *  * PNG ([PngExtractor])
 *  * WEBP ([WebpExtractor])
 *  * BMP ([BmpExtractor])
 *  * HEIF ([HeifExtractor])
 *  * AVIF ([AvifExtractor])
 *  * MIDI, if available, the MIDI extension's `androidx.media3.decoder.midi.MidiExtractor`
 * is used.
 *
 */
@UnstableApi
class UpdatedDefaultExtractorsFactory : ExtractorsFactory {
    private var constantBitrateSeekingEnabled = false
    private var constantBitrateSeekingAlwaysEnabled = false
    private var adtsFlags: @AdtsExtractor.Flags Int = 0
    private var amrFlags: @AmrExtractor.Flags Int = 0
    private var flacFlags: @FlacExtractor.Flags Int = 0
    private var matroskaFlags: @UpdatedMatroskaExtractor.Flags Int = 0
    private var mp4Flags: @Mp4Extractor.Flags Int = 0
    private var fragmentedMp4Flags: @FragmentedMp4Extractor.Flags Int = 0
    private var mp3Flags: @Mp3Extractor.Flags Int = 0
    private var tsMode: @TsExtractor.Mode Int
    private var tsFlags: @DefaultTsPayloadReaderFactory.Flags Int = 0

    // TODO (b/261183220): Initialize tsSubtitleFormats in constructor once shrinking bug is fixed.
    private var tsSubtitleFormats: ImmutableList<Format>? = null
    private var tsTimestampSearchBytes: Int
    private var textTrackTranscodingEnabled: Boolean
    private var subtitleParserFactory: SubtitleParser.Factory
    private var jpegFlags: @JpegExtractor.Flags Int = 0

    init {
        tsMode = TsExtractor.MODE_SINGLE_PMT
        tsTimestampSearchBytes = TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES
        subtitleParserFactory = DefaultSubtitleParserFactory()
        textTrackTranscodingEnabled = true
    }

    /**
     * Convenience method to set whether approximate seeking using constant bitrate assumptions should
     * be enabled for all extractors that support it. If set to true, the flags required to enable
     * this functionality will be OR'd with those passed to the setters when creating extractor
     * instances. If set to false then the flags passed to the setters will be used without
     * modification.
     *
     * @param constantBitrateSeekingEnabled Whether approximate seeking using a constant bitrate
     * assumption should be enabled for all extractors that support it.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setConstantBitrateSeekingEnabled(
        constantBitrateSeekingEnabled: Boolean
    ): UpdatedDefaultExtractorsFactory {
        this.constantBitrateSeekingEnabled = constantBitrateSeekingEnabled
        return this
    }

    /**
     * Convenience method to set whether approximate seeking using constant bitrate assumptions should
     * be enabled for all extractors that support it, and if it should be enabled even if the content
     * length (and hence the duration of the media) is unknown. If set to true, the flags required to
     * enable this functionality will be OR'd with those passed to the setters when creating extractor
     * instances. If set to false then the flags passed to the setters will be used without
     * modification.
     *
     *
     * When seeking into content where the length is unknown, application code should ensure that
     * requested seek positions are valid, or should be ready to handle playback failures reported
     * through [Player.Listener.onPlayerError] with [PlaybackException.errorCode] set to
     * [PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE].
     *
     * @param constantBitrateSeekingAlwaysEnabled Whether approximate seeking using a constant bitrate
     * assumption should be enabled for all extractors that support it, including when the content
     * duration is unknown.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setConstantBitrateSeekingAlwaysEnabled(
        constantBitrateSeekingAlwaysEnabled: Boolean
    ): UpdatedDefaultExtractorsFactory {
        this.constantBitrateSeekingAlwaysEnabled = constantBitrateSeekingAlwaysEnabled
        return this
    }

    /**
     * Sets flags for [AdtsExtractor] instances created by the factory.
     *
     * @see AdtsExtractor.AdtsExtractor
     * @param flags The flags to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setAdtsExtractorFlags(
        flags: @AdtsExtractor.Flags Int
    ): UpdatedDefaultExtractorsFactory {
        this.adtsFlags = flags
        return this
    }

    /**
     * Sets flags for [AmrExtractor] instances created by the factory.
     *
     * @see AmrExtractor.AmrExtractor
     * @param flags The flags to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setAmrExtractorFlags(flags: @AmrExtractor.Flags Int): UpdatedDefaultExtractorsFactory {
        this.amrFlags = flags
        return this
    }

    /**
     * Sets flags for [FlacExtractor] instances created by the factory. The flags are also used
     * by `androidx.media3.decoder.flac.FlacExtractor` instances if the FLAC extension is being
     * used.
     *
     * @see FlacExtractor.FlacExtractor
     * @param flags The flags to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setFlacExtractorFlags(
        flags: @FlacExtractor.Flags Int
    ): UpdatedDefaultExtractorsFactory {
        this.flacFlags = flags
        return this
    }

    /**
     * Sets flags for [UpdatedMatroskaExtractor] instances created by the factory.
     *
     * @see UpdatedMatroskaExtractor.MatroskaExtractor
     * @param flags The flags to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setMatroskaExtractorFlags(
        flags: @UpdatedMatroskaExtractor.Flags Int
    ): UpdatedDefaultExtractorsFactory {
        this.matroskaFlags = flags
        return this
    }

    /**
     * Sets flags for [Mp4Extractor] instances created by the factory.
     *
     * @see Mp4Extractor.Mp4Extractor
     * @param flags The flags to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setMp4ExtractorFlags(flags: @Mp4Extractor.Flags Int): UpdatedDefaultExtractorsFactory {
        this.mp4Flags = flags
        return this
    }

    /**
     * Sets flags for [FragmentedMp4Extractor] instances created by the factory.
     *
     * @see FragmentedMp4Extractor.FragmentedMp4Extractor
     * @param flags The flags to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setFragmentedMp4ExtractorFlags(
        flags: @FragmentedMp4Extractor.Flags Int
    ): UpdatedDefaultExtractorsFactory {
        this.fragmentedMp4Flags = flags
        return this
    }

    /**
     * Sets flags for [Mp3Extractor] instances created by the factory.
     *
     * @see Mp3Extractor.Mp3Extractor
     * @param flags The flags to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setMp3ExtractorFlags(flags: @Mp3Extractor.Flags Int): UpdatedDefaultExtractorsFactory {
        mp3Flags = flags
        return this
    }

    /**
     * Sets the mode for [TsExtractor] instances created by the factory.
     *
     * @see TsExtractor.TsExtractor
     * @param mode The mode to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setTsExtractorMode(mode: @TsExtractor.Mode Int): UpdatedDefaultExtractorsFactory {
        tsMode = mode
        return this
    }

    /**
     * Sets flags for [DefaultTsPayloadReaderFactory]s used by [TsExtractor] instances
     * created by the factory.
     *
     * @see TsExtractor.TsExtractor
     * @param flags The flags to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setTsExtractorFlags(
        flags: @DefaultTsPayloadReaderFactory.Flags Int
    ): UpdatedDefaultExtractorsFactory {
        tsFlags = flags
        return this
    }

    /**
     * Sets a list of subtitle formats to pass to the [DefaultTsPayloadReaderFactory] used by
     * [TsExtractor] instances created by the factory.
     *
     * @see DefaultTsPayloadReaderFactory.DefaultTsPayloadReaderFactory
     * @param subtitleFormats The subtitle formats.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setTsSubtitleFormats(subtitleFormats: List<Format>?): UpdatedDefaultExtractorsFactory {
        tsSubtitleFormats = ImmutableList.copyOf(subtitleFormats)
        return this
    }

    /**
     * Sets the number of bytes searched to find a timestamp for [TsExtractor] instances created
     * by the factory.
     *
     * @see TsExtractor.TsExtractor
     * @param timestampSearchBytes The number of search bytes to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setTsExtractorTimestampSearchBytes(
        timestampSearchBytes: Int
    ): UpdatedDefaultExtractorsFactory {
        tsTimestampSearchBytes = timestampSearchBytes
        return this
    }

    @Deprecated(
        """This method (and all support for 'legacy' subtitle decoding during rendering) will
        be removed in a future release."""
    )
    @Synchronized
    fun setTextTrackTranscodingEnabled(
        textTrackTranscodingEnabled: Boolean
    ): UpdatedDefaultExtractorsFactory {
        return experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled)
    }

    @Deprecated("")
    @Synchronized
    override fun experimentalSetTextTrackTranscodingEnabled(
        textTrackTranscodingEnabled: Boolean
    ): UpdatedDefaultExtractorsFactory {
        this.textTrackTranscodingEnabled = textTrackTranscodingEnabled
        return this
    }

    @Synchronized
    override fun setSubtitleParserFactory(
        subtitleParserFactory: SubtitleParser.Factory
    ): UpdatedDefaultExtractorsFactory {
        this.subtitleParserFactory = subtitleParserFactory
        return this
    }

    /**
     * Sets flags for [JpegExtractor] instances created by the factory.
     *
     * @see JpegExtractor.JpegExtractor
     * @param flags The flags to use.
     * @return The factory, for convenience.
     */
    @Synchronized
    fun setJpegExtractorFlags(
        flags: @JpegExtractor.Flags Int
    ): UpdatedDefaultExtractorsFactory {
        this.jpegFlags = flags
        return this
    }

    @Synchronized
    override fun createExtractors(): Array<Extractor> {
        return createExtractors(Uri.EMPTY, HashMap())
    }

    @Synchronized
    override fun createExtractors(
        uri: Uri, responseHeaders: Map<String, List<String>>
    ): Array<Extractor> {
        val extractors: MutableList<Extractor> =
            ArrayList<Extractor>( /* initialCapacity= */DEFAULT_EXTRACTOR_ORDER.size)

        val responseHeadersInferredFileType: @FileTypes.Type Int =
            FileTypes.inferFileTypeFromResponseHeaders(responseHeaders)
        if (responseHeadersInferredFileType != FileTypes.UNKNOWN) {
            addExtractorsForFileType(responseHeadersInferredFileType, extractors)
        }

        val uriInferredFileType: @FileTypes.Type Int = FileTypes.inferFileTypeFromUri(uri)
        if (uriInferredFileType != FileTypes.UNKNOWN
            && uriInferredFileType != responseHeadersInferredFileType
        ) {
            addExtractorsForFileType(uriInferredFileType, extractors)
        }

        for (fileType in DEFAULT_EXTRACTOR_ORDER) {
            if (fileType != responseHeadersInferredFileType && fileType != uriInferredFileType) {
                addExtractorsForFileType(fileType, extractors)
            }
        }
        return extractors.toTypedArray<Extractor>()
    }

    private fun addExtractorsForFileType(
        fileType: @FileTypes.Type Int,
        extractors: MutableList<Extractor>
    ) {
        when (fileType) {
            FileTypes.AC3 -> extractors.add(Ac3Extractor())
            FileTypes.AC4 -> extractors.add(Ac4Extractor())
            FileTypes.ADTS -> extractors.add(
                AdtsExtractor(
                    (adtsFlags
                            or (if (constantBitrateSeekingEnabled)
                        AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
                    else
                        0)
                            or (if (constantBitrateSeekingAlwaysEnabled)
                        AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS
                    else
                        0))
                )
            )

            FileTypes.AMR -> extractors.add(
                AmrExtractor(
                    (amrFlags
                            or (if (constantBitrateSeekingEnabled)
                        AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
                    else
                        0)
                            or (if (constantBitrateSeekingAlwaysEnabled)
                        AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS
                    else
                        0))
                )
            )

            FileTypes.FLAC -> {
                val flacExtractor: Extractor? = FLAC_EXTENSION_LOADER.getExtractor(flacFlags)
                if (flacExtractor != null) {
                    extractors.add(flacExtractor)
                } else {
                    extractors.add(FlacExtractor(flacFlags))
                }
            }

            FileTypes.FLV -> extractors.add(FlvExtractor())
            FileTypes.MATROSKA -> extractors.add(
                UpdatedMatroskaExtractor(
                    subtitleParserFactory,
                    matroskaFlags
                            or (if (textTrackTranscodingEnabled)
                        0
                    else
                        UpdatedMatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA)
                )
            )

            FileTypes.MP3 -> extractors.add(
                Mp3Extractor(
                    (mp3Flags
                            or (if (constantBitrateSeekingEnabled)
                        Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
                    else
                        0)
                            or (if (constantBitrateSeekingAlwaysEnabled)
                        Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS
                    else
                        0))
                )
            )

            FileTypes.MP4 -> {
                extractors.add(
                    FragmentedMp4Extractor(
                        subtitleParserFactory,
                        fragmentedMp4Flags
                                or (if (textTrackTranscodingEnabled)
                            0
                        else
                            FragmentedMp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA)
                    )
                )
                extractors.add(
                    Mp4Extractor(
                        subtitleParserFactory,
                        mp4Flags
                                or (if (textTrackTranscodingEnabled)
                            0
                        else
                            Mp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA)
                    )
                )
            }

            FileTypes.OGG -> extractors.add(OggExtractor())
            FileTypes.PS -> extractors.add(PsExtractor())
            FileTypes.TS -> {
                if (tsSubtitleFormats == null) {
                    tsSubtitleFormats = ImmutableList.of()
                }
                extractors.add(
                    TsExtractor(
                        tsMode,
                        (if (textTrackTranscodingEnabled) 0 else TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA),
                        subtitleParserFactory,
                        TimestampAdjuster(0),
                        DefaultTsPayloadReaderFactory(tsFlags, tsSubtitleFormats!!),
                        tsTimestampSearchBytes
                    )
                )
            }

            FileTypes.WAV -> extractors.add(WavExtractor())
            FileTypes.JPEG -> extractors.add(JpegExtractor(jpegFlags))
            FileTypes.MIDI -> {
                val midiExtractor: Extractor? = MIDI_EXTENSION_LOADER.getExtractor()
                if (midiExtractor != null) {
                    extractors.add(midiExtractor)
                }
            }

            FileTypes.AVI -> extractors.add(
                AviExtractor(
                    (if (textTrackTranscodingEnabled) 0 else AviExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA),
                    subtitleParserFactory
                )
            )

            FileTypes.PNG -> extractors.add(PngExtractor())
            FileTypes.WEBP -> extractors.add(WebpExtractor())
            FileTypes.BMP -> extractors.add(BmpExtractor())
            FileTypes.HEIF -> if ((mp4Flags and Mp4Extractor.FLAG_READ_MOTION_PHOTO_METADATA) == 0
                && (mp4Flags and Mp4Extractor.FLAG_READ_SEF_DATA) == 0
            ) {
                extractors.add(HeifExtractor())
            }

            FileTypes.AVIF -> extractors.add(AvifExtractor())
            FileTypes.WEBVTT, FileTypes.UNKNOWN -> {}
            else -> {}
        }
    }

    private class ExtensionLoader(private val constructorSupplier: ConstructorSupplier) {
        interface ConstructorSupplier {
            @get:Throws(
                InvocationTargetException::class,
                IllegalAccessException::class,
                NoSuchMethodException::class,
                ClassNotFoundException::class
            )
            val constructor: Constructor<out Extractor?>?
        }

        private val extensionLoaded = AtomicBoolean(false)

        @GuardedBy("extensionLoaded")
        private val extractorConstructor: Constructor<out Extractor?>? = null

        fun getExtractor(vararg constructorParams: Any?): Extractor? {
            val extractorConstructor: Constructor<out Extractor?> = maybeLoadExtractorConstructor()
                ?: return null
            try {
                return extractorConstructor.newInstance(*constructorParams)
            } catch (e: Exception) {
                throw IllegalStateException("Unexpected error creating extractor", e)
            }
        }

        fun maybeLoadExtractorConstructor(): Constructor<out Extractor?>? {
            synchronized(extensionLoaded) {
                if (extensionLoaded.get()) {
                    return extractorConstructor
                }
                try {
                    return constructorSupplier.constructor
                } catch (e: ClassNotFoundException) {
                    // Expected if the app was built without the extension.
                } catch (e: Exception) {
                    // The extension is present, but instantiation failed.
                    throw RuntimeException("Error instantiating extension", e)
                }
                extensionLoaded.set(true)
                return extractorConstructor
            }
        }
    }

    companion object {
        // Extractors order is optimized according to
        // https://docs.google.com/document/d/1w2mKaWMxfz2Ei8-LdxqbPs1VLe_oudB-eryXXw9OvQQ.
        // The JPEG extractor appears after audio/video extractors because we expect audio/video input to
        // be more common.
        private val DEFAULT_EXTRACTOR_ORDER = intArrayOf(
            FileTypes.FLV,
            FileTypes.FLAC,
            FileTypes.WAV,
            FileTypes.MP4,
            FileTypes.AMR,
            FileTypes.PS,
            FileTypes.OGG,
            FileTypes.TS,
            FileTypes.MATROSKA,
            FileTypes.ADTS,
            FileTypes.AC3,
            FileTypes.AC4,
            FileTypes.MP3,  // The following extractors are not part of the optimized ordering, and were appended
            // without further analysis.
            FileTypes.AVI,
            FileTypes.MIDI,
            FileTypes.JPEG,
            FileTypes.PNG,
            FileTypes.WEBP,
            FileTypes.BMP,
            FileTypes.HEIF,
            FileTypes.AVIF
        )

        private val FLAC_EXTENSION_LOADER =
            ExtensionLoader(object : ExtensionLoader.ConstructorSupplier {
                override val constructor get() = flacExtractorConstructor
            })
        private val MIDI_EXTENSION_LOADER =
            ExtensionLoader(object : ExtensionLoader.ConstructorSupplier {
                override val constructor get() = midiExtractorConstructor
            })

        @get:Throws(
            ClassNotFoundException::class,
            NoSuchMethodException::class
        )
        private val midiExtractorConstructor: Constructor<out Extractor?>
            get() = Class.forName("androidx.media3.decoder.midi.MidiExtractor")
                .asSubclass<Extractor>(Extractor::class.java)
                .getConstructor()

        @get:Throws(
            ClassNotFoundException::class,
            NoSuchMethodException::class,
            InvocationTargetException::class,
            IllegalAccessException::class
        )
        private val flacExtractorConstructor: Constructor<out Extractor?>?
            get() {
                val isFlacNativeLibraryAvailable =
                    java.lang.Boolean.TRUE == Class.forName("androidx.media3.decoder.flac.FlacLibrary")
                        .getMethod("isAvailable")
                        .invoke( /* obj= */null)
                if (isFlacNativeLibraryAvailable) {
                    return Class.forName("androidx.media3.decoder.flac.FlacExtractor")
                        .asSubclass<Extractor>(Extractor::class.java)
                        .getConstructor(Int::class.javaPrimitiveType)
                }
                return null
            }
    }
}
