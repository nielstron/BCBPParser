package de.nielstron.bcbp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadmeUsageTest {

    private static final String BASIC_BCBP = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 106>60000";
    private static final String MULTI_LEG_WITH_SECURITY_BCBP =
        "M2DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 14D>6181WW6225BAC 00141234560032A0141234567890 1AC AC 1234567890123    20KYLX58ZDEF456 FRAGVALH 3664 227C012C0002 12E2A0140987654321 1AC AC 1234567890123    2PCNWQ^164GIWVC5EH7JNT684FVNJ91W2QA4DVN5J8K4F0L0GEQ3DF5TGBN8709HKT5D3DW3GBHFCVHMY7J5T6HFR41W2QA4DVN5J8K4F0L0GE";

    @Test
    void quickStartExampleProducesDocumentedValues() {
        assertTrue(IataBcbp.parse(BASIC_BCBP) != null);

        IataBcbp.Parsed pass = IataBcbp.parse(BASIC_BCBP);
        assertNotNull(pass);

        assertEquals("Luc Desmarais", pass.getPassengerName());
        assertEquals("AC834", pass.flightCode());
        assertEquals("YUL", pass.getFromAirport());
        assertEquals("FRA", pass.getToAirport());
        assertEquals("1A", pass.getSeat());
        assertEquals("YUL->FRA | AC834 | Seat 1A", pass.summary());
    }

    @Test
    void multiLegExampleProducesExpectedRows() {
        IataBcbp.Parsed pass = IataBcbp.parse(MULTI_LEG_WITH_SECURITY_BCBP);
        assertNotNull(pass);
        assertEquals(2, pass.getNumberOfLegs());

        List<String> rows = new ArrayList<>();
        for (IataBcbp.Leg leg : pass.getLegs()) {
            rows.add(
                leg.getFromAirport() + " -> " + leg.getToAirport() +
                " " + leg.flightCode() +
                " seat " + leg.getSeatNumber()
            );
        }

        assertEquals("YUL -> FRA AC834 seat 1A", rows.get(0));
        assertEquals("FRA -> GVA LH3664 seat 12C", rows.get(1));
    }

    @Test
    void optionalSectionsExampleHasSecurityDataForSample() {
        IataBcbp.Parsed pass = IataBcbp.parse(MULTI_LEG_WITH_SECURITY_BCBP);
        assertNotNull(pass);
        assertNotNull(pass.getSecurityData());
        assertEquals("1", pass.getSecurityData().getType());
        assertTrue(pass.getSecurityData().getData().length() > 40);
    }
}
