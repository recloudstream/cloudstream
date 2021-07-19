package com.lagradost.cloudstream3.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

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
}