package org.offlinechess.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.offlinechess.core.Pgn
import org.offlinechess.sharing.*

@Composable
fun ShareDialog(model:AppModel,onDismiss:()->Unit) {
    val snapshot=remember { model.state.value }
    val game=snapshot.game ?: return
    val node=snapshot.node ?: return
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var preparing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun share(kind:String) {
        if(preparing!=null) return
        preparing=kind; error=null
        scope.launch {
            try {
                val payload=withContext(Dispatchers.IO) {
                    when(kind) {
                        "PGN file" -> ShareContent.pgn(context,model.repo.load(game.id))
                        "PGN text" -> SharePayload("text/plain",Pgn.write(model.repo.load(game.id)),"Chess Dude game (PGN)")
                        "Notation text" -> ShareContent.notation(model.repo.load(game.id))
                        "Position image" -> ShareContent.positionImage(context,node.fen,snapshot.flipped,snapshot.theme)
                        else -> ShareContent.fen(node.fen)
                    }
                }
                context.startActivity(ShareContent.chooser(context,payload))
                onDismiss()
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { error=e.message ?: "Unable to prepare share" }
            finally { preparing=null }
        }
    }
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Share game or position") },text={
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Choose what to share, then select WhatsApp, Teams or another installed app in Android’s share sheet.",style=MaterialTheme.typography.bodySmall)
            listOf("PGN file" to "Complete game, tags, comments and variations", "PGN text" to "Full PGN as a message", "Notation text" to "Moves, mainline and sidelines", "Position image" to "Board diagram with FEN caption", "Position FEN" to "Current position as text").forEach { (kind,caption) ->
                OutlinedButton(onClick={ share(kind) },enabled=preparing==null,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
                    Column(Modifier.fillMaxWidth()) { Text(kind); Text(caption,style=MaterialTheme.typography.labelSmall) }
                }
            }
            preparing?.let { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Preparing $it…",style=MaterialTheme.typography.bodySmall) }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        }
    },confirmButton={ TextButton(onClick=onDismiss) { Text("Cancel") } })
}
