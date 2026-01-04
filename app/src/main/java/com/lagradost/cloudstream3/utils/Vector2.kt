package com.lagradost.cloudstream3.utils

import kotlin.math.sqrt

data class Vector2(val x : Float, val y : Float) {
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun times(other: Int) = Vector2(x * other, y * other)
    override fun toString(): String = "($x, $y)"
    fun distanceTo(other: Vector2) = (this - other).length
    private val lengthSquared by lazy { x*x + y*y }
    val length by lazy { sqrt(lengthSquared) }
}