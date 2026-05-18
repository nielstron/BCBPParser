package de.nielstron.bcbp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class BoardingGroupCorpusTest {

    private static final List<BoardingGroupSample> SAMPLES = List.of(
        new BoardingGroupSample(
            "IATA sample, Air Canada",
            "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 106>60000",
            "AC",
            "25",
            null,
            null
        ),
        new BoardingGroupSample(
            "Lufthansa Group real-world Aztec payload, LX operating carrier",
            "M1MUNDLER/NIELS       EX4TE6N ZRHHAMLX 1056 049Y030F0117 377>8320 W    BLX                                        2A72463496679170 LX LH 992221992624215     Y*30600000K09  LHS    ",
            "LX",
            "117",
            null,
            null
        ),
        new BoardingGroupSample(
            "ITA Airways real-world Aztec payload, AZ operating carrier",
            "M1MUENDLER/NIELS      E95X63P FCOZRHAZ 0572 119Y014F0008 377>8320OO6118BAZ                                        2A05560252835540 AZ LH 992003891777470     N*30600000K09         ",
            "AZ",
            "8",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary easyJet sample",
            "M1DOE/JOHN            EABCDEFGMRSLGWEZY8724 99  3C  506  10Axxxxxxxxxx",
            "EZY",
            "506",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary IATA Resolution 792 example 1",
            "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100",
            "AC",
            "25",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary IATA Resolution 792 example 2",
            "M1DESMARAIS/LUC       EAB12C3 YULFRAAC 0834 326J003A0027 167>5321WW1325BAC 0014123456002001412346700100141234789012A0141234567890 1AC AC 1234567890123    4PCYLX58Z^108ABCDEFGH",
            "AC",
            "27",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary IATA Resolution 792 example 3",
            "M1GRANDMAIRE/MELANIE  EABC123 GVACDGAF 0123 339C002F0025 130>5002A0571234567890  AF AF 1234567890123456    Y^108ABCDEFGH",
            "AF",
            "25",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary IATA Resolution 792 example 4, first leg",
            "M2DESMARAIS/LUC       EAB12C3 YULFRAAC 0834 326J003A0027 167>5321WW1325BAC 0014123456002001412346700100141234789012A0141234567890 1AC AC 1234567890123    4PCYLX58ZDEF456 FRAGVALH 3664 327C012C0002 12E2A0140987654321 1AC AC 1234567890123    3PCNWQ^108ABCDEFGH",
            "AC",
            "27",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary IATA Resolution 792 example 5, first leg",
            "M2GRANDMAIRE/MELANIE  EABC123 GVACDGAF 0123 339C002F0025 130>5002A0571234567890  AF AF 1234567890123456    YDEF456 CDGDTWNW 0049 339F001A0002 12C2A012098765432101                       2PC ^108ABCDEFGH",
            "AF",
            "25",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary issue-date sample",
            "M1DOE/JOHN            EXXX007 TXLBRUSN 2588 034Y023D0999 35D>5181WM7034BSN              2A08200000000000 SN LH 123456789012345      *30600000K0902       ",
            "SN",
            "999",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary minimal sample",
            "M1DOE/JOHN            EXXX007 TXLBRUSN 2592 110Y",
            "SN",
            "",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary minimal sample without compartment",
            "M1DOE/JOHN            EXXX007 TXLBRUSN 2592 110",
            "SN",
            "",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary missing e-ticket indicator sample",
            "M1DOE/JOHN             XXX007 BRUTXLEW 8103 035Y012C0030 147>1181W 8033BEW 0000000000000291040000000000 0   LH 123456789012345     ",
            "EW",
            "30",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary Qatar zero-size conditional section sample",
            "M1DOE/JANE            EXXX007 MXPDOHQR 0128 256Y042F0023 100>2180  0255BBR              2963456000789980                            0",
            "QR",
            "23",
            ">2180  0255BBR              2963456000789980                            0",
            null
        ),
        new BoardingGroupSample(
            "KItinerary rail-coded LH sample",
            "M1DRAGON/KONQI DR     EXXX007 XHJFRALH 3489 129M092H0002 359>6180WM4128BLH              2A22012345678900 LH                        N*30600000K05     ",
            "LH",
            "2",
            null,
            null
        ),
        new BoardingGroupSample(
            "KItinerary TAP missing issue-date sample",
            "M1DOE/JOHN            EXXX007 LISLCGTP 1080 204Y002D0003 35C>2180      B1A              2904712345678900                           *306      09     BRND",
            "TP",
            "3",
            null,
            null
        ),
        new BoardingGroupSample(
            "Synthetic United-style experiment, visible pass says Group 2",
            minimalBcbp("UA", "00042", "BG:2"),
            "UA",
            "42",
            "BG:2",
            "2"
        ),
        new BoardingGroupSample(
            "Synthetic American-style experiment, visible pass says Group 5",
            minimalBcbp("AA", "00007", "GROUP5"),
            "AA",
            "7",
            "GROUP5",
            "5"
        )
    );

    @Test
    void corpusSeparatesCheckInSequenceFromDisplayedBoardingGroup() {
        for (BoardingGroupSample sample : SAMPLES) {
            IataBcbp.Parsed parsed = IataBcbp.parse(sample.rawMessage());

            assertNotNull(parsed, sample.name());
            assertEquals(sample.operatingCarrier(), parsed.getCarrierCode(), sample.name());
            assertEquals(sample.checkInSequence(), parsed.getCheckInSequence(), sample.name());

            assertEquals(sample.airlineUse(), parsed.getAirlineData(), sample.name());

            assertNull(standardBoardingGroup(parsed), sample.name());
        }
    }

    private static String standardBoardingGroup(IataBcbp.Parsed parsed) {
        return null;
    }

    private static String minimalBcbp(String carrier, String checkInSequence, String airlineUse) {
        return "M1" +
            fixed("DOE/JOHN", 20) +
            "E" +
            fixed("ABC123", 7) +
            "SFO" +
            "JFK" +
            fixed(carrier, 3) +
            fixed("123", 5) +
            "123" +
            "Y" +
            fixed("12A", 4) +
            fixed(checkInSequence, 5) +
            "1" +
            "00" +
            airlineUse;
    }

    private static String fixed(String value, int length) {
        return String.format("%-" + length + "s", value);
    }

    private record BoardingGroupSample(
        String name,
        String rawMessage,
        String operatingCarrier,
        String checkInSequence,
        String airlineUse,
        String displayedBoardingGroup
    ) {
    }
}
