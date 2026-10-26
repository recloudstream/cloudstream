package android.app

import java.lang.management.ManagementFactory

/** Only the memory query, extensions use it to scale how many requests they run in parallel */
open class ActivityManager {
    open class MemoryInfo {
        @JvmField var availMem: Long = 0
        @JvmField var totalMem: Long = 0
        @JvmField var threshold: Long = 0
        @JvmField var lowMemory: Boolean = false
    }

    open fun getMemoryInfo(outInfo: MemoryInfo) {
        // com.sun.management lives in the jdk.management module, fall back to the JVM's own numbers without it
        val (total, free) = try {
            val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
            os.totalMemorySize to os.freeMemorySize
        } catch (_: Throwable) {
            Runtime.getRuntime().maxMemory() to Runtime.getRuntime().freeMemory()
        }
        outInfo.totalMem = total
        outInfo.availMem = free
        outInfo.threshold = outInfo.totalMem / 20
        outInfo.lowMemory = outInfo.availMem < outInfo.threshold
    }

    open fun isLowRamDevice(): Boolean = false
}
