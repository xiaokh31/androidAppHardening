package ah.host.axml

import ah.host.inspector.InspectionLimits
import ah.host.inspector.ManifestSummary
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayDeque

object BinaryManifestTransformer {
    fun transform(input: ByteArray, request: ManifestTransformRequest): ManifestTransformResult {
        val source = input.copyOf()
        val beforeDigest = sha256(source)
        val before = AxmlParser(source).parse()
        verifySummary(before, request.manifestSummary)
        if (before.application.factoryValue == ManifestTransformRequest.SHELL_FACTORY ||
            request.manifestSummary.appComponentFactoryClass == ManifestTransformRequest.SHELL_FACTORY
        ) {
            fail(AxmlErrorCode.AXML_RESERVED_COLLISION, before.application.chunkOffset, TYPE_START_ELEMENT)
        }

        val rewritten = AxmlWriter(before).rewrite()
        val after = AxmlParser(rewritten).parse()
        verifyTransform(before, after)
        val change = ManifestAttributeChange(
            elementPath = APPLICATION_PATH,
            namespaceUri = ANDROID_NS,
            attributeName = APP_COMPONENT_FACTORY,
            beforeValue = before.application.factoryValue,
            afterValue = ManifestTransformRequest.SHELL_FACTORY,
        )
        return ManifestTransformResult(
            bytes = rewritten,
            beforeSha256 = beforeDigest,
            afterSha256 = sha256(rewritten),
            semanticDiff = ManifestSemanticDiff(listOf(change)),
        )
    }

    private fun verifySummary(document: AxmlDocument, expected: ManifestSummary) {
        val actual = document.summary
        val digestMatches = MessageDigest.isEqual(
            expected.packageNameSha256,
            sha256(actual.packageName.toByteArray(StandardCharsets.UTF_8)),
        )
        if (actual.packageName != expected.packageName ||
            !digestMatches ||
            actual.minSdk != expected.minSdk ||
            actual.targetSdk != expected.targetSdk ||
            actual.applicationClass != expected.applicationClass ||
            actual.factoryClass != expected.appComponentFactoryClass
        ) {
            fail(AxmlErrorCode.AXML_DIFF_VIOLATION)
        }
    }

    private fun verifyTransform(before: AxmlDocument, after: AxmlDocument) {
        if (after.application.factoryValue != ManifestTransformRequest.SHELL_FACTORY ||
            after.summary.factoryClass != ManifestTransformRequest.SHELL_FACTORY ||
            before.semanticEventsWithoutFactory != after.semanticEventsWithoutFactory ||
            before.summary.copy(factoryClass = null) != after.summary.copy(factoryClass = null) ||
            after.stringPool.strings.size < before.stringPool.strings.size ||
            after.stringPool.strings.subList(0, before.stringPool.strings.size) != before.stringPool.strings ||
            after.resourceIds.size < before.resourceIds.size ||
            !after.resourceIds.copyOf(before.resourceIds.size).contentEquals(before.resourceIds) ||
            before.unknownSummary != after.unknownSummary ||
            (before.application.factoryAttributeIndex != null &&
                before.application.factoryExtensionSha256 != after.application.factoryExtensionSha256)
        ) {
            fail(AxmlErrorCode.AXML_DIFF_VIOLATION)
        }
        val mappedIndex = after.resourceIds.indexOf(ANDROID_ATTR_APP_COMPONENT_FACTORY)
        if (mappedIndex < 0 || after.stringPool.strings.getOrNull(mappedIndex) != APP_COMPONENT_FACTORY) {
            fail(AxmlErrorCode.AXML_DIFF_VIOLATION)
        }
    }
}

private class AxmlWriter(private val document: AxmlDocument) {
    fun rewrite(): ByteArray {
        val originalStrings = document.stringPool.strings
        val resourceIds = document.resourceIds
        val targetMappings = resourceIds.indices.filter { resourceIds[it] == ANDROID_ATTR_APP_COMPONENT_FACTORY }
        val reservedNameMappings = originalStrings.indices.filter { originalStrings[it] == APP_COMPONENT_FACTORY }
        if (targetMappings.any { originalStrings.getOrNull(it) != APP_COMPONENT_FACTORY } ||
            targetMappings.size > 1 ||
            reservedNameMappings.any { resourceIds.getOrElse(it) { 0 } != ANDROID_ATTR_APP_COMPONENT_FACTORY }
        ) {
            fail(AxmlErrorCode.AXML_RESERVED_COLLISION)
        }

        val appended = ArrayList<String>(2)
        val nameIndex = targetMappings.singleOrNull() ?: run {
            appended += APP_COMPONENT_FACTORY
            originalStrings.size
        }
        val existingShellIndex = originalStrings.indexOf(ManifestTransformRequest.SHELL_FACTORY)
        val shellIndex = if (existingShellIndex >= 0) {
            existingShellIndex
        } else {
            val appendedIndex = appended.indexOf(ManifestTransformRequest.SHELL_FACTORY)
            if (appendedIndex >= 0) originalStrings.size + appendedIndex else {
                appended += ManifestTransformRequest.SHELL_FACTORY
                originalStrings.size + appended.lastIndex
            }
        }

        val rewrittenPool = rewriteStringPool(document.stringPool, appended)
        val rewrittenResourceMap = rewriteResourceMap(document.resourceMapChunk, resourceIds, nameIndex)
        val rewrittenApplication = rewriteApplication(document.application, nameIndex, shellIndex)
        val outputChunks = ArrayList<ByteArray>(document.chunks.size + if (document.resourceMapChunk == null) 1 else 0)
        for (chunk in document.chunks) {
            when (chunk.ordinal) {
                document.stringPool.chunkOrdinal -> {
                    outputChunks += rewrittenPool
                    if (document.resourceMapChunk == null) outputChunks += rewrittenResourceMap
                }
                document.resourceMapChunk?.ordinal -> outputChunks += rewrittenResourceMap
                document.application.chunkOrdinal -> outputChunks += rewrittenApplication
                else -> outputChunks += chunk.raw
            }
        }
        val total = checkedAdd(XML_HEADER_SIZE, outputChunks.sumOf { it.size })
        if (total > InspectionLimits.MAX_MANIFEST_BYTES) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
        val output = ByteArray(total)
        document.xmlHeader.copyInto(output, 0)
        putU4(output, 4, total)
        var offset = XML_HEADER_SIZE
        for (chunk in outputChunks) {
            chunk.copyInto(output, offset)
            offset += chunk.size
        }
        return output
    }

    private fun rewriteStringPool(pool: StringPoolData, appended: List<String>): ByteArray {
        if (appended.isEmpty()) return pool.chunk.raw.copyOf()
        val raw = pool.chunk.raw
        val oldCount = pool.strings.size
        val styleCount = pool.styleOffsets.size
        val oldOffsetsEnd = checkedAdd(pool.headerSize, checkedMultiply(oldCount + styleCount, 4))
        if (oldOffsetsEnd > pool.stringsStart) fail(AxmlErrorCode.AXML_MALFORMED, pool.chunk.offset, TYPE_STRING_POOL)
        val gapSize = pool.stringsStart - oldOffsetsEnd
        val oldStringDataEnd = if (pool.stylesStart == 0) raw.size else pool.stylesStart
        val oldStringData = raw.copyOfRange(pool.stringsStart, oldStringDataEnd)
        val encoded = appended.map { encodeString(it, pool.utf8) }
        val appendedBytes = encoded.sumOf { it.size }
        val newOffsetsEnd = checkedAdd(pool.headerSize, checkedMultiply(oldCount + appended.size + styleCount, 4))
        val newStringsStart = checkedAdd(newOffsetsEnd, gapSize)
        val unpaddedStringDataSize = checkedAdd(oldStringData.size, appendedBytes)
        val paddedStringDataSize = align4(unpaddedStringDataSize)
        val stylesSize = if (pool.stylesStart == 0) 0 else raw.size - pool.stylesStart
        val newStylesStart = if (styleCount == 0) 0 else checkedAdd(newStringsStart, paddedStringDataSize)
        val newSize = checkedAdd(checkedAdd(newStringsStart, paddedStringDataSize), stylesSize)
        if (newSize > InspectionLimits.MAX_MANIFEST_BYTES) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
        val result = ByteArray(newSize)
        raw.copyInto(result, 0, 0, pool.headerSize)
        putU4(result, 8, oldCount + appended.size)
        putU4(result, 16, pool.flags and SORTED_FLAG.inv())
        putU4(result, 20, newStringsStart)
        putU4(result, 24, newStylesStart)
        pool.stringOffsets.forEachIndexed { index, value -> putU4(result, pool.headerSize + index * 4, value) }
        var appendedOffset = oldStringData.size
        encoded.forEachIndexed { index, value ->
            putU4(result, pool.headerSize + (oldCount + index) * 4, appendedOffset)
            appendedOffset = checkedAdd(appendedOffset, value.size)
        }
        pool.styleOffsets.forEachIndexed { index, value ->
            putU4(result, pool.headerSize + (oldCount + appended.size + index) * 4, value)
        }
        raw.copyInto(result, newOffsetsEnd, oldOffsetsEnd, pool.stringsStart)
        oldStringData.copyInto(result, newStringsStart)
        var outputOffset = newStringsStart + oldStringData.size
        encoded.forEach {
            it.copyInto(result, outputOffset)
            outputOffset += it.size
        }
        if (stylesSize > 0) raw.copyInto(result, newStylesStart, pool.stylesStart, raw.size)
        putU4(result, 4, result.size)
        return result
    }

    private fun rewriteResourceMap(chunk: AxmlChunk?, original: LongArray, nameIndex: Int): ByteArray {
        if (nameIndex < original.size) {
            if (original[nameIndex] != ANDROID_ATTR_APP_COMPONENT_FACTORY) {
                fail(AxmlErrorCode.AXML_RESERVED_COLLISION, chunk?.offset, TYPE_RESOURCE_MAP)
            }
            return chunk?.raw?.copyOf() ?: fail(AxmlErrorCode.AXML_DIFF_VIOLATION)
        }
        val values = original.copyOf(nameIndex + 1)
        values[nameIndex] = ANDROID_ATTR_APP_COMPONENT_FACTORY
        val result = ByteArray(checkedAdd(CHUNK_HEADER_SIZE, checkedMultiply(values.size, 4)))
        putU2(result, 0, TYPE_RESOURCE_MAP)
        putU2(result, 2, CHUNK_HEADER_SIZE)
        putU4(result, 4, result.size)
        values.forEachIndexed { index, value -> putU4(result, CHUNK_HEADER_SIZE + index * 4, value) }
        return result
    }

    private fun rewriteApplication(application: ApplicationLocation, nameIndex: Int, shellIndex: Int): ByteArray {
        val raw = application.chunk.raw.copyOf()
        val existing = application.factoryAttributeIndex
        if (existing != null) {
            val offset = application.attributeOffset + existing * application.attributeSize
            writeStringAttribute(raw, offset, application.androidNamespaceIndex, nameIndex, shellIndex, clearExtension = false)
            return raw
        }
        if (application.attributeCount >= MAX_ATTRIBUTES) {
            fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED, application.chunkOffset, TYPE_START_ELEMENT)
        }
        val insertion = checkedAdd(application.attributeOffset, checkedMultiply(application.attributeCount, application.attributeSize))
        val result = ByteArray(checkedAdd(raw.size, application.attributeSize))
        raw.copyInto(result, 0, 0, insertion)
        raw.copyInto(result, insertion + application.attributeSize, insertion, raw.size)
        writeStringAttribute(result, insertion, application.androidNamespaceIndex, nameIndex, shellIndex, clearExtension = true)
        putU2(result, START_ELEMENT_ATTRIBUTE_COUNT_OFFSET, application.attributeCount + 1)
        putU4(result, 4, result.size)
        return result
    }

    private fun writeStringAttribute(
        target: ByteArray,
        offset: Int,
        namespaceIndex: Int,
        nameIndex: Int,
        valueIndex: Int,
        clearExtension: Boolean,
    ) {
        putU4(target, offset, namespaceIndex)
        putU4(target, offset + 4, nameIndex)
        putU4(target, offset + 8, valueIndex)
        putU2(target, offset + 12, TYPED_VALUE_SIZE)
        target[offset + 14] = 0
        target[offset + 15] = TYPE_STRING.toByte()
        putU4(target, offset + 16, valueIndex)
        if (clearExtension) {
            for (index in offset + ATTRIBUTE_SIZE until offset + document.application.attributeSize) target[index] = 0
        }
    }
}

private class AxmlParser(private val bytes: ByteArray) {
    private lateinit var pool: StringPoolData
    private var resourceIds = LongArray(0)
    private val chunks = ArrayList<AxmlChunk>()
    private val semanticEvents = ArrayList<SemanticEvent>()
    private val unknownDigest = MessageDigest.getInstance("SHA-256")
    private var unknownCount = 0
    private var semanticAnchor = 0
    private val elements = ArrayDeque<ElementFrame>()
    private val namespaces = ArrayDeque<NamespaceFrame>()
    private var activeNamespaceCounts = IntArray(0)
    private var resourceMapChunk: AxmlChunk? = null
    private var application: ApplicationLocation? = null
    private var applicationCount = 0
    private var usesSdkCount = 0
    private var manifestSeen = false
    private var manifestClosed = false
    private var packageName: String? = null
    private var minSdk: Int? = null
    private var targetSdk: Int? = null
    private var applicationClass: String? = null
    private var factoryClass: String? = null

    fun parse(): AxmlDocument {
        if (bytes.size > InspectionLimits.MAX_MANIFEST_BYTES) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
        if (bytes.size < XML_HEADER_SIZE || u2(bytes, 0) != TYPE_XML || u2(bytes, 2) != XML_HEADER_SIZE || u4(bytes, 4) != bytes.size) {
            fail(AxmlErrorCode.AXML_MALFORMED)
        }
        var offset = XML_HEADER_SIZE
        var ordinal = 0
        while (offset < bytes.size) {
            if (ordinal >= MAX_CHUNKS) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED, offset)
            val chunk = readChunk(offset, ordinal++)
            chunks += chunk
            when (chunk.type) {
                TYPE_STRING_POOL -> parseStringPool(chunk)
                TYPE_RESOURCE_MAP -> parseResourceMap(chunk)
                TYPE_START_NAMESPACE -> parseStartNamespace(chunk)
                TYPE_END_NAMESPACE -> parseEndNamespace(chunk)
                TYPE_START_ELEMENT -> parseStartElement(chunk)
                TYPE_END_ELEMENT -> parseEndElement(chunk)
                TYPE_CDATA -> parseCdata(chunk)
                else -> {
                    requirePool(chunk)
                    updateDigestInt(unknownDigest, semanticAnchor)
                    updateDigestInt(unknownDigest, chunk.type)
                    updateDigestInt(unknownDigest, chunk.size)
                    unknownDigest.update(chunk.raw)
                    unknownCount++
                }
            }
            if (chunk.type in TYPE_START_NAMESPACE..TYPE_CDATA) semanticAnchor++
            offset = checkedAdd(offset, chunk.size)
        }
        if (offset != bytes.size || !::pool.isInitialized || elements.isNotEmpty() || namespaces.isNotEmpty() ||
            !manifestSeen || !manifestClosed || applicationCount > 1
        ) {
            fail(AxmlErrorCode.AXML_MALFORMED)
        }
        val app = application ?: fail(AxmlErrorCode.AXML_APPLICATION_MISSING)
        val exactPackage = packageName ?: fail(AxmlErrorCode.AXML_MALFORMED)
        val exactMinSdk = minSdk ?: fail(AxmlErrorCode.AXML_MALFORMED)
        return AxmlDocument(
            xmlHeader = bytes.copyOfRange(0, XML_HEADER_SIZE),
            chunks = chunks.toList(),
            stringPool = pool,
            resourceMapChunk = resourceMapChunk,
            resourceIds = resourceIds.copyOf(),
            application = app,
            summary = ParsedSummary(
                packageName = exactPackage,
                minSdk = exactMinSdk,
                targetSdk = targetSdk,
                applicationClass = applicationClass,
                factoryClass = factoryClass,
            ),
            semanticEventsWithoutFactory = semanticEvents.map { it.withoutFactory() },
            unknownSummary = UnknownSummary(unknownCount, hex(unknownDigest.digest())),
        )
    }

    private fun parseStringPool(chunk: AxmlChunk) {
        if (::pool.isInitialized || chunks.size != 1 || chunk.headerSize < STRING_POOL_HEADER_SIZE || chunk.size < chunk.headerSize) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        val stringCount = boundedCount(u4(chunk.raw, 8), MAX_STRINGS, chunk)
        val styleCount = boundedCount(u4(chunk.raw, 12), MAX_STRINGS, chunk)
        val flagsValue = u4Long(chunk.raw, 16)
        if (flagsValue and STRING_POOL_ALLOWED_FLAGS.toLong().inv() != 0L) {
            fail(AxmlErrorCode.AXML_UNSUPPORTED_ENCODING, chunk.offset, chunk.type)
        }
        val flags = flagsValue.toInt()
        val stringsStart = toInt(u4Long(chunk.raw, 20), chunk)
        val stylesStart = toInt(u4Long(chunk.raw, 24), chunk)
        val tableSize = checkedMultiply(stringCount + styleCount, 4)
        val tableEnd = checkedAdd(chunk.headerSize, tableSize)
        if (stringsStart < tableEnd || stringsStart > chunk.size || stringsStart % 4 != 0 ||
            (styleCount == 0 && stylesStart != 0) ||
            (styleCount > 0 && (stylesStart < stringsStart || stylesStart > chunk.size || stylesStart % 4 != 0))
        ) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        val stringOffsets = IntArray(stringCount) { index -> toInt(u4Long(chunk.raw, chunk.headerSize + index * 4), chunk) }
        val styleOffsets = IntArray(styleCount) { index ->
            toInt(u4Long(chunk.raw, chunk.headerSize + stringCount * 4 + index * 4), chunk)
        }
        val stringDataEnd = if (stylesStart == 0) chunk.size else stylesStart
        val utf8 = flags and UTF8_FLAG != 0
        val strings = stringOffsets.map { relative -> decodeString(chunk, stringsStart, stringDataEnd, relative, utf8) }
        if (styleCount > 0) validateStyles(chunk, stylesStart, styleOffsets, stringCount)
        activeNamespaceCounts = IntArray(stringCount)
        pool = StringPoolData(
            chunk = chunk,
            headerSize = chunk.headerSize,
            flags = flags,
            utf8 = utf8,
            stringsStart = stringsStart,
            stylesStart = stylesStart,
            stringOffsets = stringOffsets,
            styleOffsets = styleOffsets,
            strings = strings,
        )
    }

    private fun parseResourceMap(chunk: AxmlChunk) {
        requirePool(chunk)
        if (resourceMapChunk != null || elements.isNotEmpty() || namespaces.isNotEmpty() || chunk.headerSize != CHUNK_HEADER_SIZE ||
            (chunk.size - CHUNK_HEADER_SIZE) % 4 != 0
        ) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        val count = (chunk.size - CHUNK_HEADER_SIZE) / 4
        if (count > pool.strings.size) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        resourceIds = LongArray(count) { index -> u4Long(chunk.raw, CHUNK_HEADER_SIZE + index * 4) }
        val targetIndices = resourceIds.indices.filter { resourceIds[it] == ANDROID_ATTR_APP_COMPONENT_FACTORY }
        if (targetIndices.any { pool.strings[it] != APP_COMPONENT_FACTORY } || targetIndices.size > 1) {
            fail(AxmlErrorCode.AXML_RESERVED_COLLISION, chunk.offset, chunk.type)
        }
        resourceMapChunk = chunk
    }

    private fun parseStartNamespace(chunk: AxmlChunk) {
        requirePool(chunk)
        requireNode(chunk, NAMESPACE_CHUNK_SIZE)
        if (namespaces.size >= MAX_NAMESPACES) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED, chunk.offset, chunk.type)
        val prefixIndex = indexOrNone(u4Long(chunk.raw, 16), chunk)
        val uriIndex = stringIndex(u4Long(chunk.raw, 20), chunk)
        val prefix = prefixIndex?.let { pool.strings[it] }
        val uri = pool.strings[uriIndex]
        namespaces.addLast(NamespaceFrame(prefixIndex, uriIndex, elements.size))
        activeNamespaceCounts[uriIndex]++
        semanticEvents += SemanticEvent("namespace-start", uri, prefix ?: "", emptyList(), null)
    }

    private fun parseEndNamespace(chunk: AxmlChunk) {
        requirePool(chunk)
        requireNode(chunk, NAMESPACE_CHUNK_SIZE)
        val prefixIndex = indexOrNone(u4Long(chunk.raw, 16), chunk)
        val uriIndex = stringIndex(u4Long(chunk.raw, 20), chunk)
        val expected = if (namespaces.isEmpty()) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        } else {
            namespaces.removeLast()
        }
        if (expected.prefixIndex != prefixIndex || expected.uriIndex != uriIndex || expected.depth != elements.size) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        activeNamespaceCounts[uriIndex]--
        semanticEvents += SemanticEvent(
            "namespace-end",
            pool.strings[uriIndex],
            prefixIndex?.let { pool.strings[it] } ?: "",
            emptyList(),
            null,
        )
    }

    private fun parseStartElement(chunk: AxmlChunk) {
        requirePool(chunk)
        if (elements.size >= MAX_DEPTH) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED, chunk.offset, chunk.type)
        if (chunk.headerSize != XML_NODE_HEADER_SIZE || chunk.size < START_ELEMENT_MIN_SIZE) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        validateNodeHeader(chunk)
        val namespaceIndex = indexOrNone(u4Long(chunk.raw, 16), chunk)
        val nameIndex = stringIndex(u4Long(chunk.raw, 20), chunk)
        namespaceIndex?.let { requireNamespaceActive(it, chunk) }
        val name = pool.strings[nameIndex]
        val namespace = namespaceIndex?.let { pool.strings[it] }
        val attributeStart = u2(chunk.raw, 24)
        val attributeSize = u2(chunk.raw, 26)
        val attributeCount = u2(chunk.raw, 28)
        if (attributeCount > MAX_ATTRIBUTES) {
            fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED, chunk.offset, chunk.type)
        }
        if (attributeStart < ATTRIBUTE_EXTENSION_SIZE || attributeSize < ATTRIBUTE_SIZE) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        val attributeOffset = checkedAdd(XML_NODE_HEADER_SIZE, attributeStart)
        val attributeBytes = checkedMultiply(attributeCount, attributeSize)
        if (attributeOffset > chunk.size - attributeBytes) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        validateSpecialAttributeIndex(u2(chunk.raw, 30), attributeCount, chunk)
        validateSpecialAttributeIndex(u2(chunk.raw, 32), attributeCount, chunk)
        validateSpecialAttributeIndex(u2(chunk.raw, 34), attributeCount, chunk)

        val parent = elements.lastOrNull()
        val parentIsManifest = parent?.isManifest == true
        val attributes = ArrayList<SemanticAttribute>(attributeCount)
        val seen = HashSet<Pair<String?, String>>()
        var factoryAttributeIndex: Int? = null
        for (index in 0 until attributeCount) {
            val offset = attributeOffset + index * attributeSize
            val attribute = parseAttribute(chunk, offset)
            if (!seen.add(attribute.namespace to attribute.name)) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
            if (attribute.resourceId == ANDROID_ATTR_APP_COMPONENT_FACTORY &&
                (attribute.namespace != ANDROID_NS || attribute.name != APP_COMPONENT_FACTORY)
            ) {
                fail(AxmlErrorCode.AXML_RESERVED_COLLISION, chunk.offset, chunk.type)
            }
            if (attribute.namespace == ANDROID_NS && attribute.name == APP_COMPONENT_FACTORY) {
                if (attribute.resourceId != ANDROID_ATTR_APP_COMPONENT_FACTORY || factoryAttributeIndex != null || attribute.type != TYPE_STRING) {
                    fail(AxmlErrorCode.AXML_RESERVED_COLLISION, chunk.offset, chunk.type)
                }
                factoryAttributeIndex = index
            }
            attributes += attribute
        }

        val isManifest = parent == null
        val isApplication = parentIsManifest && namespace == null && name == "application"
        if (isManifest) {
            if (manifestSeen || namespace != null || name != "manifest") fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
            manifestSeen = true
            packageName = requiredStringAttribute(attributes, null, "package", chunk).also {
                if (!PACKAGE_NAME.matches(it)) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
            }
        } else if (parentIsManifest && namespace == null && name == "uses-sdk") {
            usesSdkCount++
            if (usesSdkCount > 1) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
            minSdk = optionalIntAttribute(attributes, ANDROID_NS, "minSdkVersion", chunk)
                ?: fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
            targetSdk = optionalIntAttribute(attributes, ANDROID_NS, "targetSdkVersion", chunk)
        } else if (isApplication) {
            applicationCount++
            if (applicationCount > 1) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
            val exactPackage = packageName ?: fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
            val appRaw = optionalStringAttribute(attributes, ANDROID_NS, "name", chunk)
            val factoryRaw = factoryAttributeIndex?.let { attributes[it].stringValue }
            applicationClass = normalizeClass(exactPackage, appRaw, chunk)
            factoryClass = normalizeClass(exactPackage, factoryRaw, chunk)
            val androidNamespaceIndex = namespaces.lastOrNull { pool.strings[it.uriIndex] == ANDROID_NS }?.uriIndex
                ?: fail(AxmlErrorCode.AXML_RESERVED_COLLISION, chunk.offset, chunk.type)
            application = ApplicationLocation(
                chunk = chunk,
                chunkOrdinal = chunk.ordinal,
                chunkOffset = chunk.offset,
                attributeOffset = attributeOffset,
                attributeSize = attributeSize,
                attributeCount = attributeCount,
                factoryAttributeIndex = factoryAttributeIndex,
                factoryValue = factoryRaw,
                factoryExtensionSha256 = factoryAttributeIndex?.let { attributes[it].extensionSha256 },
                androidNamespaceIndex = androidNamespaceIndex,
            )
        }
        elements.addLast(ElementFrame(namespaceIndex, nameIndex, isManifest))
        semanticEvents += SemanticEvent("element-start", namespace, name, attributes, null, isApplication)
    }

    private fun parseEndElement(chunk: AxmlChunk) {
        requirePool(chunk)
        requireNode(chunk, END_ELEMENT_SIZE)
        val namespaceIndex = indexOrNone(u4Long(chunk.raw, 16), chunk)
        val nameIndex = stringIndex(u4Long(chunk.raw, 20), chunk)
        val expected = if (elements.isEmpty()) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        } else {
            elements.removeLast()
        }
        if (expected.namespaceIndex != namespaceIndex || expected.nameIndex != nameIndex) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        if (expected.isManifest) manifestClosed = true
        semanticEvents += SemanticEvent(
            "element-end",
            namespaceIndex?.let { pool.strings[it] },
            pool.strings[nameIndex],
            emptyList(),
            null,
        )
    }

    private fun parseCdata(chunk: AxmlChunk) {
        requirePool(chunk)
        requireNode(chunk, CDATA_CHUNK_SIZE)
        val dataIndex = stringIndex(u4Long(chunk.raw, 16), chunk)
        if (u2(chunk.raw, 20) != TYPED_VALUE_SIZE || chunk.raw[22].toInt() != 0) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        val type = chunk.raw[23].toInt() and 0xff
        val data = u4Long(chunk.raw, 24)
        val value = if (type == TYPE_STRING) pool.strings[stringIndex(data, chunk)] else data.toString()
        semanticEvents += SemanticEvent("cdata", null, pool.strings[dataIndex], emptyList(), "$type:$value")
    }

    private fun parseAttribute(chunk: AxmlChunk, offset: Int): SemanticAttribute {
        val namespaceIndex = indexOrNone(u4Long(chunk.raw, offset), chunk)
        namespaceIndex?.let { requireNamespaceActive(it, chunk) }
        val nameIndex = stringIndex(u4Long(chunk.raw, offset + 4), chunk)
        val rawIndex = indexOrNone(u4Long(chunk.raw, offset + 8), chunk)
        if (u2(chunk.raw, offset + 12) != TYPED_VALUE_SIZE || chunk.raw[offset + 14].toInt() != 0) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        val type = chunk.raw[offset + 15].toInt() and 0xff
        val data = u4Long(chunk.raw, offset + 16)
        val stringValue = if (type == TYPE_STRING) pool.strings[stringIndex(data, chunk)] else null
        val rawValue = rawIndex?.let { pool.strings[it] }
        if (stringValue != null && rawValue != null && rawValue != stringValue) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        return SemanticAttribute(
            namespace = namespaceIndex?.let { pool.strings[it] },
            name = pool.strings[nameIndex],
            resourceId = resourceIds.getOrElse(nameIndex) { 0L },
            rawValue = rawValue,
            type = type,
            typedValue = stringValue ?: data.toString(),
            stringValue = stringValue,
            extensionSha256 = if (chunkAttributeSize(chunk) == ATTRIBUTE_SIZE) null else {
                val extensionStart = offset + ATTRIBUTE_SIZE
                hex(sha256(chunk.raw.copyOfRange(extensionStart, offset + chunkAttributeSize(chunk))))
            },
        )
    }

    private fun decodeString(chunk: AxmlChunk, start: Int, end: Int, relative: Int, utf8: Boolean): String {
        val offset = checkedAdd(start, relative)
        if (relative < 0 || offset < start || offset >= end) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        return if (utf8) decodeUtf8(chunk, offset, end) else decodeUtf16(chunk, offset, end)
    }

    private fun decodeUtf8(chunk: AxmlChunk, offset: Int, end: Int): String {
        val utf16Length = readLength8(chunk, offset, end)
        val byteLength = readLength8(chunk, utf16Length.next, end)
        val contentEnd = checkedAdd(byteLength.next, byteLength.value)
        if (contentEnd >= end || chunk.raw[contentEnd].toInt() != 0) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        val value = strictDecode(chunk.raw, byteLength.next, byteLength.value, StandardCharsets.UTF_8, chunk)
        if (value.length != utf16Length.value) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        return value
    }

    private fun decodeUtf16(chunk: AxmlChunk, offset: Int, end: Int): String {
        val length = readLength16(chunk, offset, end)
        val byteLength = checkedMultiply(length.value, 2)
        val contentEnd = checkedAdd(length.next, byteLength)
        if (contentEnd > end - 2 || u2(chunk.raw, contentEnd) != 0) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        val value = strictDecode(chunk.raw, length.next, byteLength, StandardCharsets.UTF_16LE, chunk)
        if (value.length != length.value) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        return value
    }

    private fun validateStyles(chunk: AxmlChunk, stylesStart: Int, offsets: IntArray, stringCount: Int) {
        val validatedOffsets = HashSet<Int>()
        val workBudget = checkedAdd(offsets.size, (chunk.size - stylesStart) / 4)
        var work = 0
        for (relative in offsets) {
            var offset = checkedAdd(stylesStart, relative)
            if (relative < 0 || relative % 4 != 0 || offset < stylesStart || offset > chunk.size - 4) {
                fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
            }
            if (!validatedOffsets.add(relative)) continue
            while (true) {
                if (++work > workBudget) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED, chunk.offset, chunk.type)
                if (offset > chunk.size - 4) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
                val name = u4Long(chunk.raw, offset)
                if (name == NO_INDEX) break
                if (name >= stringCount.toLong() || offset > chunk.size - 12) {
                    fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
                }
                offset = checkedAdd(offset, 12)
            }
        }
    }

    private fun readChunk(offset: Int, ordinal: Int): AxmlChunk {
        if (offset < 0 || offset > bytes.size - CHUNK_HEADER_SIZE) fail(AxmlErrorCode.AXML_MALFORMED, offset)
        val type = u2(bytes, offset)
        val headerSize = u2(bytes, offset + 2)
        val size = toInt(u4Long(bytes, offset + 4), null)
        if (headerSize < CHUNK_HEADER_SIZE || size < headerSize || size % 4 != 0 || offset > bytes.size - size) {
            fail(AxmlErrorCode.AXML_MALFORMED, offset, type)
        }
        return AxmlChunk(ordinal, type, headerSize, size, offset, bytes.copyOfRange(offset, offset + size))
    }

    private fun requirePool(chunk: AxmlChunk) {
        if (!::pool.isInitialized) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
    }

    private fun requireNode(chunk: AxmlChunk, exactSize: Int) {
        if (chunk.headerSize != XML_NODE_HEADER_SIZE || chunk.size != exactSize) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        validateNodeHeader(chunk)
    }

    private fun validateNodeHeader(chunk: AxmlChunk) {
        val comment = u4Long(chunk.raw, 12)
        if (comment != NO_INDEX) stringIndex(comment, chunk)
    }

    private fun requireNamespaceActive(index: Int, chunk: AxmlChunk) {
        if (activeNamespaceCounts.getOrElse(index) { 0 } == 0) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
    }

    private fun chunkAttributeSize(chunk: AxmlChunk): Int = u2(chunk.raw, 26)

    private fun requiredStringAttribute(
        attributes: List<SemanticAttribute>,
        namespace: String?,
        name: String,
        chunk: AxmlChunk,
    ): String = optionalStringAttribute(attributes, namespace, name, chunk)?.takeIf { it.isNotEmpty() }
        ?: fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)

    private fun optionalStringAttribute(
        attributes: List<SemanticAttribute>,
        namespace: String?,
        name: String,
        chunk: AxmlChunk,
    ): String? {
        val matches = attributes.filter { it.namespace == namespace && it.name == name }
        if (matches.size > 1 || matches.firstOrNull()?.type?.let { it != TYPE_STRING } == true) {
            fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        }
        return matches.firstOrNull()?.stringValue
    }

    private fun optionalIntAttribute(
        attributes: List<SemanticAttribute>,
        namespace: String,
        name: String,
        chunk: AxmlChunk,
    ): Int? {
        val matches = attributes.filter { it.namespace == namespace && it.name == name }
        if (matches.size > 1) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        val value = matches.firstOrNull() ?: return null
        if (value.type != TYPE_INT_DEC && value.type != TYPE_INT_HEX) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        val parsed = value.typedValue.toLongOrNull() ?: fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        if (parsed > Int.MAX_VALUE) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        return parsed.toInt()
    }

    private fun normalizeClass(packageName: String, value: String?, chunk: AxmlChunk): String? {
        if (value == null || value.isEmpty()) return null
        val result = when {
            value.startsWith('.') -> packageName + value
            '.' !in value -> "$packageName.$value"
            else -> value
        }
        if (!CLASS_NAME.matches(result)) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        return result
    }

    private fun stringIndex(value: Long, chunk: AxmlChunk): Int {
        if (value < 0 || value >= pool.strings.size.toLong()) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        return value.toInt()
    }

    private fun indexOrNone(value: Long, chunk: AxmlChunk): Int? = if (value == NO_INDEX) null else stringIndex(value, chunk)

    private fun validateSpecialAttributeIndex(value: Int, count: Int, chunk: AxmlChunk) {
        if (value != 0 && value > count) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
    }

    private fun boundedCount(value: Int, maximum: Int, chunk: AxmlChunk): Int {
        if (value < 0) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        if (value > maximum) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED, chunk.offset, chunk.type)
        return value
    }

    private fun toInt(value: Long, chunk: AxmlChunk?): Int {
        if (value < 0 || value > Int.MAX_VALUE) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED, chunk?.offset, chunk?.type)
        return value.toInt()
    }
}

private data class AxmlDocument(
    val xmlHeader: ByteArray,
    val chunks: List<AxmlChunk>,
    val stringPool: StringPoolData,
    val resourceMapChunk: AxmlChunk?,
    val resourceIds: LongArray,
    val application: ApplicationLocation,
    val summary: ParsedSummary,
    val semanticEventsWithoutFactory: List<SemanticEvent>,
    val unknownSummary: UnknownSummary,
)

private data class ParsedSummary(
    val packageName: String,
    val minSdk: Int,
    val targetSdk: Int?,
    val applicationClass: String?,
    val factoryClass: String?,
)

private data class AxmlChunk(
    val ordinal: Int,
    val type: Int,
    val headerSize: Int,
    val size: Int,
    val offset: Int,
    val raw: ByteArray,
)

private data class StringPoolData(
    val chunk: AxmlChunk,
    val headerSize: Int,
    val flags: Int,
    val utf8: Boolean,
    val stringsStart: Int,
    val stylesStart: Int,
    val stringOffsets: IntArray,
    val styleOffsets: IntArray,
    val strings: List<String>,
) {
    val chunkOrdinal: Int get() = chunk.ordinal
}

private data class ApplicationLocation(
    val chunk: AxmlChunk,
    val chunkOrdinal: Int,
    val chunkOffset: Int,
    val attributeOffset: Int,
    val attributeSize: Int,
    val attributeCount: Int,
    val factoryAttributeIndex: Int?,
    val factoryValue: String?,
    val factoryExtensionSha256: String?,
    val androidNamespaceIndex: Int,
)

private data class ElementFrame(val namespaceIndex: Int?, val nameIndex: Int, val isManifest: Boolean)
private data class NamespaceFrame(val prefixIndex: Int?, val uriIndex: Int, val depth: Int)
private data class UnknownSummary(val count: Int, val sha256: String)

private data class SemanticEvent(
    val kind: String,
    val namespace: String?,
    val name: String,
    val attributes: List<SemanticAttribute>,
    val extra: String?,
    val isApplicationElement: Boolean = false,
) {
    fun withoutFactory(): SemanticEvent = if (kind == "element-start" && isApplicationElement) {
        copy(attributes = attributes.filterNot { it.namespace == ANDROID_NS && it.name == APP_COMPONENT_FACTORY })
    } else {
        this
    }
}

private data class SemanticAttribute(
    val namespace: String?,
    val name: String,
    val resourceId: Long,
    val rawValue: String?,
    val type: Int,
    val typedValue: String,
    val stringValue: String?,
    val extensionSha256: String?,
)

private data class LengthValue(val value: Int, val next: Int)

private fun readLength8(chunk: AxmlChunk, offset: Int, end: Int): LengthValue {
    if (offset >= end) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
    val first = chunk.raw[offset].toInt() and 0xff
    return if (first and 0x80 == 0) {
        LengthValue(first, offset + 1)
    } else {
        if (offset + 1 >= end) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        LengthValue(((first and 0x7f) shl 8) or (chunk.raw[offset + 1].toInt() and 0xff), offset + 2)
    }
}

private fun readLength16(chunk: AxmlChunk, offset: Int, end: Int): LengthValue {
    if (offset > end - 2) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
    val first = u2(chunk.raw, offset)
    return if (first and 0x8000 == 0) {
        LengthValue(first, offset + 2)
    } else {
        if (offset > end - 4) fail(AxmlErrorCode.AXML_MALFORMED, chunk.offset, chunk.type)
        LengthValue(((first and 0x7fff) shl 16) or u2(chunk.raw, offset + 2), offset + 4)
    }
}

private fun encodeString(value: String, utf8: Boolean): ByteArray = if (utf8) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    val output = ByteArrayOutputStream()
    writeLength8(output, value.length)
    writeLength8(output, bytes.size)
    output.write(bytes)
    output.write(0)
    output.toByteArray()
} else {
    val bytes = value.toByteArray(StandardCharsets.UTF_16LE)
    val output = ByteArrayOutputStream()
    writeLength16(output, value.length)
    output.write(bytes)
    output.write(0)
    output.write(0)
    output.toByteArray()
}

private fun writeLength8(output: ByteArrayOutputStream, value: Int) {
    if (value < 0 || value > 0x7fff) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
    if (value <= 0x7f) output.write(value) else {
        output.write((value shr 8) or 0x80)
        output.write(value and 0xff)
    }
}

private fun writeLength16(output: ByteArrayOutputStream, value: Int) {
    if (value < 0) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
    if (value <= 0x7fff) {
        output.write(value and 0xff)
        output.write(value ushr 8)
    } else {
        val high = (value ushr 16) or 0x8000
        output.write(high and 0xff)
        output.write(high ushr 8)
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }
}

private fun strictDecode(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    charset: java.nio.charset.Charset,
    chunk: AxmlChunk,
): String = try {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes, offset, length))
        .toString()
} catch (_: CharacterCodingException) {
    fail(AxmlErrorCode.AXML_UNSUPPORTED_ENCODING, chunk.offset, chunk.type)
}

private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
private fun hex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }

private fun updateDigestInt(digest: MessageDigest, value: Int) {
    digest.update((value ushr 24).toByte())
    digest.update((value ushr 16).toByte())
    digest.update((value ushr 8).toByte())
    digest.update(value.toByte())
}

private fun checkedAdd(left: Int, right: Int): Int = try {
    Math.addExact(left, right)
} catch (_: ArithmeticException) {
    fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
}

private fun checkedMultiply(left: Int, right: Int): Int = try {
    Math.multiplyExact(left, right)
} catch (_: ArithmeticException) {
    fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
}

private fun align4(value: Int): Int = checkedAdd(value, 3) and -4

private fun u2(bytes: ByteArray, offset: Int): Int {
    if (offset < 0 || offset > bytes.size - 2) fail(AxmlErrorCode.AXML_MALFORMED)
    return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
}

private fun u4(bytes: ByteArray, offset: Int): Int = u4Long(bytes, offset).let {
    if (it > Int.MAX_VALUE) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
    it.toInt()
}

private fun u4Long(bytes: ByteArray, offset: Int): Long {
    if (offset < 0 || offset > bytes.size - 4) fail(AxmlErrorCode.AXML_MALFORMED)
    return (bytes[offset].toLong() and 0xff) or
        ((bytes[offset + 1].toLong() and 0xff) shl 8) or
        ((bytes[offset + 2].toLong() and 0xff) shl 16) or
        ((bytes[offset + 3].toLong() and 0xff) shl 24)
}

private fun putU2(bytes: ByteArray, offset: Int, value: Int) {
    if (value !in 0..0xffff || offset < 0 || offset > bytes.size - 2) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
}

private fun putU4(bytes: ByteArray, offset: Int, value: Int) {
    if (value < 0 || offset < 0 || offset > bytes.size - 4) fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
    putU4(bytes, offset, value.toLong())
}

private fun putU4(bytes: ByteArray, offset: Int, value: Long) {
    if (value < 0L || value > 0xffff_ffffL || offset < 0 || offset > bytes.size - 4) {
        fail(AxmlErrorCode.AXML_LIMIT_EXCEEDED)
    }
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
    bytes[offset + 2] = (value ushr 16).toByte()
    bytes[offset + 3] = (value ushr 24).toByte()
}

private fun fail(code: AxmlErrorCode, offset: Int? = null, type: Int? = null): Nothing =
    throw AxmlTransformException(code, offset, type)

private const val TYPE_XML = 0x0003
private const val TYPE_STRING_POOL = 0x0001
private const val TYPE_RESOURCE_MAP = 0x0180
private const val TYPE_START_NAMESPACE = 0x0100
private const val TYPE_END_NAMESPACE = 0x0101
private const val TYPE_START_ELEMENT = 0x0102
private const val TYPE_END_ELEMENT = 0x0103
private const val TYPE_CDATA = 0x0104
private const val TYPE_STRING = 0x03
private const val TYPE_INT_DEC = 0x10
private const val TYPE_INT_HEX = 0x11
private const val UTF8_FLAG = 0x100
private const val SORTED_FLAG = 0x1
private const val STRING_POOL_ALLOWED_FLAGS = UTF8_FLAG or SORTED_FLAG
private const val NO_INDEX = 0xffff_ffffL
private const val CHUNK_HEADER_SIZE = 8
private const val XML_HEADER_SIZE = 8
private const val STRING_POOL_HEADER_SIZE = 28
private const val XML_NODE_HEADER_SIZE = 16
private const val ATTRIBUTE_EXTENSION_SIZE = 20
private const val ATTRIBUTE_SIZE = 20
private const val TYPED_VALUE_SIZE = 8
private const val START_ELEMENT_MIN_SIZE = 36
private const val END_ELEMENT_SIZE = 24
private const val NAMESPACE_CHUNK_SIZE = 24
private const val CDATA_CHUNK_SIZE = 28
private const val START_ELEMENT_ATTRIBUTE_COUNT_OFFSET = 28
private const val MAX_CHUNKS = 16_384
private const val MAX_STRINGS = 262_144
private const val MAX_ATTRIBUTES = 16_384
private const val MAX_DEPTH = 1_024
private const val MAX_NAMESPACES = 1_024
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val APP_COMPONENT_FACTORY = "appComponentFactory"
private const val ANDROID_ATTR_APP_COMPONENT_FACTORY = 0x0101_057aL
private const val APPLICATION_PATH = "/manifest/application"
private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
private val CLASS_NAME = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")
