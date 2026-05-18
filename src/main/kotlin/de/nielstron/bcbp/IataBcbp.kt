package de.nielstron.bcbp

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Parser for IATA BCBP payloads. */
object IataBcbp {
    private const val HEADER_LENGTH = 23
    private const val LEG_MANDATORY_MIN_LENGTH = 24
    private val numberWithSuffixPattern = Regex("^(0*)(\\d+)([A-Z]?)$")

    @JvmStatic
    fun parse(rawMessage: String): Parsed? {
        val message = normalize(rawMessage)
        if (message.length < HEADER_LENGTH + LEG_MANDATORY_MIN_LENGTH) {
            return null
        }

        val cursor = Cursor(message)

        val formatCode = cursor.read(1)
        if (formatCode != "M" && formatCode != "S") {
            return null
        }

        val numberOfLegs = cursor.read(1)?.toIntOrNull()
        if (numberOfLegs == null || numberOfLegs !in 1..9) {
            return null
        }

        val passengerNameRaw = cursor.read(20)
        if (passengerNameRaw == null || !isPassengerNameBlock(passengerNameRaw)) {
            return null
        }
        val passengerName = prettyPassengerName(passengerNameRaw)

        val ticketIndicator = cursor.read(1).trimToNull()

        val legs = mutableListOf<Leg>()
        var versionIndicator: String? = null
        var versionNumber: Int? = null
        var uniqueConditional: UniqueConditional? = null
        val referenceYear = LocalDate.now(ZoneOffset.UTC).year

        repeat(numberOfLegs) { legIndex ->
            val mandatory = parseMandatoryLeg(cursor) ?: return null

            val conditionalPayload = cursor.read(mandatory.conditionalSize) ?: return null
            val conditionalCursor = Cursor(conditionalPayload)

            if (legIndex == 0 && mandatory.conditionalSize > 0) {
                versionIndicator = conditionalCursor.read(1).trimEndToNull()
                versionNumber = conditionalCursor.read(1).trimToNull()?.toIntOrNull()
                val uniqueSize = conditionalCursor.readHex()
                if (uniqueSize != null) {
                    val uniquePayload = conditionalCursor.read(uniqueSize) ?: return null
                    uniqueConditional = parseUniqueConditional(uniquePayload, referenceYear)
                }
            }

            val repeatedConditional =
                if (conditionalCursor.remaining() >= 2) {
                    parseRepeatedConditional(conditionalCursor)
                } else {
                    null
                }

            legs += mandatory.withRepeatedConditional(repeatedConditional)
        }

        val issuedDate = uniqueConditional?.issuanceDate
        val legsWithResolvedDates = legs.map { leg ->
            leg.withFlightDate(resolveFlightDate(leg.flightDate, issuedDate, referenceYear))
        }

        val securityData = parseSecurityData(cursor)
        val airlineData = cursor.readRemaining().trimToNull()

        return Parsed(
            formatCode,
            numberOfLegs,
            passengerName,
            ticketIndicator,
            versionIndicator,
            versionNumber,
            legsWithResolvedDates,
            uniqueConditional,
            securityData,
            airlineData,
        )
    }

    private fun parseMandatoryLeg(cursor: Cursor): Leg? {
        val pnr = cursor.readPadded(7).trimToNull()
        val from = cursor.readPadded(3).trimToEmpty()
        val to = cursor.readPadded(3).trimToEmpty()
        val carrier = cursor.readPadded(3).trimToEmpty()
        val flight = normalizePaddedNumberWithOptionalSuffix(cursor.readPadded(5).trimToNull())
        val dayOfYear = cursor.readPadded(3).trimToNull()?.toIntOrNull()
        val compartment = cursor.readPadded(1).trimToNull()
        val seat = normalizePaddedNumberWithOptionalSuffix(cursor.readPadded(4).trimToNull())
        val checkIn = normalizePaddedNumberWithOptionalSuffix(cursor.readPadded(5).trimToNull())
        val passengerStatus = cursor.readPadded(1).trimToNull()
        val conditionalSize = cursor.readHexPadded() ?: return null

        if (from.length != 3 || to.length != 3) {
            return null
        }
        if (carrier.length !in 2..3) {
            return null
        }
        if (flight.isNullOrBlank()) {
            return null
        }
        if (dayOfYear != null && dayOfYear !in 1..366) {
            return null
        }

        val flightDate = dayOfYear?.let { decodeDayOfYear(it, LocalDate.now(ZoneOffset.UTC).year) }

        return Leg(
            pnr,
            from,
            to,
            carrier,
            flight,
            flightDate,
            compartment,
            seat,
            checkIn,
            passengerStatus,
            conditionalSize,
            null,
        )
    }

    private fun parseUniqueConditional(rawSection: String, referenceYear: Int): UniqueConditional {
        val cursor = Cursor(rawSection)
        val passengerDescription = cursor.read(1).cleanOptional()
        val checkInSource = cursor.read(1).cleanOptional()
        val boardingPassIssuanceSource = cursor.read(1).cleanOptional()
        val issuanceDate = decodeIssueDate(cursor.read(4), referenceYear)
        val documentType = cursor.read(1).cleanOptional()
        val issuingAirline = cursor.read(3).cleanOptional()
        val bagTags = mutableListOf<String>()

        while (cursor.remaining() >= 13) {
            val bagTag = cursor.read(13).cleanOptional()
            if (bagTag != null) {
                bagTags += bagTag
            }
        }

        return UniqueConditional(
            passengerDescription,
            checkInSource,
            boardingPassIssuanceSource,
            issuanceDate,
            documentType,
            issuingAirline,
            bagTags,
        )
    }

    private fun parseRepeatedConditional(cursor: Cursor): RepeatedConditional? {
        val sectionSize = cursor.readHex()
        if (sectionSize == null || sectionSize <= 0 || cursor.remaining() < sectionSize) {
            return null
        }

        val sectionRaw = cursor.read(sectionSize) ?: return null
        val section = Cursor(sectionRaw)
        val airlineNumericCode = section.read(3).cleanOptional()
        val documentSerialNumber = section.read(10).cleanOptional()
        val selecteeIndicator = section.read(1).cleanOptional()
        val internationalDocumentVerification = section.read(1).cleanOptional()
        val marketingCarrierDesignator = section.read(3).cleanOptional()

        val remainingAfterFixed = section.remaining()
        val frequentFlyerSize = max(remainingAfterFixed - 5, 0)
        val frequentFlyerRaw = section.read(frequentFlyerSize) ?: ""
        val frequentFlyerAirlineDesignator = frequentFlyerRaw.safeTake(3).cleanOptional()
        val frequentFlyerNumber = frequentFlyerRaw.safeDrop(3).cleanOptional()

        val idAdIndicator = section.read(1).cleanOptional()
        val freeBaggageAllowance = section.read(3).cleanOptional()

        val fastTrack = when (section.read(1).trimToNull()) {
            "Y" -> true
            "N" -> false
            else -> null
        }

        val airlineUse = section.readRemaining().cleanOptional()

        return RepeatedConditional(
            airlineNumericCode,
            documentSerialNumber,
            selecteeIndicator,
            internationalDocumentVerification,
            marketingCarrierDesignator,
            frequentFlyerAirlineDesignator,
            frequentFlyerNumber,
            idAdIndicator,
            freeBaggageAllowance,
            fastTrack,
            airlineUse,
        )
    }

    private fun parseSecurityData(cursor: Cursor): SecurityData? {
        if (cursor.remaining() < 4 || cursor.peek() != "^") {
            return null
        }

        cursor.read(1)
        val type = cursor.read(1).trimToNull() ?: return null
        val length = cursor.readHex() ?: return null

        val data = cursor.read(length) ?: return null
        return SecurityData(type, data.trimEnd())
    }

    private fun normalize(rawMessage: String): String {
        val withoutLineBreaks = rawMessage.replace("\r", "").replace("\n", "")
        return if (withoutLineBreaks.length > 3 && withoutLineBreaks[0] == ']') {
            withoutLineBreaks.substring(3).trimStartCompat()
        } else {
            withoutLineBreaks.trimStartCompat()
        }
    }

    private fun resolveFlightDate(candidate: LocalDate?, issuanceDate: LocalDate?, referenceYear: Int): LocalDate? {
        if (candidate == null || issuanceDate == null) {
            return candidate
        }
        val dateInIssueYear = decodeDayOfYear(candidate.dayOfYear, issuanceDate.year) ?: return candidate
        return if (dateInIssueYear.isBefore(issuanceDate)) {
            dateInIssueYear.plusYears(1)
        } else {
            dateInIssueYear
        }
    }

    private fun decodeDayOfYear(dayOfYear: Int, year: Int): LocalDate? {
        if (dayOfYear !in 1..366) {
            return null
        }
        return runCatching { LocalDate.ofYearDay(year, dayOfYear) }.getOrNull()
    }

    private fun decodeIssueDate(field: String?, referenceYear: Int): LocalDate? {
        val cleaned = field.trimToEmpty()
        if (cleaned.length != 4) {
            return null
        }

        val yearDigit = cleaned[0].digitToIntOrNull() ?: return null
        val dayOfYear = cleaned.substring(1).toIntOrNull()
        if (dayOfYear == null || dayOfYear !in 1..366) {
            return null
        }

        val year = closestYearWithLastDigit(referenceYear, yearDigit)
        return decodeDayOfYear(dayOfYear, year)
    }

    private fun closestYearWithLastDigit(referenceYear: Int, digit: Int): Int {
        var best = referenceYear
        var bestDistance = Int.MAX_VALUE

        for (year in (referenceYear - 20)..(referenceYear + 20)) {
            val lastDigit = ((year % 10) + 10) % 10
            if (lastDigit != digit) {
                continue
            }
            val distance = abs(year - referenceYear)
            if (distance < bestDistance) {
                bestDistance = distance
                best = year
            }
        }

        return best
    }

    private fun isPassengerNameBlock(value: String): Boolean =
        value.all { it.isUpperCase() || it.isDigit() || it == ' ' || it == '/' || it == '-' }

    private fun prettyPassengerName(value: String): String {
        val normalized = value.trim().replace(Regex("\\s+"), " ")

        val split = normalized.split("/", limit = 2)
        val lastName = split.getOrNull(0)?.trim()?.titleCaseWords().orEmpty()
        val firstName = split.getOrNull(1)?.trim()?.titleCaseWords().orEmpty()

        val joined = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        return joined.ifBlank { normalized }
    }

    private fun String.titleCaseWords(): String {
        if (isBlank()) {
            return ""
        }

        return lowercase(Locale.ROOT)
            .split(" ")
            .joinToString(" ") { word ->
                word
                    .split("-")
                    .joinToString("-") { piece ->
                        if (piece.isEmpty()) {
                            piece
                        } else {
                            piece[0].uppercaseChar() + piece.substring(1)
                        }
                    }
            }
    }

    private fun normalizePaddedNumberWithOptionalSuffix(value: String?): String? {
        val trimmed = value.trimToEmpty()
        if (trimmed.isBlank()) {
            return null
        }

        val match = numberWithSuffixPattern.matchEntire(trimmed) ?: return trimmed
        var noLeadingZeros = match.groupValues[2].replaceFirst(Regex("^0+"), "")
        if (noLeadingZeros.isBlank()) {
            noLeadingZeros = "0"
        }

        return noLeadingZeros + match.groupValues[3]
    }

    private fun String.safeTake(length: Int): String =
        substring(0, min(this.length, max(length, 0)))

    private fun String.safeDrop(length: Int): String {
        val start = min(this.length, max(length, 0))
        return substring(start)
    }

    private fun String?.trimToEmpty(): String = this?.trim().orEmpty()

    private fun String?.trimToNull(): String? {
        val trimmed = this?.trim() ?: return null
        return trimmed.ifBlank { null }
    }

    private fun String?.trimEndToNull(): String? {
        val trimmed = this?.replace(Regex("\\s+$"), "") ?: return null
        return trimmed.ifBlank { null }
    }

    private fun String.trimStartCompat(): String =
        replaceFirst(Regex("^\\s+"), "")

    private fun String?.cleanOptional(): String? = trimToNull()

    private class Cursor(private val raw: String) {
        private var index = 0

        fun remaining(): Int = raw.length - index

        fun read(length: Int): String? {
            if (length < 0 || index + length > raw.length) {
                return null
            }
            val value = raw.substring(index, index + length)
            index += length
            return value
        }

        fun readPadded(length: Int): String {
            if (length < 0 || index > raw.length) {
                return ""
            }
            val end = min(index + length, raw.length)
            val value = raw.substring(index, end)
            index = end
            return value + " ".repeat(length - value.length)
        }

        fun readHex(): Int? = read(2).parseHex()

        fun readHexPadded(): Int? = readPadded(2).parseHex()

        private fun String?.parseHex(): Int? {
            var trimmed = this?.trim() ?: return null
            if (trimmed.isBlank()) {
                trimmed = "0"
            }
            return trimmed.toIntOrNull(16)
        }

        fun readRemaining(): String? {
            if (index >= raw.length) {
                return null
            }
            val value = raw.substring(index)
            index = raw.length
            return value
        }

        fun peek(): String? =
            if (index < raw.length) {
                raw.substring(index, index + 1)
            } else {
                null
            }
    }

    data class Parsed(
        val formatCode: String,
        val numberOfLegs: Int,
        val passengerName: String,
        val ticketIndicator: String?,
        val versionNumberIndicator: String?,
        val versionNumber: Int?,
        val legs: List<Leg>,
        val uniqueConditional: UniqueConditional?,
        val securityData: SecurityData?,
        val airlineData: String?,
    ) {
        val firstLeg: Leg
            get() = legs.first()

        val fromAirport: String
            get() = firstLeg.fromAirport

        val toAirport: String
            get() = firstLeg.toAirport

        val carrierCode: String
            get() = firstLeg.operatingCarrier

        val flightNumber: String
            get() = firstLeg.flightNumber

        val flightDate: LocalDate?
            get() = firstLeg.flightDate

        val travelClass: String?
            get() = firstLeg.compartmentCode

        val seat: String?
            get() = firstLeg.seatNumber

        val pnr: String?
            get() = firstLeg.pnrCode

        val checkInSequence: String?
            get() = firstLeg.checkInSequenceNumber

        val passengerStatus: String?
            get() = firstLeg.passengerStatus

        val flightCode: String
            get() = firstLeg.flightCode

        val summary: String
            get() {
                val route = "$fromAirport->$toAirport"
                val seatLabel = seat?.let { "Seat $it" }

                return listOfNotNull(route, flightCode, seatLabel).joinToString(" | ")
            }

        fun electronicTicketNumber(legIndex: Int): String? =
            legs[legIndex].electronicTicketNumber
    }

    data class Leg(
        val pnrCode: String?,
        val fromAirport: String,
        val toAirport: String,
        val operatingCarrier: String,
        val flightNumber: String,
        val flightDate: LocalDate?,
        val compartmentCode: String?,
        val seatNumber: String?,
        val checkInSequenceNumber: String?,
        val passengerStatus: String?,
        val conditionalSize: Int,
        val repeatedConditional: RepeatedConditional?,
    ) {
        val electronicTicketNumber: String?
            get() = repeatedConditional?.electronicTicketNumber

        val flightCode: String
            get() {
                val normalizedFlight = flightNumber.replaceFirst(Regex("^0+"), "")
                val number = normalizedFlight.ifBlank { flightNumber }
                return operatingCarrier + number
            }

        internal fun withRepeatedConditional(value: RepeatedConditional?): Leg =
            copy(repeatedConditional = value)

        internal fun withFlightDate(value: LocalDate?): Leg =
            copy(flightDate = value)
    }

    data class UniqueConditional(
        val passengerDescription: String?,
        val checkInSource: String?,
        val boardingPassIssuanceSource: String?,
        val issuanceDate: LocalDate?,
        val documentType: String?,
        val issuingAirline: String?,
        val bagTagNumbers: List<String>,
    ) {
        val genderCode: String?
            get() = passengerDescription
    }

    data class RepeatedConditional(
        val airlineNumericCode: String?,
        val documentSerialNumber: String?,
        val selecteeIndicator: String?,
        val internationalDocumentVerification: String?,
        val marketingCarrierDesignator: String?,
        val frequentFlyerAirlineDesignator: String?,
        val frequentFlyerNumber: String?,
        val idAdIndicator: String?,
        val freeBaggageAllowance: String?,
        val fastTrack: Boolean?,
        val airlineUse: String?,
    ) {
        val electronicTicketNumber: String?
            get() =
                if (airlineNumericCode == null || documentSerialNumber == null) {
                    null
                } else {
                    airlineNumericCode + documentSerialNumber
                }
    }

    data class SecurityData(
        val type: String,
        val data: String,
    )
}
