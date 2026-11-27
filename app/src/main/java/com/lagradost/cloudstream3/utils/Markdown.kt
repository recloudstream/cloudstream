/**
 * Modified ver of a basic Markdown parser:
 * https://github.com/DAKSHSEMWAL/mdparserkit
 * **/
package com.lagradost.cloudstream3.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

//private val lineNumberRegex: Regex = Regex("^\\d+\\.\\s.*$")
private val boldItalicPattern = Regex("\\*\\*[*_](.*?)[*_]\\*\\*")
private val boldPattern = Regex("\\*\\*(.*?)\\*\\*")
private val italicPattern = Regex("[*_](.*?)[*_]")
private val strikethroughPattern = Regex("~~(.+?)~~")
private val linkPattern = Regex("\\[(.*?)]\\((.*?)\\)")

/**
 * Parses a given Markdown text and converts it into an [AnnotatedString] with appropriate styles.
 *
 * @param markdownText The input Markdown text to parse.
 * @return An [AnnotatedString] with styles applied according to the Markdown syntax.
 */
fun parseMarkdown(markdownText: String): AnnotatedString {
    try {
        val lines = markdownText.split("\n")
        val resultBuilder = AnnotatedString.Builder()

        lines.forEach { line ->
            when {
                // Heading 1: Extracting content, applying bold style, and appending to resultBuilder
                line.startsWith("# ") -> {
                    textMarkDown(
                        line.removePrefix("# ").trim(),
                        resultBuilder,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Similar processing for Heading 2 to Heading 6
                // Heading 2
                line.startsWith("## ") -> {
                    textMarkDown(
                        line.removePrefix("## ").trim(),
                        resultBuilder,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Heading 3
                line.startsWith("### ") -> {
                    textMarkDown(
                        line.removePrefix("### ").trim(),
                        resultBuilder,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Heading 4
                line.startsWith("#### ") -> {
                    textMarkDown(
                        line.removePrefix("#### ").trim(),
                        resultBuilder,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Heading 5
                line.startsWith("##### ") -> {
                    textMarkDown(
                        line.removePrefix("##### ").trim(),
                        resultBuilder,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Heading 6
                line.startsWith("###### ") -> {
                    textMarkDown(
                        line.removePrefix("###### ").trim(),
                        resultBuilder,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Unordered list item: Extracting content, applying bold style, appending bullet point symbol, and appending to resultBuilder
                line.startsWith("* ") || line.startsWith("- ") -> {
                    val content = line.removePrefix("* ").removePrefix("- ").trim()
                    val currentStyle = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    resultBuilder.append(
                        AnnotatedString("• ", currentStyle)
                    )
                    textMarkDown(content, resultBuilder, fontSize = 14.sp)
                }
                // Ordered list item: Extracting content, applying bold style, appending number and period, and appending to resultBuilder
                /*line.matches(lineNumberRegex) -> {
                    val startIndex = lineNumberRegex.find(line)?.range?.first ?: 0
                    val currentStyle = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    val annotatedString = buildAnnotatedString {
                        if (startIndex > 0) {
                            append(line.substring(0, startIndex))
                        }
                        withStyle(currentStyle) {
                            append(line.substring(startIndex, startIndex + 2))
                        }
                    }
                    resultBuilder.append(annotatedString)
                    textMarkDown(
                        inputText = line.substring(startIndex + 2, line.length),
                        resultBuilder = resultBuilder,
                        fontSize = 14.sp
                    )
                }*/
                // Remaining Text
                else -> {
                    textMarkDown(line, resultBuilder, fontSize = 14.sp)
                }
            } // Appending new line
            resultBuilder.append("\n")
        }
        return resultBuilder.toAnnotatedString().trim() as AnnotatedString
    } catch (_: Throwable) {
        // Just in case! We never want the UI to crash!
        return AnnotatedString(text = markdownText)
    }
}

/**
 * Converts markdown-style text formatting to [AnnotatedString] with appropriate [SpanStyle]s.
 *
 * @param inputText The input text to be converted.
 * @param resultBuilder The [AnnotatedString.Builder] to append the converted text to.
 * @param fontSize The desired font size for the text.
 * @return The converted text with Markdown formatting replaced by appropriate [SpanStyle]s.
 */
private fun textMarkDown(
    inputText: String,
    resultBuilder: AnnotatedString.Builder,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal
) {
    var currentIndex = 0

    while (currentIndex < inputText.length) {
        val nextBoldItalic = boldItalicPattern.find(inputText, startIndex = currentIndex)
        val nextBold = boldPattern.find(inputText, startIndex = currentIndex)
        val nextItalic = italicPattern.find(inputText, startIndex = currentIndex)
        val nextStrikethrough = strikethroughPattern.find(inputText, startIndex = currentIndex)
        val nextLink = linkPattern.find(inputText, startIndex = currentIndex)

        val nextMarkDown = listOfNotNull(
            nextBoldItalic,
            nextBold,
            nextItalic,
            nextStrikethrough,
            nextLink
        ).minByOrNull { it.range.first }

        if (nextMarkDown == null) {
            // Append any remaining text if no more markdown found
            val normalText = inputText.substring(currentIndex)
            val style = SpanStyle(fontWeight = fontWeight, fontSize = fontSize)
            resultBuilder.append(AnnotatedString(normalText, style))
            break
        }

        if (nextMarkDown.range.first > currentIndex) {
            // Append any normal text before the markdown
            val normalText = inputText.substring(currentIndex, nextMarkDown.range.first)
            val style = SpanStyle(fontWeight = fontWeight, fontSize = fontSize)
            resultBuilder.append(AnnotatedString(normalText, style))
        }

        val matchText = nextMarkDown.groupValues.getOrNull(1) ?: ""

        val style = when (nextMarkDown) {
            nextBoldItalic -> SpanStyle(
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontSize = fontSize
            )

            nextBold -> SpanStyle(
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            )

            nextItalic -> SpanStyle(
                fontStyle = FontStyle.Italic,
                fontSize = fontSize
            )

            nextStrikethrough -> SpanStyle(
                textDecoration = TextDecoration.LineThrough,
                fontSize = fontSize
            )

            nextLink -> {
                resultBuilder.append(buildAnnotatedString {
                    withLink(
                        LinkAnnotation.Url(
                            nextMarkDown.groupValues.getOrNull(2) ?: "",
                        )
                    ) {
                        append(matchText)
                    }
                })
                currentIndex = nextMarkDown.range.last + 1
                continue
            }

            else -> throw IllegalStateException("Unhandled markdown type")
        }

        resultBuilder.append(AnnotatedString(matchText, style))

        currentIndex = nextMarkDown.range.last + 1
    }
}