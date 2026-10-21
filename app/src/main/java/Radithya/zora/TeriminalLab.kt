package Radithya.zora

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import Radithya.zora.ui.theme.ZoraTheme

@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_8", name = "Isolated Blueprint Sandbox")
@Composable
fun ZoraTerminalSandboxLab() {
    // 1. ISOLATED BLUEPRINT INPUTS (Edit these string patterns freely!)
    val testLabLogs = remember {
        listOf(
            "[15:24:01.002] [ZoraClick] Input Trigger Clicked: KeyCode 24",
            "[15:24:01.120] [ZoraSerial] Endpoint active at 921600 Baud rate.",
            "[15:24:02.450] [ZoraTime] 1. USB Transfer: 110ms",
            "[15:24:02.880] [ZoraTime] 2. Image Proc: 430ms | Size: 112KB",
            "[15:24:06.220] [ZoraTime] 3. Gemini Roundtrip (gemini-3.1-flash-lite): 3340ms",
            "[15:24:06.225] [ZoraTime] TOTAL ROUNDTRIP: 3885ms",
            "[15:24:06.500] [ZoraLogExport] SUCCESS: Pipeline document generated."
        )
    }

    ZoraTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Isolated UI Blueprint Lab",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = Color(0xFF0F141C), // Style your lab background canvas here
                shape = RoundedCornerShape(12.dp)
            ) {
                LazyColumn(modifier = Modifier.padding(15.dp)) {
                    items(testLabLogs.size) { index ->
                        val rawLog = testLabLogs[index]
                        // 1. TEXT PARSING (Extract pure words without extra bracket collisions)
                        val timestampStr = rawLog.substringBefore("] ") + "]"
                        val remainingAfterTimestamp = rawLog.substringAfter("] ")

                        // Strip out string bracket markers cleanly so we don't duplicate them
                        val rawTag = remainingAfterTimestamp.substringBefore("] ").replace("[", "")
                            .replace("]", "")
                        val tagStr = "[$rawTag]"
                        val messageStr = remainingAfterTimestamp.substringAfter("] ")

                        // 2. CHOOSE CATEGORY COLORS FOR THE UNIQUE TAG IDENTIFIERS
                        val tagColor = when {
                            tagStr.contains("Time") -> Color(0xFF00E676) // Mint Green
                            tagStr.contains("Serial") -> Color(0xFFBA68C8) // Purple
                            tagStr.contains("Click") -> Color(0xFF29B6F6) // Blue
                            else -> Color(0xFFFFB300) // Amber
                        }

                        // 3. BUILD THE COMPREHENSIVE TIMING LOG ANNOTATIONS
                        val isTimingData = tagStr.contains("Time") && messageStr.contains("ms")

                        // We use buildAnnotatedString to bundle multiple styles into ONE single block component!
                        val liveTerminalString = buildAnnotatedString {
                            // A. Append Muted Gray Timestamp
                            withStyle(style = SpanStyle(color = Color(0x7AFFFFFF))) {
                                append("$timestampStr ")
                            }

                            // B. Append Category Tag
                            withStyle(
                                style = SpanStyle(
                                    color = tagColor,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("$tagStr ")
                            }

                            // C. Append Message Content dynamically
                            if (isTimingData) {
                                val msPattern = "(\\d+)\\s*ms".toRegex()
                                val matchResult = msPattern.find(messageStr)
                                val extractedMs =
                                    matchResult?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                                val titlePart =
                                    if (messageStr.contains(":")) messageStr.substringBefore(":") + ":" else "Metric:"
                                val valuePart = messageStr.substringAfter(":")

                                val heatColor = when {
                                    extractedMs < 200L -> Color(0xFF64FFDA)   // Fast (Cyan)
                                    extractedMs < 1000L -> Color(0xFFFFEE58)  // Medium (Yellow)
                                    else -> Color(0xFFFF5252)                 // Warning Heat Red (Gemini)
                                }

                                // Process Title uses heat color map
                                withStyle(
                                    style = SpanStyle(
                                        color = heatColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                ) {
                                    append("$titlePart ")
                                }
                                // Duration numbers use bold pop styling
                                withStyle(
                                    style = SpanStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(valuePart)
                                }
                            } else {
                                // Default notification print fallback
                                withStyle(style = SpanStyle(color = Color(0xFFECEFF1))) {
                                    append(messageStr)
                                }
                            }
                        }

                        // 4. PRINT TO CANVAS VIEW (One single clean text layout, zero row widgets!)
                        Text(
                            text = liveTerminalString,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

