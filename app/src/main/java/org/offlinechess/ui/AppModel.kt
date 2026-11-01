package org.offlinechess.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.offlinechess.core.*
import org.offlinechess.data.*
import org.offlinechess.engine.*
import java.util.concurrent.atomic.AtomicLong

const val PAGE_SIZE=40
data class Filters(val query:String="",val result:String="",val eco:String="",val category:String="",val after:String="",val before:String="",val minRating:Int=0,val maxRating:Int=4000,val analyzed:Int=-1,val favorite:Boolean=false,val collection:String="",val sort:String="import")
data class AppState(
    val screen:String="Home",val games:List<GameEntity> = emptyList(),val filters:Filters=Filters(),val page:Int=0,val hasMore:Boolean=false,
    val game:GameEntity?=null,val tree:List<TreeNode> = emptyList(),val node:TreeNode?=null,
    val continuations:List<TreeNode> = emptyList(),val pvs:List<Pv> = emptyList(),val live:Boolean=false,val searching:Boolean=false,val sessions:List<SessionEntity> = emptyList(),val session:SessionEntity?=null,val analysis:List<AnalysisEntity> = emptyList(),
    val busy:String?=null,val message:String?=null,val settings:EngineSettings=EngineSettings(),val flipped:Boolean=false,val theme:String="Forest",val dark:Boolean=false,val sound:Boolean=true
)
class AppModel(app:Application):AndroidViewModel(app) {
    val db=ChessDatabase.open(app); val repo=Repository(db); val engine=Engine(app)
    val prefs=app.getSharedPreferences("settings",Context.MODE_PRIVATE)
    val total=repo.dao.count().catch { notice("Cannot read the database: ${it.message}"); emit(0) }.stateIn(viewModelScope,SharingStarted.Eagerly,0)
    val analyzedCount=repo.dao.analyzedCount().catch { notice("Cannot read analysis count: ${it.message}"); emit(0) }.stateIn(viewModelScope,SharingStarted.Eagerly,0)
    val collections=repo.dao.collections().catch { notice("Cannot read collections: ${it.message}"); emit(emptyList()) }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    private val _state=MutableStateFlow(AppState()); val state=_state.asStateFlow()
    private var liveJob:Job?=null; private var workJob:Job?=null
    private val generation=AtomicLong(); private var searchJob:Job?=null
    init {
        readSettings()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.dao.markInterrupted(); refresh()
                prefs.getString("lastGame",null)?.let { id -> open(id,false,prefs.getString("lastNode",null)) }
            } catch(e:Exception) { notice("Unable to open saved data: ${e.message}. Your database has been retained.") }
        }
    }
    fun notice(text:String?) { _state.update { it.copy(message=text) } }
    fun navigate(screen:String) { _state.update { it.copy(screen=screen) }; if(screen=="Home" || screen=="My Games") refreshAsync() }
    fun settings(value:EngineSettings) {
        val s=value.copy(threads=value.threads.coerceIn(1,8),hashMb=value.hashMb.coerceIn(16,256),multiPv=value.multiPv.coerceIn(1,8),depth=value.depth.coerceIn(1,99),timeMs=value.timeMs.coerceIn(0,600000))
        _state.update { it.copy(settings=s) }
        prefs.edit().putString("threads",s.threads.toString()).putString("hash",s.hashMb.toString()).putString("pv",s.multiPv.toString()).putString("depth",s.depth.toString()).putString("time",s.timeMs.toString()).putString("infinite",s.infinite.toString()).putString("battery",s.battery.toString()).apply()
    }
    private fun readSettings() {
        fun i(k:String,d:Int)=prefs.getString(k,null)?.toIntOrNull() ?: d
        _state.update { it.copy(settings=EngineSettings(i("threads",1),i("hash",32),i("pv",3),i("depth",18),i("time",1500),prefs.getString("infinite","false").toBoolean(),prefs.getString("battery","true").toBoolean()),theme=prefs.getString("theme","Forest") ?: "Forest",dark=prefs.getString("dark","false").toBoolean(),sound=prefs.getString("sound","true").toBoolean()) }
    }
    fun appearance(theme:String=state.value.theme,dark:Boolean=state.value.dark,sound:Boolean=state.value.sound) { _state.update { it.copy(theme=theme,dark=dark,sound=sound) }; prefs.edit().putString("theme",theme).putString("dark",dark.toString()).putString("sound",sound.toString()).apply() }
    fun flip() { _state.update { it.copy(flipped=!it.flipped) } }
    fun filter(f:Filters) { _state.update { it.copy(filters=f,page=0) }; refreshAsync() }
    fun page(delta:Int) { _state.update { it.copy(page=(it.page+delta).coerceAtLeast(0)) }; refreshAsync() }
    fun refreshAsync() { searchJob?.cancel(); searchJob=viewModelScope.launch(Dispatchers.IO) { try { delay(120); refresh() } catch(e:CancellationException) { throw e } catch(e:Exception) { notice("Library query failed: ${e.message}") } } }
    private suspend fun refresh() {
        val snap=state.value; val f=snap.filters
        val games=repo.dao.search(f.query,f.result,f.eco,f.category,f.after,f.before,f.minRating,f.maxRating,f.analyzed,f.favorite,f.collection,f.sort,PAGE_SIZE+1,snap.page*PAGE_SIZE)
        if(state.value.filters==f && state.value.page==snap.page) _state.update { it.copy(games=games.take(PAGE_SIZE),hasMore=games.size>PAGE_SIZE) }
    }
    private fun launchWork(title:String,block:suspend ()->Unit) {
        if(workJob?.isActive==true) { notice("Finish or cancel the current operation first"); return }
        workJob=viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busy=title) }
            try { block() } catch(e:CancellationException) { notice("Operation cancelled; completed records were kept"); throw e } catch(e:Exception) { notice(e.message ?: "Operation failed") }
            finally { _state.update { it.copy(busy=null) }; runCatching { refresh() }.onFailure { notice("Cannot refresh library: ${it.message}") } }
        }
    }
    fun cancelWork() { workJob?.cancel(); engine.requestStop() }
    fun importPgn(uri:Uri,keepDuplicates:Boolean=false) { launchWork("Importing PGN…") {
        val app=getApplication<Application>()
        val report=app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            repo.import(reader,uri.lastPathSegment.orEmpty(),if(keepDuplicates) DuplicatePolicy.KEEP_ALL else DuplicatePolicy.SKIP_EXACT) { n -> _state.update { it.copy(busy="Importing PGN · $n games processed") } }
        } ?: error("Cannot open selected file")
        notice("Imported ${report.imported} games · ${report.duplicates} exact duplicates skipped"+(if(report.errors.isEmpty()) "" else "\n${report.errors.joinToString("\n")}")); navigate("My Games")
    } }
    fun export(uri:Uri,ids:List<String>?=null) { launchWork("Exporting PGN…") { getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { repo.export(it,ids) } ?: error("Cannot write file"); notice("PGN exported") } }
    fun backup(uri:Uri) { launchWork("Creating backup…") { getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { Backup(repo,prefs).write(it) } ?: error("Cannot write backup"); notice("Backup created") } }
    fun restore(uri:Uri) { stopLive(); launchWork("Restoring backup…") { getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { Backup(repo,prefs).restore(it) } ?: error("Cannot read backup"); _state.update { AppState(settings=it.settings,busy=it.busy) }; readSettings(); notice("Backup restored") } }
    fun newGame(fen:String=Chess.START) { launchWork("Creating game…") {
        Chess.board(fen)
        val tags=linkedMapOf("Result" to "*"); if(fen!=Chess.START) { tags["FEN"]=fen; tags["SetUp"]="1" }
        val game=PgnGame(tags, mutableListOf(TreeNode(fen=fen)))
        val id=repo.saveNew(game,policy=DuplicatePolicy.KEEP_ALL)!!; open(id)
    } }
    fun openGame(id:String) { viewModelScope.launch(Dispatchers.IO) { try { open(id) } catch(e:Exception) { notice(e.message ?: "Unable to open game") } } }
    private suspend fun open(id:String,navigate:Boolean=true,nodeId:String?=null) {
        stopLive()
        val game=repo.dao.game(id) ?: return
        val nodes=repo.dao.moves(id).map { it.tree() }; val sessions=repo.dao.sessions(id)
        val session=sessions.firstOrNull { it.id==game.preferredSession } ?: sessions.firstOrNull()
        val analysis=session?.let { repo.dao.analysis(it.id) }.orEmpty()
        _state.update { it.copy(continuations=emptyList(),game=game,tree=nodes,node=nodes.firstOrNull { n -> n.id==nodeId } ?: nodes.firstOrNull { n -> n.parent==null },sessions=sessions,session=session,analysis=analysis,pvs=emptyList(),screen=if(navigate) "Chessboard" else it.screen) }
        prefs.edit().putString("lastGame",id).apply()
    }
    @Synchronized fun select(node:TreeNode) {
        _state.update { it.copy(node=node,pvs=emptyList(),continuations=emptyList()) }; prefs.edit().putString("lastNode",node.id).apply()
        if(state.value.live) beginLive()
    }
    fun step(delta:Int) {
        val s=state.value; val n=s.node ?: return
        if(delta<0) { s.tree.firstOrNull { it.id==n.parent }?.let(::select); return }
        val children=s.tree.filter { it.parent==n.id }.sortedBy { it.order }
        if(children.size>1) _state.update { it.copy(continuations=children) }
        else children.firstOrNull()?.let(::select)
    }
    fun dismissContinuations() { _state.update { it.copy(continuations=emptyList()) } }
    fun chooseContinuation(id:String) {
        val s=state.value
        if(s.continuations.none { it.id==id }) return
        s.tree.firstOrNull { it.id==id && it.parent==s.node?.id }?.let(::select)
    }
    fun comment(nodeId:String,before:String,after:String,nags:String) {
        val gameId=state.value.game?.id ?: return
        launchWork("Saving comment…") {
            val latest=repo.dao.moves(gameId).firstOrNull { it.id==nodeId } ?: error("Move no longer exists")
            val updated=latest.copy(beforeComment=before,comment=after,nags=nags)
            repo.dao.updateMove(updated); repo.touch(gameId)
            val tree=updated.tree()
            _state.update { if(it.game?.id!=gameId) it else it.copy(tree=it.tree.map { n -> if(n.id==nodeId) tree else n },node=if(it.node?.id==nodeId) tree else it.node) }
        }
    }
    fun move(uci:String) { val s=state.value; val parent=s.node ?: return; val g=s.game ?: return
        viewModelScope.launch(Dispatchers.IO) { try { val n=repo.append(g.id,parent,uci); val nodes=repo.dao.moves(g.id).map { it.tree() }; _state.update { it.copy(tree=nodes) }; select(n) } catch(e:Exception) { notice(e.message ?: "Illegal move") } }
    }
    fun annotate(comment:String,nags:String) { val s=state.value; val n=s.node ?: return; val game=s.game ?: return
        launchWork("Saving annotation…") { val updated=n.copy(comment=comment,nags=nags); repo.dao.updateMove(updated.entity(game.id)); repo.touch(game.id); _state.update { it.copy(tree=it.tree.map { node -> if(node.id==n.id) updated else node },node=updated) } }
    }
    fun deleteBranch() { val snap=state.value; val game=snap.game ?: return; val node=snap.node ?: return; val parent=snap.tree.firstOrNull { it.id==node.parent } ?: return
        stopLive(); launchWork("Deleting variation…") { repo.deleteBranch(game.id,node.id); val nodes=repo.dao.moves(game.id).map { it.tree() }; _state.update { it.copy(tree=nodes,node=parent) }; notice("Move and its continuations deleted") }
    }
    fun promote() { val snap=state.value; val game=snap.game ?: return; val node=snap.node ?: return
        stopLive(); launchWork("Updating main line…") { repo.promote(game.id,node); val nodes=repo.dao.moves(game.id).map { it.tree() }; _state.update { it.copy(tree=nodes,node=nodes.first { n -> n.id==node.id }) }; notice("Variation promoted") }
    }
    fun favorite(g:GameEntity) { viewModelScope.launch(Dispatchers.IO) { try { val latest=repo.dao.game(g.id) ?: return@launch; val updated=latest.copy(favorite=!latest.favorite); repo.dao.updateGame(updated); _state.update { it.copy(game=if(it.game?.id==g.id) updated else it.game) }; refresh() } catch(e:Exception) { notice("Cannot update favorite: ${e.message}") } } }
    fun delete(ids:List<String>) { launchWork("Deleting games…") { if(state.value.game?.id in ids) { stopLive(); _state.update { it.copy(game=null,node=null,tree=emptyList(),analysis=emptyList()) }; prefs.edit().remove("lastGame").remove("lastNode").apply() }; repo.dao.delete(ids); notice("Deleted ${ids.size} games") } }
    fun saveGame(white:String,black:String,event:String,date:String,result:String,whiteElo:String="",blackElo:String="") {
        val id=state.value.game?.id ?: return
        launchWork("Saving game…") {
            require(ExportNames.validRating(whiteElo) && ExportNames.validRating(blackElo)) { "Elo must be blank or a number from 0 to 4000" }
            val updated=db.withTransaction {
                val latest=repo.dao.game(id) ?: error("Game no longer exists")
                val tags=latest.tags.tagMap()
                mapOf("White" to white,"Black" to black,"Event" to event,"Date" to date,"Result" to result,"WhiteElo" to whiteElo,"BlackElo" to blackElo).forEach { (key,value) ->
                    if(value.isNotBlank()) tags[key]=value.trim() else tags.remove(key)
                }
                repo.updateMetadata(latest,tags,latest.notes,latest.userTags)
                repo.dao.game(id)!!
            }
            _state.update { if(it.game?.id==id) it.copy(game=updated) else it }
            prefs.edit().putString("lastGame",id).putString("lastNode",state.value.node?.id).apply()
            notice("Game saved to My Games. Moves and variations are saved automatically as you play.")
        }
    }
    fun metadata(tags:MutableMap<String,String>,notes:String,userTags:String) { val g=state.value.game ?: return
        launchWork("Saving metadata…") { repo.updateMetadata(g,tags,notes,userTags); val updated=repo.dao.game(g.id); _state.update { it.copy(game=updated) }; notice("Metadata saved") }
    }
    fun addCollection(name:String) { launchWork("Creating collection…") { require(name.trim().isNotEmpty()); repo.dao.addCollection(CollectionEntity(uid(),name.trim())) } }
    fun membership(game:String,collection:String,add:Boolean) { launchWork("Updating collection…") { if(add) repo.dao.addMembership(Membership(game,collection)) else repo.dao.removeMembership(game,collection) } }
    fun bulkMembership(games:List<String>,collection:String,add:Boolean=true) { launchWork("Updating collections…") { db.withTransaction { games.forEach { if(add) repo.dao.addMembership(Membership(it,collection)) else repo.dao.removeMembership(it,collection) } }; notice("Collection updated") } }
    fun bulkTags(games:List<String>,tags:String) { launchWork("Updating tags…") { db.withTransaction { games.forEach { id -> repo.dao.game(id)?.let { game -> val merged=(game.userTags.split(',')+tags.split(',')).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", "); repo.dao.updateGame(game.copy(userTags=merged,modified=System.currentTimeMillis())) } } }; notice("Tags updated") } }
    fun deleteCollection(id:String) { launchWork("Deleting collection…") { repo.dao.deleteCollection(id) } }
    /** live is the user's follow-position switch; searching is only the current UCI search. */
    @Synchronized fun beginLive() {
        if(workJob?.isActive==true && !state.value.live) { notice("Finish or cancel the current operation first"); return }
        val snap=state.value; val node=snap.node ?: return
        val id=generation.incrementAndGet()
        liveJob?.cancel()
        _state.update { it.copy(live=true,searching=true,pvs=emptyList()) }
        liveJob=viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(100)
                val result=engine.analyze(node.fen,snap.settings) { pv ->
                    _state.update { current ->
                        if(generation.get()==id && current.node?.id==node.id && current.live)
                            current.copy(pvs=(current.pvs.filter { it.multipv!=pv.multipv }+pv).sortedBy { it.multipv })
                        else current
                    }
                }
                _state.update { current ->
                    if(generation.get()==id && current.node?.id==node.id && current.live)
                        current.copy(pvs=result,searching=false)
                    else current
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                _state.update { if(generation.get()==id) it.copy(live=false,searching=false,message=e.message ?: "Analysis failed") else it }
            } finally {
                _state.update { if(generation.get()==id) it.copy(searching=false) else it }
            }
        }
    }
    @Synchronized fun stopLive() {
        generation.incrementAndGet()
        liveJob?.cancel(); liveJob=null
        _state.update { it.copy(live=false,searching=false) }
        // The cancelled search owns the stop/bestmove drain. Do not send another stop
        // that could accidentally reach a subsequent search after the mutex transfers.
    }
    fun closeEngine() { stopLive(); viewModelScope.launch(Dispatchers.IO) { engine.close() } }
    fun background() { stopLive(); cancelWork(); viewModelScope.launch(Dispatchers.IO) { engine.close() } }
    fun chooseSession(session:SessionEntity) { viewModelScope.launch(Dispatchers.IO) { val rows=repo.dao.analysis(session.id); _state.update { it.copy(session=session,analysis=rows) }; state.value.game?.let { val g=it.copy(preferredSession=session.id); repo.dao.updateGame(g); _state.update { s -> s.copy(game=g) } } } }
    fun analyzeGame(resume:Boolean=false) {
        val snap=state.value; val game=snap.game ?: return
        stopLive()
        launchWork("Preparing complete-game analysis…") {
            val settings=engine.normalize(snap.settings.copy(infinite=false))
            val previous=if(resume) snap.sessions.firstOrNull { it.status in listOf("interrupted","cancelled","failed") } else null
            var session=previous ?: SessionEntity(uid(),game.id,System.currentTimeMillis(),threads=settings.threads,hashMb=settings.hashMb,multiPv=settings.multiPv,depth=settings.depth,timeMs=settings.timeMs)
            if(previous==null) repo.dao.addSession(session) else { session=session.copy(status="running"); repo.dao.updateSession(session) }
            val fixed=EngineSettings(session.threads,session.hashMb,session.multiPv,session.depth,session.timeMs,false,settings.battery)
            val line=Pgn.mainline(repo.dao.moves(game.id).map { it.tree() }); val saved=repo.dao.analysis(session.id).associateBy { it.moveId }.toMutableMap()
            try {
                for((index,node) in line.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    _state.update { it.copy(busy="Analyzing position ${index+1}/${line.size} · depth ${fixed.depth}",session=session) }
                    if(saved[node.id]!=null) continue
                    val board=Chess.board(node.fen)
                    val result=if(board.legalMoves().isEmpty()) listOf(if(board.isMated) Pv(mate=0) else Pv(cp=0)) else engine.analyze(node.fen,fixed)
                    val best=result.firstOrNull() ?: error("Engine produced no evaluation for position ${index+1}")
                    val classification=if(index==0) "" to 0.0 else {
                        val before=saved[line[index-1].id] ?: error("Missing preceding analysis")
                        val p=Pv(before.depth,before.cp,before.mate,moves=before.bestMove.split(' ').filter { it.isNotEmpty() })
                        fun threshold(key:String,default:Double)=prefs.getString(key,null)?.toDoubleOrNull() ?: default
                        Classification.classify(p,best,node.uci,Chess.board(line[index-1].fen).legalMoves().size,threshold("inaccuracy",5.0),threshold("mistake",10.0),threshold("blunder",20.0))
                    }
                    val row=AnalysisEntity(uid(),session.id,node.id,node.fen,System.currentTimeMillis(),best.depth,best.time,best.nodes,best.cp,best.mate,com.google.gson.Gson().toJson(result),best.moves.firstOrNull().orEmpty(),classification.first,classification.second)
                    repo.dao.saveAnalysis(row); saved[node.id]=row
                    if(state.value.game?.id==game.id) _state.update { it.copy(analysis=saved.values.toList()) }
                }
                session=session.copy(status="complete",finished=System.currentTimeMillis()); repo.dao.updateSession(session)
                repo.dao.game(game.id)?.let { repo.dao.updateGame(it.copy(analyzed=System.currentTimeMillis(),preferredSession=session.id)) }
                notice("Complete-game analysis saved")
            } catch(e:CancellationException) { withContext(NonCancellable) { session=session.copy(status="interrupted"); repo.dao.updateSession(session) }; throw e }
            catch(e:Exception) { session=session.copy(status="failed"); repo.dao.updateSession(session); throw e }
            finally {
                withContext(NonCancellable) { if(state.value.game?.id==game.id) { val sessions=repo.dao.sessions(game.id); _state.update { it.copy(sessions=sessions,session=session,analysis=saved.values.toList(),game=it.game?.copy(preferredSession=session.id)) } } }
            }
            navigate("Game Review")
        }
    }
    fun savePosition() { val s=state.value; val g=s.game ?: return; val n=s.node ?: return; val best=s.pvs.firstOrNull() ?: return
        launchWork("Saving position analysis…") {
            val settings=engine.actualSettings
            val session=SessionEntity(uid(),g.id,System.currentTimeMillis(),System.currentTimeMillis(),threads=settings.threads,hashMb=settings.hashMb,multiPv=settings.multiPv,depth=settings.depth,timeMs=settings.timeMs,status="position")
            repo.dao.addSession(session)
            repo.dao.saveAnalysis(AnalysisEntity(uid(),session.id,n.id,n.fen,System.currentTimeMillis(),best.depth,best.time,best.nodes,best.cp,best.mate,com.google.gson.Gson().toJson(s.pvs),best.moves.firstOrNull().orEmpty()))
            _state.update { it.copy(sessions=repo.dao.sessions(g.id)) }; notice("Position analysis saved")
        }
    }
    override fun onCleared() { stopLive(); super.onCleared() }
}
