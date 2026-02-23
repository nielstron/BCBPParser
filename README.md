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
