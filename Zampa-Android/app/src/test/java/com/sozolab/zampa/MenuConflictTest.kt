package com.sozolab.zampa

import com.sozolab.zampa.data.model.Menu
import org.junit.Assert.*
import org.junit.Test

class MenuConflictTest {

    private val weekday = 2 // Wednesday

    private fun daily(id: String, serviceTime: String = "both", isActive: Boolean = true) =
        Menu(id = id, businessId = "b", title = id, priceTotal = 5.0,
            isActive = isActive, serviceTime = serviceTime, isPermanent = false)

    private fun permanent(id: String, days: List<Int>? = null, serviceTime: String = "both",
                          isActive: Boolean = true) =
        Menu(id = id, businessId = "b", title = id, priceTotal = 5.0,
            isActive = isActive, serviceTime = serviceTime, isPermanent = true,
            recurringDays = days)

    @Test fun dailyVsDailySameServiceTimeConflicts() {
        assertTrue(daily("c", "lunch").conflictsWith(daily("o", "lunch"), weekday))
    }

    @Test fun dailyVsDailyDifferentServiceTimeNoConflict() {
        assertFalse(daily("c", "lunch").conflictsWith(daily("o", "dinner"), weekday))
    }

    @Test fun bothOverlapsLunch() {
        assertTrue(daily("c", "both").conflictsWith(daily("o", "lunch"), weekday))
    }

    @Test fun bothOverlapsDinner() {
        assertTrue(daily("c", "both").conflictsWith(daily("o", "dinner"), weekday))
    }

    @Test fun dailyVsPermanentTodayInDaysConflicts() {
        assertTrue(daily("c", "lunch")
            .conflictsWith(permanent("o", listOf(weekday, 5), "lunch"), weekday))
    }

    @Test fun dailyVsPermanentTodayNotInDaysNoConflict() {
        assertFalse(daily("c", "lunch")
            .conflictsWith(permanent("o", listOf(0, 5), "lunch"), weekday))
    }

    @Test fun dailyVsPermanentNullDaysAlwaysConflicts() {
        assertTrue(daily("c", "lunch")
            .conflictsWith(permanent("o", null, "lunch"), weekday))
    }

    @Test fun permanentVsPermanentOverlappingDaysConflicts() {
        assertTrue(permanent("c", listOf(0, 1, 2), "lunch")
            .conflictsWith(permanent("o", listOf(2, 3), "lunch"), weekday))
    }

    @Test fun permanentVsPermanentNonOverlappingDaysNoConflict() {
        assertFalse(permanent("c", listOf(0, 1), "lunch")
            .conflictsWith(permanent("o", listOf(2, 3), "lunch"), weekday))
    }

    @Test fun permanentVsPermanentNullVsAnyConflicts() {
        assertTrue(permanent("c", null, "lunch")
            .conflictsWith(permanent("o", listOf(4), "lunch"), weekday))
    }

    @Test fun inactiveOfferIgnored() {
        assertFalse(daily("c", "lunch")
            .conflictsWith(daily("o", "lunch", isActive = false), weekday))
    }

    @Test fun findConflictsReturnsList() {
        val c = daily("c", "both")
        val existing = listOf(
            daily("o1", "lunch"),
            daily("o2", "dinner"),
            daily("o3", "lunch", isActive = false),
        )
        val result = Menu.findConflicts(c, existing, weekday).map { it.id }.toSet()
        assertEquals(setOf("o1", "o2"), result)
    }
}
