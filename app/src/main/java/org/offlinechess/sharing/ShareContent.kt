package org.offlinechess.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.github.bhlangonijr.chesslib.Square
import org.offlinechess.R
import org.offlinechess.core.*
import java.io.File

/** User-created export files only; the provider never exposes the database or all cache files. */
data class SharePayload(val mimeType:String,val text:String,val subject:String,val uri:Uri?=null)
object ShareContent {
    private fun output(context:Context,extension:String,name:String?=null):File {
        val dir=File(context.cacheDir,"shared").apply { check(exists() || mkdirs()) }
        val cutoff=System.currentTimeMillis()-7*24*60*60*1000L
        dir.listFiles()?.filter { it.lastModified()<cutoff }?.forEach { it.deleteRecursively() }
        val session=File(dir,uid()).apply { check(mkdirs()) }
        return File(session,name ?: "ChessDude-position.$extension")
    }
    private fun title(game:PgnGame)=listOfNotNull(game.tags["White"],game.tags["Black"]).filter { it.isNotBlank() }.joinToString(" — ").ifBlank { "Chess Dude game" }
    fun pgn(context:Context,game:PgnGame):SharePayload {
        val file=output(context,"pgn",ExportNames.pgn(game.tags)).apply { writeText(Pgn.write(game),Charsets.UTF_8) }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        return SharePayload("application/x-chess-pgn","PGN from Chess Dude",title(game),uri)
    }
    fun notation(game:PgnGame):SharePayload {
        val pgn=Pgn.write(game)
        val moves=if(pgn.startsWith('[')) pgn.substringAfter("\n\n").trim() else pgn.trim()
        return SharePayload("text/plain",moves,title(game))
    }
    fun fen(fen:String):SharePayload { Chess.board(fen); return SharePayload("text/plain",fen,"Chess Dude position (FEN)") }
    fun positionImage(context:Context,fen:String,flipped:Boolean,theme:String):SharePayload {
        val board=Chess.board(fen)
        val bitmap=Bitmap.createBitmap(1080,1168,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.drawColor(Color.rgb(248,249,244))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color=Color.rgb(28,54,41); textSize=30f; typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD) }
        canvas.drawText("Chess Dude · ${if(fen.split(' ')[1]=="w") "White" else "Black"} to move",28f,40f,paint)
        val colors=when(theme) { "Walnut" -> Color.rgb(240,217,181) to Color.rgb(155,113,80); "Slate" -> Color.rgb(226,232,237) to Color.rgb(102,126,144); else -> Color.rgb(231,235,221) to Color.rgb(106,136,114) }
        val resources=mapOf('K' to R.drawable.piece_wk,'Q' to R.drawable.piece_wq,'R' to R.drawable.piece_wr,'B' to R.drawable.piece_wb,'N' to R.drawable.piece_wn,'P' to R.drawable.piece_wp,'k' to R.drawable.piece_bk,'q' to R.drawable.piece_bq,'r' to R.drawable.piece_br,'b' to R.drawable.piece_bb,'n' to R.drawable.piece_bn,'p' to R.drawable.piece_bp)
        val images=mutableMapOf<Char,Bitmap>()
        try {
            for(i in 0..63) {
                val file=i%8; val rank=i/8
                val x=28+(if(flipped) 7-file else file)*128; val y=64+(if(flipped) rank else 7-rank)*128
                val rect=Rect(x,y,x+128,y+128)
                paint.color=if((file+rank)%2==0) colors.second else colors.first
                canvas.drawRect(rect,paint)
                val symbol=board.getPiece(Square.squareAt(i)).fenSymbol.firstOrNull()
                resources[symbol]?.let { res ->
                    val piece=images.getOrPut(symbol!!) { BitmapFactory.decodeResource(context.resources,res) }
                    canvas.drawBitmap(piece,null,rect,paint)
                }
            }
            paint.color=Color.rgb(30,52,40); paint.textSize=19f
            for(i in 0..7) {
                canvas.drawText((if(flipped) 'h'-i else 'a'+i).toString(),28+i*128+109f,1080f,paint)
                canvas.drawText((if(flipped) i+1 else 8-i).toString(),32f,64+i*128+21f,paint)
            }
            paint.textSize=18f; paint.typeface=Typeface.DEFAULT
            canvas.drawText(fen,28f,1130f,paint)
            val file=output(context,"png")
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) { "Unable to create position image" } }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            return SharePayload("image/png",fen,"Chess Dude position",uri)
        } finally { bitmap.recycle(); images.values.forEach { it.recycle() } }
    }
    fun sendIntent(context:Context,payload:SharePayload):Intent = Intent(Intent.ACTION_SEND).apply {
        type=payload.mimeType
        putExtra(Intent.EXTRA_SUBJECT,payload.subject)
        putExtra(Intent.EXTRA_TEXT,payload.text)
        payload.uri?.let { uri ->
            putExtra(Intent.EXTRA_STREAM,uri)
            clipData=ClipData.newUri(context.contentResolver,payload.subject,uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    fun chooser(context:Context,payload:SharePayload):Intent = Intent.createChooser(sendIntent(context,payload),"Share with")
}
