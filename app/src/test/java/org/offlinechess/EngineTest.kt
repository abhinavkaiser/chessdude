package org.offlinechess
import org.junit.Assert.*
import org.junit.Test
import org.offlinechess.engine.*

class EngineTest {
    @Test fun completeInfoLine() { val p=UciParser.parse("info depth 19 seldepth 25 multipv 3 score cp -42 nodes 123456 nps 234567 hashfull 123 time 527 pv e2e4 e7e5")!!; assertEquals(19,p.depth); assertEquals(-42,p.cp); assertEquals(3,p.multipv); assertEquals(123456L,p.nodes); assertEquals(234567L,p.nps); assertEquals(123,p.hashfull); assertEquals(527L,p.time); assertEquals(listOf("e2e4","e7e5"),p.moves); assertEquals("+0.42",p.score(false)) }
    @Test fun mateAndIgnoredMessages() { val p=UciParser.parse("info depth 10 score mate -3 nodes 42 pv h8g8")!!; assertEquals(-3,p.mate); assertEquals("M3",p.score(false)); assertNull(UciParser.parse("info string NNUE evaluation")); assertNull(UciParser.parse("bestmove e2e4")) }
    @Test fun deliveringMateDoesNotLoseForcedMate() { assertEquals("Excellent",Classification.classify(Pv(mate=1,moves=listOf("g6g7")),Pv(mate=0),"g6g8",8).first) }
    @Test fun classifications() { val before=Pv(cp=50,moves=listOf("e2e4")); assertEquals("Best move",Classification.classify(before,Pv(cp=-50),"e2e4",20).first); assertEquals("Forced move",Classification.classify(before,Pv(cp=500),"d2d4",1).first); assertEquals("Blunder",Classification.classify(before,Pv(cp=700),"d2d4",20).first); assertEquals("Blunder",Classification.classify(Pv(mate=3),Pv(cp=-400),"d2d4",20).first) }
}
