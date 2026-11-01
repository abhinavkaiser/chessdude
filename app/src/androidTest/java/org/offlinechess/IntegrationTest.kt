package org.offlinechess

import android.content.Context
import android.provider.Settings
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.offlinechess.core.*
import org.offlinechess.data.*
import org.offlinechess.engine.*
import java.io.StringWriter

@RunWith(AndroidJUnit4::class)
class IntegrationTest {
    private lateinit var context:Context
    private lateinit var db:ChessDatabase
    private lateinit var repo:Repository
    private lateinit var name:String
    @Before fun setup() { context=ApplicationProvider.getApplicationContext(); name="test-${uid()}.db"; reopen() }
    private fun reopen() { db=Room.databaseBuilder(context,ChessDatabase::class.java,name).addMigrations(ChessDatabase.MIGRATION_1_2,ChessDatabase.MIGRATION_2_3).build(); repo=Repository(db) }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    @Test fun importPersistReloadAnalyzePersistReloadOffline()=runBlocking {
        assertEquals("Run this suite in airplane mode",1,Settings.Global.getInt(context.contentResolver,Settings.Global.AIRPLANE_MODE_ON,0))
        val text="""[Event "Offline integration"]
[White "Ada"]
[Black "Ben"]
[WhiteFideId "12345"]
[CustomTag "preserved"]
[Result "*"]

1.e4 {comment} (1.d4 d5) e5 2.Nf3 Nc6 *

[Event "Other tournament"]
[White "Ada"]
[Black "Ben"]
[Result "*"]

1.e4 e5 2.Nf3 Nc6 *
"""
        val report=repo.import(text.reader()); assertEquals(2,report.imported); assertTrue(report.errors.isEmpty())
        val id=repo.dao.search(q="Ada").first { it.event=="Offline integration" }.id
        assertEquals("12345",repo.load(id).tags["WhiteFideId"])
        assertEquals("preserved",repo.load(id).tags["CustomTag"])
        val original=repo.load(id); assertEquals(7,original.nodes.size)
        db.close(); reopen()
        assertEquals(Pgn.write(original),Pgn.write(repo.load(id)))
        val node=Pgn.mainline(repo.load(id).nodes)[2]
        val engine=Engine(context)
        try {
            val settings=EngineSettings(depth=8,timeMs=500,multiPv=3)
            val updates=mutableListOf<Pv>()
            val result=engine.analyze(node.fen,settings) { updates.add(it) }
            assertEquals(3,result.size); assertTrue(updates.isNotEmpty()); assertTrue(result.first().nodes>0)
            assertTrue(Chess.board(node.fen).legalMoves().any { it.toString()==result.first().moves.first() })
            val session=SessionEntity(uid(),id,System.currentTimeMillis(),threads=1,hashMb=32,multiPv=3,depth=8,timeMs=500,status="position")
            repo.dao.addSession(session)
            val p=result.first()
            val a=AnalysisEntity(uid(),session.id,node.id,node.fen,System.currentTimeMillis(),p.depth,p.time,p.nodes,p.cp,p.mate,com.google.gson.Gson().toJson(result),p.moves.first())
            repo.dao.saveAnalysis(a)
            db.close(); reopen()
            assertEquals(a,repo.dao.analysis(session.id).single()); assertEquals(session,repo.dao.sessions(id).single())
            engine.close()
            val restart=engine.analyze(Chess.START,settings.copy(multiPv=1))
            assertTrue(restart.first().moves.isNotEmpty())
        } finally { engine.close() }
    }
    @Test fun variationPromotionDeletionAndAppend()=runBlocking {
        val id=repo.saveNew(Pgn.parse("1.e4 (1.d4 d5) e5 *"))!!
        val before=repo.load(id); val alternative=before.nodes.first { it.san=="d4" }
        repo.promote(id,alternative)
        assertEquals("d4",Pgn.mainline(repo.load(id).nodes)[1].san)
        repo.deleteBranch(id,alternative.id)
        val remaining=repo.load(id)
        assertFalse(remaining.nodes.any { it.id==alternative.id || it.san=="d5" })
        val root=remaining.nodes.first { it.parent==null }
        repo.append(id,root,"c2c4")
        assertEquals(2,repo.load(id).nodes.count { it.parent==root.id })
        assertEquals(remaining.tags,Pgn.parse(Pgn.write(repo.load(id))).tags)
    }
    @Test fun twoThousandGameImportAndPagination()=runBlocking {
        val pgn=buildString { repeat(2000) { round -> append("[Event \"Bulk\"]\n[Round \"$round\"]\n[White \"Bulk White\"]\n[Black \"Bulk Black\"]\n1.e4 e5 2.Nf3 Nc6 *\n\n") } }
        val report=repo.import(pgn.reader())
        assertEquals(2000,report.imported); assertTrue(report.errors.isEmpty())
        val lastPage=repo.dao.search(q="Bulk",limit=40,offset=1960)
        assertEquals(40,lastPage.size)
        assertEquals(5,repo.load(lastPage.first().id).nodes.size)
    }
    @Test fun searchCancellationMateAndRestart()=runBlocking {
        val engine=Engine(context)
        try {
            val started=CompletableDeferred<Unit>()
            val job=launch(Dispatchers.Default) { engine.analyze(Chess.START,EngineSettings(infinite=true)) { started.complete(Unit) } }
            withTimeout(30000) { started.await() }; job.cancelAndJoin()
            val fen="7k/5K2/6Q1/8/8/8/8/8 w - - 0 1"
            val result=engine.analyze(fen,EngineSettings(depth=10,timeMs=1000,multiPv=1))
            assertNotNull(result.first().mate); assertTrue(result.first().mate!!>0)
            val legal=Chess.board(fen).legalMoves().map { it.toString() }
            assertTrue(result.all { it.moves.first() in legal })
            val terminal=engine.analyze("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1",EngineSettings(depth=4,timeMs=200,multiPv=1))
            assertEquals(0,terminal.single().mate)
            assertEquals(1f,Evaluation.whiteFraction(terminal.single(),false))
            val drawn=engine.analyze("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1",EngineSettings(depth=4,timeMs=200,multiPv=1))
            assertEquals(0,drawn.single().cp)
            engine.close()
            assertTrue(engine.analyze(Chess.START,EngineSettings(depth=4,timeMs=300,multiPv=1)).isNotEmpty())
        } finally { engine.close() }
    }
    @Test fun backupRestoreDuplicateHandlingAndCascades()=runBlocking {
        val game=Pgn.parse("[Event \"A\"]\n[Date \"2026.11.01\"]\n1. {Before} e4 {After} (1. {Alternative before} d4 d5) e5 *")
        val id=repo.saveNew(game)!!
        assertNull(repo.saveNew(game))
        val different=Pgn.parse("[Event \"B\"]\n[Date \"2026.11.01\"]\n1.e4 (1.d4 d5) e5 *")
        assertNotNull(repo.saveNew(different))
        val collection=CollectionEntity(uid(),"Study"); repo.dao.addCollection(collection); repo.dao.addMembership(Membership(id,collection.id))
        val prefs=context.getSharedPreferences("test-backup",Context.MODE_PRIVATE); prefs.edit().putString("depth","12").commit()
        val backup=StringWriter(); Backup(repo,prefs).write(backup)
        val before=repo.dao.ids().toSet()
        try { Backup(repo,prefs).restore(backup.toString().substringBeforeLast("{\"type\":\"end\"").reader()); fail("Truncated backup was accepted") } catch(_:IllegalArgumentException) { }
        assertEquals(before,repo.dao.ids().toSet())
        repo.dao.delete(listOf(id)); assertTrue(repo.dao.moves(id).isEmpty()); assertTrue(repo.dao.memberships(id).isEmpty())
        Backup(repo,prefs).restore(backup.toString().reader())
        assertEquals(before,repo.dao.ids().toSet()); assertEquals(1,repo.dao.memberships(id).size); assertEquals("12",prefs.getString("depth",null))
        assertEquals(1,repo.dao.search(q="A",after="2026.01.01",collection=collection.id).size)
        assertEquals(Pgn.write(game),Pgn.write(repo.load(id)))
        val legacy=backup.toString().lineSequence().map { line ->
            if(line.isBlank()) line else org.json.JSONObject(line).let { row ->
                if(row.optString("type")=="move") row.getJSONObject("data").remove("beforeComment")
                row.toString()
            }
        }.joinToString("\n")
        Backup(repo,prefs).restore(legacy.reader())
        assertTrue(repo.load(id).nodes.all { it.beforeComment.isEmpty() })
        assertTrue(repo.load(id).nodes.any { it.comment=="After" })
        prefs.edit().clear().commit(); Unit
    }
}
