package org.offlinechess.engine

import kotlin.math.exp

object Evaluation {
    fun whiteFraction(pv:Pv?,whiteToMove:Boolean):Float {
        if(pv==null) return .5f
        if(pv.mate!=null) return if(pv.whiteScore(whiteToMove)>0) 1f else 0f
        return (1/(1+exp(-pv.whiteScore(whiteToMove)/300))).toFloat().coerceIn(0f,1f)
    }
    fun description(pv:Pv?,whiteToMove:Boolean):String {
        if(pv==null) return "No evaluation yet"
        val score=pv.whiteScore(whiteToMove)
        val advantage=when { score>10 -> "White is ahead"; score< -10 -> "Black is ahead"; else -> "Equal position" }
        return "$advantage · ${pv.score(whiteToMove)}"
    }
}
