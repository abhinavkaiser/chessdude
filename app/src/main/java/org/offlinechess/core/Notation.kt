package org.offlinechess.core

data class NotationPart(val text:String,val node:TreeNode?=null,val comment:Boolean=false,val before:Boolean=false,val depth:Int=0,val branch:String?=null)
data class NotationParagraph(val id:String,val depth:Int,val parts:List<NotationPart>,val branch:String?=null,val folded:Boolean=false)
object Notation {
    /** Display text of a comment: engine/clock command tags ([%clk ...], [%emt ...], [%cal ...]) are metadata, not commentary,
     *  and several {} blocks on one move arrive joined by newlines - show them as one flowing line. */
    fun cleanComment(raw:String):String = raw.replace(Regex("\\[%[^\\]]*]")," ").replace(Regex("\\s+")," ").trim()

    /** Emit variations beside the move they replace, then resume the enclosing continuation. */
    /** ChessBase style: one flowing paragraph. Variations sit inline in brackets, in normal weight;
     *  the mainline is bold. A variation is collapsed to "( 5.Nf3 … )" when its first move id is in [collapsed]. */
    fun paragraphs(nodes:List<TreeNode>,collapsed:Set<String> = emptySet(),showComments:Boolean = true):List<NotationParagraph> {
        val root=nodes.firstOrNull { it.parent==null } ?: return emptyList()
        val byId=nodes.associateBy { it.id }
        val children=nodes.filter { it.parent!=null }.groupBy { it.parent }.mapValues { (_,v) -> v.sortedBy { it.order } }
        val result=mutableListOf<NotationParagraph>()
        var parts=mutableListOf<NotationPart>(); var seq=0
        // ChessApp way: moves and sidelines flow in one paragraph; every comment gets its own readable line {like this}.
        fun flush() { if(parts.isNotEmpty()) { result.add(NotationParagraph("p${seq++}",0,parts.toList())); parts=mutableListOf() } }
        fun comment(text:String,node:TreeNode,before:Boolean,depth:Int) {
            val c=cleanComment(text); if(!showComments || c.isBlank()) return
            flush(); result.add(NotationParagraph("c${seq++}",depth,listOf(NotationPart("{$c}",node,true,before,depth))))
        }
        comment(root.comment,root,false,0)
        fun line(first:TreeNode,alternatives:List<TreeNode>,depth:Int) {
            var next:TreeNode?=first; var alternativesHere=alternatives; var needLabel=true
            while(next!=null) {
                val n=next
                if(showComments && cleanComment(n.beforeComment).isNotBlank()) { comment(n.beforeComment,n,true,depth); needLabel=true }
                val whiteMoves=byId[n.parent]?.fen?.split(' ')?.getOrNull(1)=="w"
                val label=if(needLabel || whiteMoves) moveLabel(n,byId) else n.san
                parts.add(NotationPart(label.replace(' ','\u00A0')+glyphs(n.nags)+" ",n,false,false,depth))
                needLabel=false
                if(showComments && cleanComment(n.comment).isNotBlank()) { comment(n.comment,n,false,depth); needLabel=true }
                for(alt in alternativesHere) {
                    if(alt.id in collapsed) {
                        parts.add(NotationPart("(\u00A0"+moveLabel(alt,byId).replace(' ','\u00A0')+"\u00A0…) ",alt,false,false,depth+1,alt.id))
                    } else {
                        parts.add(NotationPart("(",null,false,false,depth+1)); line(alt,emptyList(),depth+1)
                        parts.add(NotationPart(") ",null,false,false,depth+1))
                    }
                    needLabel=true
                }
                val c=children[n.id].orEmpty(); next=c.firstOrNull(); alternativesHere=c.drop(1)
            }
        }
        val first=children[root.id].orEmpty()
        first.firstOrNull()?.let { line(it,first.drop(1),0) }
        flush()
        return result
    }
    /** Ids of every variation's first move, for fold/unfold. */
    fun variationRoots(nodes:List<TreeNode>):Set<String> {
        val children=nodes.filter { it.parent!=null }.groupBy { it.parent }
        return children.values.flatMap { it.sortedBy { n -> n.order }.drop(1) }.map { it.id }.toSet()
    }
    fun glyphs(nags:String):String = nags.split(' ').filter { it.isNotBlank() }.joinToString("") {
        mapOf("$1" to "!","$2" to "?","$3" to "!!","$4" to "??","$5" to "!?","$6" to "?!","$10" to " =","$14" to " ⩲","$15" to " ⩱","$16" to " ±","$17" to " ∓","$18" to " +−","$19" to " −+")[it] ?: " $it"
    }
    fun moveLabel(node:TreeNode,byId:Map<String,TreeNode>):String {
        val before=byId[node.parent]?.fen?.split(' ') ?: return node.san
        return "${before[5]}${if(before[1]=="w") "." else "…"} ${node.san}"
    }
}
