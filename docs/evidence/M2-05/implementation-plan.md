# M2-05 implementation plan

## Scope

Implement only the version-1 local environment risk engine owned by Issue #16. M2-06 memory controls remain unimplemented until M2-05 is merged and its final main gates pass.

## Interfaces

- Immutable Java enums/models under `ah.runtime.risk` exactly matching the task card.
- `EnvironmentRiskEngine.evaluate(ApplicationInfo)` is the only public collection entry point.
- A package-private pure scorer consumes normalized snapshots; a package-private Native bridge returns only availability, tracer presence and a two-family mapping bitmask.
- No Context, PackageManager, hidden API, cross-app scan, network, persistence or DENY action.

## Acceptance mapping

- JVM matrix: weights, 39/40/79/80/100 boundaries, mapping/emulator caps, deduplication, order independence, unavailable state, immutable reports and action derivation.
- Native matrix: bounded status/maps parsing, malformed/overlong inputs and stable family classification without retaining paths.
- Connected matrix: actual current-process collection, non-debuggable baseline, ABI-neutral samples, 1,000 evaluations below 50 ms each, redacted report serialization and no integrity failure coupling.
- Existing M2-01/M2-03/M2-04 regressions remain green through the root Build and KVM workflows.

## Security claims

Environment signals are bypassable cost controls. Signer/container failures remain independent fail-closed decisions. Reports contain no raw proc data, mapping names, full paths, process lists or device identifiers.
