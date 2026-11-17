// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.report

/** Tiny strict parser for the canonical subset (objects, arrays, strings, integers, booleans, null). Test-only. */
class JsonReaderForTests(private val s: String) {
    private var i = 0

    fun readObject(): Map<String, Any?> {
        val v = value()
        check(i == s.length) { "trailing data" }
        @Suppress("UNCHECKED_CAST")
        return v as Map<String, Any?>
    }

    private fun value(): Any? = when (val c = s[i]) {
        '{' -> obj()
        '[' -> arr()
        '"' -> str()
        't' -> lit("true", true)
        'f' -> lit("false", false)
        'n' -> lit("null", null)
        else -> if (c == '-' || c.isDigit()) num() else error("bad char $c at $i")
    }

    private fun lit(word: String, v: Any?): Any? { check(s.startsWith(word, i)); i += word.length; return v }

    private fun num(): Long { val st = i; i++; while (i < s.length && s[i].isDigit()) i++; return s.substring(st, i).toLong() }

    private fun obj(): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>(); i++
        if (s[i] == '}') { i++; return m }
        while (true) {
            val k = str(); check(s[i++] == ':'); m[k] = value()
            when (s[i++]) { ',' -> continue; '}' -> return m; else -> error("bad object") }
        }
    }

    private fun arr(): List<Any?> {
        val l = mutableListOf<Any?>(); i++
        if (s[i] == ']') { i++; return l }
        while (true) {
            l += value()
            when (s[i++]) { ',' -> continue; ']' -> return l; else -> error("bad array") }
        }
    }

    private fun str(): String {
        check(s[i++] == '"'); val sb = StringBuilder()
        while (true) {
            when (val c = s[i++]) {
                '"' -> return sb.toString()
                '\\' -> when (val e = s[i++]) {
                    'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t'); 'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c'); '"' -> sb.append('"'); '\\' -> sb.append('\\')
                    'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                    else -> error("bad escape $e")
                }
                else -> sb.append(c)
            }
        }
    }
}
