package com.sozolab.zampa

import com.sozolab.zampa.data.model.DietaryInfo
import com.sozolab.zampa.data.model.User
import com.sozolab.zampa.ui.tour.TourStep
import com.sozolab.zampa.ui.tour.TourTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun userRole_fromString_isCaseInsensitive() {
        assertEquals(User.UserRole.COMERCIO, User.UserRole.fromString("comercio"))
        assertEquals(User.UserRole.COMERCIO, User.UserRole.fromString("COMERCIO"))
        assertEquals(User.UserRole.CLIENTE, User.UserRole.fromString("cliente"))
    }

    @Test
    fun userRole_unknownStringFallsBackToCliente() {
        assertEquals(User.UserRole.CLIENTE, User.UserRole.fromString("admin"))
        assertEquals(User.UserRole.CLIENTE, User.UserRole.fromString(""))
    }

    @Test
    fun dietaryInfo_roundtripThroughMap() {
        val original = DietaryInfo(
            isVegetarian = true,
            isVegan = false,
            hasMeat = false,
            hasFish = true,
            hasGluten = true,
            hasLactose = false,
            hasNuts = false,
            hasEgg = true,
        )
        val parsed = DietaryInfo.from(original.toMap())
        assertEquals(original, parsed)
    }

    @Test
    fun dietaryInfo_fromMissingMapReturnsAllFalse() {
        val parsed = DietaryInfo.from(emptyMap())
        assertFalse(parsed.hasAnyInfo)
        assertFalse(parsed.hasAnyAllergen)
    }

    @Test
    fun dietaryInfo_hasAnyAllergen_respectsAllergenFieldsOnly() {
        val veganOnly = DietaryInfo(isVegan = true)
        assertFalse(veganOnly.hasAnyAllergen)
        assertTrue(veganOnly.hasAnyInfo)
    }

    @Test
    fun tourStep_clientSteps_hasFourSteps() {
        assertEquals(4, TourStep.clientSteps.size)
    }

    @Test
    fun tourStep_merchantSteps_hasThreeSteps() {
        assertEquals(3, TourStep.merchantSteps.size)
    }

    @Test
    fun tourStep_clientSteps_startsWithFeedCard() {
        assertEquals(TourTarget.FEED_CARD, TourStep.clientSteps.first().target)
    }

    @Test
    fun tourStep_clientSteps_endsWithFavoritesTab() {
        assertEquals(TourTarget.FAVORITES_TAB, TourStep.clientSteps.last().target)
    }

    @Test
    fun tourStep_merchantSteps_startsWithDashboardTab() {
        assertEquals(TourTarget.MERCHANT_DASHBOARD_TAB, TourStep.merchantSteps.first().target)
    }

    @Test
    fun tourStep_clientSteps_targetsAreDistinct() {
        val targets = TourStep.clientSteps.map { it.target }
        assertEquals(targets.size, targets.distinct().size)
    }

    @Test
    fun tourStep_merchantSteps_targetsAreDistinct() {
        val targets = TourStep.merchantSteps.map { it.target }
        assertEquals(targets.size, targets.distinct().size)
    }
}
