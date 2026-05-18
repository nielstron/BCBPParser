package de.nielstron.bcbp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadmeUsageTest {
    @Test
    fun quickStartExampleProducesDocumentedValues() {
        assertTrue(IataBcbp.parse(BASIC_BCBP) != null)

        val pass = IataBcbp.parse(BASIC_BCBP)
        assertNotNull(pass)
        pass!!

        assertEquals("Luc Desmarais", pass.passengerName)
        assertEquals("AC834", pass.flightCode)
        assertEquals("YUL", pass.fromAirport)
        assertEquals("FRA", pass.toAirport)
        assertEquals("1A", pass.seat)
        assertEquals("YUL->FRA | AC834 | Seat 1A", pass.summary)
    }

    @Test
    fun multiLegExampleProducesExpectedRows() {
        val pass = IataBcbp.parse(MULTI_LEG_WITH_SECURITY_BCBP)
        assertNotNull(pass)
        pass!!
        assertEquals(2, pass.numberOfLegs)

        val rows = pass.legs.map { leg ->
            "${leg.fromAirport} -> ${leg.toAirport} ${leg.flightCode} seat ${leg.seatNumber}"
        }

        assertEquals("YUL -> FRA AC834 seat 1A", rows[0])
        assertEquals("FRA -> GVA LH3664 seat 12C", rows[1])
    }

    @Test
    fun optionalSectionsExampleHasSecurityDataForSample() {
        val pass = IataBcbp.parse(MULTI_LEG_WITH_SECURITY_BCBP)
        assertNotNull(pass)
        assertNotNull(pass!!.securityData)
        assertEquals("1", pass.securityData!!.type)
        assertTrue(pass.securityData.data.length > 40)
    }

    private companion object {
        private const val BASIC_BCBP = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 106>60000"
        private const val MULTI_LEG_WITH_SECURITY_BCBP =
            "M2DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 14D>6181WW6225BAC 00141234560032A0141234567890 1AC AC 1234567890123    20KYLX58ZDEF456 FRAGVALH 3664 227C012C0002 12E2A0140987654321 1AC AC 1234567890123    2PCNWQ^164GIWVC5EH7JNT684FVNJ91W2QA4DVN5J8K4F0L0GEQ3DF5TGBN8709HKT5D3DW3GBHFCVHMY7J5T6HFR41W2QA4DVN5J8K4F0L0GE"
    }
}
