package ah.host.inspector

import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.LinkedHashSet

internal data class ParsedManifest(
    val summary: ManifestSummary,
    val splitMarkers: List<String>,
)

internal class BinaryManifestParser(private val data: SegmentedBytes) {
    fun parse(): ParsedManifest = try {
        parseChecked()
    } catch (_: DataFailure) {
        throw ManifestFailure()
    } catch (_: StructureFailure) {
        throw ManifestFailure()
    }

    private fun parseChecked(): ParsedManifest {
        if (data.size < XML_HEADER_SIZE || data.u2(0) != TYPE_XML || data.u2(2) != XML_HEADER_SIZE) {
            throw ManifestFailure()
        }
        val declaredSize = toInt(data.u4(4))
        if (declaredSize != data.size) throw ManifestFailure()
        var cursor = XML_HEADER_SIZE
        var pool: StringPool? = null
        var resourceMapSeen = false
        val elementStack = ArrayDeque<String>()
        var rootSeen = false
        var rootClosed = false
        var packageName: String? = null
        var splitName: String? = null
        var minSdk = 1
        var targetSdk: Int? = null
        var usesSdkSeen = false
        var applicationSeen = false
        var applicationClass: String? = null
        var factoryClass: String? = null
        val splitMarkers = LinkedHashSet<String>()

        while (cursor < data.size) {
            val chunk = chunkAt(cursor)
            when (chunk.type) {
                TYPE_STRING_POOL -> {
                    if (pool != null || rootSeen) throw ManifestFailure()
                    pool = parseStringPool(chunk)
                }
                TYPE_RESOURCE_MAP -> {
                    if (pool == null || resourceMapSeen || rootSeen) throw ManifestFailure()
                    if ((chunk.size - chunk.headerSize) % 4 != 0) throw ManifestFailure()
                    resourceMapSeen = true
                }
                TYPE_START_NAMESPACE, TYPE_END_NAMESPACE -> {
                    if (pool == null) throw ManifestFailure()
                }
                TYPE_START_ELEMENT -> {
                    val strings = pool ?: throw ManifestFailure()
                    if (rootClosed) throw ManifestFailure()
                    val element = parseStartElement(chunk, strings)
                    val depth = elementStack.size
                    if (depth == 0) {
                        if (rootSeen || element.name != "manifest") throw ManifestFailure()
                        rootSeen = true
                        packageName = requiredUniqueStringAttribute(element, null, "package")
                        splitName = optionalUniqueStringAttribute(element, null, "split")
                        if (!splitName.isNullOrEmpty()) splitMarkers += "MANIFEST_SPLIT_ATTRIBUTE"
                        if (element.attributes.any { it.name == "isFeatureSplit" || it.name == "configForSplit" }) {
                            splitMarkers += "MANIFEST_DYNAMIC_FEATURE_ATTRIBUTE"
                        }
                    } else if (depth == 1 && element.name == "uses-sdk") {
                        if (usesSdkSeen) throw ManifestFailure()
                        usesSdkSeen = true
                        optionalUniqueIntAttribute(element, ANDROID_NS, "minSdkVersion")?.let { minSdk = it }
                        targetSdk = optionalUniqueIntAttribute(element, ANDROID_NS, "targetSdkVersion")
                    } else if (depth == 1 && element.name == "application") {
                        if (applicationSeen) throw ManifestFailure()
                        applicationSeen = true
                        applicationClass = optionalUniqueStringAttribute(element, ANDROID_NS, "name")
                        factoryClass = optionalUniqueStringAttribute(element, ANDROID_NS, "appComponentFactory")
                    }
                    elementStack.addLast(element.name)
                }
                TYPE_END_ELEMENT -> {
                    val strings = pool ?: throw ManifestFailure()
                    val name = parseEndElement(chunk, strings)
                    if (elementStack.isEmpty() || elementStack.removeLast() != name) throw ManifestFailure()
                    if (elementStack.isEmpty()) rootClosed = true
                }
                TYPE_CDATA -> {
                    if (pool == null || elementStack.isEmpty()) throw ManifestFailure()
                }
                else -> throw ManifestFailure()
            }
            cursor = chunk.end
        }
        if (cursor != data.size || !rootSeen || !rootClosed || elementStack.isNotEmpty() || !applicationSeen) {
            throw ManifestFailure()
        }
        val exactPackage = packageName ?: throw ManifestFailure()
        if (!APPLICATION_ID.matches(exactPackage)) throw ManifestFailure()
        if (minSdk <= 0 || targetSdk?.let { it <= 0 } == true) throw ManifestFailure()
        val summary = ManifestSummary(
            packageName = exactPackage,
            packageNameSha256 = MessageDigest.getInstance("SHA-256").digest(exactPackage.toByteArray(Charsets.UTF_8)),
            minSdk = minSdk,
            targetSdk = targetSdk,
            applicationClass = normalizeClassName(exactPackage, applicationClass),
            appComponentFactoryClass = normalizeClassName(exactPackage, factoryClass),
            splitName = splitName,
        )
        return ParsedManifest(summary, immutableList(splitMarkers))
    }

    private fun parseStringPool(chunk: Chunk): StringPool {
        if (chunk.headerSize < STRING_POOL_HEADER_SIZE) throw ManifestFailure()
        val stringCount = toInt(data.u4(chunk.start + 8))
        val styleCount = toInt(data.u4(chunk.start + 12))
        val flags = data.u4(chunk.start + 16)
        val stringsStart = toInt(data.u4(chunk.start + 20))
        val stylesStart = toInt(data.u4(chunk.start + 24))
        val offsetsBytes = checkedTableBytes(checkedAddInt(stringCount, styleCount), 4)
        val offsetsStart = checkedAddInt(chunk.start, chunk.headerSize)
        val offsetsEnd = checkedAddInt(offsetsStart, offsetsBytes)
        val stringsBase = checkedAddInt(chunk.start, stringsStart)
        if (offsetsEnd > stringsBase || stringsBase > chunk.end) throw ManifestFailure()
        val stringsEnd = if (stylesStart == 0) chunk.end else checkedAddInt(chunk.start, stylesStart)
        if (stringsEnd < stringsBase || stringsEnd > chunk.end) throw ManifestFailure()
        val utf8 = flags and UTF8_FLAG != 0L
        val values = ArrayList<String>(stringCount)
        val seenOffsets = LinkedHashSet<Int>()
        repeat(stringCount) { index ->
            val relative = toInt(data.u4(offsetsStart + index * 4))
            if (!seenOffsets.add(relative)) throw ManifestFailure()
            val absolute = checkedAddInt(stringsBase, relative)
            if (absolute < stringsBase || absolute >= stringsEnd) throw ManifestFailure()
            values += if (utf8) readUtf8String(absolute, stringsEnd) else readUtf16String(absolute, stringsEnd)
        }
        return StringPool(values)
    }

    private fun readUtf8String(offset: Int, end: Int): String {
        var cursor = offset
        val utf16Length = readLength8(cursor, end)
        cursor = utf16Length.next
        val byteLength = readLength8(cursor, end)
        cursor = byteLength.next
        if (byteLength.value < 0 || cursor > end - byteLength.value - 1) throw ManifestFailure()
        val value = data.strictUtf8(cursor, byteLength.value)
        if (data.u1(cursor + byteLength.value) != 0 || value.length != utf16Length.value) throw ManifestFailure()
        return value
    }

    private fun readUtf16String(offset: Int, end: Int): String {
        var cursor = offset
        val length = readLength16(cursor, end)
        cursor = length.next
        val byteLength = checkedMultiplyInt(length.value, 2)
        if (cursor > end - byteLength - 2) throw ManifestFailure()
        val value = data.strictUtf16Le(cursor, byteLength)
        if (data.u2(cursor + byteLength) != 0 || value.length != length.value) throw ManifestFailure()
        return value
    }

    private fun parseStartElement(chunk: Chunk, strings: StringPool): Element {
        if (chunk.headerSize < XML_NODE_HEADER_SIZE || chunk.size < START_ELEMENT_MIN_SIZE) throw ManifestFailure()
        val extension = chunk.start + XML_NODE_HEADER_SIZE
        val name = strings.get(data.u4(extension + 4))
        val attributeStart = data.u2(extension + 8)
        val attributeSize = data.u2(extension + 10)
        val attributeCount = data.u2(extension + 12)
        if (attributeSize < ATTRIBUTE_SIZE) throw ManifestFailure()
        val attributesStart = checkedAddInt(extension, attributeStart)
        val attributesBytes = checkedTableBytes(attributeCount, attributeSize)
        if (attributesStart < extension + ATTRIBUTE_EXTENSION_SIZE || attributesStart > chunk.end - attributesBytes) {
            throw ManifestFailure()
        }
        val attributes = ArrayList<Attribute>(attributeCount)
        val keys = LinkedHashSet<String>()
        repeat(attributeCount) { index ->
            val offset = attributesStart + index * attributeSize
            val namespaceIndex = data.u4(offset)
            val attributeName = strings.get(data.u4(offset + 4))
            val rawIndex = data.u4(offset + 8)
            if (data.u2(offset + 12) != TYPED_VALUE_SIZE || data.u1(offset + 14) != 0) throw ManifestFailure()
            val type = data.u1(offset + 15)
            val typedData = data.u4(offset + 16)
            val namespace = if (namespaceIndex == NO_INDEX) null else strings.get(namespaceIndex)
            val rawValue = if (rawIndex == NO_INDEX) null else strings.get(rawIndex)
            val key = "${namespace ?: ""}\u0000$attributeName"
            if (!keys.add(key)) throw ManifestFailure()
            attributes += Attribute(namespace, attributeName, rawValue, type, typedData, strings)
        }
        return Element(name, attributes)
    }

    private fun parseEndElement(chunk: Chunk, strings: StringPool): String {
        if (chunk.headerSize < XML_NODE_HEADER_SIZE || chunk.size < END_ELEMENT_SIZE) throw ManifestFailure()
        return strings.get(data.u4(chunk.start + XML_NODE_HEADER_SIZE + 4))
    }

    private fun requiredUniqueStringAttribute(element: Element, namespace: String?, name: String): String =
        optionalUniqueStringAttribute(element, namespace, name)?.takeIf { it.isNotEmpty() } ?: throw ManifestFailure()

    private fun optionalUniqueStringAttribute(element: Element, namespace: String?, name: String): String? {
        val matches = element.attributes.filter { it.namespace == namespace && it.name == name }
        if (matches.size > 1) throw ManifestFailure()
        return matches.firstOrNull()?.stringValue()
    }

    private fun optionalUniqueIntAttribute(element: Element, namespace: String, name: String): Int? {
        val matches = element.attributes.filter { it.namespace == namespace && it.name == name }
        if (matches.size > 1) throw ManifestFailure()
        return matches.firstOrNull()?.intValue()
    }

    private fun normalizeClassName(packageName: String, value: String?): String? {
        if (value == null || value.isEmpty()) return null
        return when {
            value.startsWith('.') -> packageName + value
            '.' !in value -> "$packageName.$value"
            else -> value
        }
    }

    private fun chunkAt(start: Int): Chunk {
        if (start < 0 || start > data.size - CHUNK_HEADER_SIZE) throw ManifestFailure()
        val type = data.u2(start)
        val headerSize = data.u2(start + 2)
        val size = toInt(data.u4(start + 4))
        if (headerSize < CHUNK_HEADER_SIZE || size < headerSize || start > data.size - size) throw ManifestFailure()
        return Chunk(type, headerSize, size, start, start + size)
    }

    private fun readLength8(offset: Int, end: Int): Length {
        if (offset >= end) throw ManifestFailure()
        val first = data.u1(offset)
        return if (first and 0x80 == 0) {
            Length(first, offset + 1)
        } else {
            if (offset + 1 >= end) throw ManifestFailure()
            Length(((first and 0x7f) shl 8) or data.u1(offset + 1), offset + 2)
        }
    }

    private fun readLength16(offset: Int, end: Int): Length {
        if (offset > end - 2) throw ManifestFailure()
        val first = data.u2(offset)
        return if (first and 0x8000 == 0) {
            Length(first, offset + 2)
        } else {
            if (offset > end - 4) throw ManifestFailure()
            Length(((first and 0x7fff) shl 16) or data.u2(offset + 2), offset + 4)
        }
    }

    private fun toInt(value: Long): Int {
        if (value < 0L || value > Int.MAX_VALUE) throw ManifestFailure()
        return value.toInt()
    }

    private fun checkedAddInt(left: Int, right: Int): Int = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        throw ManifestFailure()
    }

    private fun checkedMultiplyInt(left: Int, right: Int): Int = try {
        Math.multiplyExact(left, right)
    } catch (_: ArithmeticException) {
        throw ManifestFailure()
    }

    private fun checkedTableBytes(count: Int, width: Int): Int {
        if (count < 0 || count > MAX_TABLE_ITEMS) throw ManifestFailure()
        return checkedMultiplyInt(count, width)
    }

    private data class Chunk(val type: Int, val headerSize: Int, val size: Int, val start: Int, val end: Int)
    private data class Length(val value: Int, val next: Int)
    private data class Element(val name: String, val attributes: List<Attribute>)

    private class Attribute(
        val namespace: String?,
        val name: String,
        private val rawValue: String?,
        private val type: Int,
        private val data: Long,
        private val strings: StringPool,
    ) {
        fun stringValue(): String = rawValue ?: if (type == TYPE_STRING) strings.get(data) else throw ManifestFailure()

        fun intValue(): Int {
            if (type != TYPE_INT_DEC && type != TYPE_INT_HEX) throw ManifestFailure()
            if (data > Int.MAX_VALUE) throw ManifestFailure()
            return data.toInt()
        }
    }

    private class StringPool(values: List<String>) {
        private val values = immutableList(values)

        fun get(index: Long): String {
            if (index < 0L || index >= values.size) throw ManifestFailure()
            return values[index.toInt()]
        }
    }

    companion object {
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
        private const val UTF8_FLAG = 0x100L
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
        private const val MAX_TABLE_ITEMS = 1_000_000
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private val APPLICATION_ID = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}
