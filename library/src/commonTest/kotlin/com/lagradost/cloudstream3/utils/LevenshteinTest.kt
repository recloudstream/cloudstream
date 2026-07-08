package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.Prerelease
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(Prerelease::class)
class LevenshteinTest {

    @Test
    fun ratioOfIdenticalStringsIsHundred() {
        assertEquals(100, Levenshtein.ratio("kitten", "kitten"))
    }

    @Test
    fun ratioOfTwoEmptyStringsIsHundred() {
        assertEquals(100, Levenshtein.ratio("", ""))
    }

    @Test
    fun ratioWithOneEmptyStringIsZero() {
        assertEquals(0, Levenshtein.ratio("abc", ""))
        assertEquals(0, Levenshtein.ratio("", "abc"))
    }

    @Test
    fun ratioOfStringsWithNoCommonCharactersIsZero() {
        // No characters in common -> edit distance equals the combined length.
        assertEquals(0, Levenshtein.ratio("abc", "xyz"))
    }

    @Test
    fun ratioIsSymmetric() {
        assertEquals(
            Levenshtein.ratio("kitten", "sitting"),
            Levenshtein.ratio("sitting", "kitten")
        )
    }

    @Test
    fun ratioIsBoundedBetweenZeroAndHundred() {
        val score = Levenshtein.ratio("similar text", "similor test")
        assertTrue(score in 0..100)
    }

    @Test
    fun ratioAppliesProcessor() {
        // Without the processor these differ only by case.
        assertEquals(100, Levenshtein.ratio("ABC", "abc") { it.lowercase() })
    }

    @Test
    fun partialRatioOfIdenticalStringsIsHundred() {
        assertEquals(100, Levenshtein.partialRatio("hello", "hello"))
    }

    @Test
    fun partialRatioFindsFullContainment() {
        // The shorter string is fully contained in the longer one.
        assertEquals(100, Levenshtein.partialRatio("bc", "abcd"))
    }

    @Test
    fun partialRatioOfTwoEmptyStringsIsHundred() {
        assertEquals(100, Levenshtein.partialRatio("", ""))
    }

    @Test
    fun partialRatioWithOneEmptyStringIsZero() {
        assertEquals(0, Levenshtein.partialRatio("abc", ""))
        assertEquals(0, Levenshtein.partialRatio("", "abc"))
    }

    @Test
    fun partialRatioIsAtLeastRatioForContainedSubstring() {
        val ratio = Levenshtein.ratio("bc", "abcd")
        val partial = Levenshtein.partialRatio("bc", "abcd")
        assertTrue(partial >= ratio)
    }
}
