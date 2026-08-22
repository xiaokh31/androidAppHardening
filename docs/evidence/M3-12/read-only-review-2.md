# M3-12 second independent read-only review

- Reviewed remediation freeze: `f651731b42b4a4f6158f3f51f7e299d49f639f17`
- Reviewed evidence successor: `a261b0ab58d6c386aececeaefa27a55397e8b08b`
- Result: `FAIL — P0=0/P1=1/P2=0`
- Scope: strict read-only static review; no modification, network, Gradle, device, KVM or benchmark

## Finding

The shared APK scanner allowed ZIP general-purpose bit 3 but did not locate or validate the data descriptor. It also did not compare local CRC/compressed-size/uncompressed-size fields with the central directory when bit 3 was clear. All six retained APKs contain reachable descriptor entries. In-memory mutations of a real descriptor signature and a real non-descriptor local CRC were accepted, so the green nested mutation set did not cover this parser boundary.

## Required disposition

The remediation freeze is rejected for publication. The scanner must validate local fields, signed or signature-less descriptors, descriptor bounds and local-record ranges, explicitly recognize only bounded zipalign padding/APK Signing Block gaps, and execute real production-predicate mutations for descriptor/local fields, flags, bounds, expansion, duplicates and symlinks. A new clean freeze and third independent review are mandatory. The unique API 36 workflow remains absent and forbidden.
