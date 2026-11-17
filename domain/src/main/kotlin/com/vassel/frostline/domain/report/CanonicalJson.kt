// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.report

/**
 * Deterministic JSON: object keys sorted by code point, no whitespace, integers only (floating point text is not portable
 * between languages), strings escaped like Python `json.dumps(ensure_ascii=False)`. The independent verifier re-serialises
 * parsed manifests with exactly these rules, so both sides hash the same bytes.
 */
object CanonicalJson {
    fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is String -> string(sb, v)
            is Map<*, *> -> {
                sb.append('{')
                val keys = v.keys.map { require(it is String) { "JSON keys must be strings" }; it }.sorted()
                keys.forEachIndexed { i, k ->
                    if (i > 0) sb.append(',')
                    string(sb, k); sb.append(':'); write(sb, v[k])
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                v.forEachIndexed { i, e -> if (i > 0) sb.append(','); write(sb, e) }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("Unsupported JSON value: ${v::class.simpleName}")
        }
    }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c == '\b' -> sb.append("\\b")
            c == '\u000c' -> sb.append("\\f")
            c < ' ' -> sb.append("\\u").append("%04x".format(c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }
}
