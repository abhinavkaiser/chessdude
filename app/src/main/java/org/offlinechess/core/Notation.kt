package org.offlinechess.core

data class NotationPart(val text:String,val node:TreeNode?=null,val comment:Boolean=false,val before:Boolean=false)
data class NotationParagraph(val id:String,val depth:Int,val parts:List<NotationPart>,val branch:String?=null,val folded:Boolean=false)
object Notation {
    /** Emit variations beside the move they replace, then resume the enclosing continuation. */
    fun paragraphs(nodes:List<TreeNode>,collapsed:Set<String> = emptySet()):List<NotationParagraph> {
        val root=nodes.firstOrNull { it.parent==null } ?: return emptyList()
        val byId=nodes.associateBy { it.id }
        val children=nodes.filter { it.parent!=null }.groupBy { it.parent }.mapValues { (_,v) -> v.sortedBy { it.order } }
        val result=mutableListOf<NotationParagraph>()
        if(root.comment.isNotBlank()) result.add(NotationParagraph("root",0,listOf(NotationPart(root.comment,root,true))))
        fun line(first:TreeNode,alternatives:List<TreeNode>,depth:Int) {
            var parts=mutableListOf<NotationPart>(); var count=0; var segment=0
            fun flush() { if(parts.isNotEmpty()) { result.add(NotationParagraph("${first.id}:${segment++}",depth,parts.toList(),if(depth>0 && segment==1) first.id else null)); parts=mutableListOf(); count=0 } }
            if(depth>0) parts.add(NotationPart(if(depth%2==1) "[ " else "( "))
            if(first.id in collapsed && depth>0) {
                parts.add(NotationPart(moveLabel(first,byId)+" … ",first))
                parts.add(NotationPart(if(depth%2==1) "]" else ")"))
                result.add(NotationParagraph(first.id,depth,parts,first.id,true)); return
            }
            var next:TreeNode?=first; var alternativesHere=alternatives
            while(next!=null) {
                val n=next
                if(n.beforeComment.isNotBlank()) parts.add(NotationPart(n.beforeComment+" ",n,true,true))
                val before=byId[n.parent]?.fen?.split(' ')
                val prefix=if(parts.none { it.node!=null && !it.comment } || before?.get(1)=="w") moveLabel(n,byId) else n.san
                parts.add(NotationPart(prefix+glyphs(n.nags)+" ",n))
                if(n.comment.isNotBlank()) parts.add(NotationPart(n.comment+" ",n,true))
                count++
                if(alternativesHere.isNotEmpty()) { flush(); alternativesHere.forEach { line(it,emptyList(),depth+1) } }
                if(count>=8) flush()
                val c=children[n.id].orEmpty(); next=c.firstOrNull(); alternativesHere=c.drop(1)
            }
            if(depth>0) parts.add(NotationPart(if(depth%2==1) "]" else ")"))
            flush()
        }
        val first=children[root.id].orEmpty()
        first.firstOrNull()?.let { line(it,first.drop(1),0) }
        return result
    }
    fun glyphs(nags:String):String = nags.split(' ').filter { it.isNotBlank() }.joinToString("") {
        mapOf("$1" to "!","$2" to "?","$3" to "!!","$4" to "??","$5" to "!?","$6" to "?!","$10" to " =","$14" to " ⩲","$15" to " ⩱","$16" to " ±","$17" to " ∓","$18" to " +−","$19" to " −+")[it] ?: " $it"
    }
    fun moveLabel(node:TreeNode,byId:Map<String,TreeNode>):String {
        val before=byId[node.parent]?.fen?.split(' ') ?: return node.san
        return "${before[5]}${if(before[1]=="w") "." else "…"} ${node.san}"
    }
}
