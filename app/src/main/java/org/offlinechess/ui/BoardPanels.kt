package org.offlinechess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import org.offlinechess.core.*
import org.offlinechess.data.*
import org.offlinechess.engine.*

@Composable
fun VerticalEvaluationBar(pv:Pv?,whiteToMove:Boolean,flipped:Boolean,modifier:Modifier=Modifier) {
    val share=Evaluation.whiteFraction(pv,whiteToMove)
    Canvas(modifier.testTag("evaluation-bar").semantics { contentDescription="Evaluation bar. ${Evaluation.description(pv,whiteToMove)}. White segment ${if(flipped) "at top" else "at bottom"}." }) {
        drawRect(Color(0xFF222824))
        val height=size.height*share
        drawRect(Color(0xFFFAFAF5),Offset(0f,if(flipped) 0f else size.height-height),Size(size.width,height))
        drawLine(Color(0xFFD2AE55),Offset(0f,size.height/2),Offset(size.width,size.height/2),2.dp.toPx())
        drawRect(Color(0xFF79847C),style=Stroke(1.dp.toPx()))
    }
}

@Composable
fun NotationPanel(nodes:List<TreeNode>,selected:String,onSelect:(TreeNode)->Unit,modifier:Modifier=Modifier,onComment:(TreeNode,Boolean)->Unit) {
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    var fontSize by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(16) }
    var menu by remember { mutableStateOf(false) }
    val byId=remember(nodes) { nodes.associateBy { it.id } }
    val root=nodes.firstOrNull { it.parent==null }
    val rows=remember(nodes,collapsed) { Notation.paragraphs(nodes,collapsed) }
    val listState=rememberLazyListState()
    LaunchedEffect(selected) {
        val path=mutableSetOf<String>(); var n=byId[selected]
        while(n!=null) { path.add(n.id); n=byId[n.parent] }
        collapsed=collapsed-path
    }
    LaunchedEffect(selected,rows) {
        val index=rows.indexOfFirst { row -> row.parts.any { it.node?.id==selected } }
        if(index>=0) listState.animateScrollToItem(index)
    }
    OutlinedCard(modifier.testTag("notation-panel")) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Notation",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(onClick={ byId[selected]?.let { onComment(it,false) } },modifier=Modifier.size(40.dp)) { Icon(androidx.compose.material.icons.Icons.Default.Edit,"Add or edit comment",Modifier.size(20.dp)) }
            Box {
                IconButton(onClick={ menu=true },modifier=Modifier.size(40.dp)) { Icon(androidx.compose.material.icons.Icons.Default.MoreVert,"Notation options") }
                DropdownMenu(expanded=menu,onDismissRequest={ menu=false }) {
                    DropdownMenuItem(text={ Text("Larger text") },onClick={ fontSize=(fontSize+2).coerceAtMost(24); menu=false })
                    DropdownMenuItem(text={ Text("Smaller text") },onClick={ fontSize=(fontSize-2).coerceAtLeast(12); menu=false })
                    DropdownMenuItem(text={ Text("Fold variations") },onClick={ collapsed=Notation.paragraphs(nodes).mapNotNull { it.branch }.toSet(); menu=false })
                    DropdownMenuItem(text={ Text("Unfold variations") },onClick={ collapsed=emptySet(); menu=false })
                }
            }
            TextButton(onClick={ root?.let(onSelect) },contentPadding=PaddingValues(horizontal=6.dp)) { Text("Start",style=MaterialTheme.typography.labelMedium) }
        }
        HorizontalDivider()
        LazyColumn(state=listState,modifier=Modifier.fillMaxWidth().weight(1f).testTag("notation-lines"),contentPadding=PaddingValues(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(rows.isEmpty()) item { Text("Play a move to begin. Moves are saved automatically.",style=MaterialTheme.typography.bodySmall) }
            items(rows.size,key={ rows[it].id }) { i ->
                val row=rows[i]
                Row(Modifier.fillMaxWidth().padding(start=(row.depth.coerceAtMost(4)*8).dp),verticalAlignment=Alignment.Top) {
                    row.branch?.let { branch ->
                        Text(if(row.folded) "⊞ " else "⊟ ",color=MaterialTheme.colorScheme.primary,modifier=Modifier.clickable { collapsed=if(branch in collapsed) collapsed-branch else collapsed+branch }.padding(vertical=2.dp).semantics { contentDescription=if(row.folded) "Expand variation" else "Collapse variation" })
                    }
                    val normal=MaterialTheme.colorScheme.onSurface
                    val commentColor=MaterialTheme.colorScheme.tertiary
                    val highlight=MaterialTheme.colorScheme.primary
                    val onHighlight=MaterialTheme.colorScheme.onPrimary
                    val text=androidx.compose.ui.text.buildAnnotatedString {
                        row.parts.forEach { part ->
                            val n=part.node
                            val active=n?.id==selected && !part.comment
                            val style=androidx.compose.ui.text.SpanStyle(color=if(active) onHighlight else if(part.comment) commentColor else normal,
                                background=if(active) highlight else Color.Transparent,fontWeight=if(row.depth==0 && !part.comment) FontWeight.Bold else FontWeight.Normal)
                            pushStyle(style)
                            if(n!=null) pushLink(androidx.compose.ui.text.LinkAnnotation.Clickable("${n.id}:${part.comment}:${part.before}",linkInteractionListener={ if(part.comment) onComment(n,part.before) else onSelect(n) }))
                            append(part.text)
                            if(n!=null) pop()
                            pop()
                        }
                    }
                    Text(text,fontSize=fontSize.sp,lineHeight=(fontSize+7).sp,modifier=Modifier.weight(1f).testTag("notation-paragraph-${row.id}"))
                }
            }
        }
    }
}

@Composable
fun CommentDialog(node:TreeNode,beforeInitially:Boolean,onDismiss:()->Unit,onSave:(String,String,String)->Unit) {
    var before by remember(node.id) { mutableStateOf(node.beforeComment) }
    var after by remember(node.id) { mutableStateOf(node.comment) }
    var nags by remember(node.id) { mutableStateOf(node.nags) }
    var editingBefore by remember(node.id) { mutableStateOf(beforeInitially && node.parent!=null) }
    AlertDialog(onDismissRequest=onDismiss,title={ Text(if(node.parent==null) "Position comment" else "Comment · ${node.san}") },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(node.parent!=null) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(editingBefore,{ editingBefore=true },label={ Text("Before move") })
                FilterChip(!editingBefore,{ editingBefore=false },label={ Text("After move") })
            }
            OutlinedTextField(if(editingBefore) before else after,{ if(editingBefore) before=it else after=it },label={ Text(if(editingBefore) "Comment before move" else "Comment after move") },minLines=4,maxLines=10,modifier=Modifier.fillMaxWidth())
            if(node.parent!=null) {
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf("$1","$2","$3","$4","$5","$6").forEach { nag ->
                    Text(Notation.glyphs(nag),modifier=Modifier.clickable { val values=nags.split(' ').filter { it.isNotBlank() }; nags=(if(nag in values) values-nag else values+ nag).joinToString(" ") }.padding(8.dp))
                } }
                OutlinedTextField(nags,{ nags=it },label={ Text("Annotation symbols · NAGs") },singleLine=true)
            }
            Text("Comments appear in the notation and are included when sharing PGN.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={ TextButton(onClick={ onSave(before,after,nags) }) { Text("Save comment") } },dismissButton={ TextButton(onClick=onDismiss) { Text("Cancel") } })
}

@Composable
fun SaveGameDialog(game:GameEntity,onDismiss:()->Unit,onSave:(String,String,String,String,String,String,String)->Unit) {
    var white by remember(game.id) { mutableStateOf(game.white) }
    var black by remember(game.id) { mutableStateOf(game.black) }
    var whiteElo by remember(game.id) { mutableStateOf(game.whiteElo?.toString().orEmpty()) }
    var blackElo by remember(game.id) { mutableStateOf(game.blackElo?.toString().orEmpty()) }
    var event by remember(game.id) { mutableStateOf(game.event) }
    var date by remember(game.id) { mutableStateOf(game.date) }
    var result by remember(game.id) { mutableStateOf(game.result.ifBlank { "*" }) }
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Save game to My Games") },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Every move, variation and comment is already saved on this device. Add details to find this game in your library.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(white,{ white=it },label={ Text("White player") },singleLine=true)
            OutlinedTextField(black,{ black=it },label={ Text("Black player") },singleLine=true)
            OutlinedTextField(whiteElo,{ whiteElo=it },label={ Text("White Elo (optional)") },singleLine=true,isError=!ExportNames.validRating(whiteElo),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
            OutlinedTextField(blackElo,{ blackElo=it },label={ Text("Black Elo (optional)") },singleLine=true,isError=!ExportNames.validRating(blackElo),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
            OutlinedTextField(event,{ event=it },label={ Text("Game name / Event") },singleLine=true)
            OutlinedTextField(date,{ date=it },label={ Text("Date · YYYY.MM.DD (optional)") },singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf("*","1-0","0-1","1/2-1/2").forEach { value ->
                FilterChip(selected=result==value,onClick={ result=value },label={ Text(value,style=MaterialTheme.typography.labelSmall) })
            } }
        }
    },confirmButton={ TextButton(onClick={ onSave(white,black,event,date,result,whiteElo,blackElo) },enabled=ExportNames.validRating(whiteElo) && ExportNames.validRating(blackElo)) { Text("Save to My Games") } },dismissButton={ TextButton(onClick=onDismiss) { Text("Cancel") } })
}
