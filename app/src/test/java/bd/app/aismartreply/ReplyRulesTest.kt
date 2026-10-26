package bd.app.aismartreply
import org.junit.Assert.*
import org.junit.Test
class ReplyRulesTest {
    @Test fun englishKeywordDoesNotMatchInsideAnotherWord() {
        val hi = ReplyRule("1", "hi", "hello")
        assertFalse(ReplyRules.matches(hi, "this light"))
        assertTrue(ReplyRules.matches(hi, "Hi!"))
    }
    @Test fun exactMatchNormalizesWhitespaceAndCase() {
        val rule = ReplyRule("1", "order status|অর্ডার কোথায়", "checking", true)
        assertTrue(ReplyRules.matches(rule, "  ORDER   STATUS  "))
        assertFalse(ReplyRules.matches(rule, "my order status"))
        assertTrue(ReplyRules.matches(rule, "অর্ডার কোথায়"))
    }
    @Test fun firstEnabledRuleWinsAndDeletedRulesHaveNoFallback() {
        val first = ReplyRule("1", "লাইট", "first", enabled = false)
        val second = ReplyRule("2", "লাইট", "second")
        assertEquals("second", ReplyRules.resolve(listOf(first, second), "লাইট চাই"))
        assertNull(ReplyRules.resolve(emptyList(), "লাইট চাই"))
    }
    @Test fun regexCharactersAreLiteralAndBlankTriggersAreIgnored() {
        val r = ReplyRule("1", " |a+b| ", "matched")
        assertTrue(ReplyRules.matches(r, "a+b"))
        assertFalse(ReplyRules.matches(r, "aaab"))
        assertFalse(ReplyRules.matches(r, "unrelated"))
    }
    @Test fun disabledFallbackSendsNothingForUnmatchedMessages() {
        assertNull(ReplyRules.templateReply(emptyList(), "অচেনা মেসেজ", false, "সাধারণ উত্তর"))
    }
    @Test fun enabledFallbackOnlyAppliesToUnmatchedMessages() {
        val rules = listOf(ReplyRule("1", "দাম", "নিজের উত্তর"))
        assertEquals("সাধারণ উত্তর", ReplyRules.templateReply(rules, "অন্য মেসেজ", true, "সাধারণ উত্তর"))
        assertEquals("নিজের উত্তর", ReplyRules.templateReply(rules, "দাম কত", false, "সাধারণ উত্তর"))
        assertEquals("নিজের উত্তর", ReplyRules.templateReply(rules, "দাম কত", true, "সাধারণ উত্তর"))
        assertNull(ReplyRules.templateReply(emptyList(), "অন্য মেসেজ", true, "  "))
    }

    @Test fun requiredGroupsMatchLongBanglaAndEnglishMessagesWithoutExactSentence() {
        val rule = ReplyRule("agent", "বিকাশ/bkash & এজেন্ট/agent & নাম্বার/নম্বর/number", "agent details", allWords = true)
        assertTrue(ReplyRules.matches(rule, "ভাই বিকাশের এজেন্ট নাম্বারটা আমাকে দেন"))
        assertTrue(ReplyRules.matches(rule, "Vai Bkash agent number daw please"))
        assertTrue(ReplyRules.matches(rule, "NUMBER please, bkash AGENT"))
        assertFalse(ReplyRules.matches(rule, "বিকাশে সমস্যা হচ্ছে"))
        assertFalse(ReplyRules.matches(rule, "ভাই আপনার ফোন নাম্বার দেন"))
        assertFalse(ReplyRules.matches(rule, "bkash agent account"))
    }
    @Test fun allWordsEnglishBoundariesAndLiteralSymbolsRemainSafe() {
        val rule = ReplyRule("1", "hi & a+b", "yes", allWords = true)
        assertTrue(ReplyRules.matches(rule, "hi a+b"))
        assertFalse(ReplyRules.matches(rule, "this a+b"))
        assertFalse(ReplyRules.matches(rule, "hi aaab"))
        assertFalse(ReplyRules.matches(rule.copy(enabled = false), "hi a+b"))
    }
    @Test fun completeAlternativeGroupsDoNotMixAcrossAlternatives() {
        val rule = ReplyRule("1", "bkash & agent|nagad & personal", "yes", allWords = true)
        assertTrue(ReplyRules.matches(rule, "bkash agent number daw"))
        assertTrue(ReplyRules.matches(rule, "nagad personal number please"))
        assertFalse(ReplyRules.matches(rule, "bkash personal number"))
    }
    @Test fun invalidRequiredGroupsNeverBecomeCatchAllRules() {
        listOf("", "number", "bkash &", "& number", "bkash & /", "bkash & agent|").forEach {
            assertFalse(ReplyRules.validAllWords(it))
        }
        assertFalse(ReplyRules.matches(ReplyRule("1", "bkash &", "wrong", allWords = true), "bkash"))
        assertTrue(ReplyRules.validAllWords("বিকাশ/bkash & নাম্বার/number"))
    }
    @Test fun moreSpecificRuleBeatsBroadRuleButEqualRulesKeepOwnerPriority() {
        val broad = ReplyRule("broad", "number", "general")
        val two = ReplyRule("two", "bkash & number", "bkash", allWords = true)
        val three = ReplyRule("three", "bkash & agent & number", "agent", allWords = true)
        val exact = ReplyRule("exact", "bkash agent number", "exact", exact = true)
        assertEquals("agent", ReplyRules.resolve(listOf(broad, two, three), "bkash agent number daw"))
        assertEquals("exact", ReplyRules.resolve(listOf(broad, three, exact), "bkash agent number"))
        assertEquals("bkash", ReplyRules.resolve(listOf(two, two.copy(id = "second", reply = "second")), "bkash number"))
        assertEquals("agent", ReplyRules.templateReply(listOf(three), "bkash agent number daw", true, "fallback"))
    }
}
