package org.offlinechess.data

import androidx.room.withTransaction
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import org.offlinechess.core.*
import java.io.Reader
import java.io.Writer
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

fun Map<String,String>.json(): String = JSONObject(this).toString()
fun String.tagMap(): MutableMap<String,String> = JSONObject(this).let { j -> j.keys().asSequence().associateWith { j.getString(it) }.toMutableMap() }
fun MoveEntity.tree()=TreeNode(id,parent,uci,san,fen,ply,ordinal,comment,nags,beforeComment)
fun TreeNode.entity(game:String)=MoveEntity(id,game,parent,uci,san,fen,ply,order,comment,nags,beforeComment)

data class ImportReport(val imported:Int,val duplicates:Int,val errors:List<String>)
enum class DuplicatePolicy { SKIP_EXACT, KEEP_ALL }
class Repository(val db:ChessDatabase) {
    val dao=db.dao()
    private fun fingerprint(game:PgnGame):String {
        val value=game.tags.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }+"\n"+Pgn.write(game.copy(tags=game.tags.toSortedMap().toMutableMap()))
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun entity(id:String,game:PgnGame,source:String=""):GameEntity {
        val t=game.tags
        return GameEntity(id,t.json(),t["White"].orEmpty(),t["Black"].orEmpty(),t["Event"].orEmpty(),t["Site"].orEmpty(),t["Date"].orEmpty(),t["Result"].orEmpty(),t["ECO"].orEmpty(),t["Opening"].orEmpty(),t["WhiteElo"]?.toIntOrNull(),t["BlackElo"]?.toIntOrNull(),t["TimeControlCategory"] ?: category(t["TimeControl"]),fingerprint(game),source)
    }
    private fun category(tc:String?):String { val seconds=tc?.substringBefore('+')?.substringAfter(':')?.toIntOrNull() ?: return ""; return when { seconds<180 -> "Bullet"; seconds<600 -> "Blitz"; seconds<3600 -> "Rapid"; else -> "Classical" } }
    suspend fun saveNew(game:PgnGame,source:String="",policy:DuplicatePolicy=DuplicatePolicy.SKIP_EXACT):String? = db.withTransaction {
        val id=uid(); val entity=entity(id,game,source)
        if(policy==DuplicatePolicy.SKIP_EXACT && dao.duplicate(entity.fingerprint)!=null) return@withTransaction null
        dao.insertGame(entity); dao.insertMoves(game.nodes.map { it.entity(id) }); id
    }
    suspend fun import(reader:Reader,source:String="",policy:DuplicatePolicy=DuplicatePolicy.SKIP_EXACT,progress:(Int)->Unit={}):ImportReport {
        var imported=0; var dupes=0; var processed=0; val errors=mutableListOf<String>()
        for(g in Pgn.read(reader) { index,error -> if(errors.size<100) errors.add("Game $index: $error") }) {
            coroutineContext.ensureActive()
            if(saveNew(g,source,policy)==null) dupes++ else imported++
            progress(++processed)
        }
        return ImportReport(imported,dupes,errors)
    }
    suspend fun load(id:String):PgnGame { val g=dao.game(id) ?: error("Game no longer exists"); return PgnGame(g.tags.tagMap(),dao.moves(id).map { it.tree() }.toMutableList()) }
    suspend fun export(writer:Writer,ids:List<String>?=null) {
        if(ids!=null) ids.forEach { coroutineContext.ensureActive(); writer.write(Pgn.write(load(it))+"\n") }
        else { var offset=0; while(true) { val page=dao.pageIds(100,offset); if(page.isEmpty()) break; for(id in page) { coroutineContext.ensureActive(); writer.write(Pgn.write(load(id))+"\n") }; offset+=page.size } }
        writer.flush()
    }
    suspend fun updateMetadata(old:GameEntity,tags:MutableMap<String,String>,notes:String,userTags:String) {
        val tree=load(old.id); tree.tags.clear(); tree.tags.putAll(tags)
        require(tags["FEN"]==old.tags.tagMap()["FEN"]) { "Change the starting position by creating a new game" }
        val e=entity(old.id,tree,old.source)
        dao.updateGame(e.copy(created=old.created,imported=old.imported,modified=System.currentTimeMillis(),analyzed=old.analyzed,favorite=old.favorite,notes=notes,userTags=userTags,preferredSession=old.preferredSession))
    }
    suspend fun append(game:String,parent:TreeNode,uci:String):TreeNode = db.withTransaction {
        val existing=dao.moves(game).filter { it.parent==parent.id }
        existing.firstOrNull { it.uci==uci }?.let { return@withTransaction it.tree() }
        val (san,fen)=Chess.move(parent.fen,uci)
        val n=TreeNode(parent=parent.id,uci=uci,san=san,fen=fen,ply=parent.ply+1,order=(existing.maxOfOrNull { it.ordinal } ?: -1)+1)
        dao.insertMoves(listOf(n.entity(game)))
        touch(game,true)
        n
    }
    suspend fun touch(id:String,movesChanged:Boolean=false) {
        val game=dao.game(id) ?: return
        dao.updateGame(game.copy(fingerprint=fingerprint(load(id)),modified=System.currentTimeMillis(),analyzed=if(movesChanged) null else game.analyzed))
    }
    suspend fun deleteBranch(game:String,node:String) = db.withTransaction { dao.deleteMove(node); touch(game,true) }
    suspend fun promote(game:String,node:TreeNode) = db.withTransaction {
        require(node.parent!=null) { "The root cannot be promoted" }
        val siblings=dao.moves(game).filter { it.parent==node.parent }.sortedBy { it.ordinal }
        val ordered=listOf(siblings.first { it.id==node.id })+siblings.filter { it.id!=node.id }
        ordered.forEachIndexed { index,n -> dao.updateMove(n.copy(ordinal=-index-1)) }
        ordered.forEachIndexed { index,n -> dao.updateMove(n.copy(ordinal=index)) }
        touch(game,true)
    }

}
