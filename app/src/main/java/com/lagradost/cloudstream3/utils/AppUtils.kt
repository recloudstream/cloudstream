package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.wrappers.Wrappers
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.utils.FillerEpisodeCheck.toClassDir
import com.lagradost.cloudstream3.utils.JsUnpacker.Companion.load
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import okhttp3.Cache
import java.io.*
import java.net.URL
import java.net.URLDecoder

object AppUtils {
    fun getVideoContentUri(context: Context, videoFilePath: String): Uri? {
        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID),
            MediaStore.Video.Media.DATA + "=? ", arrayOf(videoFilePath), null
        )
        return if (cursor != null && cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))
            cursor.close()
            Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "" + id)
        } else {
            val values = ContentValues()
            values.put(MediaStore.Video.Media.DATA, videoFilePath)
            context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            )
        }
    }

    fun Context.openBrowser(url: String) {
        try {
            val components = arrayOf(ComponentName(applicationContext, MainActivity::class.java))
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                startActivity(
                    Intent.createChooser(intent, null)
                        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, components)
                )
            else
                startActivity(intent)
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun splitQuery(url: URL): Map<String, String> {
        val queryPairs: MutableMap<String, String> = LinkedHashMap()
        val query: String = url.query
        val pairs = query.split("&").toTypedArray()
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            queryPairs[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] =
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }
        return queryPairs
    }

    /** Any object as json string */
    fun Any.toJson(): String {
        return mapper.writeValueAsString(this)
    }

    /**| S1:E2 Hello World
     * | Episode 2. Hello world
     * | Hello World
     * | Season 1 - Episode 2
     * | Episode 2
     * **/
    fun Context.getNameFull(name: String?, episode: Int?, season: Int?): String {
        val rEpisode = if (episode == 0) null else episode
        val rSeason = if (season == 0) null else season

        val seasonName = getString(R.string.season)
        val episodeName = getString(R.string.episode)
        val seasonNameShort = getString(R.string.season_short)
        val episodeNameShort = getString(R.string.episode_short)

        if (name != null) {
            return if (rEpisode != null && rSeason != null) {
                "$seasonNameShort${rSeason}:$episodeNameShort${rEpisode} $name"
            } else if (rEpisode != null) {
                "$episodeName $rEpisode. $name"
            } else {
                name
            }
        } else {
            if (rEpisode != null && rSeason != null) {
                return "$seasonName $rSeason - $episodeName $rEpisode"
            } else if (rSeason == null) {
                return "$episodeName $rEpisode"
            }
        }
        return ""
    }

    fun Activity?.loadCache() {
        try {
            cacheClass("android.net.NetworkCapabilities".load())
        } catch (_: Exception) {
        }
    }

    //private val viewModel: ResultViewModel by activityViewModels()

    fun AppCompatActivity.loadResult(
        url: String,
        apiName: String,
        startAction: Int = 0,
        startValue: Int = 0
    ) {
        this.runOnUiThread {
            // viewModelStore.clear()
            this.navigate(
                R.id.global_to_navigation_results,
                ResultFragment.newInstance(url, apiName, startAction, startValue)
            )
        }
    }

    fun Activity?.loadSearchResult(
        card: SearchResponse,
        startAction: Int = 0,
        startValue: Int = 0
    ) {
        (this as AppCompatActivity?)?.loadResult(card.url, card.apiName, startAction, startValue)
    }

    fun Activity.requestLocalAudioFocus(focusRequest: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(focusRequest)
        } else {
            val audioManager: AudioManager =
                getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private var currentAudioFocusRequest: AudioFocusRequest? = null
    private var currentAudioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    var onAudioFocusEvent = Event<Boolean>()

    private fun getAudioListener(): AudioManager.OnAudioFocusChangeListener? {
        if (currentAudioFocusChangeListener != null) return currentAudioFocusChangeListener
        currentAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
            onAudioFocusEvent.invoke(
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> false
                    else -> true
                }
            )
        }
        return currentAudioFocusChangeListener
    }

    fun Context.isCastApiAvailable(): Boolean {
        val isCastApiAvailable =
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(applicationContext) == ConnectionResult.SUCCESS
        try {
            applicationContext?.let { CastContext.getSharedInstance(it) }
        } catch (e: Exception) {
            println(e)
            // track non-fatal
            return false
        }
        return isCastApiAvailable
    }

    fun Context.isConnectedToChromecast(): Boolean {
        if (isCastApiAvailable()) {
            val castContext = CastContext.getSharedInstance(this)
            if (castContext.castState == CastState.CONNECTED) {
                return true
            }
        }
        return false
    }

    // Copied from https://github.com/videolan/vlc-android/blob/master/application/vlc-android/src/org/videolan/vlc/util/FileUtils.kt
    fun Context.getUri(data: Uri?): Uri? {
        var uri = data
        val ctx = this
        if (data != null && data.scheme == "content") {
            // Mail-based apps - download the stream to a temporary file and play it
            if ("com.fsck.k9.attachmentprovider" == data.host || "gmail-ls" == data.host) {
                var inputStream: InputStream? = null
                var os: OutputStream? = null
                var cursor: Cursor? = null
                try {
                    cursor = ctx.contentResolver.query(
                        data,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
                    )
                    if (cursor != null && cursor.moveToFirst()) {
                        val filename = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))
                            .replace("/", "")
                        inputStream = ctx.contentResolver.openInputStream(data)
                        if (inputStream == null) return data
                        os = FileOutputStream(Environment.getExternalStorageDirectory().path + "/Download/" + filename)
                        val buffer = ByteArray(1024)
                        var bytesRead = inputStream.read(buffer)
                        while (bytesRead >= 0) {
                            os.write(buffer, 0, bytesRead)
                            bytesRead = inputStream.read(buffer)
                        }
                        uri =
                            Uri.fromFile(File(Environment.getExternalStorageDirectory().path + "/Download/" + filename))
                    }
                } catch (e: Exception) {
                    return null
                } finally {
                    inputStream?.close()
                    os?.close()
                    cursor?.close()
                }
            } else if (data.authority == "media") {
                uri = this.contentResolver.query(
                    data,
                    arrayOf(MediaStore.Video.Media.DATA), null, null, null
                )?.use {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    if (it.moveToFirst()) Uri.fromFile(File(it.getString(columnIndex))) ?: data else data
                }
                //uri = MediaUtils.getContentMediaUri(data)
                /*} else if (data.authority == ctx.getString(R.string.tv_provider_authority)) {
                    println("TV AUTHORITY")
                    //val medialibrary = Medialibrary.getInstance()
                    //val media = medialibrary.getMedia(data.lastPathSegment!!.toLong())
                    uri = null//media.uri*/
            } else {
                val inputPFD: ParcelFileDescriptor?
                try {
                    inputPFD = ctx.contentResolver.openFileDescriptor(data, "r")
                    if (inputPFD == null) return data
                    uri = Uri.parse("fd://" + inputPFD.fd)
                    //                    Cursor returnCursor =
                    //                            getContentResolver().query(data, null, null, null, null);
                    //                    if (returnCursor != null) {
                    //                        if (returnCursor.getCount() > 0) {
                    //                            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    //                            if (nameIndex > -1) {
                    //                                returnCursor.moveToFirst();
                    //                                title = returnCursor.getString(nameIndex);
                    //                            }
                    //                        }
                    //                        returnCursor.close();
                    //                    }
                } catch (e: FileNotFoundException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: IllegalArgumentException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: IllegalStateException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: NullPointerException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: SecurityException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                }
            }// Media or MMS URI
        }
        return uri
    }

    fun Context.isUsingMobileData(): Boolean {
        val conManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = conManager.allNetworks
        return networkInfo.any {
            conManager.getNetworkCapabilities(it)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }
    }

    private fun Activity?.cacheClass(clazz: String?) {
        clazz?.let { c ->
            this?.cacheDir?.let {
                Cache(
                    directory = File(it, c.toClassDir()),
                    maxSize = 20L * 1024L * 1024L // 20 MiB
                )
            }
        }
    }

    fun Context.isAppInstalled(uri: String): Boolean {
        val pm = Wrappers.packageManager(this)
        var appInstalled = false
        appInstalled = try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        return appInstalled
    }

    fun getFocusRequest(): AudioFocusRequest? {
        if (currentAudioFocusRequest != null) return currentAudioFocusRequest
        currentAudioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                getAudioListener()?.let {
                    setOnAudioFocusChangeListener(it)
                }
                build()
            }
        } else {
            null
        }
        return currentAudioFocusRequest
    }
}