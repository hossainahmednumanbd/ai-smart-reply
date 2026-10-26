package bd.app.aismartreply

import java.text.Normalizer
import java.util.Locale

data class ReplyRule(val id: String, val keywords: String, val reply: String,
    val exact: Boolean = false, val enabled: Boolean = true)

/** Literal matching only: no user regular expressions or executable instructions. */
object ReplyRules {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    fun matches(rule: ReplyRule, message: String): Boolean {
        if (!rule.enabled) return false
        val input = normalize(message)
        return rule.keywords.split('|').map(::normalize).filter { it.isNotEmpty() }.any { term ->
            if (rule.exact) input == term
            else if (term.all { it.code < 128 } && term.first().isLetterOrDigit() && term.last().isLetterOrDigit())
                Regex("(?<![a-z0-9])" + Regex.escape(term) + "(?![a-z0-9])").containsMatchIn(input)
            else input.contains(term)
        }
    }
    fun templateReply(rules: List<ReplyRule>, message: String, fallbackEnabled: Boolean, fallback: String): String? =
        resolve(rules, message) ?: fallback.takeIf { fallbackEnabled && it.isNotBlank() }
    fun resolve(rules: List<ReplyRule>, message: String): String? =
        rules.firstOrNull { matches(it, message) }?.reply
}
