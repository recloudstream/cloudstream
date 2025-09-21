package com.lagradost.cloudstream3.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.biometric.AuthenticationResult
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.lagradost.cloudstream3.SearchResponse
import android.content.ContentUris
import com.lagradost.cloudstream3.utils.DataStore.getStoredProgramIds
import com.lagradost.cloudstream3.utils.DataStore.removeProgramId
import com.lagradost.cloudstream3.utils.DataStore.saveProgramId
import android.content.Intent
import android.os.Parcelable
import com.google.gson.Gson
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.ui.home.HomeFragment
import kotlinx.parcelize.Parcelize



object TvChannelUtils {

    /** Get channel ID by name */
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

    /** Insert one program into a channel */
    fun addProgram(context: Context, channelId: Long, item: HomeFragment.SearchResponseImpl) {
         try {
             val intent = Intent(context, MainActivity::class.java).apply {
                 action = Intent.ACTION_VIEW
                 putExtra("OPEN_PROGRAM_DETAIL", true)
                 val json = Gson().toJson(item)
                 putExtra("PROGRAM_CARD_JSON", json)        // cardd must be Parcelable
                 flags = Intent.FLAG_ACTIVITY_NEW_TASK
             }

             val program = PreviewProgram.Builder()
                 .setChannelId(channelId)
                 .setTitle(item.name)
                 .setDescription(item.apiName)
                 .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                 .setPosterArtUri(Uri.parse(item.posterUrl ?: ""))
                 .setIntentUri(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
                 .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                 .build()

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





             Log.d("TvChannelUtils", "Inserted program ${item.name}, uri=$uri")
         }catch (error: Exception){
             Log.e("Program error"," some error${error}")
         }
    }

    /** Insert multiple programs */
    fun addPrograms(context: Context, channelId: Long, items: List<SearchResponse>) {
        items.forEach { item ->
//            addProgram(context, channelId, item)
        }
    }


    fun deleteAllProgramsForChannel(context: Context, channelId: Long) {
        val projection = arrayOf(
            TvContractCompat.PreviewPrograms._ID,
            TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID
        )

        val cursor = context.contentResolver.query(
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            projection,
            null, // No selection allowed
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(TvContractCompat.PreviewPrograms._ID))
                val cid = it.getLong(it.getColumnIndexOrThrow(TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID))

                if (cid == channelId) {
                    val uri = ContentUris.withAppendedId(TvContractCompat.PreviewPrograms.CONTENT_URI, id)
                    try {
                        context.contentResolver.delete(uri, null, null)
                        Log.d("ProgramDelete", "Deleted program ID: $id")
                    } catch (e: Exception) {
                        Log.e("ProgramDelete", "Failed to delete program ID: $id", e)
                    }
                }
            }
        }
        Log.d("delete","program deleted")
    }

    fun deleteStoredPrograms(context: Context) {
        val programIds = context.getStoredProgramIds()

        for (id in programIds) {
            val uri = ContentUris.withAppendedId(TvContractCompat.PreviewPrograms.CONTENT_URI, id)
            try {
                val rowsDeleted = context.contentResolver.delete(uri, null, null)
                if (rowsDeleted > 0) {
                    Log.d("ProgramDelete", "Deleted program ID: $id")
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
}
