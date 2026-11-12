package org.offlinechess

import org.junit.Test
import org.junit.Assert.*
import org.offlinechess.core.*

class ChessBaseStyleTest {
    private fun render(pgn: String, showComments: Boolean = true): String {
        val g = Pgn.parse(pgn)
        return Notation.paragraphs(g.nodes, emptySet(), showComments).joinToString("\n") { it.parts.joinToString("") { p -> p.text } }
    }
    @Test fun chessBaseExportWithVariationsAndComments() {
        val pgn = "[Event \"x\"]\n[White \"A\"]\n[Black \"B\"]\n[Result \"*\"]\n\n" +
            "1. d4 {[%clk 1:30:00]} {Solid start} 1... Nf6 ( 1... d5 {the main alternative} 2. c4 e6 ) 2. Nc3 $1 d6 {[%emt 0:00:12]} 3. e4 ( 3. Nf3 Nbd7 { a quiet sideline } ) 3... Nbd7 *"
        val out = render(pgn)
        println("RENDER: " + out)
        assertTrue("sideline must show", out.contains("(") && out.contains("d5"))
        assertTrue("real comment must show", out.contains("Solid start"))
        assertFalse("clock tags are metadata", out.contains("[%"))
        assertFalse("no line breaks inside the notation", out.contains("\n"))
    }
    @Test fun semicolonAndFirstMoveComments() {
        val pgn = "[Event \"x\"]\n[Result \"*\"]\n\n{Before move one} 1. e4 e5 ; line comment\n2. Nf3 (2. f4 {King's Gambit}) 2... Nc6 *"
        val out = render(pgn); println("RENDER2: " + out)
        assertTrue(out.contains("Before move one")); assertTrue(out.contains("King's Gambit")); assertTrue(out.contains("line comment"))
    }
}
