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
        val os = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
        outInfo.totalMem = os?.totalMemorySize ?: Runtime.getRuntime().maxMemory()
        outInfo.availMem = os?.freeMemorySize ?: Runtime.getRuntime().freeMemory()
        outInfo.threshold = outInfo.totalMem / 20
        outInfo.lowMemory = outInfo.availMem < outInfo.threshold
    }

    open fun isLowRamDevice(): Boolean = false
}
