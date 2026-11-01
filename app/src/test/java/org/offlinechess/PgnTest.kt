package org.offlinechess
import org.junit.Assert.*
import org.junit.Test
import org.offlinechess.core.*

class PgnTest {
    private val pgn="""
        [Event "Local Open"]
        [White "A"]
        [Black "B"]
        [CustomTag "quoted \"value\" and \\ slash"]
        [Result "*"]

        {opening note} 1.e4 {[%clk 0:05:00] [%cal Ge2e4] [%eval 0.25]} ${'$'}1 (1. d4 d5 (1... Nf6 {nested}) 2. c4) e5 2.Nf3!? Nc6 ; line comment
        3.Bb5 a6 *
    """.trimIndent()
    @Test fun nestedVariationsAndAnnotationsRoundTrip() {
        val g=Pgn.parse(pgn); val output=Pgn.write(g); val again=Pgn.parse(output)
        assertEquals(g.tags,again.tags)
        assertEquals(11,g.nodes.size)
        fun shape(game:PgnGame)=game.nodes.map { listOf(it.uci,it.san,it.fen,it.comment,it.beforeComment,it.nags,it.order.toString()) }.sortedBy { it.joinToString() }
        assertEquals(shape(g),shape(again)); assertEquals(7,Pgn.mainline(g.nodes).size)
        assertTrue(again.nodes.any { it.nags=="${'$'}5" }); assertTrue(again.nodes.any { it.comment.contains("[%clk") })
    }
    @Test fun multipleGamesAndGracefulFailure() {
        val errors=mutableListOf<String>()
        val games=Pgn.read((pgn+"\n[Event \"Broken\"]\n1. e5 *\n[Event \"Next\"]\n1. d4 d5 *").reader()) { _,e -> errors.add(e) }.toList()
        assertEquals(2,games.size); assertEquals(1,errors.size); assertEquals("Next",games.last().tags["Event"])
    }
    @Test fun fenBasedGame() { val g=Pgn.parse("[SetUp \"1\"]\n[FEN \"7k/P7/8/8/8/8/8/4K3 w - - 0 40\"]\n40. a8=Q+ *"); assertTrue(g.nodes.last().fen.startsWith("Q6k")); assertEquals(g.tags,Pgn.parse(Pgn.write(g)).tags) }
    @Test(expected=IllegalStateException::class) fun rejectTruncatedComment() { Pgn.parse("[Event \"x\"]\n1. e4 {truncated") }
    @Test(expected=IllegalStateException::class) fun rejectUnclosedVariation() { Pgn.parse("1. e4 (1. d4 *") }
    @Test(expected=IllegalStateException::class) fun rejectUnsupportedVariant() { Pgn.parse("[Variant \"Atomic\"]\n*") }
    @Test fun commentsBeforeAndAfterMovesSurviveNestedRoundTrip() {
        val game=Pgn.parse("{Root} 1. {Before e4} e4 {After e4} (1. {Before d4} d4 {After d4} d5 (1... {Before Nf6} Nf6 {After Nf6})) 1... {Before e5} e5 {After e5} *")
        val again=Pgn.parse(Pgn.write(game))
        fun shape(g:PgnGame)=g.nodes.map { listOf(it.uci,it.beforeComment,it.comment) }.sortedBy { it.joinToString() }
        assertEquals(shape(game),shape(again))
        assertEquals("Before Nf6",again.nodes.first { it.san=="Nf6" }.beforeComment)
        assertEquals("After e4",again.nodes.first { it.san=="e4" }.comment)
    }
    @Test fun missingTagsAreNotInvented() { val g=Pgn.parse("1. e4 *"); assertEquals(setOf("Result"),g.tags.keys) }
}
