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
}
