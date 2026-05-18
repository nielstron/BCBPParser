package de.nielstron.bcbp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IataBcbpTest {
    @Test
    fun recognizesValidBcbpPayloads() {
        assertTrue(IataBcbp.parse(BASIC_BCBP) != null)
    }

    @Test
    fun recognizesKitineraryBcbpFixtures() {
        for (fixture in KITINERARY_BCBP_FIXTURES) {
            assertNotNull(IataBcbp.parse(fixture), fixture)
        }
    }

    @Test
    fun recognizesBcbpWithSymbologyPrefix() {
        assertTrue(IataBcbp.parse("]Q3$BASIC_BCBP") != null)
    }

    @Test
    fun rejectsClearlyInvalidPayloads() {
        assertFalse(IataBcbp.parse("M1short") != null)
        assertFalse(IataBcbp.parse("M1Doe/John            E" + "X".repeat(40)) != null)
        assertFalse(IataBcbp.parse("Q1DOE/JOHN            E" + "X".repeat(40)) != null)
    }

    @Test
    fun extractsRelevantData() {
        val parsed = IataBcbp.parse(BASIC_BCBP)
        assertNotNull(parsed)
        parsed!!

        assertEquals("Luc Desmarais", parsed.passengerName)
        assertEquals("YUL", parsed.fromAirport)
        assertEquals("FRA", parsed.toAirport)
        assertEquals("AC834", parsed.flightCode)
        assertEquals("1A", parsed.seat)
        assertEquals("ABC123", parsed.pnr)
        assertEquals("25", parsed.checkInSequence)
        assertNull(parsed.electronicTicketNumber(0))
        assertEquals(1, parsed.numberOfLegs)
        assertEquals(">", parsed.versionNumberIndicator)
        assertEquals(6, parsed.versionNumber)
    }

    @Test
    fun exposesBlankOptionalFieldsAsNull() {
        val parsed = IataBcbp.parse(BCBP_WITH_BLANK_OPTIONAL_FIELDS)
        assertNotNull(parsed)
        parsed!!

        assertNull(parsed.ticketIndicator)
        assertNull(parsed.pnr)
        assertNull(parsed.travelClass)
        assertNull(parsed.seat)
        assertNull(parsed.checkInSequence)
        assertNull(parsed.passengerStatus)
        assertNull(parsed.versionNumberIndicator)
        assertNull(parsed.versionNumber)
        assertNull(parsed.securityData)
        assertEquals("YUL", parsed.fromAirport)
        assertEquals("FRA", parsed.toAirport)
        assertEquals("AC834", parsed.flightCode)
        assertEquals("YUL->FRA | AC834", parsed.summary)
    }

    @Test
    fun parsesMultiLegAndSecurityData() {
        val parsed = IataBcbp.parse(MULTI_LEG_WITH_SECURITY_BCBP)
        assertNotNull(parsed)
        parsed!!

        assertEquals(2, parsed.numberOfLegs)
        assertEquals("Luc Desmarais", parsed.passengerName)
        assertEquals("YUL", parsed.legs[0].fromAirport)
        assertEquals("FRA", parsed.legs[0].toAirport)
        assertEquals("FRA", parsed.legs[1].fromAirport)
        assertEquals("GVA", parsed.legs[1].toAirport)
        assertEquals("LH3664", parsed.legs[1].flightCode)
        assertEquals("12C", parsed.legs[1].seatNumber)
        assertEquals("1", parsed.securityData!!.type)
        assertTrue(parsed.securityData.data.length > 40)
        assertEquals("0014123456003", parsed.uniqueConditional!!.bagTagNumbers[0])
        assertEquals("0141234567890", parsed.electronicTicketNumber(0))
        assertEquals("0141234567890", parsed.legs[0].electronicTicketNumber)
        assertEquals("0140987654321", parsed.legs[1].electronicTicketNumber)
    }

    @Test
    fun recognizesBcbpWithMeaningfulTrailingSpaces() {
        val parsed = IataBcbp.parse(LUFTHANSA_AZTEC_BCBP_WITH_TRAILING_SPACES)
        assertNotNull(parsed)
        parsed!!

        assertTrue(IataBcbp.parse(LUFTHANSA_AZTEC_BCBP_WITH_TRAILING_SPACES) != null)
        assertEquals("Niels Mundler", parsed.passengerName)
        assertEquals("ZRH", parsed.fromAirport)
        assertEquals("HAM", parsed.toAirport)
        assertEquals("LX1056", parsed.flightCode)
        assertEquals("7246349667917", parsed.electronicTicketNumber(0))
    }

    @Test
    fun extractsElectronicTicketNumberFromRepeatedConditionalFields() {
        val parsed = IataBcbp.parse(ITA_BCBP_WITH_ELECTRONIC_TICKET)
        assertNotNull(parsed)
        parsed!!

        assertEquals("AZ572", parsed.flightCode)
        assertEquals("0556025283554", parsed.electronicTicketNumber(0))
    }

    @Test
    fun exposesResolution792Version8GenderCodes() {
        val unspecified = IataBcbp.parse(minimalBcbpWithGenderCode("X"))
        val undisclosed = IataBcbp.parse(minimalBcbpWithGenderCode("U"))
        assertNotNull(unspecified)
        assertNotNull(undisclosed)

        assertEquals(8, unspecified!!.versionNumber)
        assertEquals("X", unspecified.uniqueConditional!!.genderCode)
        assertEquals("U", undisclosed!!.uniqueConditional!!.genderCode)
    }

    private fun minimalBcbpWithGenderCode(genderCode: String): String =
        "M1" +
            fixed("DOE/JOHN", 20) +
            "E" +
            fixed("ABC123", 7) +
            "SFO" +
            "JFK" +
            fixed("UA", 3) +
            fixed("42", 5) +
            "123" +
            "Y" +
            fixed("12A", 4) +
            fixed("1", 5) +
            "1" +
            "05" +
            ">801" +
            genderCode

    private fun fixed(value: String, length: Int): String = value.padEnd(length)

    private companion object {
        private const val BASIC_BCBP = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 106>60000"
        private const val MULTI_LEG_WITH_SECURITY_BCBP =
            "M2DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 14D>6181WW6225BAC 00141234560032A0141234567890 1AC AC 1234567890123    20KYLX58ZDEF456 FRAGVALH 3664 227C012C0002 12E2A0140987654321 1AC AC 1234567890123    2PCNWQ^164GIWVC5EH7JNT684FVNJ91W2QA4DVN5J8K4F0L0GEQ3DF5TGBN8709HKT5D3DW3GBHFCVHMY7J5T6HFR41W2QA4DVN5J8K4F0L0GE"
        private const val LUFTHANSA_AZTEC_BCBP_WITH_TRAILING_SPACES =
            "M1MUNDLER/NIELS       EX4TE6N ZRHHAMLX 1056 049Y030F0117 377>8320 W    BLX                                        2A72463496679170 LX LH 992221992624215     Y*30600000K09  LHS    "
        private val BCBP_WITH_BLANK_OPTIONAL_FIELDS =
            "M1DOE/JOHN            " +
                " " +
                " ".repeat(7) +
                "YUL" +
                "FRA" +
                "AC " +
                "0834 " +
                "226" +
                " " +
                " ".repeat(4) +
                " ".repeat(5) +
                " " +
                "00"
        private const val ITA_BCBP_WITH_ELECTRONIC_TICKET =
            "M1MUENDLER/NIELS      E95X63P FCOZRHAZ 0572 119Y014F0008 377>8320OO6118BAZ                                        2A05560252835540 AZ LH 992003891777470     N*30600000K09         "
        private val KITINERARY_BCBP_FIXTURES = listOf(
            "M1DOE/JOHN            EABCDEFGMRSLGWEZY8724 99  3C  506  10Axxxxxxxxxx",
            "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100",
            "M1DESMARAIS/LUC       EAB12C3 YULFRAAC 0834 326J003A0027 167>5321WW1325BAC 0014123456002001412346700100141234789012A0141234567890 1AC AC 1234567890123    4PCYLX58Z^108ABCDEFGH",
            "M1GRANDMAIRE/MELANIE  EABC123 GVACDGAF 0123 339C002F0025 130>5002A0571234567890  AF AF 1234567890123456    Y^108ABCDEFGH",
            "M2DESMARAIS/LUC       EAB12C3 YULFRAAC 0834 326J003A0027 167>5321WW1325BAC 0014123456002001412346700100141234789012A0141234567890 1AC AC 1234567890123    4PCYLX58ZDEF456 FRAGVALH 3664 327C012C0002 12E2A0140987654321 1AC AC 1234567890123    3PCNWQ^108ABCDEFGH",
            "M2GRANDMAIRE/MELANIE  EABC123 GVACDGAF 0123 339C002F0025 130>5002A0571234567890  AF AF 1234567890123456    YDEF456 CDGDTWNW 0049 339F001A0002 12C2A012098765432101                       2PC ^108ABCDEFGH",
            "M1DOE/JOHN            EXXX007 TXLBRUSN 2588 034Y023D0999 35D>5181WM7034BSN              2A08200000000000 SN LH 123456789012345      *30600000K0902       ",
            "M1DOE/JOHN            EXXX007 TXLBRUSN 2592 110Y",
            "M1DOE/JOHN            EXXX007 TXLBRUSN 2592 110",
            "M1DOE/JOHN             XXX007 BRUTXLEW 8103 035Y012C0030 147>1181W 8033BEW 0000000000000291040000000000 0   LH 123456789012345     ",
            "M1DOE/JANE            EXXX007 MXPDOHQR 0128 256Y042F0023 100>2180  0255BBR              2963456000789980                            0",
            "M1DRAGON/KONQI DR     EXXX007 XHJFRALH 3489 129M092H0002 359>6180WM4128BLH              2A22012345678900 LH                        N*30600000K05     ",
            "M1DOE/JOHN            EXXX007 LISLCGTP 1080 204Y002D0003 35C>2180      B1A              2904712345678900                           *306      09     BRND",
        )
    }
}
