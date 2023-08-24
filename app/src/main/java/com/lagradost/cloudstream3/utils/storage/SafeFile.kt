package com.lagradost.cloudstream3.utils.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.hippo.unifile.UniFile
import com.hippo.unifile.UniRandomAccessFile

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface SafeFile {
    companion object {
        fun fromUri(context: Context, uri: Uri): SafeFile? {
            return UniFileWrapper(UniFile.fromUri(context, uri) ?: return null)
        }

        fun fromFile(context: Context, file: File?): SafeFile? {
            if (file == null) return null
            // because UniFile sucks balls on Media we have to do this
            val absPath = file.absolutePath.removePrefix(File.separator)
            for (value in MediaFileContentType.values()) {
                val prefixes = listOf(value.toAbsolutePath(), value.toPath()).map { it.removePrefix(File.separator) }
                for (prefix in prefixes) {
                    if (!absPath.startsWith(prefix)) continue
                    return fromMedia(
                        context,
                        value,
                        absPath.removePrefix(prefix).ifBlank { File.separator }
                    )
                }
            }

            return UniFileWrapper(UniFile.fromFile(file) ?: return null)
        }

        fun fromAsset(
            context: Context,
            filename: String?
        ): SafeFile? {
            return UniFileWrapper(
                UniFile.fromAsset(context.assets, filename ?: return null) ?: return null
            )
        }

        fun fromResource(
            context: Context,
            id: Int
        ): SafeFile? {
            return UniFileWrapper(
                UniFile.fromResource(context, id) ?: return null
            )
        }

        fun fromMedia(
            context: Context,
            folderType: MediaFileContentType,
            path: String = File.separator,
            external: Boolean = true,
        ): SafeFile? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //fromUri(context, folderType.toUri(external))?.findFile(folderType.toPath())?.gotoDirectory(path)

                return MediaFile(
                    context = context,
                    folderType = folderType,
                    external = external,
                    absolutePath = path
                )
            } else {
                fromFile(
                    context,
                    File(
                        (Environment.getExternalStorageDirectory().absolutePath + File.separator +
                                folderType.toPath() + File.separator + folderType).replace(
                            File.separator + File.separator,
                            File.separator
                        )
                    )
                )
            }

        }
    }

    /*val uri: Uri? get() = getUri()
    val name: String? get() = getName()
    val type: String? get() = getType()
    val filePath: String? get() = getFilePath()
    val isFile: Boolean? get() = isFile()
    val isDirectory: Boolean? get() = isDirectory()
    val length: Long? get() = length()
    val canRead: Boolean get() = canRead()
    val canWrite: Boolean get() = canWrite()
    val lastModified: Long? get() = lastModified()*/

    @Throws(IOException::class)
    fun isFileOrThrow(): Boolean {
        return isFile() ?: throw IOException("Unable to get if file is a file or directory")
    }

    @Throws(IOException::class)
    fun lengthOrThrow(): Long {
        return length() ?: throw IOException("Unable to get file length")
    }

    @Throws(IOException::class)
    fun isDirectoryOrThrow(): Boolean {
        return isDirectory() ?: throw IOException("Unable to get if file is a directory")
    }

    @Throws(IOException::class)
    fun filePathOrThrow(): String {
        return filePath() ?: throw IOException("Unable to get file path")
    }

    @Throws(IOException::class)
    fun uriOrThrow(): Uri {
        return uri() ?: throw IOException("Unable to get uri")
    }

    @Throws(IOException::class)
    fun renameOrThrow(name: String?) {
        if (!renameTo(name)) {
            throw IOException("Unable to rename to $name")
        }
    }

    @Throws(IOException::class)
    fun openOutputStreamOrThrow(append: Boolean = false): OutputStream {
        return openOutputStream(append) ?: throw IOException("Unable to open output stream")
    }

    @Throws(IOException::class)
    fun openInputStreamOrThrow(): InputStream {
        return openInputStream() ?: throw IOException("Unable to open input stream")
    }

    @Throws(IOException::class)
    fun existsOrThrow(): Boolean {
        return exists() ?: throw IOException("Unable get if file exists")
    }

    @Throws(IOException::class)
    fun findFileOrThrow(displayName: String?, ignoreCase: Boolean = false): SafeFile {
        return findFile(displayName, ignoreCase) ?: throw IOException("Unable find file")
    }

    @Throws(IOException::class)
    fun gotoDirectoryOrThrow(
        directoryName: String?,
        createMissingDirectories: Boolean = true
    ): SafeFile {
        return gotoDirectory(directoryName, createMissingDirectories)
            ?: throw IOException("Unable to go to directory $directoryName")
    }

    @Throws(IOException::class)
    fun listFilesOrThrow(): List<SafeFile> {
        return listFiles() ?: throw IOException("Unable to get files")
    }


    @Throws(IOException::class)
    fun createFileOrThrow(displayName: String?): SafeFile {
        return createFile(displayName) ?: throw IOException("Unable to create file $displayName")
    }

    @Throws(IOException::class)
    fun createDirectoryOrThrow(directoryName: String?): SafeFile {
        return createDirectory(
            directoryName ?: throw IOException("Unable to create file with invalid name")
        )
            ?: throw IOException("Unable to create directory $directoryName")
    }

    @Throws(IOException::class)
    fun deleteOrThrow() {
        if (!delete()) {
            throw IOException("Unable to delete file")
        }
    }

    /** file.gotoDirectory("a/b/c") -> "file/a/b/c/" where a null or blank directoryName
     * returns itself. createMissingDirectories specifies if the dirs should be created
     * when travelling or break at a dir not found */
    fun gotoDirectory(
        directoryName: String?,
        createMissingDirectories: Boolean = true
    ): SafeFile? {
        if (directoryName == null) return this

        return directoryName.split(File.separatorChar).filter { it.isNotBlank() }
            .fold(this) { file: SafeFile?, directory ->
                // as MediaFile does not actually create a directory we can do this
                if (createMissingDirectories || this is MediaFile) {
                    file?.createDirectory(directory)
                } else {
                    val next = file?.findFile(directory)

                    // we require the file to be a directory
                    if (next?.isDirectory() != true) {
                        null
                    } else {
                        next
                    }
                }
            }
    }


    fun createFile(displayName: String?): SafeFile?
    fun createDirectory(directoryName: String?): SafeFile?
    fun uri(): Uri?
    fun name(): String?
    fun type(): String?
    fun filePath(): String?
    fun isDirectory(): Boolean?
    fun isFile(): Boolean?
    fun lastModified(): Long?
    fun length(): Long?
    fun canRead(): Boolean
    fun canWrite(): Boolean
    fun delete(): Boolean
    fun exists(): Boolean?
    fun listFiles(): List<SafeFile>?

    // fun listFiles(filter: FilenameFilter?): Array<File>?
    fun findFile(displayName: String?, ignoreCase: Boolean = false): SafeFile?

    fun renameTo(name: String?): Boolean

    /** Open a stream on to the content associated with the file */
    fun openOutputStream(append: Boolean = false): OutputStream?

    /** Open a stream on to the content associated with the file */
    fun openInputStream(): InputStream?

    /** Get a random access stuff of the UniFile, "r" or "rw" */
    fun createRandomAccessFile(mode: String?): UniRandomAccessFile?
}