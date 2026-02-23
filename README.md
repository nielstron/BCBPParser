# BCBPParser

Standalone Java parser for IATA Bar Coded Boarding Pass (BCBP) payloads.

## License

This project is licensed under the MIT License. See [LICENSE](./LICENSE).

## Build

```bash
./gradlew test
./gradlew build
```

Artifacts are generated in `build/libs/`:
- `bcbp-parser-<version>.jar`
- `bcbp-parser-<version>-sources.jar`
- `bcbp-parser-<version>-javadoc.jar`

## GitHub Packages

On tag pushes like `v0.0.1`, CI publishes this library to GitHub Packages:
- `de.nielstron:bcbp-parser:<version>`

To consume it in Gradle:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/nielstron/BCBPParser")
        credentials {
            username = providers.gradleProperty("gpr.user")
                .orElse(System.getenv("GITHUB_ACTOR"))
                .get()
            password = providers.gradleProperty("gpr.key")
                .orElse(System.getenv("GITHUB_TOKEN"))
                .get()
        }
    }
}

dependencies {
    implementation("de.nielstron:bcbp-parser:0.0.1")
}
```

## Usage

```java
import de.nielstron.bcbp.IataBcbp;

IataBcbp.Parsed parsed = IataBcbp.parse(raw);
if (parsed != null) {
    String summary = parsed.summary();
}
```

### Quick Start

```java
import de.nielstron.bcbp.IataBcbp;

String raw = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 106>60000";

if (!IataBcbp.isBcbp(raw)) {
    return;
}

IataBcbp.Parsed pass = IataBcbp.parse(raw);
if (pass == null) {
    return;
}

System.out.println(pass.getPassengerName()); // Luc Desmarais
System.out.println(pass.flightCode());       // AC834
System.out.println(pass.getFromAirport());   // YUL
System.out.println(pass.getToAirport());     // FRA
System.out.println(pass.getSeat());          // 1A
System.out.println(pass.summary());          // YUL->FRA | AC834 | Seat 1A
```

### Common Parsing Pattern

- Use `isBcbp(...)` for a quick payload validity check.
- Use `parse(...)` and handle `null` for invalid/non-BCBP payloads.
- Use first-leg convenience accessors (`getFromAirport`, `getToAirport`, `flightCode`, `getSeat`) for single-leg UX.
- Iterate `getLegs()` for multi-leg itineraries.

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

## Acknowledgements

This parser was implemented by translating and adapting ideas and behavior from prior open-source work, especially:

- [anomaddev/BoardingPassKit](https://github.com/anomaddev/BoardingPassKit)
- [georgesmith46/bcbp](https://github.com/georgesmith46/bcbp)

Those projects were used as references for decoding structure, field handling, and sample behavior during this implementation.
