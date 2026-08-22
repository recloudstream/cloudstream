package com.lagradost.cloudstream3.utils

@OptIn(ExperimentalMultiplatform::class) // OptionalExpectation is an experimental annotation for now
@OptionalExpectation
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
expect annotation class IgnoreOnWeb()
