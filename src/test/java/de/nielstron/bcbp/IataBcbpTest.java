package de.nielstron.bcbp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IataBcbpTest {

    private static final String BASIC_BCBP = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 106>60000";
    private static final String MULTI_LEG_WITH_SECURITY_BCBP =
        "M2DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 14D>6181WW6225BAC 00141234560032A0141234567890 1AC AC 1234567890123    20KYLX58ZDEF456 FRAGVALH 3664 227C012C0002 12E2A0140987654321 1AC AC 1234567890123    2PCNWQ^164GIWVC5EH7JNT684FVNJ91W2QA4DVN5J8K4F0L0GEQ3DF5TGBN8709HKT5D3DW3GBHFCVHMY7J5T6HFR41W2QA4DVN5J8K4F0L0GE";
    private static final String LUFTHANSA_AZTEC_BCBP_WITH_TRAILING_SPACES =
        "M1MUNDLER/NIELS       EX4TE6N ZRHHAMLX 1056 049Y030F0117 377>8320 W    BLX                                        2A72463496679170 LX LH 992221992624215     Y*30600000K09  LHS    ";

    @Test
    void recognizesValidBcbpPayloads() {
        assertTrue(IataBcbp.isBcbp(BASIC_BCBP));
    }

    @Test
    void recognizesBcbpWithSymbologyPrefix() {
        assertTrue(IataBcbp.isBcbp("]Q3" + BASIC_BCBP));
    }

    @Test
    void rejectsClearlyInvalidPayloads() {
        assertFalse(IataBcbp.isBcbp("M1short"));
        assertFalse(IataBcbp.isBcbp("M1Doe/John            E" + "X".repeat(40)));
        assertFalse(IataBcbp.isBcbp("Q1DOE/JOHN            E" + "X".repeat(40)));
    }

    @Test
    void extractsRelevantData() {
        IataBcbp.Parsed parsed = IataBcbp.parse(BASIC_BCBP);
        assertNotNull(parsed);
        assertEquals("Luc Desmarais", parsed.getPassengerName());
        assertEquals("YUL", parsed.getFromAirport());
        assertEquals("FRA", parsed.getToAirport());
        assertEquals("AC834", parsed.flightCode());
        assertEquals("1A", parsed.getSeat());
        assertEquals("ABC123", parsed.getPnr());
        assertEquals("25", parsed.getCheckInSequence());
        assertEquals(1, parsed.getNumberOfLegs());
        assertEquals(">", parsed.getVersionNumberIndicator());
        assertEquals(6, parsed.getVersionNumber());
    }

    @Test
    void parsesMultiLegAndSecurityData() {
        IataBcbp.Parsed parsed = IataBcbp.parse(MULTI_LEG_WITH_SECURITY_BCBP);
        assertNotNull(parsed);
        assertEquals(2, parsed.getNumberOfLegs());
        assertEquals("Luc Desmarais", parsed.getPassengerName());
        assertEquals("YUL", parsed.getLegs().get(0).getFromAirport());
        assertEquals("FRA", parsed.getLegs().get(0).getToAirport());
        assertEquals("FRA", parsed.getLegs().get(1).getFromAirport());
        assertEquals("GVA", parsed.getLegs().get(1).getToAirport());
        assertEquals("LH3664", parsed.getLegs().get(1).flightCode());
        assertEquals("12C", parsed.getLegs().get(1).getSeatNumber());
        assertEquals("1", parsed.getSecurityData().getType());
        assertTrue(parsed.getSecurityData().getData().length() > 40);
        assertEquals("0014123456003", parsed.getUniqueConditional().getBagTagNumbers().get(0));
    }

    @Test
    void recognizesBcbpWithMeaningfulTrailingSpaces() {
        IataBcbp.Parsed parsed = IataBcbp.parse(LUFTHANSA_AZTEC_BCBP_WITH_TRAILING_SPACES);
        assertNotNull(parsed);
        assertTrue(IataBcbp.isBcbp(LUFTHANSA_AZTEC_BCBP_WITH_TRAILING_SPACES));
        assertEquals("Niels Mundler", parsed.getPassengerName());
        assertEquals("ZRH", parsed.getFromAirport());
        assertEquals("HAM", parsed.getToAirport());
        assertEquals("LX1056", parsed.flightCode());
    }
}
