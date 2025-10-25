package com.lagradost.cloudstream3.utils

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.APP_STRING_SHARE
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import java.net.URLEncoder

const val PROGRAM_ID_LIST_KEY = "persistent_program_ids"

object TvChannelUtils {
    fun Context.saveProgramId(programId: Long) {
        val existing: List<Long> = getKey(PROGRAM_ID_LIST_KEY) ?: emptyList()
        val updated = (existing + programId).distinct()
        setKey(PROGRAM_ID_LIST_KEY, updated)
    }
    fun Context.getStoredProgramIds(): List<Long> {
        return getKey(PROGRAM_ID_LIST_KEY) ?: emptyList()
    }
    fun Context.removeProgramId(programId: Long) {
        val existing: List<Long> = getKey(PROGRAM_ID_LIST_KEY) ?: emptyList()
        val updated = existing.filter { it != programId }
        setKey(PROGRAM_ID_LIST_KEY, updated)
    }


    fun getChannelId(context: Context, channelName: String): Long? {
        return try {
            context.contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                arrayOf(
                    TvContractCompat.Channels._ID,
                    TvContractCompat.Channels.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(
                        cursor.getColumnIndexOrThrow(TvContractCompat.Channels._ID)
                    )
                    val name = cursor.getString(
                        cursor.getColumnIndexOrThrow(TvContractCompat.Channels.COLUMN_DISPLAY_NAME)
                    )
                    if (name == channelName) return id
                }
                null
            }
        } catch (e: Exception) {
            Log.e("TvChannelUtils", "Query failed: ${e.message}", e)
            null
        }
    }

    /** Insert programs into a channel */
    fun addPrograms(context: Context, channelId: Long, items: List<SearchResponse>) {
        for (item in items) {
            try {
                val nameBase64 = base64Encode(item.apiName.toByteArray(Charsets.UTF_8))
                val urlBase64 = base64Encode(item.url.toByteArray(Charsets.UTF_8))
                val csshareUri = "$APP_STRING_SHARE:$nameBase64?$urlBase64"
                val poster=item.posterUrl
                val builder = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setTitle(item.name)
                    .apply {
                        val scoreText = item.score?.toStringNull(0.1, 10, 1)?.let {
                            " - " + txt(R.string.rating_format, it).asString(context)
                        } ?: ""
                        setDescription("${item.apiName}$scoreText")
                    }
                    .setContentId(item.url)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                    .setIntentUri(Uri.parse(csshareUri))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)

                // Validate poster URL before setting
                if (!poster.isNullOrBlank() && poster.startsWith("http")) {
                    builder.setPosterArtUri(Uri.parse(poster))

                }
                val program = builder.build()

                val uri = context.contentResolver.insert(
                    TvContractCompat.PreviewPrograms.CONTENT_URI,
                    program.toContentValues()
                )

                if (uri != null) {
                    val programId = ContentUris.parseId(uri)
                    context.saveProgramId(programId)
                    Log.d("TvChannelUtils", "Inserted program ${item.name}, ID=$programId")
                } else {
                    Log.e("TvChannelUtils", "Insert failed for ${item.name}")
                }

            } catch (error: Exception) {
                Log.e("TvChannelUtils", "Error inserting ${item.name}: $error")
            }
        }
    }

    fun deleteStoredPrograms(context: Context) {
        val programIds = context.getStoredProgramIds()

        for (id in programIds) {
            val uri = ContentUris.withAppendedId(TvContractCompat.PreviewPrograms.CONTENT_URI, id)
            try {
                val rowsDeleted = context.contentResolver.delete(uri, null, null)
                if (rowsDeleted > 0) {
                    context.removeProgramId(id) // Remove from persistent list
                } else {
                    Log.w("ProgramDelete", "No program found for ID: $id")
                }
            } catch (e: Exception) {
                Log.e("ProgramDelete", "Failed to delete program ID: $id", e)
            }
        }

        Log.d("ProgramDelete", "Finished deleting stored programs")
    }

    fun createTvChannel(context: Context) {
        val componentName = ComponentName(context, MainActivity::class.java)
        val iconUri = Uri.parse("android.resource://${context.packageName}/mipmap/ic_launcher")
        val inputId = TvContractCompat.buildInputId(componentName)
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setAppLinkIconUri(iconUri)
            .setDisplayName(context.getString(R.string.app_name))
            .setAppLinkIntent(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("cloudstreamapp://open")
            })
            .setInputId(inputId)
            .build()

        val channelUri = context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            channel.toContentValues()
        )

        channelUri?.let {
            val channelId = ContentUris.parseId(it)
            TvContractCompat.requestChannelBrowsable(context, channelId)
            Log.d("TvChannelUtils", "Channel Created: $channelId")
        }
    }

}