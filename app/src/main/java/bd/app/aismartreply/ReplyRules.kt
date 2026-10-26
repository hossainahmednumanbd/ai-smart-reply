package bd.app.aismartreply

import java.text.Normalizer
import java.util.Locale

data class ReplyRule(val id: String, val keywords: String, val reply: String,
    val exact: Boolean = false, val enabled: Boolean = true, val allWords: Boolean = false)

/** Literal matching only: no user regular expressions or executable instructions. */
object ReplyRules {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    private fun contains(input: String, term: String): Boolean =
        if (term.all { it.code < 128 } && term.first().isLetterOrDigit() && term.last().isLetterOrDigit())
            Regex("(?<![a-z0-9])" + Regex.escape(term) + "(?![a-z0-9])").containsMatchIn(input)
        else input.contains(term)
    // | separates complete alternatives; & requires every group; / separates spellings in a group.
    private fun groups(alternative: String) = alternative.split('&').map { group ->
        group.split('/').map(::normalize).filter { it.isNotEmpty() }
    }
    fun validAllWords(keywords: String): Boolean = keywords.split('|').all { alternative ->
        val parts = groups(alternative)
        parts.size >= 2 && parts.all { it.isNotEmpty() }
    }
    fun matches(rule: ReplyRule, message: String): Boolean {
        if (!rule.enabled) return false
        val input = normalize(message)
        return rule.keywords.split('|').map(::normalize).filter { it.isNotEmpty() }.any { term ->
            when {
                rule.exact -> input == term
                rule.allWords -> {
                    val parts = groups(term)
                    parts.size >= 2 && parts.all { group -> group.any { contains(input, it) } }
                }
                else -> contains(input, term)
            }
        }
    }
    private fun specificity(rule: ReplyRule, message: String): Int {
        if (rule.exact) return 10000
        if (!rule.allWords) return 0
        val input = normalize(message)
        return rule.keywords.split('|').map { groups(it) }
            .filter { parts -> parts.size >= 2 && parts.all { group -> group.any { contains(input, it) } } }
            .maxOfOrNull { it.size } ?: 0
    }
    fun templateReply(rules: List<ReplyRule>, message: String, fallbackEnabled: Boolean, fallback: String): String? =
        resolve(rules, message) ?: fallback.takeIf { fallbackEnabled && it.isNotBlank() }
    fun resolve(rules: List<ReplyRule>, message: String): String? =
        rules.filter { matches(it, message) }.maxByOrNull { specificity(it, message) }?.reply
}
