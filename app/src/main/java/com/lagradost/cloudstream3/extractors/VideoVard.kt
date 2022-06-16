package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import kotlinx.coroutines.delay
import java.math.BigInteger

class VideovardSX : WcoStream() {
    override var mainUrl = "https://videovard.sx"
}

class VideoVard : ExtractorApi() {
    override var name = "Videovard" // Cause works for animekisa and wco
    override var mainUrl = "https://videovard.to"
    override val requiresReferer = false

    //The following code was extracted from https://github.com/saikou-app/saikou/blob/main/app/src/main/java/ani/saikou/parsers/anime/extractors/VideoVard.kt
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val id = url.substringAfter("e/").substringBefore("/")
        val sources = mutableListOf<ExtractorLink>()
        val hash = app.get("$mainUrl/api/make/download/$id").parsed<HashResponse>()
        delay(11_000)
        val resm3u8 = app.post(
            "$mainUrl/api/player/setup",
            mapOf("Referer" to "$mainUrl/"),
            data = mapOf(
                "cmd" to "get_stream",
                "file_code" to id,
                "hash" to hash.hash!!
            )
        ).parsed<SetupResponse>()
        val m3u8 = decode(resm3u8.src!!, resm3u8.seed)
        sources.addAll(
            generateM3u8(
                name,
                m3u8,
                mainUrl,
                headers = mapOf("Referer" to mainUrl)
            )
        )
        return sources
    }

    companion object {
        private val big0 = 0.toBigInteger()
        private val big3 = 3.toBigInteger()
        private val big4 = 4.toBigInteger()
        private val big15 = 15.toBigInteger()
        private val big16 = 16.toBigInteger()
        private val big255 = 255.toBigInteger()

        private fun decode(dataFile: String, seed: String): String {
            val dataSeed = replace(seed)
            val newDataSeed = binaryDigest(dataSeed)
            val newDataFile = bytes2blocks(ascii2bytes(dataFile))
            var list = listOf(1633837924, 1650680933).map { it.toBigInteger() }
            val xorList = mutableListOf<BigInteger>()
            for (i in newDataFile.indices step 2) {
                val temp = newDataFile.slice(i..i + 1)
                xorList += xorBlocks(list, tearDecode(temp, newDataSeed))
                list = temp
            }

            val result = replace(unPad(blocks2bytes(xorList)).map { it.toInt().toChar() }.joinToString(""))
            return padLastChars(result)
        }

        private fun binaryDigest(input: String): List<BigInteger> {
            val keys = listOf(1633837924, 1650680933, 1667523942, 1684366951).map { it.toBigInteger() }
            var list1 = keys.slice(0..1)
            var list2 = list1
            val blocks = bytes2blocks(digestPad(input))

            for (i in blocks.indices step 4) {
                list1 = tearCode(xorBlocks(blocks.slice(i..i + 1), list1), keys).toMutableList()
                list2 = tearCode(xorBlocks(blocks.slice(i + 2..i + 3), list2), keys).toMutableList()

                val temp = list1[0]
                list1[0] = list1[1]
                list1[1] = list2[0]
                list2[0] = list2[1]
                list2[1] = temp
            }

            return listOf(list1[0], list1[1], list2[0], list2[1])
        }

        private fun tearDecode(a90: List<BigInteger>, a91: List<BigInteger>): MutableList<BigInteger> {
            var (a95, a96) = a90

            var a97 = (-957401312).toBigInteger()
            for (_i in 0 until 32) {
                a96 -= ((((a95 shl 4) xor rShift(a95, 5)) + a95) xor (a97 + a91[rShift(a97, 11).and(3.toBigInteger()).toInt()]))
                a97 += 1640531527.toBigInteger()
                a95 -= ((((a96 shl 4) xor rShift(a96, 5)) + a96) xor (a97 + a91[a97.and(3.toBigInteger()).toInt()]))

            }

            return mutableListOf(a95, a96)
        }

        private fun digestPad(string: String): List<BigInteger> {
            val empList = mutableListOf<BigInteger>()
            val length = string.length
            val extra = big15 - (length.toBigInteger() % big16)
            empList.add(extra)
            for (i in 0 until length) {
                empList.add(string[i].code.toBigInteger())
            }
            for (i in 0 until extra.toInt()) {
                empList.add(big0)
            }

            return empList
        }

        private fun bytes2blocks(a22: List<BigInteger>): List<BigInteger> {
            val empList = mutableListOf<BigInteger>()
            val length = a22.size
            var listIndex = 0

            for (i in 0 until length) {
                val subIndex = i % 4
                val shiftedByte = a22[i] shl (3 - subIndex) * 8

                if (subIndex == 0) {
                    empList.add(shiftedByte)
                } else {
                    empList[listIndex] = empList[listIndex] or shiftedByte
                }

                if (subIndex == 3) listIndex += 1
            }

            return empList
        }

        private fun blocks2bytes(inp: List<BigInteger>): List<BigInteger> {
            val tempList = mutableListOf<BigInteger>()
            inp.indices.forEach { i ->
                tempList += (big255 and rShift(inp[i], 24))
                tempList += (big255 and rShift(inp[i], 16))
                tempList += (big255 and rShift(inp[i], 8))
                tempList += (big255 and inp[i])
            }
            return tempList
        }

        private fun unPad(a46: List<BigInteger>): List<BigInteger> {
            val evenOdd = a46[0].toInt().mod(2)
            return (1 until (a46.size - evenOdd)).map {
                a46[it]
            }
        }

        private fun xorBlocks(a76: List<BigInteger>, a77: List<BigInteger>): List<BigInteger> {
            return listOf(a76[0] xor a77[0], a76[1] xor a77[1])
        }

        private fun rShift(input: BigInteger, by: Int): BigInteger {
            return (input.mod(4294967296.toBigInteger()) shr by)
        }

        private fun tearCode(list1: List<BigInteger>, list2: List<BigInteger>): MutableList<BigInteger> {
            var a1 = list1[0]
            var a2 = list1[1]
            var temp = big0

            for (_i in 0 until 32) {
                a1 += (a2 shl 4 xor rShift(a2, 5)) + a2 xor temp + list2[(temp and big3).toInt()]
                temp -= 1640531527.toBigInteger()
                a2 += (a1 shl 4 xor rShift(a1, 5)) + a1 xor temp + list2[(rShift(temp, 11) and big3).toInt()]
            }
            return mutableListOf(a1, a2)
        }

        private fun ascii2bytes(input: String): List<BigInteger> {
            val abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
            val abcMap = abc.mapIndexed { i, c -> c to i.toBigInteger() }.toMap()
            var index = -1
            val length = input.length
            var listIndex = 0
            val bytes = mutableListOf<BigInteger>()

            while (true) {
                for (i in input) {
                    if (abc.contains(i)) {
                        index++
                        break
                    }
                }

                bytes.add((abcMap[input.getOrNull(index)?:return bytes]!! * big4))

                while (true) {
                    index++
                    if (abc.contains(input[index])) {
                        break
                    }
                }

                var temp = abcMap[input[index]]!!

                bytes[listIndex] = bytes[listIndex] or rShift(temp, 4)
                listIndex++
                temp = (big15.and(temp))

                if ((temp == big0) && (index == (length - 1))) return bytes

                bytes.add((temp * big4 * big4))

                while (true) {
                    index++
                    if (index >= length) return bytes
                    if (abc.contains(input[index])) break
                }

                temp = abcMap[input[index]]!!
                bytes[listIndex] = bytes[listIndex] or rShift(temp, 2)
                listIndex++
                temp = (big3 and temp)
                if ((temp == big0) && (index == (length - 1))) {
                    return bytes
                }
                bytes.add((temp shl 6))
                for (i in input) {
                    index++
                    if (abc.contains(input[index])) {
                        break
                    }
                }
                bytes[listIndex] = bytes[listIndex] or abcMap[input[index]]!!
                listIndex++
            }
        }

        private fun replace(a: String): String {
            val map = mapOf(
                '0' to '5',
                '1' to '6',
                '2' to '7',
                '5' to '0',
                '6' to '1',
                '7' to '2'
            )
            var b = ""
            a.forEach {
                b += if (map.containsKey(it)) map[it] else it
            }
            return b
        }

        private fun padLastChars(input:String):String{
            return if(input.reversed()[3].isDigit()) input
            else input.dropLast(4)
        }

        private data class HashResponse(
            val hash: String? = null,
            val version:String? = null
        )

        private data class SetupResponse(
            val seed: String,
            val src: String?=null,
            val link:String?=null
        )
    }
}