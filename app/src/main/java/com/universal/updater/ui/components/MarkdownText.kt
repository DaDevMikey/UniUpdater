package com.universal.updater.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universal.updater.theme.ThemeTokens

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val lines = markdown.split("\n")
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# ") -> {
                    val text = trimmed.removePrefix("# ")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.TextPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    val text = trimmed.removePrefix("## ")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.TextPrimary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    val text = trimmed.removePrefix("### ")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ThemeTokens.AccentIndigo,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val cleanText = trimmed.substring(2)
                    BulletRow(text = cleanText)
                }
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmed),
                        fontSize = 15.sp,
                        color = ThemeTokens.TextSecondary,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "•",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = ThemeTokens.AccentCyan,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = parseInlineMarkdown(text),
            fontSize = 15.sp,
            color = ThemeTokens.TextSecondary,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val parts = text.split("**")
        for (i in parts.indices) {
            if (i % 2 == 1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ThemeTokens.TextPrimary)) {
                    append(parts[i])
                }
            } else {
                append(parts[i])
            }
        }
    }
}
