package com.lagradost.cloudstream3

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeDateTest {

    private val api = object : MainAPI() {
        override var name = "Test"
        override var mainUrl = "https://test.com"
    }

    private fun episode() = api.newEpisode("")

    @Test
    fun addDateDefaultFormatParsesIsoDate() {
        val ep = episode()
        ep.addDate("2026-05-17")
        val expected = LocalDate(2026, 5, 17)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        assertEquals(expected, ep.date)
    }

    @Test
    fun addDateNullDoesNotSetDate() {
        val ep = episode()
        ep.addDate(null as String?)
        assertNull(ep.date)
    }

    @Test
    fun addDateInvalidStringLeavesDateNull() {
        val ep = episode()
        ep.addDate("not-a-date")
        assertNull(ep.date)
    }

    @Test
    fun addDateCustomFormatParsesSlashDate() {
        val ep = episode()
        ep.addDate("17/05/2026", "dd/MM/yyyy")
        val expected = LocalDate(2026, 5, 17)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        assertEquals(expected, ep.date)
    }

    @Test
    fun addDateIsoDateTimeWithOffsetUsesExactInstant() {
        val ep = episode()
        ep.addDate("2026-05-17T10:30:00.000+05:00", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        val expected = Instant.parse("2026-05-17T10:30:00.000+05:00").toEpochMilliseconds()
        assertEquals(expected, ep.date)
    }

    @Test
    fun addDateUtcDateTimeUsesExactInstant() {
        val ep = episode()
        ep.addDate("2026-05-17T10:30:00.000Z", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        val expected = Instant.parse("2026-05-17T10:30:00.000Z").toEpochMilliseconds()
        assertEquals(expected, ep.date)
    }

    @Test
    fun addDateDateTimeNoOffsetUsesSystemTimezone() {
        val ep = episode()
        ep.addDate("2026-05-17T10:30:00", "yyyy-MM-dd'T'HH:mm:ss")
        val expected = LocalDateTime(2026, 5, 17, 10, 30, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        assertEquals(expected, ep.date)
    }

    @Test
    fun addDateLocalDateSetsCorrectEpochMillis() {
        val ep = episode()
        ep.addDate(LocalDate(2026, 5, 17))
        val expected = LocalDate(2026, 5, 17)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        assertEquals(expected, ep.date)
    }

    @Test
    fun addDateNullLocalDateLeavesDateNull() {
        val ep = episode()
        ep.addDate(null as LocalDate?)
        assertNull(ep.date)
    }

    @Test
    fun addDateInstantSetsCorrectEpochMillis() {
        val ep = episode()
        val instant = Instant.parse("2026-05-17T10:30:00Z")
        ep.addDate(instant)
        assertEquals(instant.toEpochMilliseconds(), ep.date)
    }

    @Test
    fun addDateNullInstantLeavesDateNull() {
        val ep = episode()
        ep.addDate(null as Instant?)
        assertNull(ep.date)
    }
}

class IsUpcomingTest {

    @Test
    fun isUpcomingFutureDate() {
        assertTrue(isUpcoming("2099-01-01"))
    }

    @Test
    fun isUpcomingPastDate() {
        assertFalse(isUpcoming("2000-01-01"))
    }

    @Test
    fun isUpcomingNullReturnsFalse() {
        assertFalse(isUpcoming(null))
    }

    @Test
    fun isUpcomingInvalidStringReturnsFalse() {
        assertFalse(isUpcoming("not-a-date"))
    }
}
