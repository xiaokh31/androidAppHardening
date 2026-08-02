package ah.host.inspector

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.zip.Adler32
import java.util.zip.CRC32
import java.util.zip.Deflater

internal data class SyntheticZipEntry(
    val name: String,
    val data: ByteArray,
    val method: Int = 0,
    val flags: Int = 0x0800,
    val localUncompressedSizeDelta: Int = 0,
    val dataDescriptor: Boolean = false,
)

internal object SyntheticApkFixtures {
    fun manifest(
        packageName: String? = "ah.fixtures.inspector",
        minSdk: Int = 29,
        targetSdk: Int = 36,
        applicationClass: String? = ".FixtureApplication",
        factoryClass: String? = ".FixtureFactory",
        splitName: String? = null,
    ): ByteArray {
        val strings = StringTable()
        val manifestName = strings.add("manifest")
        val packageAttribute = strings.add("package")
        val splitAttribute = strings.add("split")
        val usesSdkName = strings.add("uses-sdk")
        val minSdkAttribute = strings.add("minSdkVersion")
        val targetSdkAttribute = strings.add("targetSdkVersion")
        val applicationName = strings.add("application")
        val nameAttribute = strings.add("name")
        val factoryAttribute = strings.add("appComponentFactory")
        val androidNamespace = strings.add(ANDROID_NAMESPACE)
        val packageValue = packageName?.let(strings::add)
        val splitValue = splitName?.let(strings::add)
        val applicationValue = applicationClass?.let(strings::add)
        val factoryValue = factoryClass?.let(strings::add)

        val xmlBody = ByteWriter()
        xmlBody.bytes(strings.encodePool())
        val manifestAttributes = ArrayList<XmlAttribute>()
        if (packageValue != null) manifestAttributes += XmlAttribute(NO_INDEX, packageAttribute, packageValue, TYPE_STRING, packageValue)
        if (splitValue != null) manifestAttributes += XmlAttribute(NO_INDEX, splitAttribute, splitValue, TYPE_STRING, splitValue)
        xmlBody.bytes(startElement(manifestName, manifestAttributes))
        xmlBody.bytes(
            startElement(
                usesSdkName,
                listOf(
                    XmlAttribute(androidNamespace, minSdkAttribute, NO_INDEX, TYPE_INT_DEC, minSdk),
                    XmlAttribute(androidNamespace, targetSdkAttribute, NO_INDEX, TYPE_INT_DEC, targetSdk),
                ),
            ),
        )
        xmlBody.bytes(endElement(usesSdkName))
        val applicationAttributes = ArrayList<XmlAttribute>()
        if (applicationValue != null) {
            applicationAttributes += XmlAttribute(androidNamespace, nameAttribute, applicationValue, TYPE_STRING, applicationValue)
        }
        if (factoryValue != null) {
            applicationAttributes += XmlAttribute(androidNamespace, factoryAttribute, factoryValue, TYPE_STRING, factoryValue)
        }
        xmlBody.bytes(startElement(applicationName, applicationAttributes))
        xmlBody.bytes(endElement(applicationName))
        xmlBody.bytes(endElement(manifestName))

        val body = xmlBody.toByteArray()
        return ByteWriter().apply {
            u2(TYPE_XML)
            u2(8)
            u4(8 + body.size)
            bytes(body)
        }.toByteArray()
    }

    fun manifestWithDuplicateStringOffset(): ByteArray {
        val bytes = manifest()
        val stringPoolStart = 8
        val offsetsStart = stringPoolStart + 28
        putU4(bytes, offsetsStart + 4, readU4(bytes, offsetsStart))
        return bytes
    }

    fun manifestWithOversizedResourceMap(): ByteArray {
        val base = manifest()
        val poolStart = 8
        val poolSize = readU4(base, poolStart + 4).toInt()
        val poolEnd = poolStart + poolSize
        val stringCount = readU4(base, poolStart + 8).toInt()
        val resourceMap = ByteWriter().apply {
            u2(0x0180)
            u2(8)
            u4(8 + (stringCount + 1) * 4)
            repeat(stringCount + 1) { u4(0) }
        }.toByteArray()
        val result = ByteWriter().apply {
            bytes(base.copyOfRange(0, poolEnd))
            bytes(resourceMap)
            bytes(base.copyOfRange(poolEnd, base.size))
        }.toByteArray()
        putU4(result, 4, result.size)
        return result
    }

    fun dex(vararg descriptors: String): ByteArray {
        require(descriptors.isNotEmpty())
        val encodedStrings = descriptors.map { descriptor ->
            ByteWriter().apply {
                uleb128(descriptor.length)
                bytes(descriptor.toByteArray(Charsets.UTF_8))
                u1(0)
            }.toByteArray()
        }
        val stringIdsOffset = DEX_HEADER_SIZE
        val typeIdsOffset = stringIdsOffset + descriptors.size * 4
        val classDefsOffset = typeIdsOffset + descriptors.size * 4
        val stringDataOffset = classDefsOffset + descriptors.size * CLASS_DEF_SIZE
        val stringOffsets = ArrayList<Int>(descriptors.size)
        var cursor = stringDataOffset
        for (encoded in encodedStrings) {
            stringOffsets += cursor
            cursor += encoded.size
        }
        val bytes = ByteArray(cursor)
        "dex\n035\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
        putU4(bytes, 32, bytes.size)
        putU4(bytes, 36, DEX_HEADER_SIZE)
        putU4(bytes, 40, 0x12345678)
        putU4(bytes, 56, descriptors.size)
        putU4(bytes, 60, stringIdsOffset)
        putU4(bytes, 64, descriptors.size)
        putU4(bytes, 68, typeIdsOffset)
        putU4(bytes, 96, descriptors.size)
        putU4(bytes, 100, classDefsOffset)
        putU4(bytes, 104, bytes.size - stringDataOffset)
        putU4(bytes, 108, stringDataOffset)
        descriptors.indices.forEach { index ->
            putU4(bytes, stringIdsOffset + index * 4, stringOffsets[index])
            putU4(bytes, typeIdsOffset + index * 4, index)
            putU4(bytes, classDefsOffset + index * CLASS_DEF_SIZE, index)
            encodedStrings[index].copyInto(bytes, stringOffsets[index])
        }
        sealDex(bytes)
        return bytes
    }

    fun dexWithDeclaredUtf16Length(length: Int): ByteArray {
        val bytes = dex("Lfixture/Main;")
        val stringIdsOffset = readU4(bytes, 60).toInt()
        val stringDataOffset = readU4(bytes, stringIdsOffset).toInt()
        val encodedLength = ByteWriter().apply { uleb128(length) }.toByteArray()
        encodedLength.copyInto(bytes, stringDataOffset)
        sealDex(bytes)
        return bytes
    }

    fun apk(entries: List<SyntheticZipEntry>): ByteArray {
        val output = ByteWriter()
        val central = ByteWriter()
        for (entry in entries) {
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            val compressed = if (entry.method == METHOD_DEFLATED) deflate(entry.data) else entry.data
            val crc = CRC32().apply { update(entry.data) }.value
            val effectiveFlags = if (entry.dataDescriptor) entry.flags or 0x0008 else entry.flags
            val localOffset = output.size()
            output.u4(LOCAL_SIGNATURE)
            output.u2(20)
            output.u2(effectiveFlags)
            output.u2(entry.method)
            output.u2(0)
            output.u2(0)
            output.u4(if (entry.dataDescriptor) 0 else crc)
            output.u4(if (entry.dataDescriptor) 0 else compressed.size)
            output.u4(if (entry.dataDescriptor) 0 else entry.data.size + entry.localUncompressedSizeDelta)
            output.u2(nameBytes.size)
            output.u2(0)
            output.bytes(nameBytes)
            output.bytes(compressed)
            if (entry.dataDescriptor) {
                output.u4(DATA_DESCRIPTOR_SIGNATURE)
                output.u4(crc)
                output.u4(compressed.size)
                output.u4(entry.data.size)
            }

            central.u4(CENTRAL_SIGNATURE)
            central.u2(20)
            central.u2(20)
            central.u2(effectiveFlags)
            central.u2(entry.method)
            central.u2(0)
            central.u2(0)
            central.u4(crc)
            central.u4(compressed.size)
            central.u4(entry.data.size)
            central.u2(nameBytes.size)
            central.u2(0)
            central.u2(0)
            central.u2(0)
            central.u2(0)
            central.u4(0)
            central.u4(localOffset)
            central.bytes(nameBytes)
        }
        val centralOffset = output.size()
        val centralBytes = central.toByteArray()
        output.bytes(centralBytes)
        output.u4(EOCD_SIGNATURE)
        output.u2(0)
        output.u2(0)
        output.u2(entries.size)
        output.u2(entries.size)
        output.u4(centralBytes.size)
        output.u4(centralOffset)
        output.u2(0)
        return output.toByteArray()
    }

    fun baselineEntries(
        manifest: ByteArray = manifest(),
        dexDescriptors: List<List<String>> = listOf(listOf("Lfixture/Main;"), listOf("Lfixture/Secondary;")),
        additional: List<SyntheticZipEntry> = emptyList(),
    ): List<SyntheticZipEntry> {
        val entries = ArrayList<SyntheticZipEntry>()
        entries += SyntheticZipEntry("AndroidManifest.xml", manifest)
        dexDescriptors.forEachIndexed { index, descriptors ->
            val name = if (index == 0) "classes.dex" else "classes${index + 1}.dex"
            entries += SyntheticZipEntry(name, dex(*descriptors.toTypedArray()))
        }
        entries += SyntheticZipEntry("lib/armeabi-v7a/libfixture.so", byteArrayOf(1))
        entries += SyntheticZipEntry("lib/arm64-v8a/libfixture.so", byteArrayOf(2))
        entries += SyntheticZipEntry("lib/x86/libfixture.so", byteArrayOf(3))
        entries += SyntheticZipEntry("lib/x86_64/libfixture.so", byteArrayOf(4))
        entries += additional
        return entries
    }

    fun mutateCentralLocalOffset(bytes: ByteArray, value: Long): ByteArray {
        val result = bytes.copyOf()
        val central = findSignature(result, CENTRAL_SIGNATURE)
        putU4(result, central + 42, value)
        return result
    }

    fun mutateZip64Eocd(bytes: ByteArray): ByteArray {
        val result = bytes.copyOf()
        val eocd = findSignature(result, EOCD_SIGNATURE)
        putU2(result, eocd + 8, 0xffff)
        putU2(result, eocd + 10, 0xffff)
        return result
    }

    private fun startElement(nameIndex: Int, attributes: List<XmlAttribute>): ByteArray = ByteWriter().apply {
        u2(TYPE_START_ELEMENT)
        u2(16)
        u4(36 + attributes.size * 20)
        u4(1)
        u4(NO_INDEX)
        u4(NO_INDEX)
        u4(nameIndex)
        u2(20)
        u2(20)
        u2(attributes.size)
        u2(0)
        u2(0)
        u2(0)
        for (attribute in attributes) {
            u4(attribute.namespace)
            u4(attribute.name)
            u4(attribute.rawValue)
            u2(8)
            u1(0)
            u1(attribute.type)
            u4(attribute.data)
        }
    }.toByteArray()

    private fun endElement(nameIndex: Int): ByteArray = ByteWriter().apply {
        u2(TYPE_END_ELEMENT)
        u2(16)
        u4(24)
        u4(1)
        u4(NO_INDEX)
        u4(NO_INDEX)
        u4(nameIndex)
    }.toByteArray()

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(9, true)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count <= 0) error("deflater made no progress")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun sealDex(bytes: ByteArray) {
        val signature = MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size))
        signature.copyInto(bytes, 12)
        val adler = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value
        putU4(bytes, 8, adler)
    }

    private fun findSignature(bytes: ByteArray, signature: Long): Int {
        for (index in bytes.indices.reversed()) {
            if (index <= bytes.size - 4 && readU4(bytes, index) == signature) return index
        }
        error("signature not found")
    }

    private data class XmlAttribute(
        val namespace: Int,
        val name: Int,
        val rawValue: Int,
        val type: Int,
        val data: Int,
    )

    private class StringTable {
        private val indices = LinkedHashMap<String, Int>()

        fun add(value: String): Int = indices.getOrPut(value) { indices.size }

        fun encodePool(): ByteArray {
            val values = indices.keys.toList()
            val strings = ByteWriter()
            val offsets = ArrayList<Int>(values.size)
            for (value in values) {
                offsets += strings.size()
                val encoded = value.toByteArray(Charsets.UTF_8)
                strings.length8(value.length)
                strings.length8(encoded.size)
                strings.bytes(encoded)
                strings.u1(0)
            }
            val stringBytes = strings.toByteArray()
            val stringsStart = 28 + values.size * 4
            return ByteWriter().apply {
                u2(TYPE_STRING_POOL)
                u2(28)
                u4(stringsStart + stringBytes.size)
                u4(values.size)
                u4(0)
                u4(0x100)
                u4(stringsStart)
                u4(0)
                offsets.forEach(::u4)
                bytes(stringBytes)
            }.toByteArray()
        }
    }

    private class ByteWriter {
        private val output = ByteArrayOutputStream()

        fun size(): Int = output.size()
        fun u1(value: Int) = output.write(value and 0xff)
        fun u2(value: Int) {
            u1(value)
            u1(value ushr 8)
        }
        fun u4(value: Int) = u4(value.toLong() and 0xffff_ffffL)
        fun u4(value: Long) {
            repeat(4) { shift -> u1((value ushr (shift * 8)).toInt()) }
        }
        fun bytes(value: ByteArray) = output.write(value)
        fun uleb128(value: Int) {
            var remaining = value
            do {
                var byte = remaining and 0x7f
                remaining = remaining ushr 7
                if (remaining != 0) byte = byte or 0x80
                u1(byte)
            } while (remaining != 0)
        }
        fun length8(value: Int) {
            if (value <= 0x7f) {
                u1(value)
            } else {
                u1((value ushr 8) or 0x80)
                u1(value)
            }
        }
        fun toByteArray(): ByteArray = output.toByteArray()
    }

    private fun putU2(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU4(bytes: ByteArray, offset: Int, value: Int) = putU4(bytes, offset, value.toLong())

    private fun putU4(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { shift -> bytes[offset + shift] = (value ushr (shift * 8)).toByte() }
    }

    private fun readU4(bytes: ByteArray, offset: Int): Long =
        bytes[offset].toLong() and 0xff or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)

    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    private const val NO_INDEX = -1
    private const val TYPE_XML = 0x0003
    private const val TYPE_STRING_POOL = 0x0001
    private const val TYPE_START_ELEMENT = 0x0102
    private const val TYPE_END_ELEMENT = 0x0103
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val DEX_HEADER_SIZE = 112
    private const val CLASS_DEF_SIZE = 32
    private const val METHOD_DEFLATED = 8
    private const val LOCAL_SIGNATURE = 0x04034b50L
    private const val CENTRAL_SIGNATURE = 0x02014b50L
    private const val EOCD_SIGNATURE = 0x06054b50L
    private const val DATA_DESCRIPTOR_SIGNATURE = 0x08074b50L
}
