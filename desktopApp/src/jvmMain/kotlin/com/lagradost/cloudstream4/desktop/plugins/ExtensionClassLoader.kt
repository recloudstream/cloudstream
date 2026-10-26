package com.lagradost.cloudstream4.desktop.plugins

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile

/**
 * Loads one extension jar. Extensions built for Android reference Android and app classes that do
 * not exist on desktop, mostly in settings screens. Instead of failing with NoClassDefFoundError as
 * soon as such a class is touched, an empty placeholder class is generated for it. Calling into a
 * placeholder still fails, but only in the code path that really needs Android.
 */
class ExtensionClassLoader(private val jar: File, parent: ClassLoader) :
    URLClassLoader(arrayOf(jar.toURI().toURL()), parent) {

    private val stubbedPackages = listOf("android.", "androidx.", "com.google.android.", "com.lagradost.cloudstream3.")

    /** Classes the extension uses as interfaces, a placeholder must then be an interface too */
    private val interfaces: Set<String> by lazy { findInterfaces() }

    private val _stubs = mutableListOf<String>()
    val stubs: List<String> get() = synchronized(_stubs) { _stubs.toList() }

    override fun findClass(name: String): Class<*> {
        try {
            return super.findClass(name)
        } catch (e: ClassNotFoundException) {
            if (stubbedPackages.none { name.startsWith(it) }) throw e
            val bytes = generateStub(name.replace('.', '/'))
            synchronized(_stubs) { _stubs += name }
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    private fun generateStub(internalName: String): ByteArray {
        val writer = ClassWriter(0)
        if (internalName in interfaces) {
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT or Opcodes.ACC_INTERFACE, internalName, null, "java/lang/Object", null)
        } else {
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
            writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
                visitCode()
                visitVarInsn(Opcodes.ALOAD, 0)
                visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(1, 1)
                visitEnd()
            }
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun findInterfaces(): Set<String> {
        val result = HashSet<String>()
        ZipFile(jar).use { zip ->
            for (entry in zip.entries()) {
                if (!entry.name.endsWith(".class")) continue
                val reader = ClassReader(zip.getInputStream(entry).use { it.readBytes() })
                reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(
                        version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?,
                    ) {
                        interfaces?.let { result.addAll(it) }
                    }

                    override fun visitMethod(
                        access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?,
                    ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String?, descriptor: String?, isInterface: Boolean) {
                            if (isInterface) result += owner
                        }
                    }
                }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            }
        }
        return result
    }
}
