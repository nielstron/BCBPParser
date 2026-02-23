# BCBPParser
[![JitPack](https://jitpack.io/v/nielstron/BCBPParser.svg)](https://jitpack.io/#nielstron/BCBPParser)
[![GitHub Packages](https://img.shields.io/badge/GitHub%20Packages-available-181717?logo=github)](https://github.com/nielstron/BCBPParser/packages)

Parser for IATA Bar Coded Boarding Pass (BCBP) payloads, written in Java.

## Usage

```java
import de.nielstron.bcbp.IataBcbp;

String raw = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 106>60000";

// parse(...) returns null for invalid/non-BCBP payloads.
IataBcbp.Parsed pass = IataBcbp.parse(raw);
if (pass == null) {
    return;
}

// First-leg convenience accessors for single-leg UX.
System.out.println(pass.getPassengerName()); // Luc Desmarais
System.out.println(pass.flightCode());       // AC834
System.out.println(pass.getFromAirport());   // YUL
System.out.println(pass.getToAirport());     // FRA
System.out.println(pass.getSeat());          // 1A
System.out.println(pass.summary());          // YUL->FRA | AC834 | Seat 1A
```

### Multi-Leg Data

```java
IataBcbp.Parsed pass = IataBcbp.parse(raw);
if (pass != null) {
    System.out.println("Legs: " + pass.getNumberOfLegs());
    for (IataBcbp.Leg leg : pass.getLegs()) {
        System.out.println(
            leg.getFromAirport() + " -> " + leg.getToAirport() +
            " " + leg.flightCode() +
            " seat " + leg.getSeatNumber()
        );
    }
}
```

### Optional Sections

`UniqueConditional`, `RepeatedConditional`, and `SecurityData` are optional and may be `null`.

```java
IataBcbp.Parsed pass = IataBcbp.parse(raw);
if (pass != null && pass.getSecurityData() != null) {
    System.out.println(pass.getSecurityData().getType());
    System.out.println(pass.getSecurityData().getData());
}
```

### Notes

- Symbology prefixes like `]Q3` are accepted.

## Dependency

### JitPack

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.nielstron:bcbp-parser:<version>")
}
```

### GitHub Packages

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.pkg.github.com/nielstron/BCBPParser") {
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.token").orNull
        }
    }
}

dependencies {
    implementation("de.nielstron:bcbp-parser:<version>")
}
```

## Build

```bash
./gradlew test
./gradlew build
```

## Acknowledgements

This parser was implemented by translating and adapting ideas and behavior from prior open-source work, especially:

- [anomaddev/BoardingPassKit](https://github.com/anomaddev/BoardingPassKit)
- [georgesmith46/bcbp](https://github.com/georgesmith46/bcbp)

Those projects were used as references for decoding structure, field handling, and sample behavior during this implementation.
