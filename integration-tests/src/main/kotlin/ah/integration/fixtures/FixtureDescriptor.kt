package ah.integration.fixtures

import java.nio.file.Path

data class FixtureDescriptor(
    val id: String,
    val unsignedFixtureApk: Path,
    val expectedEvents: List<String>,
    val payloadAbis: List<String>,
    val expectedOutcome: String,
)
