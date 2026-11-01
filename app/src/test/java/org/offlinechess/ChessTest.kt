package org.offlinechess

import com.github.bhlangonijr.chesslib.Board
import org.junit.Assert.*
import org.junit.Test
import org.offlinechess.core.Chess

class ChessTest {
    private fun perft(b:Board,depth:Int):Long { if(depth==0) return 1; var n=0L; for(m in b.legalMoves()) { b.doMove(m,true); n+=perft(b,depth-1); b.undoMove() }; return n }
    @Test fun startPositionPerft() { val b=Chess.board(Chess.START); assertEquals(20L,perft(b,1)); assertEquals(400L,perft(b,2)); assertEquals(8902L,perft(b,3)); assertEquals(197281L,perft(b,4)) }
    @Test fun kiwipetePerft() { val b=Chess.board("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"); assertEquals(48L,perft(b,1)); assertEquals(2039L,perft(b,2)); assertEquals(97862L,perft(b,3)) }
    @Test fun castleBothSides() { val fen="r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"; assertEquals("O-O",Chess.move(fen,"e1g1").first); assertEquals("O-O-O",Chess.move(fen,"e1c1").first) }
    @Test fun castlingThroughCheckRejected() { val b=Chess.board("4kr2/8/8/8/8/8/8/4K2R w K - 0 1"); assertFalse(b.legalMoves().any { it.toString()=="e1g1" }) }
    @Test fun enPassant() { val result=Chess.move("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2","e5d6"); assertEquals("exd6",result.first); assertTrue(result.second.startsWith("4k3/8/3P4/8/")) }
    @Test fun enPassantCannotExposeKing() { val b=Chess.board("k3r3/8/8/3pP3/8/8/8/4K3 w - d6 0 2"); assertFalse(b.legalMoves().any { it.toString()=="e5d6" }) }
    @Test fun allPromotions() { val b=Chess.board("7k/P7/8/8/8/8/8/4K3 w - - 0 1"); assertEquals(4,b.legalMoves().count { it.from.toString()=="A7" }); assertTrue(Chess.move(b.fen,"a7a8n").first.startsWith("a8=N")) }
    @Test fun mateAndStalemate() { assertEquals("Checkmate",Chess.drawStatus(Chess.board("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1"))); assertEquals("Stalemate",Chess.drawStatus(Chess.board("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1"))) }
    @Test fun drawRulesAndRepetition() {
        val b=Chess.board(Chess.START)
        repeat(2) { listOf("Nf3","Nf6","Ng1","Ng8").forEach { assertTrue(b.doMove(it)) } }
        assertTrue(Chess.drawStatus(b)!!.startsWith("Threefold"))
        repeat(2) { listOf("Nf3","Nf6","Ng1","Ng8").forEach { b.doMove(it) } }
        assertTrue(Chess.drawStatus(b)!!.contains("fivefold"))
        assertTrue(Chess.drawStatus(Chess.board("7k/8/8/8/8/8/8/R3K3 w - - 100 51"))!!.startsWith("50-move"))
        assertTrue(Chess.drawStatus(Chess.board("7k/8/8/8/8/8/8/R3K3 w - - 150 76"))!!.contains("75-move"))
    }
    @Test fun fenRoundTrip() { assertEquals(Chess.START,Chess.board(Chess.START).fen) }
    @Test(expected=IllegalArgumentException::class) fun rejectBadFen() { Chess.board("8/8/8/8/8/8/8/8 w - - 0 1") }
    @Test(expected=IllegalStateException::class) fun rejectIllegalMove() { Chess.move(Chess.START,"e2e5") }
}
