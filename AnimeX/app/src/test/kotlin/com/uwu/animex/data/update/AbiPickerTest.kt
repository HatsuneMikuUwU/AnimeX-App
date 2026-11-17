package com.uwu.animex.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AbiPickerTest {
    @Test
    fun usesTheDevicesFirstPreferredAbi() {
        assertEquals("x86_64", pickPreferredAbi(arrayOf("x86_64", "arm64-v8a")))
        assertEquals("arm64-v8a", pickPreferredAbi(arrayOf("arm64-v8a", "armeabi-v7a", "armeabi")))
        assertEquals("armeabi-v7a", pickPreferredAbi(arrayOf("armeabi-v7a", "armeabi")))
    }

    @Test
    fun skipsUnknownAbisAndFallsBackToUniversal() {
        assertEquals("arm64-v8a", pickPreferredAbi(arrayOf("mips", "arm64-v8a")))
        assertEquals("universal", pickPreferredAbi(arrayOf("x86", "mips")))
        assertEquals("universal", pickPreferredAbi(emptyArray()))
    }
}
