package org.offlinechess

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.offlinechess.ui.*

class MainActivity:ComponentActivity() {
    private val model:AppModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ChessApp(model) }
        lifecycle.addObserver(LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) model.background() })
        if(savedInstanceState==null) receive(intent)
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); receive(intent) }
    private fun receive(intent:Intent?) {
        if(intent?.action==Intent.ACTION_VIEW) { intent.data?.let { uri ->
            val name=(uri.lastPathSegment.orEmpty()).lowercase()
            if(name.endsWith(".cbh")||name.endsWith(".cbv")||name.endsWith(".cbz")) model.notice("ChessBase files (.cbh) can't be read directly. In ChessBase choose File → Export → PGN, then open the .pgn file with Chess Dude.")
            else model.importPgn(uri)
        }; return }
        if(intent?.action==Intent.ACTION_SEND) {
            @Suppress("DEPRECATION") val uri=intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            if(uri!=null) model.importPgn(uri)
            else intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                val file=java.io.File(cacheDir,"shared.pgn").apply { writeText(text) }
                model.importPgn(android.net.Uri.fromFile(file))
            }
        }
    }
}
