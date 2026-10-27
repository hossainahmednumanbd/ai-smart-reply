package bd.app.aismartreply

/**
 * Isolated transcript accumulator for the optional Voice Writer feature.
 * Keeps confirmed speech separate from temporary recognition hypotheses.
 * No changes to notification listening, automatic replies, or account state.
 */
class VoiceTranscriptBuffer {
    private val confirmed = StringBuilder()
    private var interim = ""

    @Synchronized fun acceptInterim(text: String) {
        interim = text.trim()
    }

    @Synchronized fun acceptFinal(text: String) {
        val part = text.trim()
        if (part.isNotEmpty()) {
            if (confirmed.isNotEmpty()) confirmed.append(' ')
            confirmed.append(part)
        }
        interim = ""
    }

    @Synchronized fun preview(): String =
        listOf(confirmed.toString(), interim).filter { it.isNotBlank() }.joinToString(" ")

    @Synchronized fun snapshot(): String = confirmed.toString().trim()

    @Synchronized fun reset() {
        confirmed.clear()
        interim = ""
    }
}
