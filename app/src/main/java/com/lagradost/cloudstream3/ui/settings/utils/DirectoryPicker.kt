package com.lagradost.cloudstream3.ui.settings.utils

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.safefile.SafeFile

fun Fragment.getChooseFolderLauncher(dirSelected: (uri: Uri?, path: String?) -> Unit) =
    registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        // It lies, it can be null if file manager quits.
        if (uri == null) {
            dirSelected(null, null)
            return@registerForActivityResult
        }
        val context = context ?: CloudStreamApp.context ?: return@registerForActivityResult
        // RW perms for the path
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // Some non-system SAF providers grant temporary access but do not expose a
            // persistable grant. Keep the returned URI usable for this session instead
            // of crashing while delivering the picker result.
            logError(e)
        }

        val filePath = SafeFile.fromUri(context, uri)?.filePath()
        println("Selected URI path: $uri - Full path: $filePath")

        // store the actual URI instead of the path due to permissions.
        // filePath should only be used for cosmetic purposes.
        dirSelected(uri, filePath)
    }
