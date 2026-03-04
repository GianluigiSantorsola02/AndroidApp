package com.example.toxicchat.androidapp.data.parser

import com.example.toxicchat.androidapp.domain.model.DateOrderUsed
import com.example.toxicchat.androidapp.domain.model.ImportMetadata
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import com.example.toxicchat.androidapp.domain.model.Source
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject

class WhatsAppTxtParser @Inject constructor() {

    private val bracketPattern = Pattern.compile(
        """^\[(\d{1,2}[\/.\-]\d{1,2}[\/.\-]\d{2,4})\s*,\s*(\d{1,2}:\d{2}(?::\d{2})?(?:\s?(?:AM|PM|am|pm))?)\]\s*(.*)$"""
    )

    private val mainPattern = Pattern.compile(
        """^(\d{1,2}[\/.\-]\d{1,2}[\/.\-]\d{2,4})\s*,\s*(\d{1,2}:\d{2}(?::\d{2})?(?:\s?(?:AM|PM|am|pm))?)\s*[-–—]\s*(.*)$"""
    )

    private val leadingInvisibles = setOf(
        '\u200E', '\u200F',
        '\u202A', '\u202B', '\u202C',
        '\u202D', '\u202E',
        '\u2066', '\u2067', '\u2068', '\u2069'
    )

    private val bidiMarksAnywhereRegex = Regex("[\\uFEFF\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

    private val systemTextPatterns = listOf(
        Regex("^i messaggi e le chiamate sono crittografati end-to-end\\b", RegexOption.IGNORE_CASE),
        Regex("^messages and calls are end-to-end encrypted\\b", RegexOption.IGNORE_CASE),

        Regex("^hai cambiato le impostazioni dei messaggi effimeri\\b", RegexOption.IGNORE_CASE),
        Regex("^i messaggi effimeri sono attivi\\b", RegexOption.IGNORE_CASE),
        Regex("^you changed the disappearing messages settings\\b", RegexOption.IGNORE_CASE),
        Regex("^disappearing messages are on\\b", RegexOption.IGNORE_CASE),
        Regex("^disappearing messages are off\\b", RegexOption.IGNORE_CASE)
    )

    private fun stripLeadingInvisibles(raw: String): String {
        if (raw.isEmpty()) return raw
        var s = raw
        if (s.isNotEmpty() && s[0] == '\uFEFF') s = s.substring(1)
        var i = 0
        while (i < s.length && leadingInvisibles.contains(s[i])) i++
        return if (i == 0) s else s.substring(i)
    }

    private fun normalizeCommonWeirdChars(s: String): String {
        return s
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u2011', '-')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u2212', '-')
    }

    private fun preprocessLineForMatch(rawLine: String): String {
        return normalizeCommonWeirdChars(stripLeadingInvisibles(rawLine)).trimEnd()
    }

    private fun preprocessLineForAppend(rawLine: String): String {
        return normalizeCommonWeirdChars(stripLeadingInvisibles(rawLine)).trimEnd()
    }

    private fun splitDateParts(dateRaw: String): List<String> =
        dateRaw.split(Regex("[/.-]"))

    private fun cleanForSystemMatch(raw: String): String {
        val noLeading = stripLeadingInvisibles(raw)
        val noBidi = bidiMarksAnywhereRegex.replace(noLeading, "")
        return normalizeCommonWeirdChars(noBidi).trim()
    }

    private fun cleanTextForStorage(raw: String): String {
        val noLeading = stripLeadingInvisibles(raw)
        val noBidi = bidiMarksAnywhereRegex.replace(noLeading, "")
        return normalizeCommonWeirdChars(noBidi).trim()
    }

    private fun isSystemText(cleanText: String): Boolean {
        val v = cleanText.trim()
        if (v.isEmpty()) return false
        return systemTextPatterns.any { it.containsMatchIn(v) }
    }

    fun detectDateOrder(lines: Sequence<String>, sampleSize: Int = 50): DateOrderUsed? {
        var dmyFound = false
        var mdyFound = false

        lines.take(sampleSize).forEach { rawLine ->
            if (rawLine.isBlank()) return@forEach

            val normalized = normalizeLine(preprocessLineForMatch(rawLine))
            val matcher = mainPattern.matcher(normalized)
            if (!matcher.matches()) return@forEach

            val dateParts = splitDateParts(matcher.group(1))
            if (dateParts.size < 2) return@forEach

            val a = dateParts[0].toIntOrNull() ?: 0
            val b = dateParts[1].toIntOrNull() ?: 0

            if (a > 12 && b <= 12) dmyFound = true
            if (b > 12 && a <= 12) mdyFound = true
        }

        return when {
            dmyFound && !mdyFound -> DateOrderUsed.DMY
            mdyFound && !dmyFound -> DateOrderUsed.MDY
            else -> null
        }
    }

    fun parseStreaming(
        lines: Sequence<String>,
        dateOrder: DateOrderUsed,
        conversationId: String,
        onMetadataComplete: (ImportMetadata) -> Unit
    ): Sequence<MessageRecord> = sequence {
        var multilineAppends = 0
        var skippedLines = 0
        var invalidDates = 0
        val examplesSkipped = mutableListOf<String>()
        var systemCount = 0
        var parsedMessagesCount = 0L
        val zoneId = ZoneId.systemDefault()

        var currentMessage: MessageRecord? = null

        lines.forEach { rawLine ->
            if (rawLine.isBlank()) return@forEach

            val normalizedLine = normalizeLine(preprocessLineForMatch(rawLine))
            val matcher = mainPattern.matcher(normalizedLine)

            if (matcher.matches()) {
                currentMessage?.let {
                    yield(it)
                    parsedMessagesCount++
                }

                val dateRaw = matcher.group(1)
                val timeRaw = matcher.group(2)
                val payloadRaw = matcher.group(3)

                val isoTimestamp = parseToIso(dateRaw, timeRaw, dateOrder, zoneId)
                if (isoTimestamp == null) {
                    invalidDates++
                    skippedLines++
                    if (examplesSkipped.size < 3) examplesSkipped.add(rawLine)
                    currentMessage = null
                } else {
                    val (speakerRaw, text, isSystem) = parsePayload(payloadRaw)
                    if (isSystem) systemCount++

                    val epochMillis = try {
                        OffsetDateTime.parse(isoTimestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
                            .toInstant().toEpochMilli()
                    } catch (e: Exception) {
                        0L
                    }

                    currentMessage = MessageRecord(
                        conversationId = conversationId,
                        messageId = parsedMessagesCount + 1,
                        timestampIso8601 = isoTimestamp,
                        timestampEpochMillis = epochMillis,
                        speaker = null,
                        speakerRaw = speakerRaw,
                        textOriginal = text,
                        source = Source.WHATSAPP_TXT,
                        isSystem = isSystem,
                        isToxic = null,
                        toxScore = null
                    )
                }
            } else {
                val appendLine = preprocessLineForAppend(rawLine)
                if (appendLine.isBlank()) return@forEach

                if (currentMessage != null) {
                    val base = currentMessage!!.textOriginal
                    val merged = if (base.isBlank()) appendLine else (base + "\n" + appendLine)
                    currentMessage = currentMessage!!.copy(textOriginal = merged)
                    multilineAppends++
                } else {
                    skippedLines++
                    if (examplesSkipped.size < 3) examplesSkipped.add(rawLine)
                }
            }
        }

        currentMessage?.let {
            yield(it)
            parsedMessagesCount++
        }

        onMetadataComplete(
            ImportMetadata(
                deviceTimezoneId = zoneId.id,
                dateOrderUsed = dateOrder.name,
                parsedMessagesCount = parsedMessagesCount.toInt(),
                systemMessagesCount = systemCount,
                multilineAppendsCount = multilineAppends,
                skippedLinesCount = skippedLines,
                invalidDatesCount = invalidDates,
                examplesSkippedLines = examplesSkipped
            )
        )
    }

    private fun normalizeLine(line: String): String {
        val matcher = bracketPattern.matcher(line)
        if (matcher.matches()) {
            val date = matcher.group(1)
            val time = matcher.group(2)
            var rest = matcher.group(3).trim()
            if (rest.startsWith("-") || rest.startsWith("–") || rest.startsWith("—")) rest = rest.substring(1).trim()
            return "$date, $time - $rest"
        }
        return line
    }

    private fun parsePayload(payload: String): Triple<String?, String, Boolean> {
        val pRaw = payload.trim()

        val wholeClean = cleanForSystemMatch(pRaw)
        if (isSystemText(wholeClean)) {
            return Triple(null, cleanTextForStorage(pRaw), true)
        }

        val sep = pRaw.indexOf(": ")
        if (sep != -1) {
            val speakerRaw = pRaw.substring(0, sep).trim()
            val textRaw = pRaw.substring(sep + 2)

            val textCleanForMatch = cleanForSystemMatch(textRaw)
            if (isSystemText(textCleanForMatch)) {
                return Triple(null, cleanTextForStorage(textRaw), true)
            }

            val text = cleanTextForStorage(textRaw)
            return if (speakerRaw.isEmpty()) Triple(null, cleanTextForStorage(pRaw), true)
            else Triple(speakerRaw, text, false)
        }

        val colon = pRaw.indexOf(':')
        if (colon > 0) {
            val speakerCandidate = pRaw.substring(0, colon).trim()
            val textRaw = pRaw.substring(colon + 1).trimStart()

            if (speakerCandidate.isNotEmpty() && speakerCandidate.length <= 80) {
                val textCleanForMatch = cleanForSystemMatch(textRaw)
                if (isSystemText(textCleanForMatch)) {
                    return Triple(null, cleanTextForStorage(textRaw), true)
                }
                return Triple(speakerCandidate, cleanTextForStorage(textRaw), false)
            }
        }

        return Triple(null, cleanTextForStorage(pRaw), true)
    }

    private fun parseToIso(dateRaw: String, timeRaw: String, order: DateOrderUsed, zoneId: ZoneId): String? {
        return try {
            val dateParts = splitDateParts(dateRaw)
            if (dateParts.size != 3) return null

            val yearRaw = dateParts[2]
            val year = if (yearRaw.length == 2) 2000 + yearRaw.toInt() else yearRaw.toInt()

            val (day, month) = if (order == DateOrderUsed.DMY) {
                dateParts[0].toInt() to dateParts[1].toInt()
            } else {
                dateParts[1].toInt() to dateParts[0].toInt()
            }

            val timeClean = timeRaw.replace("\u202f", " ").trim()
            val hasAmPm = timeClean.lowercase().contains("am") || timeClean.lowercase().contains("pm")
            val timePattern = if (hasAmPm) {
                if (timeClean.split(":").size == 3) "h:mm:ss a" else "h:mm a"
            } else {
                if (timeClean.split(":").size == 3) "H:mm:ss" else "H:mm"
            }

            val formatter = DateTimeFormatter.ofPattern(timePattern, Locale.US)
            val localTime = java.time.LocalTime.parse(timeClean, formatter)
            val localDateTime = LocalDateTime.of(year, month, day, localTime.hour, localTime.minute, localTime.second)
            localDateTime.atZone(zoneId).toOffsetDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
        } catch (_: Exception) {
            null
        }
    }
}
