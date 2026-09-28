package com.ucprovpn.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainRoutingRulesTest {
    @Test
    fun rulesCanMoveInEitherDirectionWithoutLosingEntries() {
        val rules = listOf("first", "second", "third")

        assertEquals(listOf("second", "first", "third"), rules.move(1, 0))
        assertEquals(listOf("first", "third", "second"), rules.move(1, 2))
        assertEquals(listOf("second", "third", "first"), rules.move(0, 2))
        assertEquals(rules, rules.move(0, 8))
    }
}
