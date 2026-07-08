package com.lagradost.cloudstream3.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsUnpackerTest {

    // Minimal P.A.C.K.E.R. coded payload: symbols "hello" and "world" indexed in base 2.
    private val packed =
        "eval(function(p,a,c,k,e,d){}('0 1',2,2,'hello|world'.split('|'),0,{}))"

    @Test
    fun detectsPackedJs() {
        assertTrue(JsUnpacker(packed).detect())
    }

    @Test
    fun doesNotDetectPlainJs() {
        assertFalse(JsUnpacker("var x = 1; console.log(x);").detect())
    }

    @Test
    fun unpacksPackedJs() {
        assertEquals("hello world", JsUnpacker(packed).unpack())
    }

    @Test
    fun unpackReturnsNullForNullInput() {
        assertNull(JsUnpacker(null).unpack())
    }

    @Test
    fun unpackReturnsNullForNonPackedInput() {
        assertNull(JsUnpacker("just some text without packer structure").unpack())
    }
}
