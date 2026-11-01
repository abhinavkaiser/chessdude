package org.offlinechess.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import org.offlinechess.R
import org.offlinechess.core.Chess
import org.offlinechess.core.TreeNode
import kotlin.math.*

@Composable
fun ChessBoard(node:TreeNode, flipped:Boolean,theme:String,best:String?,marking:Boolean,onMove:(String)->Unit,onMarks:(String)->Unit) {
    val board=remember(node.fen) { Chess.board(node.fen) }
    val legal=remember(node.fen) { board.legalMoves() }
    val legalLabels=remember(node.fen) { legal.associate { it.toString() to Chess.move(node.fen,it.toString()).first } }
    var selected by remember(node.id) { mutableStateOf<Int?>(null) }
    var promotion by remember { mutableStateOf<List<String>>(emptyList()) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    val animation=remember { Animatable(1f) }
    LaunchedEffect(node.id) { selected=null; animation.snapTo(0f); animation.animateTo(1f,tween(180)) }
    val images=mapOf(
        'K' to ImageBitmap.imageResource(R.drawable.piece_wk),'Q' to ImageBitmap.imageResource(R.drawable.piece_wq),'R' to ImageBitmap.imageResource(R.drawable.piece_wr),
        'B' to ImageBitmap.imageResource(R.drawable.piece_wb),'N' to ImageBitmap.imageResource(R.drawable.piece_wn),'P' to ImageBitmap.imageResource(R.drawable.piece_wp),
        'k' to ImageBitmap.imageResource(R.drawable.piece_bk),'q' to ImageBitmap.imageResource(R.drawable.piece_bq),'r' to ImageBitmap.imageResource(R.drawable.piece_br),
        'b' to ImageBitmap.imageResource(R.drawable.piece_bb),'n' to ImageBitmap.imageResource(R.drawable.piece_bn),'p' to ImageBitmap.imageResource(R.drawable.piece_bp))
    fun name(i:Int)="${('a'.code+i%8).toChar()}${i/8+1}"
    fun index(s:String):Int?=if(s.matches(Regex("[a-h][1-8]"))) s[0]-'a'+(s[1]-'1')*8 else null
    fun hit(p:Offset):Int? { val x=floor(p.x/(canvasWidth/8)).toInt(); val y=floor(p.y/(canvasWidth/8)).toInt(); if(x !in 0..7 || y !in 0..7) return null; return if(flipped) 7-x+y*8 else x+(7-y)*8 }
    fun attempt(from:Int,to:Int) {
        val candidates=legal.filter { it.from.ordinal==from && it.to.ordinal==to }.map { it.toString() }
        if(candidates.size>1) promotion=candidates else candidates.firstOrNull()?.let { onMove(it) }
    }
    fun choose(i:Int) {
        if(marking) {
            val from=selected
            if(from==null) selected=i else {
                val directive=if(from==i) "[%csl G${name(i)}]" else "[%cal G${name(from)}${name(i)}]"
                onMarks((node.comment+" "+directive).trim()); selected=null
            }; return
        }
        val from=selected
        if(from!=null && legal.any { it.from.ordinal==from && it.to.ordinal==i }) { attempt(from,i); selected=null }
        else selected=if(legal.any { it.from.ordinal==i }) i else null
    }
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription="Chessboard. ${if(board.sideToMove.toString()=="WHITE") "White" else "Black"} to move. FEN ${node.fen}"; customActions=legal.map { move -> CustomAccessibilityAction("Play ${legalLabels[move.toString()]}") { onMove(move.toString()); true } } }
        .pointerInput(node.id,flipped,marking,selected) { detectTapGestures(onTap={ p -> hit(p)?.let(::choose) },onLongPress={ p -> hit(p)?.let { i -> onMarks((node.comment+" [%csl G${name(i)}]").trim()) } }) }
        .pointerInput(node.id,flipped,marking) { detectDragGestures(onDragStart={ p -> dragFrom=hit(p); dragAt=p },onDrag={ change,delta -> change.consume(); dragAt+=delta },onDragEnd={ val from=dragFrom; val to=hit(dragAt); if(from!=null && to!=null) { if(marking) onMarks((node.comment+" [%cal G${name(from)}${name(to)}]").trim()) else attempt(from,to) }; dragFrom=null },onDragCancel={ dragFrom=null }) }
    ) {
        canvasWidth=size.width; val cell=size.width/8
        fun xy(i:Int):Offset { val file=i%8; val rank=i/8; return Offset((if(flipped) 7-file else file)*cell,(if(flipped) rank else 7-rank)*cell) }
        fun center(i:Int)=xy(i)+Offset(cell/2,cell/2)
        val (light,dark)=when(theme) { "Walnut" -> Color(0xFFF0D9B5) to Color(0xFF9B7150); "Slate" -> Color(0xFFE2E8ED) to Color(0xFF667E90); else -> Color(0xFFE7EBDD) to Color(0xFF6A8872) }
        val last=listOfNotNull(index(node.uci.take(2)),index(node.uci.drop(2).take(2)))
        for(i in 0..63) {
            val p=xy(i); drawRect(if((i/8+i%8)%2==0) dark else light,p,Size(cell,cell))
            if(i in last) drawRect(Color(0x99EED86B),p,Size(cell,cell))
            if(selected==i) drawRect(Color(0xAAEAC355),p,Size(cell,cell))
            if(legal.any { it.from.ordinal==selected && it.to.ordinal==i }) drawCircle(Color(0x66304C3B),cell*.13f,center(i))
            if(board.isKingAttacked && board.getKingSquare(board.sideToMove).ordinal==i) drawCircle(Color(0x99DC463F),cell*.47f,center(i))
        }
        Regex("\\[%csl ([^]]+)]").findAll(node.comment).forEach { match -> match.groupValues[1].split(',').forEach { mark -> index(mark.drop(1))?.let { drawRect(Color(0x6635A07A),xy(it),Size(cell,cell)) } } }
        val aFrom=index(node.uci.take(2)); val aTo=index(node.uci.drop(2).take(2))
        fun piece(i:Int,position:Offset) { val symbol=board.getPiece(Square.squareAt(i)).fenSymbol.firstOrNull(); val img=images[symbol] ?: return; drawImage(img,dstOffset=IntOffset(position.x.roundToInt(),position.y.roundToInt()),dstSize=IntSize(cell.roundToInt(),cell.roundToInt())) }
        for(i in 0..63) if(i!=dragFrom) {
            val p=if(i==aTo && aFrom!=null && animation.value<1) xy(aFrom)+(xy(i)-xy(aFrom))*animation.value else xy(i)
            piece(i,p)
        }
        fun arrow(from:Int,to:Int,color:Color) {
            if(from==to) return
            val start=center(from); val end=center(to); val delta=end-start; val length=delta.getDistance(); val unit=delta/length
            val tip=end-unit*cell*.12f; val base=tip-unit*cell*.27f
            drawLine(color,start,base,cell*.12f)
            val perp=Offset(-unit.y,unit.x)*cell*.18f
            drawPath(Path().apply { moveTo(tip.x,tip.y); lineTo((base+perp).x,(base+perp).y); lineTo((base-perp).x,(base-perp).y); close() },color)
        }
        best?.let { u -> val f=index(u.take(2)); val t=index(u.drop(2).take(2)); if(f!=null && t!=null) arrow(f,t,Color(0xBF23A56E)) }
        Regex("\\[%cal ([^]]+)]").findAll(node.comment).forEach { match -> match.groupValues[1].split(',').forEach { mark -> val f=index(mark.drop(1).take(2)); val t=index(mark.drop(3).take(2)); if(f!=null && t!=null) arrow(f,t,Color(0xCC377EC7)) } }
        dragFrom?.let { piece(it,dragAt-Offset(cell/2,cell/2)) }
        val paint=android.graphics.Paint().apply { isAntiAlias=true; textSize=cell*.18f; typeface=android.graphics.Typeface.DEFAULT_BOLD }
        for(i in 0..7) {
            paint.color=android.graphics.Color.rgb(32,55,40)
            drawContext.canvas.nativeCanvas.drawText((if(flipped) 'h'-i else 'a'+i).toString(),i*cell+cell*.82f,size.height-cell*.05f,paint)
            drawContext.canvas.nativeCanvas.drawText((if(flipped) i+1 else 8-i).toString(),cell*.05f,i*cell+cell*.20f,paint)
        }
    }
    if(promotion.isNotEmpty()) AlertDialog(onDismissRequest={ promotion=emptyList() },title={ Text("Promote pawn") },text={ Column { promotion.forEach { u -> TextButton(onClick={ onMove(u); promotion=emptyList() }) { Text(when(u.last()) { 'q' -> "Queen"; 'r' -> "Rook"; 'b' -> "Bishop"; else -> "Knight" }) } } } },confirmButton={})
}
