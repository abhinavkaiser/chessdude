package org.offlinechess

import org.junit.Assert.*
import org.junit.Test
import org.offlinechess.core.*
import org.offlinechess.engine.*

class BoardPresentationTest {
    @Test fun evaluationNormalizesSideToMove() {
        assertTrue(Evaluation.whiteFraction(Pv(cp=300),true)>.5f)
        assertTrue(Evaluation.whiteFraction(Pv(cp=300),false)<.5f)
        assertEquals(Evaluation.whiteFraction(Pv(cp=300),true),Evaluation.whiteFraction(Pv(cp=-300),false))
        assertEquals(.5f,Evaluation.whiteFraction(null,true))
        assertTrue(Evaluation.description(Pv(cp=300),false).startsWith("Black is ahead"))
        assertTrue(Evaluation.description(Pv(cp=0),true).startsWith("Equal position"))
        assertEquals("No evaluation yet",Evaluation.description(null,true))
    }
    @Test fun terminalUciScoresHaveNoVariation() {
        val mate=UciParser.parse("info depth 0 score mate 0")!!
        assertEquals(0,mate.mate); assertTrue(mate.moves.isEmpty())
        assertEquals(1f,Evaluation.whiteFraction(mate,false))
        assertEquals(.5f,Evaluation.whiteFraction(UciParser.parse("info depth 0 score cp 0"),true))
    }
    @Test fun mateFillsCorrectSideOfBar() {
        assertEquals(1f,Evaluation.whiteFraction(Pv(mate=2),true))
        assertEquals(0f,Evaluation.whiteFraction(Pv(mate=2),false))
        assertEquals(1f,Evaluation.whiteFraction(Pv(mate=0),false))
    }
    @Test fun notationInterleavesNestedVariationsAtTheirBranchPoints() {
        val game=Pgn.parse("1.e4 (1.d4 d5 (1...Nf6 2.c4) 2.Nf3) e5 2.Nf3 (2.Bc4 Bc5) Nc6 *")
        val rows=Notation.paragraphs(game.nodes)
        val parts=rows.flatMap { it.parts }.filter { it.node!=null && !it.comment }
        assertEquals(listOf("e4","d4","d5","Nf6","c4","Nf3","e5","Nf3","Bc4","Bc5","Nc6"),parts.map { it.node!!.san })
        assertEquals(game.nodes.size-1,parts.size)
        assertEquals(parts.size,parts.map { it.node!!.id }.distinct().size)
        assertTrue(rows.flatMap { it.parts }.any { it.depth==2 })
        assertEquals(listOf("e4","e5","Nf3","Nc6"),rows.flatMap { it.parts }.filter { it.depth==0 }.mapNotNull { it.node?.san })
        val branch=game.nodes.first { it.san=="d4" }
        val folded=Notation.paragraphs(game.nodes,setOf(branch.id))
        assertTrue(folded.flatMap { it.parts }.any { it.branch==branch.id && it.text.contains("…") })
        assertFalse(folded.flatMap { it.parts }.any { it.node?.san=="c4" })
        assertTrue(folded.flatMap { it.parts }.any { it.node?.san=="Nc6" })
    }
    @Test fun commentsStayInlineAndUseNormalWeightEvenInGameMoves() {
        val game=Pgn.parse("1. {Plan before} e4 {Plan after} (1.d4 {Alternative}) e5 *")
        val parts=Notation.paragraphs(game.nodes).flatMap { it.parts }
        assertTrue(parts.first().comment && parts.first().before)
        assertEquals("{Plan before}",parts.first().text)
        assertEquals("e4",parts[1].node!!.san)
        assertEquals("{Plan after}",parts[2].text)
        assertEquals("!?",Notation.glyphs("${'$'}5"))
    }
    @Test fun notationNumbersUseStartingFen() {
        val g=Pgn.parse("[FEN \"7k/P7/8/8/8/8/8/4K3 w - - 0 40\"]\n40.a8=Q+ *")
        assertEquals("40. a8=Q+",Notation.moveLabel(g.nodes.last(),g.nodes.associateBy { it.id }))
    }
}
