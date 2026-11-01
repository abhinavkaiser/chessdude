package org.offlinechess

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.offlinechess.core.*
import org.offlinechess.engine.EngineSettings
import org.offlinechess.ui.AppModel

@RunWith(AndroidJUnit4::class)
class UiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun navigationFullGameAnalysisAndRotation() {
        lateinit var model:AppModel
        ui.activityRule.scenario.onActivity { model=ViewModelProvider(it)[AppModel::class.java] }
        val id=runBlocking(Dispatchers.IO) { model.repo.saveNew(Pgn.parse("[Event \"UI integration\"]\n[White \"Test white\"]\n[Black \"Test black\"]\n1.e4 e5 2.Nf3 *"),policy=org.offlinechess.data.DuplicatePolicy.KEEP_ALL)!! }
        try {
            model.settings(EngineSettings(depth=5,timeMs=300,multiPv=2))
            model.openGame(id)
            ui.waitUntil(15000) { model.state.value.game?.id==id }
            ui.onNodeWithContentDescription("Chessboard.",substring=true).assertIsDisplayed()
            ui.onNodeWithContentDescription("Open navigation").performClick()
            ui.onNodeWithText("Game Review").performClick()
            ui.onNodeWithText("Analyze complete game").performClick()
            ui.waitUntil(90000) { model.state.value.sessions.any { it.status=="complete" } }
            val state=model.state.value
            assertEquals(4,state.analysis.size)
            assertTrue(state.analysis.drop(1).any { it.classification.isNotEmpty() })
            val session=state.session!!
            assertEquals("complete",session.status)
            val saved=runBlocking(Dispatchers.IO) { model.repo.dao.analysis(session.id) }
            assertEquals(4,saved.size)
            ui.activityRule.scenario.recreate()
            ui.waitUntil(15000) { model.state.value.game?.id==id }
            ui.onNodeWithContentDescription("Open navigation").performClick()
            ui.onNodeWithText("My Games").performClick()
            ui.onNodeWithText("Search players, event, site, opening, tags").assertIsDisplayed()
        } finally { runBlocking(Dispatchers.IO) { model.repo.dao.delete(listOf(id)) }; model.closeEngine() }
    }
    @Test fun analysisFollowsBoardMovesAfterTimedSearchAndGameCanBeSaved() {
        lateinit var model:AppModel
        ui.activityRule.scenario.onActivity { model=ViewModelProvider(it)[AppModel::class.java] }
        ui.waitUntil(15000) { model.state.value.busy==null }
        val id=runBlocking(Dispatchers.IO) { model.repo.saveNew(Pgn.parse("[CustomTag \"preserved\"]\n*"),policy=org.offlinechess.data.DuplicatePolicy.KEEP_ALL)!! }
        try {
            model.settings(EngineSettings(depth=4,timeMs=200,multiPv=2,infinite=false))
            model.openGame(id)
            ui.waitUntil(15000) { model.state.value.game?.id==id }
            ui.onNodeWithTag("notation-panel").assertIsDisplayed()
            ui.onNodeWithTag("evaluation-bar").assertIsDisplayed()
            ui.onNodeWithText("Save game").assertIsDisplayed()
            assertTrue(ui.onAllNodesWithText("CHESS DUDE").fetchSemanticsNodes().isNotEmpty())
            assertEquals("Chess Dude",ui.activity.applicationInfo.loadLabel(ui.activity.packageManager).toString())
            ui.onNodeWithText("Analyze",useUnmergedTree=true).performClick()
            ui.waitUntil(30000) { model.state.value.pvs.isNotEmpty() && !model.state.value.searching }
            assertTrue("Follow-moves must remain enabled after the finite search finishes",model.state.value.live)
            fun tap(file:Int,rank:Int) {
                ui.onNodeWithContentDescription("Chessboard.",substring=true).performTouchInput {
                    click(androidx.compose.ui.geometry.Offset(width*(file+.5f)/8,height*(7-rank+.5f)/8))
                }
            }
            tap(4,1); tap(4,3) // e2-e4, with real board taps.
            ui.waitUntil(15000) { model.state.value.node?.uci=="e2e4" }
            ui.waitUntil(30000) { model.state.value.pvs.isNotEmpty() && !model.state.value.searching }
            assertTrue(model.state.value.live)
            val fen=model.state.value.node!!.fen
            val legal=Chess.board(fen).legalMoves().map { it.toString() }
            assertTrue("Only the new position's legal PVs may be displayed",model.state.value.pvs.all { it.moves.firstOrNull() in legal })
            val root=model.state.value.tree.first { it.parent==null }
            model.select(root)
            model.move("d2d4")
            ui.waitUntil(15000) { model.state.value.node?.uci=="d2d4" }
            ui.waitUntil(30000) { model.state.value.pvs.isNotEmpty() && !model.state.value.searching }
            assertTrue("Switching to a sideline must also follow analysis",model.state.value.live)
            ui.onNodeWithText("Mainline").assertDoesNotExist()
            ui.onNodeWithText("Sideline 1").assertDoesNotExist()
            model.select(root)
            ui.onNodeWithContentDescription("Next move").performClick()
            ui.onNodeWithText("Choose move").assertIsDisplayed()
            assertEquals(root.id,model.state.value.node!!.id)
            ui.onNodeWithText("Cancel").performClick()
            assertEquals(root.id,model.state.value.node!!.id)
            ui.onNodeWithContentDescription("Next move").performClick()
            val alternative=model.state.value.tree.first { it.uci=="d2d4" }
            ui.onNodeWithTag("choose-${alternative.id}").performClick()
            ui.waitUntil(30000) { model.state.value.node?.id==alternative.id && model.state.value.pvs.isNotEmpty() && !model.state.value.searching }
            assertTrue(model.state.value.live)
            ui.onNodeWithContentDescription("Add or edit comment").performClick()
            ui.onNodeWithText("Comment after move").performTextInput("A flexible central plan")
            ui.onNodeWithText("Before move").performClick()
            ui.onNodeWithText("Comment before move").performTextInput("Consider the queen pawn")
            ui.onNodeWithText("Save comment").performClick()
            ui.waitUntil(15000) { model.state.value.node?.beforeComment=="Consider the queen pawn" && model.state.value.busy==null }
            ui.onNodeWithText("A flexible central plan",substring=true).assertIsDisplayed()
            ui.onNodeWithContentDescription("Flip board").performClick()
            ui.onNodeWithTag("evaluation-bar").assertContentDescriptionContains("White segment at top",substring=true)
            ui.onNodeWithContentDescription("Share game or position").performClick()
            ui.onNodeWithText("PGN file").assertIsDisplayed()
            ui.onNodeWithText("Notation text").assertIsDisplayed()
            ui.onNodeWithText("Position image").assertIsDisplayed()
            ui.onNodeWithText("Position FEN").assertIsDisplayed()
            ui.onNodeWithText("Cancel").performClick()
            ui.onNodeWithText("Save game").performClick()
            ui.onNodeWithText("Game name / Event").performTextInput("Board save regression")
            ui.onNodeWithText("White player").performTextInput("Player White")
            ui.onNodeWithText("Black player").performTextInput("Player Black")
            ui.onNodeWithText("White Elo (optional)").performScrollTo().performTextInput("2100")
            ui.onNodeWithText("Black Elo (optional)").performScrollTo().performTextInput("2050")
            ui.onNodeWithText("Save to My Games").performClick()
            ui.waitUntil(15000) { model.state.value.game?.event=="Board save regression" && model.state.value.busy==null }
            val saved=runBlocking(Dispatchers.IO) { model.repo.load(id) }
            assertEquals("preserved",saved.tags["CustomTag"])
            assertEquals("Player White",saved.tags["White"])
            assertEquals("2100",saved.tags["WhiteElo"]); assertEquals("2050",saved.tags["BlackElo"])
            assertEquals(2100,model.state.value.game!!.whiteElo)
            assertEquals("Consider the queen pawn",saved.nodes.first { it.uci=="d2d4" }.beforeComment)
            assertEquals("A flexible central plan",saved.nodes.first { it.uci=="d2d4" }.comment)
            assertTrue(saved.nodes.any { it.uci=="e2e4" }); assertTrue(saved.nodes.any { it.uci=="d2d4" })
            val roundTrip=Pgn.parse(Pgn.write(saved))
            assertEquals(saved.nodes.size,roundTrip.nodes.size)
            ui.onNodeWithContentDescription("Open navigation").performClick()
            ui.onNodeWithText("My Games").performClick()
            model.filter(org.offlinechess.ui.Filters(query="Board save regression"))
            ui.waitUntil(15000) { model.state.value.games.any { it.id==id } }
            ui.onNodeWithText("Player White  —  Player Black").assertIsDisplayed()
        } finally {
            model.stopLive()
            runBlocking(Dispatchers.IO) { model.repo.dao.delete(listOf(id)); model.engine.close() }
        }
    }

}
