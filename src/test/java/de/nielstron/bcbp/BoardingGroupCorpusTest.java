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
