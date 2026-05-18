# BCBPParser
[![JitPack](https://jitpack.io/v/nielstron/BCBPParser.svg)](https://jitpack.io/#nielstron/BCBPParser)
[![GitHub Packages](https://img.shields.io/badge/GitHub%20Packages-available-181717?logo=github)](https://github.com/nielstron/BCBPParser/packages)

Parser for IATA Bar Coded Boarding Pass (BCBP) payloads, written in Kotlin.

Implements the structured data message defined by IATA Resolution 792 for Bar Coded Boarding
Passes, using IATA's public [BCBP Implementation Guide, 7th edition](https://www.iata.org/contentassets/1dccc9ed041b4f3bbdcf8ee8682e75c4/2021_03_02-bcbp-implementation-guide-version-7-.pdf)
as the main reference. The parser also supports the public Resolution 792 version 8 gender-code
update for field 15.

## Usage

```kotlin
import de.nielstron.bcbp.IataBcbp

val raw = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 106>60000"

// parse(...) returns null for invalid/non-BCBP payloads.
val pass = IataBcbp.parse(raw) ?: return

// First-leg convenience accessors for single-leg UX.
println(pass.passengerName) // Luc Desmarais
println(pass.flightCode)    // AC834
println(pass.fromAirport)   // YUL
println(pass.toAirport)     // FRA
println(pass.seat)          // 1A
println(pass.summary)       // YUL->FRA | AC834 | Seat 1A
```

### Multi-Leg Data

```kotlin
val pass = IataBcbp.parse(raw)
if (pass != null) {
    println("Legs: ${pass.numberOfLegs}")
    for (leg in pass.legs) {
        println("${leg.fromAirport} -> ${leg.toAirport} ${leg.flightCode} seat ${leg.seatNumber}")
    }
}
```

### Optional Sections

`UniqueConditional`, `RepeatedConditional`, and `SecurityData` are optional and may be `null`.

```kotlin
val pass = IataBcbp.parse(raw)
if (pass?.securityData != null) {
    println(pass.securityData.type)
    println(pass.securityData.data)
}
```

### Notes

- Symbology prefixes like `]Q3` are accepted.
- `UniqueConditional.genderCode` exposes Resolution 792 field 15, including the version 8
  `X` and `U` values.

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
