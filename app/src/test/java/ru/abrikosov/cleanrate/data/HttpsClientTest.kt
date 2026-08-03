package ru.abrikosov.cleanrate.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpsClientTest {
    @Test
    fun `ограниченный читатель принимает ответ в пределах лимита`() {
        val expected = byteArrayOf(1, 2, 3, 4)

        assertArrayEquals(
            expected,
            ByteArrayInputStream(expected).readBytesLimited(4, "слишком большой ответ"),
        )
    }

    @Test
    fun `ограниченный читатель отклоняет ответ сверх лимита`() {
        assertThrows(IllegalStateException::class.java) {
            ByteArrayInputStream(ByteArray(9)).readBytesLimited(8, "слишком большой ответ")
        }
    }
}
