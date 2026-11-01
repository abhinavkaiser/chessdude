package org.offlinechess.data

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import org.offlinechess.core.Chess
import java.io.Reader
import java.io.Writer
import kotlin.coroutines.coroutineContext

/** Offline Chess Backup v1: UTF-8 JSON lines; a header followed by typed Room records. */
class Backup(private val repo:Repository, private val prefs:SharedPreferences) {
    private val gson=Gson()
    suspend fun write(writer:Writer) = repo.db.withTransaction {
        var records=0
        fun row(type:String,value:Any) { records++; writer.write("{\"type\":\"$type\",\"data\":${gson.toJson(value)}}\n") }
        writer.write("{\"format\":\"offline-chess-backup\",\"version\":1}\n")
        row("preferences",prefs.all.mapValues { it.value.toString() })
        repo.dao.allCollections().forEach { row("collection",it) }
        var offset=0
        while(true) {
            val ids=repo.dao.pageIds(50,offset); if(ids.isEmpty()) break
            for(id in ids) {
                coroutineContext.ensureActive()
                row("game",repo.dao.game(id)!!)
                repo.dao.moves(id).forEach { row("move",it) }
                repo.dao.sessions(id).forEach { session -> row("session",session); repo.dao.analysis(session.id).forEach { row("analysis",it) } }
            }; offset+=ids.size
        }
        repo.dao.allMemberships().forEach { row("membership",it) }; writer.write("{\"type\":\"end\",\"data\":{\"records\":$records}}\n"); writer.flush()
    }
    suspend fun restore(reader:Reader) {
        val lines=reader.buffered()
        val header=JSONObject(lines.readLine() ?: error("Empty backup"))
        require(header.optString("format")=="offline-chess-backup" && header.optInt("version")==1) { "Unsupported backup format" }
        var preferences:JSONObject?=null
        repo.db.withTransaction {
            repo.dao.delete(repo.dao.ids())
            repo.dao.allCollections().forEach { repo.dao.deleteCollection(it.id) }
            var count=0; var ended=false
            while(true) {
                coroutineContext.ensureActive()
                val line=lines.readLine() ?: break
                require(++count<=5_000_000 && line.length<8_000_000) { "Backup exceeds safety limits" }
                val row=JSONObject(line); val data=row.getJSONObject("data").toString()
                when(row.getString("type")) {
                    "end" -> { require(JSONObject(data).getInt("records")==count-1) { "Backup record count mismatch" }; ended=true; require(lines.readLine()==null) { "Trailing backup data" }; break }
                    "preferences" -> preferences=JSONObject(data)
                    "collection" -> repo.dao.addCollection(gson.fromJson(data,CollectionEntity::class.java))
                    "game" -> { val g=gson.fromJson(data,GameEntity::class.java); require(g.id.isNotBlank()); g.tags.tagMap(); repo.dao.insertGame(g) }
                    "move" -> { val normalized=JSONObject(data); if(normalized.isNull("beforeComment")) normalized.put("beforeComment",""); val m=gson.fromJson(normalized.toString(),MoveEntity::class.java); Chess.board(m.fen); repo.dao.insertMoves(listOf(m)) }
                    "session" -> { val s=gson.fromJson(data,SessionEntity::class.java); repo.dao.addSession(if(s.status=="running") s.copy(status="interrupted") else s) }
                    "analysis" -> repo.dao.saveAnalysis(gson.fromJson(data,AnalysisEntity::class.java))
                    "membership" -> repo.dao.addMembership(gson.fromJson(data,Membership::class.java))
                    else -> error("Unknown backup record")
                }
            }
            require(ended) { "Backup is truncated: missing completion record" }
            require(count>0) { "Backup contains no records" }
            // Validate complete trees before committing; a corrupt backup rolls everything back.
            for(id in repo.dao.ids()) {
                val moves=repo.dao.moves(id); require(moves.count { it.parent==null }==1) { "Invalid game root" }
                val byId=moves.associateBy { it.id }
                for(m in moves.filter { it.parent!=null }) {
                    val parent=byId[m.parent] ?: error("Move parent belongs to another game")
                    require(m.ply==parent.ply+1 && Chess.move(parent.fen,m.uci).second==m.fen) { "Corrupted move tree" }
                }
            }
        }
        preferences?.let { p -> prefs.edit().clear().also { edit -> p.keys().forEach { edit.putString(it,p.getString(it)) } }.commit() }
    }
}
