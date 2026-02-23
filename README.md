# bcbp-parser

Standalone Java parser for IATA Bar Coded Boarding Pass (BCBP) payloads.

## Build

```bash
./gradlew test
./gradlew build
```

Artifacts are generated in `build/libs/`:
- `bcbp-parser-<version>.jar`
- `bcbp-parser-<version>-sources.jar`
- `bcbp-parser-<version>-javadoc.jar`

## Usage

```java
import com.google.zxing.BarcodeFormat;
import de.nielstron.bcbp.IataBcbp;

IataBcbp.Parsed parsed = IataBcbp.parse(BarcodeFormat.AZTEC, raw);
if (parsed != null) {
    String summary = parsed.summary();
}
```

## Consume From FossWallet (local dev)

Use Gradle composite build in FossWallet:

```kotlin
includeBuild("../BCBPParser")
implementation("de.nielstron:bcbp-parser:0.1.0-SNAPSHOT")
```

## CI and Releases

- `CI` workflow runs tests on pushes and pull requests.
- `Release` workflow runs when a tag matching `v*` is pushed, builds the project, and attaches jars from `build/libs/*.jar` to a GitHub Release.

Example release:

```bash
git tag v0.1.0
git push origin v0.1.0
```
