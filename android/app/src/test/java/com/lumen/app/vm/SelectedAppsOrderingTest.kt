package com.lumen.app.vm

import com.lumen.ui.screens.AppEntryUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedAppsOrderingTest {
    @Test
    fun selectedApplicationsAreShownFirstAndRemainAlphabetical() {
        val apps = listOf(
            AppEntryUiModel("app.alpha", "Alpha"),
            AppEntryUiModel("app.bravo", "Bravo"),
            AppEntryUiModel("app.charlie", "Charlie")
        )

        val sorted = selectedAppsFirst(apps, setOf("app.charlie", "app.bravo"))

        assertEquals(listOf("app.bravo", "app.charlie", "app.alpha"), sorted.map { it.packageName })
        assertTrue(sorted.take(2).all { it.isSelected })
    }
}
