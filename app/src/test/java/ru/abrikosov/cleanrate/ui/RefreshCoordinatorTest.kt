package ru.abrikosov.cleanrate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshCoordinatorTest {
    @Test
    fun `повторное обновление выполняется после текущего`() {
        val coordinator = RefreshCoordinator()
        val initial = RefreshRequest(showSuccessMessage = false, force = false)
        val afterSourceChange = RefreshRequest(showSuccessMessage = false, force = false)

        assertEquals(initial, coordinator.submit(initial))
        assertNull(coordinator.submit(afterSourceChange))
        assertEquals(afterSourceChange, coordinator.complete())
        assertNull(coordinator.complete())
    }

    @Test
    fun `ожидающие запросы сохраняют принудительное обновление и сообщение`() {
        val coordinator = RefreshCoordinator()

        coordinator.submit(RefreshRequest(showSuccessMessage = false, force = false))
        coordinator.submit(RefreshRequest(showSuccessMessage = false, force = false))
        coordinator.submit(RefreshRequest(showSuccessMessage = true, force = true))

        assertEquals(
            RefreshRequest(showSuccessMessage = true, force = true),
            coordinator.complete(),
        )
    }
}
