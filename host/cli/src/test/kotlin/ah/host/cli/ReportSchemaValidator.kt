package ah.host.cli

import java.time.Instant

/** Minimal Draft 2020-12 evaluator for every keyword used by REPORT_V1's checked-in schema. */
internal class ReportSchemaValidator(private val root: Map<String, Any?>) {
    fun validate(instance: Any?) = validateNode(instance, root, "$")

    private fun validateNode(instance: Any?, schema: Map<String, Any?>, path: String) {
        (schema["${'$'}ref"] as? String)?.let { reference ->
            validateNode(instance, resolve(reference), path)
            return
        }
        (schema["anyOf"] as? List<*>)?.let { alternatives ->
            check(alternatives.any { candidate ->
                @Suppress("UNCHECKED_CAST")
                runCatching { validateNode(instance, candidate as Map<String, Any?>, path) }.isSuccess
            }) { "$path does not satisfy anyOf" }
            return
        }

        schema["type"]?.let { declared ->
            val types = if (declared is List<*>) declared.filterIsInstance<String>() else listOf(declared as String)
            check(types.any { type -> matchesType(instance, type) }) { "$path has wrong type; expected $types" }
        }
        schema["const"]?.let { expected -> check(instance == expected) { "$path does not match const" } }
        (schema["enum"] as? List<*>)?.let { values -> check(instance in values) { "$path is outside enum" } }

        when (instance) {
            is String -> validateString(instance, schema, path)
            is Long -> validateInteger(instance, schema, path)
            is List<*> -> validateArray(instance, schema, path)
            is Map<*, *> -> validateObject(instance, schema, path)
        }
    }

    private fun validateString(value: String, schema: Map<String, Any?>, path: String) {
        (schema["minLength"] as? Long)?.let { check(value.codePointCount(0, value.length).toLong() >= it) { "$path is too short" } }
        (schema["maxLength"] as? Long)?.let { check(value.codePointCount(0, value.length).toLong() <= it) { "$path is too long" } }
        (schema["pattern"] as? String)?.let { check(Regex(it).containsMatchIn(value)) { "$path does not match pattern" } }
        if (schema["format"] == "date-time") check(runCatching { Instant.parse(value) }.isSuccess) { "$path is not date-time" }
    }

    private fun validateInteger(value: Long, schema: Map<String, Any?>, path: String) {
        (schema["minimum"] as? Long)?.let { check(value >= it) { "$path is below minimum" } }
        (schema["maximum"] as? Long)?.let { check(value <= it) { "$path is above maximum" } }
    }

    private fun validateArray(value: List<*>, schema: Map<String, Any?>, path: String) {
        (schema["maxItems"] as? Long)?.let { check(value.size.toLong() <= it) { "$path has too many items" } }
        if (schema["uniqueItems"] == true) check(value.distinct().size == value.size) { "$path has duplicate items" }
        @Suppress("UNCHECKED_CAST")
        val itemSchema = schema["items"] as? Map<String, Any?>
        itemSchema?.let { value.forEachIndexed { index, item -> validateNode(item, it, "$path[$index]") } }
    }

    private fun validateObject(value: Map<*, *>, schema: Map<String, Any?>, path: String) {
        check(value.keys.all { it is String }) { "$path has a non-string key" }
        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as? Map<String, Map<String, Any?>> ?: emptyMap()
        (schema["required"] as? List<*>)?.filterIsInstance<String>()?.forEach { name ->
            check(value.containsKey(name)) { "$path is missing $name" }
        }
        if (schema["additionalProperties"] == false) {
            check(value.keys.all(properties::containsKey)) { "$path has additional properties" }
        }
        value.forEach { (key, child) -> properties[key]?.let { validateNode(child, it, "$path.$key") } }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolve(reference: String): Map<String, Any?> {
        check(reference.startsWith("#/")) { "only local schema references are supported" }
        var current: Any? = root
        reference.removePrefix("#/").split('/').forEach { token ->
            val key = token.replace("~1", "/").replace("~0", "~")
            current = (current as Map<String, Any?>).getValue(key)
        }
        return current as Map<String, Any?>
    }

    private fun matchesType(value: Any?, type: String): Boolean = when (type) {
        "null" -> value == null
        "object" -> value is Map<*, *>
        "array" -> value is List<*>
        "string" -> value is String
        "integer" -> value is Long
        "boolean" -> value is Boolean
        else -> error("unsupported schema type: $type")
    }
}
