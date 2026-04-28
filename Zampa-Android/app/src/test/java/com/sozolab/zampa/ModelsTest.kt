package com.sozolab.zampa

import com.sozolab.zampa.data.model.Menu
import org.junit.Assert.*
import org.junit.Test

class RecurringDaysTest {

    @Test
    fun nonPermanentAlwaysVisible() {
        val menu = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = false)
        assertTrue(menu.isVisibleOnDay(0))
        assertTrue(menu.isVisibleOnDay(3))
        assertTrue(menu.isVisibleOnDay(6))
    }

    @Test
    fun permanentWithoutDaysAlwaysVisible() {
        val menu = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = null)
        assertTrue(menu.isVisibleOnDay(0))
        assertTrue(menu.isVisibleOnDay(6))
    }

    @Test
    fun permanentWithEmptyDaysAlwaysVisible() {
        val menu = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = emptyList())
        assertTrue(menu.isVisibleOnDay(0))
    }

    @Test
    fun permanentOnlyVisibleOnSelectedDays() {
        val menu = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = listOf(3))
        assertFalse(menu.isVisibleOnDay(0))
        assertFalse(menu.isVisibleOnDay(2))
        assertTrue(menu.isVisibleOnDay(3))
        assertFalse(menu.isVisibleOnDay(4))
    }

    @Test
    fun permanentMultipleDays() {
        val menu = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = listOf(0, 2, 4))
        assertTrue(menu.isVisibleOnDay(0))
        assertFalse(menu.isVisibleOnDay(1))
        assertTrue(menu.isVisibleOnDay(2))
        assertFalse(menu.isVisibleOnDay(3))
        assertTrue(menu.isVisibleOnDay(4))
        assertFalse(menu.isVisibleOnDay(5))
        assertFalse(menu.isVisibleOnDay(6))
    }

    @Test
    fun occupiedDaysEmpty() {
        assertEquals(emptySet<Int>(), Menu.occupiedDays(emptyList()))
    }

    @Test
    fun occupiedDaysFromSingleOffer() {
        val m = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = listOf(1, 3, 5))
        assertEquals(setOf(1, 3, 5), Menu.occupiedDays(listOf(m)))
    }

    @Test
    fun occupiedDaysFromMultipleOffers() {
        val m1 = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = listOf(0, 1))
        val m2 = Menu(id = "2", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = listOf(4, 5))
        assertEquals(setOf(0, 1, 4, 5), Menu.occupiedDays(listOf(m1, m2)))
    }

    @Test
    fun legacyPermanentOccupiesAll() {
        val m = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = null)
        assertEquals((0..6).toSet(), Menu.occupiedDays(listOf(m)))
    }

    @Test
    fun legacyEmptyOccupiesAll() {
        val m = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = emptyList())
        assertEquals((0..6).toSet(), Menu.occupiedDays(listOf(m)))
    }
}
