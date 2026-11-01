package org.offlinechess

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.offlinechess.core.*
import org.offlinechess.sharing.*

@RunWith(AndroidJUnit4::class)
class SharingTest {
    private val context:Context=ApplicationProvider.getApplicationContext()
    private val game=Pgn.parse("[White \"A\"]\n[Black \"B\"]\n[CustomTag \"kept\"]\n1.e4 {note} (1.d4 d5) e5 *")
    @Test fun pgnFileAndReadableGrantIntentPreserveTree() {
        val payload=ShareContent.pgn(context,game)
        assertEquals("content",payload.uri!!.scheme)
        assertEquals("${context.packageName}.files",payload.uri.authority)
        val text=context.contentResolver.openInputStream(payload.uri)!!.bufferedReader().use { it.readText() }
        val parsed=Pgn.parse(text)
        assertEquals(game.tags,parsed.tags); assertEquals(game.nodes.size,parsed.nodes.size)
        context.contentResolver.query(payload.uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("A_vs_B.pgn",cursor.getString(0))
        }
        val second=ShareContent.pgn(context,game)
        assertNotEquals(payload.uri,second.uri)
        val send=ShareContent.sendIntent(context,payload)
        assertEquals(Intent.ACTION_SEND,send.action); assertEquals("application/x-chess-pgn",send.type)
        @Suppress("DEPRECATION") val stream=send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals(payload.uri,stream)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
        assertEquals(payload.uri,send.clipData!!.getItemAt(0).uri)
        @Suppress("DEPRECATION") val provider=context.packageManager.resolveContentProvider("${context.packageName}.files",PackageManager.GET_META_DATA)!!
        assertFalse(provider.exported); assertTrue(provider.grantUriPermissions)
        val chooser=ShareContent.chooser(context,payload)
        assertEquals(Intent.ACTION_CHOOSER,chooser.action)
        @Suppress("DEPRECATION") val target=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND,target.action)
    }
    @Test fun notationAndFenShareAsText() {
        val notation=ShareContent.notation(game)
        assertEquals("text/plain",notation.mimeType)
        assertFalse(notation.text.contains("[White")); assertTrue(notation.text.contains("d4")); assertTrue(notation.text.contains("{note}"))
        assertEquals(game.nodes.size,Pgn.parse(notation.text).nodes.size)
        val fen=Pgn.mainline(game.nodes).last().fen
        val payload=ShareContent.fen(fen)
        val intent=ShareContent.sendIntent(context,payload)
        assertEquals(fen,intent.getStringExtra(Intent.EXTRA_TEXT)); assertEquals("text/plain",intent.type)
    }
    @Test fun positionImageIsActualPngWithFenCaption() {
        val fen=Pgn.mainline(game.nodes).last().fen
        val payload=ShareContent.positionImage(context,fen,false,"Forest")
        assertEquals("image/png",payload.mimeType); assertEquals(fen,payload.text)
        context.contentResolver.openInputStream(payload.uri!!)!!.use { input ->
            val bitmap=BitmapFactory.decodeStream(input)!!
            assertEquals(1080,bitmap.width); assertEquals(1168,bitmap.height); bitmap.recycle()
        }
        val flipped=ShareContent.positionImage(context,fen,true,"Slate")
        assertNotEquals(payload.uri,flipped.uri)
        assertEquals(fen,flipped.text)
    }
}
