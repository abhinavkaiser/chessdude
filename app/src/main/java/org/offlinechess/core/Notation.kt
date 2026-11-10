package org.offlinechess.core

data class NotationPart(val text:String,val node:TreeNode?=null,val comment:Boolean=false,val before:Boolean=false,val depth:Int=0,val branch:String?=null)
data class NotationParagraph(val id:String,val depth:Int,val parts:List<NotationPart>,val branch:String?=null,val folded:Boolean=false)
object Notation {
    /** Emit variations beside the move they replace, then resume the enclosing continuation. */
    /** ChessBase style: one flowing paragraph. Variations sit inline in brackets, in normal weight;
     *  the mainline is bold. A variation is collapsed to "( 5.Nf3 … )" when its first move id is in [collapsed]. */
    fun paragraphs(nodes:List<TreeNode>,collapsed:Set<String> = emptySet(),showComments:Boolean = true):List<NotationParagraph> {
        val root=nodes.firstOrNull { it.parent==null } ?: return emptyList()
        val byId=nodes.associateBy { it.id }
        val children=nodes.filter { it.parent!=null }.groupBy { it.parent }.mapValues { (_,v) -> v.sortedBy { it.order } }
        val parts=mutableListOf<NotationPart>()
        if(showComments && root.comment.isNotBlank()) parts.add(NotationPart(root.comment+" ",root,true))
        fun line(first:TreeNode,alternatives:List<TreeNode>,depth:Int) {
            var next:TreeNode?=first; var alternativesHere=alternatives; var needLabel=true
            while(next!=null) {
                val n=next
                if(showComments && n.beforeComment.isNotBlank()) { parts.add(NotationPart(n.beforeComment+" ",n,true,true,depth)); needLabel=true }
                val whiteMoves=byId[n.parent]?.fen?.split(' ')?.getOrNull(1)=="w"
                val label=if(needLabel || whiteMoves) moveLabel(n,byId) else n.san
                parts.add(NotationPart(label.replace(' ','\u00A0')+glyphs(n.nags)+" ",n,false,false,depth))
                needLabel=false
                if(showComments && n.comment.isNotBlank()) { parts.add(NotationPart(n.comment+" ",n,true,false,depth)); needLabel=true }
                for(alt in alternativesHere) {
                    val open=if(depth%2==0) "(" else "["
                    val close=if(depth%2==0) ") " else "] "
                    if(alt.id in collapsed) {
                        parts.add(NotationPart("$open\u00A0"+moveLabel(alt,byId).replace(' ','\u00A0')+"\u00A0…"+close,alt,false,false,depth+1,alt.id))
                    } else {
                        parts.add(NotationPart("$open",null,false,false,depth+1)); line(alt,emptyList(),depth+1)
                        parts.add(NotationPart(close,null,false,false,depth+1)); 
                    }
                    needLabel=true
                }
                val c=children[n.id].orEmpty(); next=c.firstOrNull(); alternativesHere=c.drop(1)
            }
        }
        val first=children[root.id].orEmpty()
        first.firstOrNull()?.let { line(it,first.drop(1),0) }
        return if(parts.isEmpty()) emptyList() else listOf(NotationParagraph("main",0,parts))
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
