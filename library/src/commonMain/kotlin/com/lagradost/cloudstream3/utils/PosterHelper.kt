package com.lagradost.cloudstream3.utils

/**
 * Generates a placeholder poster image URL using placehold.co.
 *
 * @param title The text to display on the poster.
 *              You can add a newline using `\n`.
 * @param width Poster width in pixels. Default is 2400.
 * @param height Poster height in pixels. Default is 400.
 * @param backgroundColor Background color.
 *        Supports hex values (with or without `#`) and CSS color names
 *        like orange or white. Use "transparent" for transparency.
 * @param textColor Text color.
 *        Supports hex values (with or without `#`) and CSS color names.
 * @param font Font name.
 *        Spaces are automatically replaced with `-`.
 *
 * Available fonts:
 * lato, lora, montserrat, noto-sans, open-sans,
 * oswald, playfair-display, poppins, pt-sans,
 * raleway, roboto, source-sans-pro
 *
 * @return A URL string pointing to the generated placeholder image.
 */
fun posterHelper(
    title: String,
    width: Int? = null,
    height: Int? = null,
    backgroundColor: String? = null,
    textColor: String? = null,
    font: String? = null,
): String {
    // Check the documentation on the official website.
    val domain    = "https://placehold.co"
    val imgWidth  = width ?: 2400
    val imgHeight = height ?: 400
    val bgColor   = backgroundColor?.removePrefix("#") ?: "EEE"
    val txtColor  = textColor?.removePrefix("#") ?: "31343C"
    val txtFont   = font?.lowercase()?.replace(" ","-") ?: "lato"

    return "$domain/${imgWidth}x${imgHeight}/$bgColor/$txtColor.png?text=$title&font=$txtFont"
}