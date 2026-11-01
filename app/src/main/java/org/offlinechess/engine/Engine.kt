package org.offlinechess.engine

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import kotlin.math.*

const val NETWORK="nn-1a298aa575a0.nnue"
const val NETWORK_SHA="1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2"
data class EngineSettings(val threads:Int=1,val hashMb:Int=32,val multiPv:Int=3,val depth:Int=18,val timeMs:Int=1500,val infinite:Boolean=false,val battery:Boolean=true)
data class Pv(val depth:Int=0,val cp:Int?=null,val mate:Int?=null,val multipv:Int=1,val nodes:Long=0,val nps:Long=0,val hashfull:Int=0,val time:Long=0,val moves:List<String> = emptyList()) {
    fun whiteScore(whiteToMove:Boolean):Double = (mate?.let { if(it>0) 10000.0 else -10000.0 } ?: cp?.toDouble() ?: 0.0)*(if(whiteToMove) 1 else -1)
    fun score(whiteToMove:Boolean):String { val sign=if(whiteToMove) 1 else -1; return mate?.let { "M${it*sign}" } ?: cp?.let { "%+.2f".format(java.util.Locale.US,it*sign/100.0) } ?: "—" }
}
object UciParser {
    fun parse(line:String):Pv? {
        if(!line.startsWith("info ") || line.contains(" string ")) return null
        val p=line.split(' ').filter { it.isNotEmpty() }
        fun v(key:String)=p.indexOf(key).takeIf { it>=0 }?.let { p.getOrNull(it+1) }
        val score=p.indexOf("score"); var cp:Int?=null; var mate:Int?=null
        if(score>=0) when(p.getOrNull(score+1)) { "cp" -> cp=p.getOrNull(score+2)?.toIntOrNull(); "mate" -> mate=p.getOrNull(score+2)?.toIntOrNull() }
        if(cp==null && mate==null) return null
        val pvIndex=p.indexOf("pv")
        // Upstream reports terminal positions as depth 0 with a score and no PV.
        if(pvIndex<0 && v("depth")!="0") return null
        return Pv(v("depth")?.toIntOrNull() ?: 0,cp,mate,v("multipv")?.toIntOrNull() ?: 1,v("nodes")?.toLongOrNull() ?: 0,v("nps")?.toLongOrNull() ?: 0,v("hashfull")?.toIntOrNull() ?: 0,v("time")?.toLongOrNull() ?: 0,if(pvIndex>=0) p.drop(pvIndex+1) else emptyList())
    }
}

/** Single owner of a packaged, read-only native executable. All searches are serialized.
 * Cancellation drains bestmove before a subsequent position is sent, preventing stale output.
 */
class Engine(private val context:Context) {
    private val lock=Mutex()
    private var process:Process?=null
    private var lines=Channel<String>(Channel.UNLIMITED)
    private var reader:Job?=null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val _status=MutableStateFlow("Stopped")
    val status:StateFlow<String> = _status
    @Volatile private var stopping=false
    @Volatile var actualSettings=EngineSettings(); private set
    fun normalize(settings:EngineSettings):EngineSettings {
        val memory=ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val maxHash=min(256,(memory.availMem/1024/1024/8).toInt()).coerceAtLeast(16)
        val maxThreads=Runtime.getRuntime().availableProcessors().coerceAtMost(if(settings.battery) 2 else 8).coerceAtLeast(1)
        return settings.copy(threads=settings.threads.coerceIn(1,maxThreads),hashMb=settings.hashMb.coerceIn(16,maxHash),multiPv=settings.multiPv.coerceIn(1,8))
    }
    private val thermal=context.getSystemService(PowerManager::class.java)
    @Synchronized private fun send(s:String) { val p=process ?: error("Engine is not running"); p.outputStream.write((s+"\n").toByteArray()); p.outputStream.flush() }
    private suspend fun until(match:(String)->Boolean):String { while(true) { val line=lines.receive(); if(match(line)) return line } }
    private suspend fun start() {
        if(process?.isAlive==true) return
        _status.value="Loading Stockfish 19"
        val network=File(context.filesDir,NETWORK)
        if(!network.exists() || network.length()!=context.assets.openFd(NETWORK).use { it.length }) {
            val temp=File(context.filesDir,"$NETWORK.tmp")
            context.assets.open(NETWORK).use { input -> temp.outputStream().use { input.copyTo(it) } }
            val digest=MessageDigest.getInstance("SHA-256"); temp.inputStream().use { input -> val buf=ByteArray(65536); while(true) { val n=input.read(buf); if(n<0) break; digest.update(buf,0,n) } }
            require(digest.digest().joinToString("") { "%02x".format(it) }==NETWORK_SHA) { "Bundled NNUE verification failed" }
            check(temp.renameTo(network)) { "Unable to install bundled neural network" }
        }
        val binary=File(context.applicationInfo.nativeLibraryDir,"libstockfish.so")
        require(binary.canExecute()) { "Stockfish executable missing or not executable: $binary" }
        val p=ProcessBuilder(binary.absolutePath).directory(context.filesDir).redirectErrorStream(true).start()
        process=p; lines=Channel(Channel.UNLIMITED)
        val channel=lines
        reader=scope.launch { try { p.inputStream.bufferedReader().useLines { stream -> stream.forEach { channel.trySend(it) } }; channel.close(IllegalStateException("Stockfish exited (${p.waitFor()})")) } catch(e:Exception) { channel.close(e) } }
        send("uci"); withTimeout(15000) { until { it=="uciok" } }
        send("setoption name EvalFile value ${network.absolutePath}")
        send("isready"); withTimeout(15000) { until { it=="readyok" } }
        _status.value="Stockfish 19 ready"
    }
    suspend fun analyze(fen:String,settings:EngineSettings,onUpdate:(Pv)->Unit={}):List<Pv> = lock.withLock {
        withContext(Dispatchers.IO) {
            var searching=false
            try {
                stopping=false; start()
                val am=context.getSystemService(ActivityManager::class.java)
                val memory=ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                require(!memory.lowMemory) { "Analysis paused: device memory is low" }
                require(thermal.currentThermalStatus < PowerManager.THERMAL_STATUS_SEVERE) { "Analysis paused: device is too hot" }
                val effective=normalize(settings); actualSettings=effective
                send("setoption name Threads value ${effective.threads}")
                send("setoption name Hash value ${effective.hashMb}")
                send("setoption name MultiPV value ${effective.multiPv}")
                send("setoption name UCI_AnalyseMode value true")
                send("isready"); withTimeout(15000) { until { it=="readyok" } }
                send("position fen $fen")
                val command=when { settings.infinite -> "go infinite"; settings.timeMs>0 -> "go depth ${settings.depth.coerceIn(1,99)} movetime ${settings.timeMs.coerceIn(50,600000)}"; else -> "go depth ${settings.depth.coerceIn(1,99)}" }
                send(command); searching=true; _status.value="Analyzing"
                val result=mutableMapOf<Int,Pv>()
                withTimeout(if(settings.infinite) Long.MAX_VALUE else max(120000L,settings.timeMs+30000L)) {
                    while(true) {
                        val line=lines.receive()
                        if(line.startsWith("bestmove")) break
                        if(line.contains("ERROR") || line.contains("not loaded")) error(line)
                        UciParser.parse(line)?.let { pv -> result[pv.multipv]=pv; if(!stopping) onUpdate(pv) }
                        if(thermal.currentThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE && !stopping) { stopping=true; send("stop") }
                    }
                }
                searching=false; _status.value="Stockfish 19 ready"
                result.toSortedMap().values.toList()
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { _status.value="Engine error: ${e.message}"; destroy(); throw e }
            finally {
                if(searching) withContext(NonCancellable) {
                    runCatching { send("stop"); withTimeout(5000) { until { it.startsWith("bestmove") } } }.onFailure { destroy() }
                    _status.value="Stopped"
                }
            }
        }
    }
    fun requestStop() { stopping=true; runCatching { send("stop") } }
    private fun destroy() { runCatching { process?.destroyForcibly() }; process=null; reader?.cancel(); reader=null }
    suspend fun close() = lock.withLock { withContext(Dispatchers.IO) { runCatching { send("quit"); process?.waitFor(2,java.util.concurrent.TimeUnit.SECONDS) }; destroy(); _status.value="Stopped" } }
}

object Classification {
    /** Logistic winning chance proxy (not a Stockfish output). Mate is treated as a forced win/loss. */
    fun chance(p:Pv,side:Int):Double { val score=p.mate?.let { if(it>0) 10000.0 else -10000.0 } ?: p.cp?.toDouble() ?: 0.0; return 100/(1+exp(-(score*side).coerceIn(-10000.0,10000.0)/300)) }
    fun classify(before:Pv,after:Pv,played:String,legalCount:Int,inaccuracy:Double=5.0,mistake:Double=10.0,blunder:Double=20.0):Pair<String,Double> {
        val loss=(chance(before,1)-chance(after,-1)).coerceAtLeast(0.0)
        val label=when {
            legalCount==1 -> "Forced move"
            before.moves.firstOrNull()==played -> "Best move"
            before.mate!=null && before.mate>0 && (after.mate==null || after.mate>0) -> "Blunder"
            loss>=blunder -> "Blunder"
            loss>=mistake -> "Mistake"
            loss>=inaccuracy -> "Inaccuracy"
            loss<=1.0 -> "Excellent"
            else -> "Good"
        }; return label to loss
    }
}
