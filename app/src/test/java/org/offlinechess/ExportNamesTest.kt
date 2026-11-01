package org.offlinechess
import org.junit.Test
import org.junit.Assert.*
import org.offlinechess.core.ExportNames

class ExportNamesTest {
    @Test fun namesDescribeTheActualGameAndKeepUnicode() {
        assertEquals("Carlsen_vs_Gukesh_Tata_Steel_2026-11-01.pgn",ExportNames.pgn(mapOf("White" to "Carlsen","Black" to "Gukesh","Event" to "Tata Steel","Date" to "2026.11.01")))
        assertEquals("ChessDude_Analysis.pgn",ExportNames.pgn(emptyMap()))
        assertEquals("José_vs_B.pgn",ExportNames.pgn(mapOf("White" to "José","Black" to "B")))
    }
    @Test fun namesCannotContainPathsOrControlCharacters() {
        val name=ExportNames.pgn(mapOf("White" to "../A/../../", "Black" to "B\\C\nD"))
        assertFalse(name.contains('/')); assertFalse(name.contains('\\')); assertFalse(name.contains('\n')); assertFalse(name.startsWith('.'))
        assertTrue(name.endsWith(".pgn"))
    }
    @Test fun ratingsAreOptionalButValidatedWhenProvided() {
        listOf(""," ","0","2100","4000").forEach { assertTrue(ExportNames.validRating(it)) }
        listOf("-1","4001","abc","21.5","99999999999999999999").forEach { assertFalse(ExportNames.validRating(it)) }
    }
}
