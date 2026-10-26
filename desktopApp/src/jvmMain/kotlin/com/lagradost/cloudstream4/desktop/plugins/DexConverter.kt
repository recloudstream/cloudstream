package com.lagradost.cloudstream4.desktop.plugins

import com.googlecode.d2j.dex.Dex2jar
import com.lagradost.cloudstream4.desktop.data.AppDirs
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Turns an Android extension (.cs3, a zip with classes.dex and manifest.json) into a JVM jar.
 */
object DexConverter {
    /** Bump when the conversion changes, older conversions are then repaired on startup */
    const val VERSION = 1

    /** Applies the bytecode fixes of the current [VERSION] to a jar converted by an older version */
    fun repairJar(jar: File) {
        val temp = File.createTempFile("extension", ".jar", AppDirs.cacheDir)
        try {
            ZipOutputStream(temp.outputStream()).use { out ->
                ZipFile(jar).use { zip ->
                    for (entry in zip.entries()) {
                        if (entry.isDirectory) continue
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        out.putNextEntry(ZipEntry(entry.name))
                        out.write(if (entry.name.endsWith(".class")) fixLocalClasses(bytes) else bytes)
                        out.closeEntry()
                    }
                }
            }
            Files.move(temp.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    /**
     * dex2jar turns local classes (classes declared inside a function) into member classes of the
     * outer class and drops their EnclosingMethod attribute. The JVM is fine with that, but Kotlin
     * reflection then looks for `Outer.Name`, which does not exist, so Jackson fails to parse into them.
     * A member class is always named `Outer$Name`, anything else is turned back into a local class.
     */
    private fun fixLocalClasses(bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(0)
        var changed = false
        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            private var className = ""
            private var hasOuterClass = false
            private var localOuter: String? = null

            override fun visit(
                version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?,
            ) {
                className = name
                super.visit(version, access, name, signature, superName, interfaces)
            }

            override fun visitOuterClass(owner: String?, name: String?, descriptor: String?) {
                hasOuterClass = true
                super.visitOuterClass(owner, name, descriptor)
            }

            override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
                if (outerName != null && innerName != null && name != "$outerName$$innerName") {
                    changed = true
                    if (name == className) localOuter = outerName
                    super.visitInnerClass(name, null, innerName, access)
                } else {
                    super.visitInnerClass(name, outerName, innerName, access)
                }
            }

            override fun visitEnd() {
                // EnclosingMethod must come before InnerClasses in ASM's visit order, but ClassWriter
                // does not care about the order of attributes, so adding it here is fine
                localOuter?.takeIf { !hasOuterClass }?.let { super.visitOuterClass(it, null, null) }
                super.visitEnd()
            }
        }, 0)
        return if (changed) writer.toByteArray() else bytes
    }

    fun cs3ToJar(cs3: File): File {
        val classesDir = Files.createTempDirectory(AppDirs.cacheDir.toPath(), "dex2jar").toFile()
        val jar = File.createTempFile("extension", ".jar", AppDirs.cacheDir)
        try {
            ZipOutputStream(jar.outputStream()).use { out ->
                ZipFile(cs3).use { zip ->
                    for (entry in zip.entries()) {
                        if (entry.isDirectory) continue
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        if (entry.name.matches(Regex("classes\\d*\\.dex"))) {
                            Dex2jar.from(bytes)
                                .reUseReg(false)
                                .topoLogicalSort()
                                .skipDebug(false)
                                .optimizeSynchronized(true)
                                .to(classesDir.toPath())
                        } else if (entry.name == "manifest.json" || !entry.name.startsWith("res/")) {
                            // Keep the manifest and any plain java resources, Android resources are useless here
                            out.putNextEntry(ZipEntry(entry.name))
                            out.write(bytes)
                            out.closeEntry()
                        }
                    }
                }

                classesDir.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.forEach { file ->
                    out.putNextEntry(ZipEntry(file.relativeTo(classesDir).invariantSeparatorsPath))
                    out.write(fixLocalClasses(file.readBytes()))
                    out.closeEntry()
                }
            }
            return jar
        } catch (t: Throwable) {
            jar.delete()
            throw t
        } finally {
            classesDir.deleteRecursively()
        }
    }
}
