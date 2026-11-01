package org.offlinechess.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import org.offlinechess.core.*
import org.offlinechess.data.*
import org.offlinechess.engine.*
import kotlin.math.*

private val destinations=listOf("Home","My Games","Chessboard","Analysis","Game Review","Import / Export","Collections","Settings")
private val green=Color(0xFF255D48)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessApp(model:AppModel) {
    val s by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val drawer=rememberDrawerState(DrawerValue.Closed)
    var exportIds by remember { mutableStateOf<List<String>?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var keepDuplicates by rememberSaveable { mutableStateOf(false) }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> model.importPgn(uri,keepDuplicates) } }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-chess-pgn")) { it?.let { uri -> model.export(uri,exportIds) } }
    val backup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::backup) }
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri=it }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it,duration=if(it.length>120) SnackbarDuration.Indefinite else SnackbarDuration.Long,withDismissAction=true); model.notice(null) } }
    val colors=if(s.dark) darkColorScheme(primary=Color(0xFF91D6AE),secondary=Color(0xFFE1C886),background=Color(0xFF101C18),surface=Color(0xFF14251E)) else lightColorScheme(primary=green,secondary=Color(0xFF846A35),secondaryContainer=Color(0xFFE1EBDD),onSecondaryContainer=Color(0xFF173B2C),background=Color(0xFFF5F6F0),surface=Color(0xFFFCFDF8),surfaceVariant=Color(0xFFE4EADF))
    MaterialTheme(colorScheme=colors) {
        ModalNavigationDrawer(drawerState=drawer,drawerContent={ ModalDrawerSheet(modifier=Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { BrandMark(48.dp); Text("CHESS DUDE",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
            Text("DATABASE & ANALYSIS",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(start=24.dp,bottom=20.dp))
            destinations.forEach { name -> NavigationDrawerItem(label={ Text(name) },selected=s.screen==name,onClick={ model.navigate(name); scope.launch { drawer.close() } },modifier=Modifier.padding(horizontal=12.dp)) }
            Spacer(Modifier.height(16.dp)); Text("Stockfish 19 · No connection required",style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(24.dp))
        } }) {
            Scaffold(topBar={ TopAppBar(title={ Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) { BrandMark(34.dp); Column { Text(s.screen,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold); Text("CHESS DUDE",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary) } } },navigationIcon={ IconButton(onClick={ scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu,"Open navigation") } },actions={ if(s.game!=null && s.node!=null && s.screen in listOf("Chessboard","Analysis","Game Review")) IconButton(onClick={ sharing=true },enabled=s.busy==null) { Icon(Icons.Default.Share,"Share game or position") }; IconButton(onClick={ model.navigate("Settings") }) { Icon(Icons.Default.Settings,"Settings") } }) },snackbarHost={ SnackbarHost(snackbar) }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if(s.busy!=null) Surface(color=MaterialTheme.colorScheme.secondaryContainer) { Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp); Text(s.busy!!,Modifier.weight(1f).padding(12.dp),style=MaterialTheme.typography.bodySmall); TextButton(onClick=model::cancelWork) { Text("Cancel") } } }
                    when(s.screen) {
                        "Home" -> HomeScreen(model,{ import.launch(arrayOf("*/*")) })
                        "My Games" -> LibraryScreen(model) { ids -> exportIds=ids; export.launch("games.pgn") }
                        "Chessboard", "Analysis" -> BoardScreen(model,s.screen=="Analysis") { id -> exportIds=listOf(id); export.launch(ExportNames.pgn(s.game?.tags?.tagMap().orEmpty())) }
                        "Game Review" -> ReviewScreen(model)
                        "Import / Export" -> TransferScreen(model,keepDuplicates,{ keepDuplicates=it },{ import.launch(arrayOf("*/*")) },{ exportIds=null; export.launch("chess-dude.pgn") },{ backup.launch("chess-dude-backup.jsonl") },{ restore.launch(arrayOf("*/*")) })
                        "Collections" -> CollectionsScreen(model) { ids -> exportIds=ids; export.launch("collection.pgn") }
                        "Settings" -> SettingsScreen(model)
                    }
                }
            }
        }
        if(sharing && s.game!=null && s.node!=null) ShareDialog(model) { sharing=false }
        if(restoreUri!=null) AlertDialog(onDismissRequest={ restoreUri=null },title={ Text("Restore complete backup?") },text={ Text("This replaces the current library, analysis, collections and settings. Create a backup first if you want to keep them. Invalid backups roll back the database transaction.") },confirmButton={ TextButton(onClick={ restoreUri?.let(model::restore); restoreUri=null }) { Text("Replace and restore") } },dismissButton={ TextButton(onClick={ restoreUri=null }) { Text("Cancel") } })
    }
}

@Composable private fun SectionTitle(title:String,caption:String?=null) { Column(Modifier.padding(vertical=8.dp)) { Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold); if(caption!=null) Text(caption,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun EmptyState(title:String,body:String) { Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Default.GridOn,null,Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp)); Text(title,style=MaterialTheme.typography.titleMedium); Text(body,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=8.dp)) } }
@Composable private fun Action(text:String,onClick:()->Unit) { FilledTonalButton(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(text) } }
@Composable fun HomeScreen(model:AppModel,onImport:()->Unit) {
    val s by model.state.collectAsStateWithLifecycle(); val total by model.total.collectAsStateWithLifecycle(); val analyzed by model.analyzedCount.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item { Surface(color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp)) { Text("Your chess.\nYour discoveries.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold); Spacer(Modifier.height(12.dp)); Text("A private game library and the full power of Stockfish 19, wherever you are.",style=MaterialTheme.typography.bodyMedium) } } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { Card(Modifier.weight(1f)) { Column(Modifier.padding(20.dp)) { Text(total.toString(),style=MaterialTheme.typography.headlineLarge); Text("Stored games",style=MaterialTheme.typography.labelMedium) } }; Card(Modifier.weight(1f)) { Column(Modifier.padding(20.dp)) { Text(analyzed.toString(),style=MaterialTheme.typography.headlineLarge); Text("Analyzed games",style=MaterialTheme.typography.labelMedium) } } } }
        item { Action("New analysis board") { model.newGame() } }
        item { Action("Import PGN games",onImport) }
        item { SectionTitle("Recent games","Stored entirely on this device") }
        if(total==0) item { EmptyState("Start your library","Import a PGN file or create a new game. No account or download is needed.") }
        items(s.games.take(5),key={ it.id }) { game -> GameCard(game,onOpen={ model.openGame(game.id) },onFavorite={ model.favorite(game) }) }
        item { TextButton(onClick={ model.navigate("My Games") }) { Text("View game library") } }
    }
}
@Composable private fun GameCard(game:GameEntity,onOpen:()->Unit,onFavorite:()->Unit,selected:Boolean=false,onSelect:(()->Unit)?=null) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick=onOpen)) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
        if(onSelect!=null) Checkbox(checked=selected,onCheckedChange={ onSelect() })
        Column(Modifier.weight(1f)) { Text("${game.white.ifBlank { "White" }}  —  ${game.black.ifBlank { "Black" }}",style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis); Text(listOf(game.event,game.date,game.result).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Untitled game" },style=MaterialTheme.typography.bodySmall); Text(listOf(game.eco,game.opening,if(game.analyzed!=null) "Analyzed" else "").filter { it.isNotBlank() }.joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary) }
        IconButton(onClick=onFavorite) { Icon(if(game.favorite) Icons.Default.Star else Icons.Default.StarBorder,"Toggle favorite",tint=MaterialTheme.colorScheme.secondary) }
    } }
}
@Composable fun LibraryScreen(model:AppModel,onExport:(List<String>)->Unit) {
    val s by model.state.collectAsStateWithLifecycle(); var filters by remember { mutableStateOf(false) }; var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }; var delete by remember { mutableStateOf(false) }; var bulkCollection by remember { mutableStateOf(false) }; var bulkTags by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        OutlinedTextField(value=s.filters.query,onValueChange={ model.filter(s.filters.copy(query=it)) },label={ Text("Search players, event, site, opening, tags") },leadingIcon={ Icon(Icons.Default.Search,null) },modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { TextButton(onClick={ filters=true }) { Icon(Icons.Default.FilterList,null); Text("Filters & sort") }; TextButton(onClick={ selected=if(selected.isEmpty()) s.games.map { it.id } else emptyList() }) { Text(if(selected.isEmpty()) "Select page" else "Clear selection") } }
        if(selected.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) { Text("${selected.size} selected"); TextButton(onClick={ onExport(selected) }) { Text("Export") }; TextButton(onClick={ bulkCollection=true }) { Text("Collect") }; TextButton(onClick={ bulkTags=true }) { Text("Tag") }; if(s.filters.collection.isNotBlank()) TextButton(onClick={ model.bulkMembership(selected,s.filters.collection,false); selected=emptyList() }) { Text("Remove from collection") }; TextButton(onClick={ delete=true }) { Text("Delete") } }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)) {
            if(s.games.isEmpty()) item { EmptyState("No matching games","Import games or adjust your filters.") }
            items(s.games,key={ it.id }) { g -> GameCard(g,{ model.openGame(g.id) },{ model.favorite(g) },g.id in selected) { selected=if(g.id in selected) selected-g.id else selected+g.id } }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { TextButton(enabled=s.page>0,onClick={ model.page(-1) }) { Text("Previous") }; Text("Page ${s.page+1}",style=MaterialTheme.typography.labelMedium); TextButton(enabled=s.hasMore,onClick={ model.page(1) }) { Text("Next") } }
    }
    if(filters) FilterDialog(s.filters,{ model.filter(it); filters=false },{ filters=false })
    if(delete) AlertDialog(onDismissRequest={ delete=false },title={ Text("Delete ${selected.size} games?") },text={ Text("Their moves and analysis sessions will also be deleted.") },confirmButton={ TextButton(onClick={ model.delete(selected); selected=emptyList(); delete=false }) { Text("Delete") } },dismissButton={ TextButton(onClick={ delete=false }) { Text("Cancel") } })
    if(bulkCollection) CollectionPicker(model,{ collection -> model.bulkMembership(selected,collection); bulkCollection=false },{ bulkCollection=false })
    if(bulkTags) TextDialog("Add tags to selected games","Comma-separated tags",onDismiss={ bulkTags=false }) { model.bulkTags(selected,it); bulkTags=false }
}
@Composable private fun FilterDialog(initial:Filters,onApply:(Filters)->Unit,onDismiss:()->Unit) {
    var f by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Library filters") },text={ Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Choice("Result",listOf("","1-0","0-1","1/2-1/2","*"),f.result) { f=f.copy(result=it) }
        Choice("Sort",listOf("import","date","event","player","rating","opening"),f.sort) { f=f.copy(sort=it) }
        Choice("Time control",listOf("","Bullet","Blitz","Rapid","Classical"),f.category) { f=f.copy(category=it) }
        Choice("Analysis",listOf("All","Analyzed","Not analyzed"),when(f.analyzed) { 1 -> "Analyzed"; 0 -> "Not analyzed"; else -> "All" }) { f=f.copy(analyzed=when(it) { "Analyzed" -> 1; "Not analyzed" -> 0; else -> -1 }) }
        OutlinedTextField(f.eco,{ f=f.copy(eco=it) },label={ Text("ECO prefix") })
        OutlinedTextField(f.after,{ f=f.copy(after=it) },label={ Text("From date · YYYY.MM.DD") })
        OutlinedTextField(f.before,{ f=f.copy(before=it) },label={ Text("To date · YYYY.MM.DD") })
        NumberField("Minimum rating",f.minRating) { f=f.copy(minRating=it.coerceIn(0,4000)) }; NumberField("Maximum rating",f.maxRating) { f=f.copy(maxRating=it.coerceIn(0,4000)) }
        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(f.favorite,{ f=f.copy(favorite=it) }); Text("Favorites only") }
    } },confirmButton={ TextButton(onClick={ onApply(f) }) { Text("Apply") } },dismissButton={ Row { TextButton(onClick={ f=Filters(query=initial.query) }) { Text("Reset") }; TextButton(onClick=onDismiss) { Text("Cancel") } } })
}
@Composable private fun Choice(label:String,values:List<String>,value:String,onValue:(String)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={ open=true },modifier=Modifier.fillMaxWidth()) { Text("$label: ${value.ifBlank { "All" }}"); Icon(Icons.Default.ArrowDropDown,null) }; DropdownMenu(expanded=open,onDismissRequest={ open=false }) { values.forEach { item -> DropdownMenuItem(text={ Text(item.ifBlank { "All" }) },onClick={ onValue(item); open=false }) } } }
}
@Composable private fun NumberField(label:String,value:Int,onChange:(Int)->Unit) { var text by remember(value) { mutableStateOf(value.toString()) }; OutlinedTextField(text,{ text=it; it.toIntOrNull()?.let(onChange) },label={ Text(label) },singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number)) }
@Composable private fun TextDialog(title:String,label:String,value:String="",onDismiss:()->Unit,onSave:(String)->Unit) {
    var text by remember { mutableStateOf(value) }
    AlertDialog(onDismissRequest=onDismiss,title={ Text(title) },text={ OutlinedTextField(text,{ text=it },label={ Text(label) },modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=12) },confirmButton={ TextButton(onClick={ onSave(text) }) { Text("Save") } },dismissButton={ TextButton(onClick=onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BoardScreen(model:AppModel,analysisMode:Boolean,onExport:(String)->Unit) {
    val s by model.state.collectAsStateWithLifecycle(); val node=s.node; val game=s.game
    var marking by rememberSaveable { mutableStateOf(false) }; var metadata by remember { mutableStateOf(false) }; var annotate by remember { mutableStateOf(false) }; var fen by remember { mutableStateOf(false) }; var collection by remember { mutableStateOf(false) }; var deleteBranch by remember { mutableStateOf(false) }
    var commentTarget by remember { mutableStateOf<TreeNode?>(null) }; var commentBefore by remember { mutableStateOf(false) }
    var saveGame by remember { mutableStateOf(false) }; var details by remember { mutableStateOf(false) }
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    fun copy(label:String,text:String) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label,text)); model.notice("$label copied") }
    if(node==null || game==null) { Column(Modifier.padding(24.dp)) { EmptyState("Open a game to begin","Create an analysis board or open a game from My Games."); Action("New analysis board") { model.newGame() }; Action("Open My Games") { model.navigate("My Games") } }; return }
    val stored=s.analysis.firstOrNull { it.moveId==node.id }
    val savedPvs=remember(stored) { stored?.let { runCatching { com.google.gson.Gson().fromJson(it.pv,Array<Pv>::class.java).toList() }.getOrNull() }.orEmpty() }
    val pvs=if(s.live || s.pvs.isNotEmpty()) s.pvs else savedPvs
    val best=pvs.firstOrNull(); val whiteToMove=node.fen.split(' ')[1]=="w"
    val tone=remember { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC,30) }
    DisposableEffect(Unit) { onDispose { tone.release() } }
    val history=remember(node.id,s.tree) {
        val b=Chess.board(s.tree.first { it.parent==null }.fen); val byId=s.tree.associateBy { it.id }
        val path=mutableListOf<TreeNode>(); var n:TreeNode?=node
        while(n?.parent!=null) { path.add(n); n=byId[n.parent] }
        path.asReversed().forEach { b.doMove(com.github.bhlangonijr.chesslib.move.Move(it.uci,b.sideToMove),true) }
        Chess.drawStatus(b) ?: if(b.isKingAttacked) "Check" else "${if(whiteToMove) "White" else "Black"} to move"
    }
    val boardArea:@Composable (androidx.compose.ui.unit.Dp,Boolean)->Unit = { edge,compact ->
        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()) {
            if(!compact) Text(if(s.flipped) game.white.ifBlank { "White" } else game.black.ifBlank { "Black" },style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth().padding(vertical=3.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                VerticalEvaluationBar(best,whiteToMove,s.flipped,Modifier.width(18.dp).height(edge))
                Surface(shape=RoundedCornerShape(5.dp),shadowElevation=2.dp,modifier=Modifier.size(edge)) {
                    ChessBoard(node,s.flipped,s.theme,best?.moves?.firstOrNull(),marking,{ u -> model.move(u); if(s.sound) tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,45) },{ comment -> model.annotate(comment,node.nags) })
                }
            }
            if(!compact) Text(if(s.flipped) game.black.ifBlank { "Black" } else game.white.ifBlank { "White" },style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth().padding(vertical=3.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                IconButton(onClick={ s.tree.firstOrNull { it.parent==null }?.let(model::select) }) { Icon(Icons.Default.FirstPage,"Starting position") }
                IconButton(onClick={ model.step(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Previous move") }
                IconButton(onClick={ model.step(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward,"Next move") }
                IconButton(onClick={ Pgn.mainline(s.tree).lastOrNull()?.let(model::select) }) { Icon(Icons.Default.LastPage,"End of mainline") }
                IconButton(onClick=model::flip) { Icon(Icons.Default.FlipCameraAndroid,"Flip board") }
                IconToggleButton(checked=marking,onCheckedChange={ marking=it }) { Icon(Icons.Default.Edit,"Draw arrows and marks") }
            }
            Text(if(marking) "Marking: tap two squares or drag" else "$history · ${Evaluation.description(best,whiteToMove)}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth())
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>=760.dp || (maxWidth>maxHeight && maxWidth>=540.dp)
        val paneWidth=maxWidth*.46f
        val edge=if(wide) minOf(maxWidth*.46f-24.dp,(maxHeight-164.dp).coerceAtLeast(96.dp))
            else minOf(maxWidth-48.dp,(maxHeight-360.dp).coerceAtLeast(120.dp))
        Column(Modifier.fillMaxSize().padding(horizontal=12.dp).padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                FilledTonalButton(onClick={ saveGame=true },enabled=s.busy==null,modifier=Modifier.weight(1f)) { Icon(Icons.Default.Save,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save game") }
                Button(onClick={ if(s.live) model.stopLive() else model.beginLive() },enabled=s.busy==null,modifier=Modifier.weight(1f)) { Icon(if(s.live) Icons.Default.Stop else Icons.Default.PlayArrow,null,Modifier.size(18.dp)); Text(if(s.live) "Stop" else "Analyze") }
                IconButton(onClick={ details=true }) { Icon(Icons.Default.Tune,"Game and engine details") }
            }
            Text(if(s.live) if(s.searching) "Stockfish analyzing · follows each move" else "Position analyzed · follows each move" else "Analysis stopped · tap Analyze to follow moves",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(wide) Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Box(Modifier.width(paneWidth).fillMaxHeight().verticalScroll(rememberScrollState())) { boardArea(edge,true) }
                NotationPanel(s.tree,node.id,model::select,Modifier.weight(1f).fillMaxHeight()) { target,before -> model.select(target); commentTarget=target; commentBefore=before }
            } else {
                boardArea(edge,false)
                NotationPanel(s.tree,node.id,model::select,Modifier.fillMaxWidth().weight(1f)) { target,before -> model.select(target); commentTarget=target; commentBefore=before }
            }
        }
    }
    if(details) ModalBottomSheet(onDismissRequest={ details=false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            AnalysisPanel(model,node,pvs,!s.live && stored!=null && s.pvs.isEmpty())
            Text("All moves and variations are saved automatically to My Games.",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick={ details=false; model.analyzeGame() },enabled=s.busy==null) { Text("Analyze complete game") }
                if(s.sessions.any { it.status in listOf("interrupted","failed") }) TextButton(onClick={ details=false; model.analyzeGame(true) },enabled=s.busy==null) { Text("Resume analysis") }
            }
            if(node.comment.isNotBlank() || node.nags.isNotBlank()) OutlinedCard { Column(Modifier.padding(12.dp)) { Text(node.comment.ifBlank { "No comment" }); if(node.nags.isNotBlank()) Text(node.nags,style=MaterialTheme.typography.labelMedium) } }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick={ details=false; metadata=true }) { Text("Metadata") }; TextButton(onClick={ details=false; annotate=true }) { Text("Annotate") }; TextButton(onClick={ details=false; collection=true }) { Text("Add to collection") }
                if(node.parent!=null) { TextButton(onClick={ details=false; model.promote() }) { Text("Promote line") }; TextButton(onClick={ details=false; deleteBranch=true }) { Text("Delete branch") } }
                TextButton(onClick={ model.favorite(game) }) { Text(if(game.favorite) "★ Favorite" else "☆ Favorite") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick={ copy("FEN",node.fen) }) { Text("Copy FEN") }; TextButton(onClick={ details=false; fen=true }) { Text("Load FEN") }; TextButton(onClick={ details=false; onExport(game.id) }) { Text("Export PGN") }
                TextButton(onClick={ scope.launch { runCatching { withContext(Dispatchers.IO) { Pgn.write(model.repo.load(game.id)) } }.onSuccess { copy("PGN",it) }.onFailure { model.notice("Cannot copy PGN: ${it.message}") } } }) { Text("Copy PGN") }
            }
            Text(node.fen,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    commentTarget?.let { target -> CommentDialog(target,commentBefore,{ commentTarget=null }) { before,after,nags -> model.comment(target.id,before,after,nags); commentTarget=null } }
    if(s.continuations.isNotEmpty()) AlertDialog(onDismissRequest=model::dismissContinuations,title={ Text("Choose move") },text={
        val mainIds=remember(s.tree) { Pgn.mainline(s.tree).map { it.id }.toSet() }
        val byId=s.tree.associateBy { it.id }
        Column(Modifier.verticalScroll(rememberScrollState())) { s.continuations.forEach { choice ->
            TextButton(onClick={ model.chooseContinuation(choice.id) },modifier=Modifier.fillMaxWidth().testTag("choose-${choice.id}")) {
                Text(Notation.moveLabel(choice,byId)+Notation.glyphs(choice.nags),fontWeight=if(choice.id in mainIds) FontWeight.Bold else FontWeight.Normal,modifier=Modifier.fillMaxWidth())
            }
        } }
    },confirmButton={},dismissButton={ TextButton(onClick=model::dismissContinuations) { Text("Cancel") } })
    if(saveGame) SaveGameDialog(game,{ saveGame=false }) { white,black,event,date,result,whiteElo,blackElo -> model.saveGame(white,black,event,date,result,whiteElo,blackElo); saveGame=false }
    if(deleteBranch) AlertDialog(onDismissRequest={ deleteBranch=false },title={ Text("Delete this move and its continuations?") },text={ Text("All nested variations and analysis associated with these moves will be removed.") },confirmButton={ TextButton(onClick={ model.deleteBranch(); deleteBranch=false }) { Text("Delete branch") } },dismissButton={ TextButton(onClick={ deleteBranch=false }) { Text("Cancel") } })
    if(metadata) MetadataDialog(game,{ metadata=false }) { tags,notes,userTags -> model.metadata(tags,notes,userTags); metadata=false }
    if(annotate) AnnotationDialog(node,{ annotate=false }) { comment,nags -> model.annotate(comment,nags); annotate=false }
    if(fen) TextDialog("New board from FEN","Six-field FEN",node.fen,{ fen=false }) { model.newGame(it.trim()); fen=false }
    if(collection) CollectionPicker(model,{ model.membership(game.id,it,true); collection=false },{ collection=false })
}

@Composable private fun AnalysisPanel(model:AppModel,node:TreeNode,pvs:List<Pv>,saved:Boolean) {
    val s by model.state.collectAsStateWithLifecycle(); val status by model.engine.status.collectAsStateWithLifecycle()
    val best=pvs.firstOrNull(); val white=node.fen.split(' ')[1]=="w"
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("STOCKFISH 19",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold); Text(if(saved) "Saved analysis" else status,style=MaterialTheme.typography.bodySmall) }; Text(best?.score(white) ?: "—",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold) }
        val ratio=best?.let { (1/(1+exp(-it.whiteScore(white)/300))).toFloat().coerceIn(.02f,.98f) } ?: .5f
        Box(Modifier.fillMaxWidth().height(14.dp).background(Color(0xFF25322C),RoundedCornerShape(4.dp))) { Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(Color(0xFFF5EFDF),RoundedCornerShape(4.dp))) }
        Text("Evaluation from White’s perspective",style=MaterialTheme.typography.labelSmall)
        if(best!=null) Text("Depth ${best.depth} · ${"%,d".format(best.nodes)} nodes · ${"%,d".format(best.nps)} nps\nHash ${best.hashfull/10.0}% · ${"%.1f".format(best.time/1000.0)} s",style=MaterialTheme.typography.labelSmall)
        pvs.forEach { pv -> Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Text(pv.score(white),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.width(50.dp)); Text(remember(node.fen,pv.moves) { Chess.pvSan(node.fen,pv.moves.take(14)) },style=MaterialTheme.typography.bodySmall,maxLines=3,overflow=TextOverflow.Ellipsis) } }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={ if(s.live) model.stopLive() else model.beginLive() },enabled=s.busy==null) { Icon(if(s.live) Icons.Default.Stop else Icons.Default.PlayArrow,null); Text(if(s.live) "Stop" else "Analyze") }
            OutlinedButton(onClick=model::savePosition,enabled=s.pvs.isNotEmpty() && s.busy==null) { Text("Save analysis") }
        }
    } }
}
private val standardTags=listOf("White","Black","WhiteFideId","BlackFideId","WhiteElo","BlackElo","WhiteTitle","BlackTitle","Event","Site","Country","EventDate","Date","Round","SubRound","Board","Result","Termination","TimeControl","TimeControlCategory","ECO","Opening","Variation","FEN","SetUp","Variant","Source")
@Composable private fun MetadataDialog(game:GameEntity,onDismiss:()->Unit,onSave:(MutableMap<String,String>,String,String)->Unit) {
    val original=remember(game.id) { game.tags.tagMap() }; val tags=remember { mutableStateMapOf<String,String>().apply { putAll(original) } }
    var notes by remember { mutableStateOf(game.notes) }; var userTags by remember { mutableStateOf(game.userTags) }; var customKey by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Game metadata") },text={ LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Text("Only provided values are stored. Unknown PGN tags are preserved.",style=MaterialTheme.typography.bodySmall); Text("ID: ${game.id}\nImported: ${java.util.Date(game.imported)}\nSource: ${game.source}",style=MaterialTheme.typography.labelSmall) }
        items((standardTags+tags.keys).distinct()) { key -> OutlinedTextField(tags[key].orEmpty(),{ tags[key]=it },label={ Text(key) },enabled=key!in listOf("FEN","SetUp","Variant"),modifier=Modifier.fillMaxWidth()) }
        item { OutlinedTextField(customKey,{ customKey=it },label={ Text("New custom tag name") }); TextButton(onClick={ if(customKey.matches(Regex("[A-Za-z0-9_]+"))) { tags[customKey]=""; customKey="" } }) { Text("Add custom tag") } }
        item { OutlinedTextField(userTags,{ userTags=it },label={ Text("User tags") }); OutlinedTextField(notes,{ notes=it },label={ Text("Private notes") },minLines=3) }
    } },confirmButton={ TextButton(onClick={ onSave(tags.filter { it.value.isNotEmpty() || original.containsKey(it.key) }.toMutableMap(),notes,userTags) }) { Text("Save") } },dismissButton={ TextButton(onClick=onDismiss) { Text("Close") } })
}
@Composable private fun AnnotationDialog(node:TreeNode,onDismiss:()->Unit,onSave:(String,String)->Unit) {
    var comment by remember { mutableStateOf(node.comment) }; var nags by remember { mutableStateOf(node.nags) }
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Position annotations") },text={ Column { OutlinedTextField(comment,{ comment=it },label={ Text("Comments, clocks, arrows and evaluations") },minLines=4,maxLines=10); OutlinedTextField(nags,{ nags=it },label={ Text("NAGs · e.g. \$1 \$14") }); TextButton(onClick={ comment=comment.replace(Regex("\\[%(cal|csl) [^]]+]"),"").trim() }) { Text("Clear arrows and markings") } } },confirmButton={ TextButton(onClick={ onSave(comment,nags) }) { Text("Save") } },dismissButton={ TextButton(onClick=onDismiss) { Text("Cancel") } })
}

@Composable fun ReviewScreen(model:AppModel) {
    val s by model.state.collectAsStateWithLifecycle(); val game=s.game
    if(game==null) { EmptyState("No game selected","Open a game from My Games to review its analysis."); return }
    val line=remember(s.tree) { Pgn.mainline(s.tree) }; val byId=remember(s.analysis) { s.analysis.associateBy { it.moveId } }; val rows=line.mapNotNull { n -> byId[n.id]?.let { n to it } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item { SectionTitle("${game.white.ifBlank { "White" }} — ${game.black.ifBlank { "Black" }}",listOf(game.opening,game.eco,game.event).filter { it.isNotBlank() }.joinToString(" · ")) }
        item { Action("Analyze complete game") { model.analyzeGame() } }
        if(s.sessions.isEmpty()) item { EmptyState("No saved analysis","Analyze the complete game or save a position analysis.") }
        if(s.sessions.isNotEmpty()) item { Choice("Session",s.sessions.map { "${java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date(it.started))} · d${it.depth} · ${it.status} · ${it.id.take(4)}" },s.session?.let { "${java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date(it.started))} · d${it.depth} · ${it.status} · ${it.id.take(4)}" }.orEmpty()) { label -> val index=s.sessions.indexOfFirst { label.endsWith(it.id.take(4)) }; if(index>=0) model.chooseSession(s.sessions[index]) } }
        if(rows.isNotEmpty()) {
            item { EvaluationGraph(rows,s.node?.id) { node -> model.select(node); model.navigate("Chessboard") } }
            item { val moves=rows.filter { it.second.classification.isNotBlank() }; val accuracy=if(moves.isEmpty()) 100.0 else moves.map { exp(-it.second.loss/20)*100 }.average()
                OutlinedCard { Column(Modifier.padding(16.dp)) { Text("${moves.count { it.second.classification=="Blunder" }} blunders · ${moves.count { it.second.classification=="Mistake" }} mistakes",style=MaterialTheme.typography.titleSmall); Text("Estimated accuracy: ${"%.1f".format(accuracy)}%",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary); Text("App-defined metric: mean of 100 × exp(−winning-chance loss / 20). These labels and this score are not Stockfish outputs.",style=MaterialTheme.typography.bodySmall); s.session?.let { Text("${it.engine} · ${it.network}\nThreads ${it.threads} · Hash ${it.hashMb} MB · MultiPV ${it.multiPv}\nDepth limit ${it.depth} · ${it.timeMs} ms/position · ${it.status}"+(it.finished?.let { end -> "\nSession duration ${(end-it.started)/1000}s" } ?: ""),style=MaterialTheme.typography.labelSmall) } } }
            }
            item { SectionTitle("Move report","Tap a move to inspect the position. Evaluations favor White when positive.") }
            items(rows,key={ it.first.id }) { (node,row) ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { model.select(node); model.navigate("Chessboard") }) { Column(Modifier.padding(12.dp)) {
                    Row { Text(if(node.parent==null) "Initial position" else "${node.ply}. ${node.san}",Modifier.weight(1f),fontWeight=FontWeight.SemiBold); Text(Pv(cp=row.cp,mate=row.mate).score(node.fen.split(' ')[1]=="w"),fontWeight=FontWeight.Bold) }
                    Text(row.classification,style=MaterialTheme.typography.labelLarge,color=when(row.classification) { "Blunder","Mistake" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary })
                    val parent=s.tree.firstOrNull { it.id==node.parent }; val baseline=parent?.let { byId[it.id] }
                    if(parent!=null && baseline?.bestMove?.isNotBlank()==true) Text("Preferred: ${Chess.pvSan(parent.fen,listOf(baseline.bestMove))} · Chance loss ${"%.1f".format(row.loss)} pp",style=MaterialTheme.typography.bodySmall)
                    Text("Depth ${row.depth} · ${row.nodes} nodes",style=MaterialTheme.typography.labelSmall)
                } }
            }
        }
    }
}
@Composable private fun EvaluationGraph(rows:List<Pair<TreeNode,AnalysisEntity>>,selected:String?,onSelect:(TreeNode)->Unit) {
    var width by remember { mutableFloatStateOf(1f) }
    val primary=MaterialTheme.colorScheme.primary; val background=MaterialTheme.colorScheme.surfaceVariant
    Column { Text("Evaluation · tap to navigate",style=MaterialTheme.typography.labelMedium)
        Canvas(Modifier.fillMaxWidth().height(160.dp).background(background,RoundedCornerShape(12.dp)).pointerInput(rows) { detectTapGestures { p -> val i=(p.x/width*(rows.size-1)).roundToInt().coerceIn(rows.indices); onSelect(rows[i].first) } }) {
            width=size.width
            drawLine(Color.Gray.copy(alpha=.5f),Offset(0f,size.height/2),Offset(size.width,size.height/2),1.dp.toPx())
            fun point(i:Int):Offset { val (n,a)=rows[i]; val value=Pv(cp=a.cp,mate=a.mate).whiteScore(n.fen.split(' ')[1]=="w").coerceIn(-800.0,800.0); return Offset(if(rows.size==1) size.width/2 else i*size.width/(rows.size-1),size.height/2-(value/800*size.height*.43f).toFloat()) }
            val path=Path(); rows.indices.forEach { i -> val p=point(i); if(i==0) path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y) }; drawPath(path,primary,style=Stroke(2.5.dp.toPx()))
            rows.indices.forEach { i -> if(rows[i].first.id==selected || rows.size==1) drawCircle(primary,4.dp.toPx(),point(i)) }
        }
    }
}
@Composable fun TransferScreen(model:AppModel,duplicates:Boolean,onDuplicates:(Boolean)->Unit,onImport:()->Unit,onExport:()->Unit,onBackup:()->Unit,onRestore:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle("Portable by design","Use Android’s document picker. No broad storage access is requested.")
        Action("Import PGN files",onImport)
        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(duplicates,onDuplicates); Text("Keep exact duplicates on import") }
        Text("Custom tags, comments, nested variations and NAGs are retained. Identical moves from different tournament records are kept as separate games.",style=MaterialTheme.typography.bodySmall)
        Action("Export all games as PGN",onExport)
        HorizontalDivider(); SectionTitle("Complete local backup","Includes games, annotations, collections, analysis sessions and settings.")
        Action("Create complete backup",onBackup); Action("Restore complete backup",onRestore)
        Text("Backup format: Chess Dude Backup v1 (UTF-8 JSON lines). Restore validates every game tree in a database transaction. Keep backup files somewhere safe on your device or removable storage.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun CollectionPicker(model:AppModel,onPick:(String)->Unit,onDismiss:()->Unit) {
    val collections by model.collections.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Choose collection") },text={ Column(Modifier.verticalScroll(rememberScrollState())) { if(collections.isEmpty()) Text("Create a collection from the Collections screen first."); collections.forEach { c -> TextButton(onClick={ onPick(c.id) }) { Text(c.name) } } } },confirmButton={ TextButton(onClick=onDismiss) { Text("Close") } })
}
@Composable fun CollectionsScreen(model:AppModel,onExport:(List<String>)->Unit) {
    val collections by model.collections.collectAsStateWithLifecycle(); var create by remember { mutableStateOf(false) }; var delete by remember { mutableStateOf<CollectionEntity?>(null) }; val scope=rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Action("Create collection") { create=true } }
        if(collections.isEmpty()) item { EmptyState("Organize your library","Create collections, then add games from the library or chessboard.") }
        items(collections,key={ it.id }) { c -> OutlinedCard { Column(Modifier.padding(16.dp)) { Text(c.name,style=MaterialTheme.typography.titleMedium); Row { TextButton(onClick={ model.filter(Filters(collection=c.id)); model.navigate("My Games") }) { Text("Open") }; TextButton(onClick={ scope.launch { val ids=withContext(Dispatchers.IO) { model.repo.dao.allMemberships().filter { it.collectionId==c.id }.map { it.gameId } }; onExport(ids) } }) { Text("Export") }; TextButton(onClick={ delete=c }) { Text("Delete") } } } } }
    }
    if(create) TextDialog("Create collection","Collection name",onDismiss={ create=false }) { model.addCollection(it); create=false }
    delete?.let { c -> AlertDialog(onDismissRequest={ delete=null },title={ Text("Delete ${c.name}?") },text={ Text("The games remain in your library.") },confirmButton={ TextButton(onClick={ model.deleteCollection(c.id); delete=null }) { Text("Delete") } },dismissButton={ TextButton(onClick={ delete=null }) { Text("Cancel") } }) }
}
@Composable fun SettingsScreen(model:AppModel) {
    val s by model.state.collectAsStateWithLifecycle(); val settings=s.settings; var license by remember { mutableStateOf(false) }; val context=LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SectionTitle("Appearance")
        Choice("Board",listOf("Forest","Walnut","Slate"),s.theme) { model.appearance(theme=it) }
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(s.dark,{ model.appearance(dark=it) }); Text("Dark appearance",Modifier.padding(start=12.dp)) }
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(s.sound,{ model.appearance(sound=it) }); Text("Move sounds",Modifier.padding(start=12.dp)) }
        SectionTitle("Engine","Stockfish 19 · full NNUE · ARM64 baseline NEON")
        Text("NNUE: $NETWORK\nBundled in the APK. No downloads at runtime.",style=MaterialTheme.typography.bodySmall)
        NumberField("CPU threads (1–${Runtime.getRuntime().availableProcessors().coerceAtMost(8)})",settings.threads) { model.settings(settings.copy(threads=it.coerceIn(1,Runtime.getRuntime().availableProcessors().coerceAtMost(8)))) }
        NumberField("Hash memory · MB (16–256)",settings.hashMb) { model.settings(settings.copy(hashMb=it)) }
        NumberField("MultiPV (1–8)",settings.multiPv) { model.settings(settings.copy(multiPv=it)) }
        NumberField("Target depth (1–99)",settings.depth) { model.settings(settings.copy(depth=it)) }
        NumberField("Time per position · ms (0 = depth only)",settings.timeMs) { model.settings(settings.copy(timeMs=it)) }
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(settings.infinite,{ model.settings(settings.copy(infinite=it)) }); Text("Unlimited live analysis",Modifier.padding(start=12.dp)) }
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(settings.battery,{ model.settings(settings.copy(battery=it)) }); Text("Battery saving (at most 2 threads)",Modifier.padding(start=12.dp)) }
        Text("Performance mode: disable battery saving. Hash is capped at one eighth of available memory, up to 256 MB. Severe thermal conditions and low memory pause analysis. Analysis stops when the app enters the background.",style=MaterialTheme.typography.bodySmall)
        Action("Stop and unload engine",model::closeEngine)
        SectionTitle("Move classification","App-defined winning-chance loss thresholds, in percentage points.")
        listOf("inaccuracy" to 5,"mistake" to 10,"blunder" to 20).forEach { (key,default) ->
            var number by remember { mutableIntStateOf(model.prefs.getString(key,null)?.toIntOrNull() ?: default) }
            NumberField(key.replaceFirstChar { it.uppercase() },number) { number=it.coerceIn(1,100); model.prefs.edit().putString(key,number.toString()).apply() }
        }
        Text("Winning chance = 100 / (1 + exp(−centipawns / 300)). Labels compare evaluations before and after a played move, with special handling for forced moves and lost forced mates. Accuracy is an estimate. No opening book is bundled, so no Book move label is assigned.",style=MaterialTheme.typography.bodySmall)
        SectionTitle("Licenses")
        Text("Chess Dude is distributed under GPLv3 or later, with complete corresponding source. Stockfish © 2004–2026 its developers. Chesslib © Ben-Hur Langoni Junior (Apache 2.0). Cburnett pieces © Colin M. L. Burnett (GPLv2 or later).",style=MaterialTheme.typography.bodySmall)
        Action("Read license notices") { license=true }
        Spacer(Modifier.height(24.dp))
    }
    if(license) AlertDialog(onDismissRequest={ license=false },title={ Text("Open-source notices") },text={ Column(Modifier.verticalScroll(rememberScrollState())) { Text(remember { context.assets.open("licenses/NOTICE.txt").bufferedReader().use { it.readText() } },style=MaterialTheme.typography.bodySmall); Text(remember { context.assets.open("licenses/GPL-3.0.txt").bufferedReader().use { it.readText() } },style=MaterialTheme.typography.bodySmall) } },confirmButton={ TextButton(onClick={ license=false }) { Text("Close") } })
}
