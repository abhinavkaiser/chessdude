package org.offlinechess.core

import java.io.Reader
import java.io.PushbackReader
import java.util.UUID

fun uid(): String = UUID.randomUUID().toString()
data class TreeNode(
    val id: String = uid(), val parent: String? = null, val uci: String = "", val san: String = "",
    val fen: String = Chess.START, val ply: Int = 0, val order: Int = 0,
    var comment: String = "", var nags: String = "", var beforeComment: String = ""
)
data class PgnGame(val tags: MutableMap<String,String>, val nodes: MutableList<TreeNode>)

/** Streaming lexer: braces, semicolon comments, RAVs, escaped tag strings and NAGs. */
object Pgn {
    private class Lexer(reader: Reader) {
        val r = PushbackReader(reader, 2)
        fun next(): String? {
            var c: Int
            do { c = r.read() } while (c >= 0 && (c.toChar().isWhitespace() || c == 0xFEFF))
            if(c < 0) return null
            val s = StringBuilder().append(c.toChar())
            when(c.toChar()) {
                '{' -> { while(true) { c = r.read(); require(c >= 0) { "Unclosed PGN comment" }; s.append(c.toChar()); if(c == '}'.code) break } }
                ';' -> { s.clear().append('{'); while(true) { c = r.read(); if(c < 0 || c == 10 || c == 13) break; s.append(c.toChar()) }; s.append('}') }
                '[' -> { var quote = false; var escaped = false
                    while(true) { c = r.read(); require(c >= 0) { "Unclosed PGN tag" }; val ch = c.toChar(); s.append(ch)
                        if(ch == '"' && !escaped) quote = !quote
                        if(ch == ']' && !quote) break
                        escaped = ch == '\\' && !escaped
                    }
                }
                '(', ')' -> Unit
                else -> while(true) { c = r.read(); if(c < 0) break; if(c.toChar().isWhitespace()) break
                    if(c.toChar() in "{}[]();") { r.unread(c); break }; s.append(c.toChar()) }
            }
            require(s.length <= 4_000_000) { "PGN token is too large" }
            return s.toString()
        }
    }
    fun read(reader: Reader, failure: (Int,String) -> Unit = { _, _ -> }): Sequence<PgnGame> = sequence {
        val lexer = Lexer(reader); var tokens = mutableListOf<String>(); var hasMoves = false; var index = 0
        fun parse(): PgnGame? { index++; return try { parseTokens(tokens) } catch(e: Exception) { failure(index,e.message ?: "Invalid PGN"); null } }
        while(true) {
            val t = try { lexer.next() } catch(e: Exception) { failure(index + 1,e.message ?: "Truncated PGN"); break } ?: break
            if(t.startsWith('[') && hasMoves) { parse()?.let { yield(it) }; tokens = mutableListOf(); hasMoves = false }
            tokens.add(t)
            if(!t.startsWith('[') && !t.startsWith('{')) hasMoves = true
            if(tokens.size > 1_000_000) error("A game exceeds the one-million-token safety limit")
        }
        if(tokens.isNotEmpty()) parse()?.let { yield(it) }
    }
    fun parse(text: String): PgnGame = read(text.reader()) { _, e -> error(e) }.firstOrNull() ?: error("No PGN game found")
    private val tag = Regex("^\\[([A-Za-z0-9_]+)\\s+\"((?:\\\\.|[^\"\\\\])*)\"\\]$")
    private fun parseTokens(tokens: List<String>): PgnGame {
        val tags = linkedMapOf<String,String>()
        tokens.filter { it.startsWith('[') }.forEach { t ->
            val m = tag.matchEntire(t) ?: error("Malformed PGN tag: $t")
            tags[m.groupValues[1]] = m.groupValues[2].replace(Regex("\\\\(.)"), "$1")
        }
        require(tags["Variant"].orEmpty().lowercase() in listOf("", "standard", "chess", "normal")) { "Unsupported chess variant" }
        val start = tags["FEN"] ?: Chess.START
        Chess.board(start)
        val root = TreeNode(fen = start)
        val nodes = mutableListOf(root)
        var current = root
        val stack = ArrayDeque<TreeNode>()
        var beforeMove=false; var pendingBefore=""
        for(raw in tokens.filterNot { it.startsWith('[') }) {
            when {
                raw == "(" -> { require(stack.size < 128) { "Variations nested too deeply" }; stack.addLast(current); current = nodes.firstOrNull { it.id == current.parent } ?: error("Variation without a preceding move"); beforeMove=true; pendingBefore="" }
                raw == ")" -> { current = stack.removeLastOrNull() ?: error("Unmatched variation close"); beforeMove=false; pendingBefore="" }
                raw.startsWith('{') -> {
                    val text=raw.drop(1).dropLast(1).trim()
                    if(beforeMove) pendingBefore=listOf(pendingBefore,text).filter { it.isNotEmpty() }.joinToString("\n")
                    else current.comment=listOf(current.comment,text).filter { it.isNotEmpty() }.joinToString("\n")
                }
                raw.startsWith('$') -> { require(raw.drop(1).toIntOrNull() != null) { "Invalid NAG" }; current.nags = (current.nags + " " + raw).trim() }
                else -> {
                    var token = raw.replace(Regex("^\\d+\\.(?:\\.\\.)?"), "")
                    if(token.isEmpty() || token == "..." || token.matches(Regex("\\d+\\.+"))) { beforeMove=true; continue }
                    if(token in listOf("1-0", "0-1", "1/2-1/2", "*")) { if(stack.isEmpty() && tags["Result"] == null) tags["Result"] = token; continue }
                    val annotation = Regex("[!?]+$").find(token)?.value
                    token = token.replace(Regex("[!?]+$"), "")
                    if(token == "e.p.") continue
                    val (uci,san,fen) = Chess.san(current.fen,token)
                    val node = TreeNode(parent=current.id, uci=uci, san=san, fen=fen, ply=current.ply+1, order=nodes.count { it.parent==current.id }, nags=when(annotation) { "!" -> "$1"; "?" -> "$2"; "!!" -> "$3"; "??" -> "$4"; "!?" -> "$5"; "?!" -> "$6"; else -> "" })
                    node.beforeComment=pendingBefore; pendingBefore=""; beforeMove=false
                    nodes.add(node); current=node
                }
            }
        }
        require(stack.isEmpty()) { "Unclosed PGN variation" }
        return PgnGame(tags,nodes)
    }
    private fun escape(s: String) = s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ")
    fun write(game: PgnGame): String = buildString {
        game.tags.forEach { (k,v) -> append("[$k \"${escape(v)}\"]\n") }; append('\n')
        val root = game.nodes.firstOrNull { it.parent == null } ?: error("Missing game root")
        val children = game.nodes.filter { it.parent != null }.groupBy { it.parent }
        if(root.comment.isNotBlank()) append("{${root.comment.replace('}',']')}} ")
        val byId=game.nodes.associateBy { it.id }
        fun line(n:TreeNode,alternatives:List<TreeNode>) {
            val before=byId[n.parent]?.fen?.split(' ') ?: error("Missing move parent")
            append(before[5]).append(if(before[1]=="w") ". " else "... ")
            if(n.beforeComment.isNotBlank()) append('{').append(n.beforeComment.replace('}',']')).append("} ")
            append(n.san).append(' ')
            if(n.nags.isNotBlank()) append(n.nags).append(' ')
            if(n.comment.isNotBlank()) append('{').append(n.comment.replace('}',']')).append("} ")
            alternatives.forEach { append("( "); line(it,emptyList()); append(") ") }
            val next=children[n.id].orEmpty().sortedBy { it.order }
            if(next.isNotEmpty()) line(next.first(),next.drop(1))
        }
        val first=children[root.id].orEmpty().sortedBy { it.order }
        if(first.isNotEmpty()) line(first.first(),first.drop(1))
        append(game.tags["Result"] ?: "*").append('\n')
    }
    fun mainline(nodes: List<TreeNode>): List<TreeNode> {
        val children=nodes.groupBy { it.parent }; val result=mutableListOf<TreeNode>()
        var n=nodes.first { it.parent==null }; result.add(n)
        while(true) { n=children[n.id]?.minByOrNull { it.order } ?: break; result.add(n) }
        return result
    }
}
