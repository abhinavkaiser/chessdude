package org.offlinechess.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="games", indices=[Index("white"),Index("black"),Index("event"),Index("date"),Index("eco"),Index("fingerprint"),Index("modified")])
data class GameEntity(
    @PrimaryKey val id: String,
    val tags: String, val white: String, val black: String, val event: String, val site: String,
    val date: String, val result: String, val eco: String, val opening: String,
    val whiteElo: Int?, val blackElo: Int?, val category: String,
    val fingerprint: String, val source: String = "", val created: Long = System.currentTimeMillis(),
    val imported: Long = System.currentTimeMillis(), val modified: Long = System.currentTimeMillis(),
    val analyzed: Long? = null, val favorite: Boolean = false, val userTags: String = "", val notes: String = "",
    val preferredSession: String? = null
)
@Entity(tableName="moves", foreignKeys=[ForeignKey(entity=GameEntity::class,parentColumns=["id"],childColumns=["gameId"],onDelete=ForeignKey.CASCADE),ForeignKey(entity=MoveEntity::class,parentColumns=["id"],childColumns=["parent"],onDelete=ForeignKey.CASCADE)], indices=[Index("gameId"),Index("parent"),Index(value=["gameId","parent","ordinal"],unique=true)])
data class MoveEntity(@PrimaryKey val id: String, val gameId: String, val parent: String?, val uci: String, val san: String, val fen: String, val ply: Int, val ordinal: Int, val comment: String, val nags: String, @ColumnInfo(defaultValue="''") val beforeComment: String = "")
@Entity(tableName="collections", indices=[Index(value=["name"],unique=true)])
data class CollectionEntity(@PrimaryKey val id: String, val name: String)
@Entity(tableName="memberships", primaryKeys=["gameId","collectionId"], foreignKeys=[ForeignKey(entity=GameEntity::class,parentColumns=["id"],childColumns=["gameId"],onDelete=ForeignKey.CASCADE),ForeignKey(entity=CollectionEntity::class,parentColumns=["id"],childColumns=["collectionId"],onDelete=ForeignKey.CASCADE)],indices=[Index("collectionId")])
data class Membership(val gameId: String, val collectionId: String)
@Entity(tableName="sessions",foreignKeys=[ForeignKey(entity=GameEntity::class,parentColumns=["id"],childColumns=["gameId"],onDelete=ForeignKey.CASCADE)],indices=[Index("gameId")])
data class SessionEntity(@PrimaryKey val id: String, val gameId: String, val started: Long, val finished: Long? = null, val engine: String = "Stockfish 19", val network: String = "nn-1a298aa575a0.nnue", val threads: Int, val hashMb: Int, val multiPv: Int, val depth: Int, val timeMs: Int, val status: String = "running")
@Entity(tableName="analysis",foreignKeys=[ForeignKey(entity=SessionEntity::class,parentColumns=["id"],childColumns=["sessionId"],onDelete=ForeignKey.CASCADE),ForeignKey(entity=MoveEntity::class,parentColumns=["id"],childColumns=["moveId"],onDelete=ForeignKey.CASCADE)], indices=[Index("sessionId"),Index("moveId"),Index(value=["sessionId","moveId"],unique=true)])
data class AnalysisEntity(@PrimaryKey val id: String, val sessionId: String, val moveId: String, val fen: String, val timestamp: Long, val depth: Int, val elapsed: Long, val nodes: Long, val cp: Int?, val mate: Int?, val pv: String, val bestMove: String, val classification: String = "", val loss: Double = 0.0, val complete: Boolean = true)

@Dao
interface ChessDao {
    @Query("SELECT * FROM games WHERE (:q='' OR white LIKE '%'||:q||'%' OR black LIKE '%'||:q||'%' OR event LIKE '%'||:q||'%' OR site LIKE '%'||:q||'%' OR opening LIKE '%'||:q||'%' OR userTags LIKE '%'||:q||'%') AND (:result='' OR result=:result) AND (:eco='' OR eco LIKE :eco||'%') AND (:category='' OR category=:category) AND (:after='' OR date>=:after) AND (:before='' OR date<=:before) AND (:minRating=0 OR MAX(COALESCE(whiteElo,0),COALESCE(blackElo,0))>=:minRating) AND (:maxRating=4000 OR MIN(COALESCE(whiteElo,4000),COALESCE(blackElo,4000))<=:maxRating) AND (:analyzed=-1 OR (:analyzed=1 AND analyzed IS NOT NULL) OR (:analyzed=0 AND analyzed IS NULL)) AND (:favorite=0 OR favorite=1) AND (:collection='' OR id IN (SELECT gameId FROM memberships WHERE collectionId=:collection)) ORDER BY CASE WHEN :sort='player' THEN white WHEN :sort='event' THEN event WHEN :sort='opening' THEN opening END COLLATE NOCASE ASC, CASE WHEN :sort='rating' THEN MAX(COALESCE(whiteElo,0),COALESCE(blackElo,0)) END DESC, CASE WHEN :sort='date' THEN date END DESC, imported DESC LIMIT :limit OFFSET :offset")
    suspend fun search(q: String="",result: String="",eco: String="", category: String="",after: String="",before: String="",minRating:Int=0,maxRating:Int=4000,analyzed:Int=-1,favorite:Boolean=false,collection:String="",sort:String="import",limit:Int=50,offset:Int=0): List<GameEntity>
    @Query("SELECT COUNT(*) FROM games") fun count(): Flow<Int>
    @Query("SELECT COUNT(*) FROM games WHERE analyzed IS NOT NULL") fun analyzedCount(): Flow<Int>
    @Query("SELECT * FROM games WHERE id=:id") suspend fun game(id:String): GameEntity?
    @Query("SELECT * FROM moves WHERE gameId=:id ORDER BY ply,ordinal") suspend fun moves(id:String): List<MoveEntity>
    @Query("SELECT id FROM games WHERE fingerprint=:fingerprint LIMIT 1") suspend fun duplicate(fingerprint:String): String?
    @Insert suspend fun insertGame(game:GameEntity)
    @Update suspend fun updateGame(game:GameEntity)
    @Insert suspend fun insertMoves(moves:List<MoveEntity>)
    @Update suspend fun updateMove(move:MoveEntity)
    @Query("DELETE FROM moves WHERE id=:id AND parent IS NOT NULL") suspend fun deleteMove(id:String)
    @Query("DELETE FROM games WHERE id IN (:ids)") suspend fun delete(ids:List<String>)
    @Query("SELECT id FROM games ORDER BY imported") suspend fun ids(): List<String>
    @Query("SELECT id FROM games ORDER BY imported LIMIT :limit OFFSET :offset") suspend fun pageIds(limit:Int,offset:Int): List<String>
    @Query("SELECT * FROM collections ORDER BY name") fun collections(): Flow<List<CollectionEntity>>
    @Query("SELECT * FROM collections ORDER BY name") suspend fun allCollections(): List<CollectionEntity>
    @Insert suspend fun addCollection(c:CollectionEntity)
    @Query("DELETE FROM collections WHERE id=:id") suspend fun deleteCollection(id:String)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun addMembership(m:Membership)
    @Query("DELETE FROM memberships WHERE gameId=:game AND collectionId=:collection") suspend fun removeMembership(game:String,collection:String)
    @Query("SELECT * FROM memberships") suspend fun allMemberships(): List<Membership>
    @Query("SELECT * FROM memberships WHERE gameId=:id") suspend fun memberships(id:String): List<Membership>
    @Insert suspend fun addSession(s:SessionEntity)
    @Update suspend fun updateSession(s:SessionEntity)
    @Query("SELECT * FROM sessions WHERE gameId=:id ORDER BY started DESC") suspend fun sessions(id:String): List<SessionEntity>
    @Query("SELECT * FROM sessions") suspend fun allSessions(): List<SessionEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveAnalysis(a:AnalysisEntity)
    @Query("SELECT * FROM analysis WHERE sessionId=:id ORDER BY timestamp") suspend fun analysis(id:String): List<AnalysisEntity>
    @Query("SELECT * FROM analysis") suspend fun allAnalysis(): List<AnalysisEntity>
    @Query("UPDATE sessions SET status='interrupted' WHERE status='running'") suspend fun markInterrupted()
}

@Database(entities=[GameEntity::class,MoveEntity::class,CollectionEntity::class,Membership::class,SessionEntity::class,AnalysisEntity::class],version=3,exportSchema=true)
abstract class ChessDatabase : RoomDatabase() {
    abstract fun dao(): ChessDao
    companion object {
        val MIGRATION_1_2=object: Migration(1,2) { override fun migrate(db:SupportSQLiteDatabase) { db.execSQL("ALTER TABLE games ADD COLUMN preferredSession TEXT") } }
        val MIGRATION_2_3=object: Migration(2,3) { override fun migrate(db:SupportSQLiteDatabase) { db.execSQL("ALTER TABLE moves ADD COLUMN beforeComment TEXT NOT NULL DEFAULT ''") } }
        fun open(context:Context)=Room.databaseBuilder(context,ChessDatabase::class.java,"offline-chess.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3).build()
    }
}
