package com.lagradost.cloudstream3

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import io.github.classgraph.ClassGraph
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import org.instancio.Instancio
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class SerializationClassTester {
    // Same as app, or using app reference
    val jacksonMapper = mapper
    val kotlinxMapper = json

    @Test
    fun isIdenticalSerialization() {
        val serializableClasses = findSerializableClasses("com.lagradost")
        println("Number of serializable classes: ${serializableClasses.size}")

        serializableClasses.forEach { kClass ->
            val instance = Instancio.create(kClass.java)

            val jacksonJson = jacksonMapper.writeValueAsString(instance)
            val kotlinxJson = serializeWithKotlinx(kClass, instance)

            assertEquals(
                jacksonJson,
                kotlinxJson,
                """
                    Serialization mismatch for:
                    ${kClass.qualifiedName}

                    Jackson:
                    $jacksonJson

                    Kotlinx:
                    $kotlinxJson
                    
                    """.trimIndent()
            )
            println("Identical serialization for: ${kClass.jvmName}")
        }
    }

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    @Test
    fun isIdenticalDeserialization() {
        val serializableClasses = findSerializableClasses("com.lagradost")
        println("Number of serializable classes: ${serializableClasses.size}")

        serializableClasses.forEach { kClass ->
            val instance = Instancio.create(kClass.java)
            // Convert to JSON to get example JSON object
            // We prefer jackson here because the app may have many jackson JSON strings in local storage
            val originalJson = jacksonMapper.writeValueAsString(instance)

            // Create an object from the JSON using kotlinx
            val serializer =
                kClass.serializerOrNull() ?: kotlinxMapper.serializersModule.getContextual(kClass)
            assertNotNull(serializer, "The class: ${kClass.jvmName} must be serializable!")
            val kotlinxDecoded = kotlinxMapper.decodeFromString(serializer, originalJson)

            // Create an object from the JSON using jackson
            val mapperDecoded = jacksonMapper.readValue(originalJson, kClass.java)


            // Deep inspect both object using the mapper toJson function.
            // This deep equality check can be performed using other methods, but this just works.
            val jacksonJson = mapperDecoded.toJson()
            val kotlinxJson = kotlinxDecoded.toJson()

            assertEquals(
                jacksonJson,
                kotlinxJson,
                """
                    Serialization mismatch for:
                    ${kClass.qualifiedName}

                    Jackson:
                    $jacksonJson

                    Kotlinx:
                    $kotlinxJson
                    
                    """.trimIndent()
            )
            println("Identical deserialization for: ${kClass.jvmName}")
        }
    }

    private fun findSerializableClasses(packageName: String): List<KClass<*>> {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        return ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .overrideClassLoaders(context.classLoader)
            .acceptPackages(packageName)
            .scan()
            .getClassesWithAnnotation(Serializable::class.java.name)
            .mapNotNull { runCatching { Class.forName(it.name, false, context.classLoader).kotlin }.getOrNull() }
    }

    @OptIn(InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun serializeWithKotlinx(
        kClass: KClass<*>,
        value: Any
    ): String {
        val serializer = kClass.serializer() as KSerializer<Any>
        return kotlinxMapper.encodeToString(serializer, value)
    }
}
