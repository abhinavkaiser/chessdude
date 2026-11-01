package org.offlinechess
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.offlinechess.data.ChessDatabase

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),ChessDatabase::class.java)
    @Test fun migrationOneToThreePreservesRows() {
        val name="migration-test.db"
        helper.createDatabase(name,1).apply {
            execSQL("INSERT INTO collections (id,name) VALUES ('test','Preserved')")
            close()
        }
        helper.runMigrationsAndValidate(name,3,true,ChessDatabase.MIGRATION_1_2,ChessDatabase.MIGRATION_2_3).apply {
            query("SELECT name FROM collections WHERE id='test'").use { assertTrue(it.moveToFirst()); assertEquals("Preserved",it.getString(0)) }
            query("PRAGMA table_info(games)").use { var found=false; while(it.moveToNext()) if(it.getString(1)=="preferredSession") found=true; assertTrue(found) }
            query("PRAGMA table_info(moves)").use { var found=false; while(it.moveToNext()) if(it.getString(1)=="beforeComment") { found=true; assertEquals("''",it.getString(4)) }; assertTrue(found) }
            close()
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }
    @Test fun migrationTwoToThreePreservesExistingGamesAndComments() {
        val name="migration-two-three.db"
        helper.createDatabase(name,2).apply {
            execSQL("INSERT INTO games (id,tags,white,black,event,site,date,result,eco,opening,whiteElo,blackElo,category,fingerprint,source,created,imported,modified,analyzed,favorite,userTags,notes,preferredSession) VALUES ('game','{}','A','B','Study','','','*','','',2100,2050,'','fingerprint','',1,1,1,NULL,1,'','Preserved notes',NULL)")
            execSQL("INSERT INTO moves (id,gameId,parent,uci,san,fen,ply,ordinal,comment,nags) VALUES ('root','game',NULL,'','','${org.offlinechess.core.Chess.START}',0,0,'Preserved comment','')")
            close()
        }
        helper.runMigrationsAndValidate(name,3,true,ChessDatabase.MIGRATION_2_3).apply {
            query("SELECT comment,beforeComment FROM moves WHERE id='root'").use { assertTrue(it.moveToFirst()); assertEquals("Preserved comment",it.getString(0)); assertEquals("",it.getString(1)) }
            query("SELECT whiteElo,notes,favorite FROM games WHERE id='game'").use { assertTrue(it.moveToFirst()); assertEquals(2100,it.getInt(0)); assertEquals("Preserved notes",it.getString(1)); assertEquals(1,it.getInt(2)) }
            close()
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }

}
