package de.nielstron.bcbp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IataBcbpTest {

    private static final String BASIC_BCBP = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 106>60000";
    private static final String MULTI_LEG_WITH_SECURITY_BCBP =
        "M2DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 14D>6181WW6225BAC 00141234560032A0141234567890 1AC AC 1234567890123    20KYLX58ZDEF456 FRAGVALH 3664 227C012C0002 12E2A0140987654321 1AC AC 1234567890123    2PCNWQ^164GIWVC5EH7JNT684FVNJ91W2QA4DVN5J8K4F0L0GEQ3DF5TGBN8709HKT5D3DW3GBHFCVHMY7J5T6HFR41W2QA4DVN5J8K4F0L0GE";
    private static final String LUFTHANSA_AZTEC_BCBP_WITH_TRAILING_SPACES =
        "M1MUNDLER/NIELS       EX4TE6N ZRHHAMLX 1056 049Y030F0117 377>8320 W    BLX                                        2A72463496679170 LX LH 992221992624215     Y*30600000K09  LHS    ";
    private static final String ITA_BCBP_WITH_ELECTRONIC_TICKET =
        "M1MUENDLER/NIELS      E95X63P FCOZRHAZ 0572 119Y014F0008 377>8320OO6118BAZ                                        2A05560252835540 AZ LH 992003891777470     N*30600000K09         ";
    private static final List<String> KITINERARY_BCBP_FIXTURES = List.of(
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
        "M1DOE/JOHN            EXXX007 LISLCGTP 1080 204Y002D0003 35C>2180      B1A              2904712345678900                           *306      09     BRND"
    );

    @Test
    void recognizesValidBcbpPayloads() {
        assertTrue(IataBcbp.parse(BASIC_BCBP) != null);
    }

    @Test
    void recognizesKitineraryBcbpFixtures() {
        for (String fixture : KITINERARY_BCBP_FIXTURES) {
            assertNotNull(IataBcbp.parse(fixture), fixture);
        }
    }

    @Test
    void recognizesBcbpWithSymbologyPrefix() {
        assertTrue(IataBcbp.parse("]Q3" + BASIC_BCBP) != null);
    }

    @Test
    void rejectsClearlyInvalidPayloads() {
        assertFalse(IataBcbp.parse("M1short") != null);
        assertFalse(IataBcbp.parse("M1Doe/John            E" + "X".repeat(40)) != null);
        assertFalse(IataBcbp.parse("Q1DOE/JOHN            E" + "X".repeat(40)) != null);
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
        assertNull(parsed.getElectronicTicketNumber());
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
        assertEquals("0141234567890", parsed.getElectronicTicketNumber());
        assertEquals("0141234567890", parsed.getLegs().get(0).getElectronicTicketNumber());
        assertEquals("0140987654321", parsed.getLegs().get(1).getElectronicTicketNumber());
    }

    @Test
    void recognizesBcbpWithMeaningfulTrailingSpaces() {
        IataBcbp.Parsed parsed = IataBcbp.parse(LUFTHANSA_AZTEC_BCBP_WITH_TRAILING_SPACES);
        assertNotNull(parsed);
        assertTrue(IataBcbp.parse(LUFTHANSA_AZTEC_BCBP_WITH_TRAILING_SPACES) != null);
        assertEquals("Niels Mundler", parsed.getPassengerName());
        assertEquals("ZRH", parsed.getFromAirport());
        assertEquals("HAM", parsed.getToAirport());
        assertEquals("LX1056", parsed.flightCode());
        assertEquals("7246349667917", parsed.getElectronicTicketNumber());
    }

    @Test
    void extractsElectronicTicketNumberFromRepeatedConditionalFields() {
        IataBcbp.Parsed parsed = IataBcbp.parse(ITA_BCBP_WITH_ELECTRONIC_TICKET);
        assertNotNull(parsed);
        assertEquals("AZ572", parsed.flightCode());
        assertEquals("0556025283554", parsed.getElectronicTicketNumber());
    }
}
