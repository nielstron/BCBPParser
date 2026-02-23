package de.nielstron.bcbp;

import com.google.zxing.BarcodeFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser for IATA BCBP payloads. */
public final class IataBcbp {

    private static final int HEADER_LENGTH = 23;
    private static final int LEG_MANDATORY_LENGTH = 37;

    private static final Set<BarcodeFormat> SUPPORTED_FORMATS = Collections.unmodifiableSet(
        new HashSet<>(List.of(
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX
        ))
    );

    private static final Pattern NUMBER_WITH_SUFFIX_PATTERN = Pattern.compile("^(0*)(\\d+)([A-Z]?)$");

    private IataBcbp() {
    }

    public static boolean isBcbp(BarcodeFormat format, String rawMessage) {
        return parse(format, rawMessage) != null;
    }

    public static Parsed parse(BarcodeFormat format, String rawMessage) {
        if (format == null || rawMessage == null || !SUPPORTED_FORMATS.contains(format)) {
            return null;
        }

        String message = normalize(rawMessage);
        if (message.length() < HEADER_LENGTH + LEG_MANDATORY_LENGTH) {
            return null;
        }

        Cursor cursor = new Cursor(message);

        String formatCode = cursor.read(1);
        if (formatCode == null || (!"M".equals(formatCode) && !"S".equals(formatCode))) {
            return null;
        }

        Integer numberOfLegs = toInt(cursor.read(1));
        if (numberOfLegs == null || numberOfLegs < 1 || numberOfLegs > 9) {
            return null;
        }

        String passengerNameRaw = cursor.read(20);
        if (passengerNameRaw == null || !isPassengerNameBlock(passengerNameRaw)) {
            return null;
        }
        String passengerName = prettyPassengerName(passengerNameRaw);

        String ticketIndicator = trimToEmpty(cursor.read(1));

        List<Leg> legs = new ArrayList<>();
        String versionIndicator = null;
        Integer versionNumber = null;
        UniqueConditional uniqueConditional = null;
        int referenceYear = LocalDate.now(ZoneOffset.UTC).getYear();

        for (int legIndex = 0; legIndex < numberOfLegs; legIndex++) {
            Leg mandatory = parseMandatoryLeg(cursor);
            if (mandatory == null) {
                return null;
            }

            String conditionalPayload = cursor.read(mandatory.getConditionalSize());
            if (conditionalPayload == null) {
                return null;
            }
            Cursor conditionalCursor = new Cursor(conditionalPayload);

            if (legIndex == 0 && mandatory.getConditionalSize() > 0) {
                versionIndicator = trimEndToEmpty(conditionalCursor.read(1));
                versionNumber = toInt(trimToNull(conditionalCursor.read(1)));
                Integer uniqueSize = conditionalCursor.readHex();
                if (uniqueSize != null) {
                    String uniquePayload = conditionalCursor.read(uniqueSize);
                    if (uniquePayload == null) {
                        return null;
                    }
                    uniqueConditional = parseUniqueConditional(uniquePayload, referenceYear);
                }
            }

            RepeatedConditional repeatedConditional = null;
            if (conditionalCursor.remaining() >= 2) {
                repeatedConditional = parseRepeatedConditional(conditionalCursor);
            }

            legs.add(mandatory.withRepeatedConditional(repeatedConditional));
        }

        LocalDate issuedDate = uniqueConditional != null ? uniqueConditional.getIssuanceDate() : null;
        List<Leg> legsWithResolvedDates = new ArrayList<>(legs.size());
        for (Leg leg : legs) {
            legsWithResolvedDates.add(leg.withFlightDate(resolveFlightDate(leg.getFlightDate(), issuedDate, referenceYear)));
        }

        SecurityData securityData = parseSecurityData(cursor);
        String airlineData = trimToNull(cursor.readRemaining());

        return new Parsed(
            formatCode,
            numberOfLegs,
            passengerName,
            ticketIndicator,
            versionIndicator,
            versionNumber,
            legsWithResolvedDates,
            uniqueConditional,
            securityData,
            airlineData
        );
    }

    private static Leg parseMandatoryLeg(Cursor cursor) {
        String pnr = trimToEmpty(cursor.read(7));
        String from = trimToEmpty(cursor.read(3));
        String to = trimToEmpty(cursor.read(3));
        String carrier = trimToEmpty(cursor.read(3));
        String flight = normalizePaddedNumberWithOptionalSuffix(trimToNull(cursor.read(5)));
        Integer dayOfYear = toInt(trimToNull(cursor.read(3)));
        String compartment = trimToEmpty(cursor.read(1));
        String seat = normalizePaddedNumberWithOptionalSuffix(trimToNull(cursor.read(4)));
        String checkIn = normalizePaddedNumberWithOptionalSuffix(trimToNull(cursor.read(5)));
        String passengerStatus = trimToEmpty(cursor.read(1));
        Integer conditionalSize = cursor.readHex();

        if (conditionalSize == null) {
            return null;
        }
        if (from.length() != 3 || to.length() != 3) {
            return null;
        }
        if (carrier.length() < 2 || carrier.length() > 3) {
            return null;
        }
        if (flight.isBlank()) {
            return null;
        }
        if (dayOfYear != null && (dayOfYear < 1 || dayOfYear > 366)) {
            return null;
        }

        LocalDate flightDate = null;
        if (dayOfYear != null) {
            flightDate = decodeDayOfYear(dayOfYear, LocalDate.now(ZoneOffset.UTC).getYear());
        }

        return new Leg(
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
            null
        );
    }

    private static UniqueConditional parseUniqueConditional(String rawSection, int referenceYear) {
        Cursor cursor = new Cursor(rawSection);
        String passengerDescription = cleanOptional(cursor.read(1));
        String checkInSource = cleanOptional(cursor.read(1));
        String boardingPassIssuanceSource = cleanOptional(cursor.read(1));
        LocalDate issuanceDate = decodeIssueDate(cursor.read(4), referenceYear);
        String documentType = cleanOptional(cursor.read(1));
        String issuingAirline = cleanOptional(cursor.read(3));
        List<String> bagTags = new ArrayList<>();

        while (cursor.remaining() >= 13) {
            String bagTag = cleanOptional(cursor.read(13));
            if (bagTag != null) {
                bagTags.add(bagTag);
            }
        }

        return new UniqueConditional(
            passengerDescription,
            checkInSource,
            boardingPassIssuanceSource,
            issuanceDate,
            documentType,
            issuingAirline,
            bagTags
        );
    }

    private static RepeatedConditional parseRepeatedConditional(Cursor cursor) {
        Integer sectionSize = cursor.readHex();
        if (sectionSize == null || sectionSize <= 0 || cursor.remaining() < sectionSize) {
            return null;
        }

        String sectionRaw = cursor.read(sectionSize);
        if (sectionRaw == null) {
            return null;
        }

        Cursor section = new Cursor(sectionRaw);
        String airlineNumericCode = cleanOptional(section.read(3));
        String documentSerialNumber = cleanOptional(section.read(10));
        String selecteeIndicator = cleanOptional(section.read(1));
        String internationalDocumentVerification = cleanOptional(section.read(1));
        String marketingCarrierDesignator = cleanOptional(section.read(3));

        int remainingAfterFixed = section.remaining();
        int frequentFlyerSize = Math.max(remainingAfterFixed - 5, 0);
        String frequentFlyerRaw = Objects.requireNonNullElse(section.read(frequentFlyerSize), "");
        String frequentFlyerAirlineDesignator = cleanOptional(safeTake(frequentFlyerRaw, 3));
        String frequentFlyerNumber = cleanOptional(safeDrop(frequentFlyerRaw, 3));

        String idAdIndicator = cleanOptional(section.read(1));
        String freeBaggageAllowance = cleanOptional(section.read(3));

        Boolean fastTrack = null;
        String fastTrackRaw = trimToNull(section.read(1));
        if ("Y".equals(fastTrackRaw)) {
            fastTrack = Boolean.TRUE;
        } else if ("N".equals(fastTrackRaw)) {
            fastTrack = Boolean.FALSE;
        }

        String airlineUse = cleanOptional(section.readRemaining());

        return new RepeatedConditional(
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
            airlineUse
        );
    }

    private static SecurityData parseSecurityData(Cursor cursor) {
        if (cursor.remaining() < 4 || !"^".equals(cursor.peek())) {
            return null;
        }

        cursor.read(1);
        String type = trimToEmpty(cursor.read(1));
        Integer length = cursor.readHex();
        if (length == null) {
            return null;
        }

        String data = cursor.read(length);
        if (data == null) {
            return null;
        }

        return new SecurityData(type, trimEndToEmpty(data));
    }

    private static String normalize(String rawMessage) {
        String withoutLineBreaks = rawMessage.replace("\r", "").replace("\n", "");
        if (withoutLineBreaks.length() > 3 && withoutLineBreaks.charAt(0) == ']') {
            return trimStart(withoutLineBreaks.substring(3));
        }
        return trimStart(withoutLineBreaks);
    }

    private static LocalDate resolveFlightDate(LocalDate candidate, LocalDate issuanceDate, int referenceYear) {
        if (candidate == null || issuanceDate == null) {
            return candidate;
        }
        LocalDate dateInIssueYear = decodeDayOfYear(candidate.getDayOfYear(), issuanceDate.getYear());
        if (dateInIssueYear == null) {
            return candidate;
        }
        if (dateInIssueYear.isBefore(issuanceDate)) {
            return dateInIssueYear.plusYears(1);
        }
        return dateInIssueYear;
    }

    private static LocalDate decodeDayOfYear(int dayOfYear, int year) {
        if (dayOfYear < 1 || dayOfYear > 366) {
            return null;
        }
        try {
            return LocalDate.ofYearDay(year, dayOfYear);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDate decodeIssueDate(String field, int referenceYear) {
        String cleaned = trimToEmpty(field);
        if (cleaned.length() != 4) {
            return null;
        }

        int yearDigit = Character.digit(cleaned.charAt(0), 10);
        if (yearDigit < 0) {
            return null;
        }

        Integer dayOfYear = toInt(cleaned.substring(1));
        if (dayOfYear == null || dayOfYear < 1 || dayOfYear > 366) {
            return null;
        }

        int year = closestYearWithLastDigit(referenceYear, yearDigit);
        return decodeDayOfYear(dayOfYear, year);
    }

    private static int closestYearWithLastDigit(int referenceYear, int digit) {
        int best = referenceYear;
        int bestDistance = Integer.MAX_VALUE;

        for (int year = referenceYear - 20; year <= referenceYear + 20; year++) {
            int lastDigit = ((year % 10) + 10) % 10;
            if (lastDigit != digit) {
                continue;
            }
            int distance = Math.abs(year - referenceYear);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = year;
            }
        }

        return best;
    }

    private static boolean isPassengerNameBlock(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isUpperCase(c) || Character.isDigit(c) || c == ' ' || c == '/' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private static String prettyPassengerName(String value) {
        String normalized = value.trim().replaceAll("\\s+", " ");

        String[] split = normalized.split("/", 2);
        String lastName = split.length > 0 ? titleCaseWords(split[0].trim()) : "";
        String firstName = split.length > 1 ? titleCaseWords(split[1].trim()) : "";

        String joined = String.join(" ", List.of(firstName, lastName).stream().filter(s -> !s.isBlank()).toList());
        return joined.isBlank() ? normalized : joined;
    }

    private static String titleCaseWords(String value) {
        if (value.isBlank()) {
            return "";
        }

        String[] words = value.toLowerCase(Locale.ROOT).split(" ");
        List<String> casedWords = new ArrayList<>(words.length);
        for (String word : words) {
            String[] pieces = word.split("-");
            List<String> casedPieces = new ArrayList<>(pieces.length);
            for (String piece : pieces) {
                if (piece.isEmpty()) {
                    casedPieces.add(piece);
                } else {
                    char first = Character.toUpperCase(piece.charAt(0));
                    casedPieces.add(first + piece.substring(1));
                }
            }
            casedWords.add(String.join("-", casedPieces));
        }
        return String.join(" ", casedWords);
    }

    private static String normalizePaddedNumberWithOptionalSuffix(String value) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isBlank()) {
            return "";
        }

        Matcher match = NUMBER_WITH_SUFFIX_PATTERN.matcher(trimmed);
        if (!match.matches()) {
            return trimmed;
        }

        String number = match.group(2);
        String noLeadingZeros = number.replaceFirst("^0+", "");
        if (noLeadingZeros.isBlank()) {
            noLeadingZeros = "0";
        }

        return noLeadingZeros + match.group(3);
    }

    private static String safeTake(String value, int length) {
        return value.substring(0, Math.min(value.length(), Math.max(length, 0)));
    }

    private static String safeDrop(String value, int length) {
        int start = Math.min(value.length(), Math.max(length, 0));
        return value.substring(start);
    }

    private static Integer toInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String trimEndToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceFirst("\\s+$", "");
    }

    private static String trimStart(String value) {
        return value.replaceFirst("^\\s+", "");
    }

    private static String cleanOptional(String value) {
        return trimToNull(value);
    }

    private static final class Cursor {
        private final String raw;
        private int index = 0;

        Cursor(String raw) {
            this.raw = raw;
        }

        int remaining() {
            return raw.length() - index;
        }

        String read(int length) {
            if (length < 0 || index + length > raw.length()) {
                return null;
            }
            String value = raw.substring(index, index + length);
            index += length;
            return value;
        }

        Integer readHex() {
            String value = read(2);
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.isBlank()) {
                trimmed = "0";
            }
            try {
                return Integer.valueOf(trimmed, 16);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        String readRemaining() {
            if (index >= raw.length()) {
                return null;
            }
            String value = raw.substring(index);
            index = raw.length();
            return value;
        }

        String peek() {
            if (index < raw.length()) {
                return raw.substring(index, index + 1);
            }
            return null;
        }
    }

    public static final class Parsed {
        private final String formatCode;
        private final int numberOfLegs;
        private final String passengerName;
        private final String ticketIndicator;
        private final String versionNumberIndicator;
        private final Integer versionNumber;
        private final List<Leg> legs;
        private final UniqueConditional uniqueConditional;
        private final SecurityData securityData;
        private final String airlineData;

        public Parsed(
            String formatCode,
            int numberOfLegs,
            String passengerName,
            String ticketIndicator,
            String versionNumberIndicator,
            Integer versionNumber,
            List<Leg> legs,
            UniqueConditional uniqueConditional,
            SecurityData securityData,
            String airlineData
        ) {
            this.formatCode = formatCode;
            this.numberOfLegs = numberOfLegs;
            this.passengerName = passengerName;
            this.ticketIndicator = ticketIndicator;
            this.versionNumberIndicator = versionNumberIndicator;
            this.versionNumber = versionNumber;
            this.legs = List.copyOf(legs);
            this.uniqueConditional = uniqueConditional;
            this.securityData = securityData;
            this.airlineData = airlineData;
        }

        public String getFormatCode() {
            return formatCode;
        }

        public int getNumberOfLegs() {
            return numberOfLegs;
        }

        public String getPassengerName() {
            return passengerName;
        }

        public String getTicketIndicator() {
            return ticketIndicator;
        }

        public String getVersionNumberIndicator() {
            return versionNumberIndicator;
        }

        public Integer getVersionNumber() {
            return versionNumber;
        }

        public List<Leg> getLegs() {
            return legs;
        }

        public UniqueConditional getUniqueConditional() {
            return uniqueConditional;
        }

        public SecurityData getSecurityData() {
            return securityData;
        }

        public String getAirlineData() {
            return airlineData;
        }

        public Leg getFirstLeg() {
            return legs.isEmpty() ? null : legs.get(0);
        }

        public String getFromAirport() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.getFromAirport() : "";
        }

        public String getToAirport() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.getToAirport() : "";
        }

        public String getCarrierCode() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.getOperatingCarrier() : "";
        }

        public String getFlightNumber() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.getFlightNumber() : "";
        }

        public LocalDate getFlightDate() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.getFlightDate() : null;
        }

        public String getTravelClass() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.getCompartmentCode() : "";
        }

        public String getSeat() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.getSeatNumber() : "";
        }

        public String getPnr() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.getPnrCode() : "";
        }

        public String getCheckInSequence() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.getCheckInSequenceNumber() : "";
        }

        public String getPassengerStatus() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.getPassengerStatus() : "";
        }

        public String flightCode() {
            Leg firstLeg = getFirstLeg();
            return firstLeg != null ? firstLeg.flightCode() : "";
        }

        public String summary() {
            String route = (getFromAirport().isBlank() || getToAirport().isBlank()) ? "" : getFromAirport() + "->" + getToAirport();
            String flight = flightCode();
            String seatLabel = getSeat().isBlank() ? "" : "Seat " + getSeat();

            List<String> parts = new ArrayList<>(3);
            if (!route.isBlank()) {
                parts.add(route);
            }
            if (!flight.isBlank()) {
                parts.add(flight);
            }
            if (!seatLabel.isBlank()) {
                parts.add(seatLabel);
            }
            return String.join(" | ", parts);
        }
    }

    public static final class Leg {
        private final String pnrCode;
        private final String fromAirport;
        private final String toAirport;
        private final String operatingCarrier;
        private final String flightNumber;
        private final LocalDate flightDate;
        private final String compartmentCode;
        private final String seatNumber;
        private final String checkInSequenceNumber;
        private final String passengerStatus;
        private final int conditionalSize;
        private final RepeatedConditional repeatedConditional;

        public Leg(
            String pnrCode,
            String fromAirport,
            String toAirport,
            String operatingCarrier,
            String flightNumber,
            LocalDate flightDate,
            String compartmentCode,
            String seatNumber,
            String checkInSequenceNumber,
            String passengerStatus,
            int conditionalSize,
            RepeatedConditional repeatedConditional
        ) {
            this.pnrCode = pnrCode;
            this.fromAirport = fromAirport;
            this.toAirport = toAirport;
            this.operatingCarrier = operatingCarrier;
            this.flightNumber = flightNumber;
            this.flightDate = flightDate;
            this.compartmentCode = compartmentCode;
            this.seatNumber = seatNumber;
            this.checkInSequenceNumber = checkInSequenceNumber;
            this.passengerStatus = passengerStatus;
            this.conditionalSize = conditionalSize;
            this.repeatedConditional = repeatedConditional;
        }

        public String getPnrCode() {
            return pnrCode;
        }

        public String getFromAirport() {
            return fromAirport;
        }

        public String getToAirport() {
            return toAirport;
        }

        public String getOperatingCarrier() {
            return operatingCarrier;
        }

        public String getFlightNumber() {
            return flightNumber;
        }

        public LocalDate getFlightDate() {
            return flightDate;
        }

        public String getCompartmentCode() {
            return compartmentCode;
        }

        public String getSeatNumber() {
            return seatNumber;
        }

        public String getCheckInSequenceNumber() {
            return checkInSequenceNumber;
        }

        public String getPassengerStatus() {
            return passengerStatus;
        }

        public int getConditionalSize() {
            return conditionalSize;
        }

        public RepeatedConditional getRepeatedConditional() {
            return repeatedConditional;
        }

        public String flightCode() {
            String normalizedFlight = flightNumber.replaceFirst("^0+", "");
            String number = normalizedFlight.isBlank() ? flightNumber : normalizedFlight;
            return operatingCarrier + number;
        }

        private Leg withRepeatedConditional(RepeatedConditional value) {
            return new Leg(
                pnrCode,
                fromAirport,
                toAirport,
                operatingCarrier,
                flightNumber,
                flightDate,
                compartmentCode,
                seatNumber,
                checkInSequenceNumber,
                passengerStatus,
                conditionalSize,
                value
            );
        }

        private Leg withFlightDate(LocalDate value) {
            return new Leg(
                pnrCode,
                fromAirport,
                toAirport,
                operatingCarrier,
                flightNumber,
                value,
                compartmentCode,
                seatNumber,
                checkInSequenceNumber,
                passengerStatus,
                conditionalSize,
                repeatedConditional
            );
        }
    }

    public static final class UniqueConditional {
        private final String passengerDescription;
        private final String checkInSource;
        private final String boardingPassIssuanceSource;
        private final LocalDate issuanceDate;
        private final String documentType;
        private final String issuingAirline;
        private final List<String> bagTagNumbers;

        public UniqueConditional(
            String passengerDescription,
            String checkInSource,
            String boardingPassIssuanceSource,
            LocalDate issuanceDate,
            String documentType,
            String issuingAirline,
            List<String> bagTagNumbers
        ) {
            this.passengerDescription = passengerDescription;
            this.checkInSource = checkInSource;
            this.boardingPassIssuanceSource = boardingPassIssuanceSource;
            this.issuanceDate = issuanceDate;
            this.documentType = documentType;
            this.issuingAirline = issuingAirline;
            this.bagTagNumbers = List.copyOf(bagTagNumbers);
        }

        public String getPassengerDescription() {
            return passengerDescription;
        }

        public String getCheckInSource() {
            return checkInSource;
        }

        public String getBoardingPassIssuanceSource() {
            return boardingPassIssuanceSource;
        }

        public LocalDate getIssuanceDate() {
            return issuanceDate;
        }

        public String getDocumentType() {
            return documentType;
        }

        public String getIssuingAirline() {
            return issuingAirline;
        }

        public List<String> getBagTagNumbers() {
            return bagTagNumbers;
        }
    }

    public static final class RepeatedConditional {
        private final String airlineNumericCode;
        private final String documentSerialNumber;
        private final String selecteeIndicator;
        private final String internationalDocumentVerification;
        private final String marketingCarrierDesignator;
        private final String frequentFlyerAirlineDesignator;
        private final String frequentFlyerNumber;
        private final String idAdIndicator;
        private final String freeBaggageAllowance;
        private final Boolean fastTrack;
        private final String airlineUse;

        public RepeatedConditional(
            String airlineNumericCode,
            String documentSerialNumber,
            String selecteeIndicator,
            String internationalDocumentVerification,
            String marketingCarrierDesignator,
            String frequentFlyerAirlineDesignator,
            String frequentFlyerNumber,
            String idAdIndicator,
            String freeBaggageAllowance,
            Boolean fastTrack,
            String airlineUse
        ) {
            this.airlineNumericCode = airlineNumericCode;
            this.documentSerialNumber = documentSerialNumber;
            this.selecteeIndicator = selecteeIndicator;
            this.internationalDocumentVerification = internationalDocumentVerification;
            this.marketingCarrierDesignator = marketingCarrierDesignator;
            this.frequentFlyerAirlineDesignator = frequentFlyerAirlineDesignator;
            this.frequentFlyerNumber = frequentFlyerNumber;
            this.idAdIndicator = idAdIndicator;
            this.freeBaggageAllowance = freeBaggageAllowance;
            this.fastTrack = fastTrack;
            this.airlineUse = airlineUse;
        }

        public String getAirlineNumericCode() {
            return airlineNumericCode;
        }

        public String getDocumentSerialNumber() {
            return documentSerialNumber;
        }

        public String getSelecteeIndicator() {
            return selecteeIndicator;
        }

        public String getInternationalDocumentVerification() {
            return internationalDocumentVerification;
        }

        public String getMarketingCarrierDesignator() {
            return marketingCarrierDesignator;
        }

        public String getFrequentFlyerAirlineDesignator() {
            return frequentFlyerAirlineDesignator;
        }

        public String getFrequentFlyerNumber() {
            return frequentFlyerNumber;
        }

        public String getIdAdIndicator() {
            return idAdIndicator;
        }

        public String getFreeBaggageAllowance() {
            return freeBaggageAllowance;
        }

        public Boolean getFastTrack() {
            return fastTrack;
        }

        public String getAirlineUse() {
            return airlineUse;
        }
    }

    public static final class SecurityData {
        private final String type;
        private final String data;

        public SecurityData(String type, String data) {
            this.type = type;
            this.data = data;
        }

        public String getType() {
            return type;
        }

        public String getData() {
            return data;
        }
    }
}
