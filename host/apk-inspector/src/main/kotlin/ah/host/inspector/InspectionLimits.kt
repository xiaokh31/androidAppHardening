package ah.host.inspector

object InspectionLimits {
    const val MAX_APK_BYTES: Long = 2_147_483_647L
    const val MAX_ENTRIES: Int = 65_535
    const val MAX_PATH_UTF8_BYTES: Int = 1_024
    const val MAX_ENTRY_UNCOMPRESSED_BYTES: Long = 1_073_741_824L
    const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 4_294_967_296L
    const val MAX_COMPRESSION_RATIO: Long = 200L
    const val MAX_MANIFEST_BYTES: Long = 16_777_216L
    const val MAX_DEX_BYTES: Long = 536_870_912L
    const val MAX_DEX_ENTRIES: Int = 64

    fun snapshot(): LimitsApplied = LimitsApplied(
        linkedMapOf(
            "apkBytes" to MAX_APK_BYTES,
            "entries" to MAX_ENTRIES.toLong(),
            "pathUtf8Bytes" to MAX_PATH_UTF8_BYTES.toLong(),
            "entryUncompressedBytes" to MAX_ENTRY_UNCOMPRESSED_BYTES,
            "totalUncompressedBytes" to MAX_TOTAL_UNCOMPRESSED_BYTES,
            "compressionRatio" to MAX_COMPRESSION_RATIO,
            "manifestBytes" to MAX_MANIFEST_BYTES,
            "dexBytes" to MAX_DEX_BYTES,
            "dexEntries" to MAX_DEX_ENTRIES.toLong(),
        ),
    )
}
