package org.offlinechess.core

object ExportNames {
    private fun component(value:String?):String = value.orEmpty().trim().takeUnless { it in listOf("","?","*","????.??.??") }
        ?.replace(Regex("[^\\p{L}\\p{N}._-]+"),"_")?.trim('_','.','-')?.take(48).orEmpty()
    fun pgn(tags:Map<String,String>):String {
        val white=component(tags["White"]); val black=component(tags["Black"])
        val players=if(white.isNotEmpty() || black.isNotEmpty()) "${white.ifEmpty { "White" }}_vs_${black.ifEmpty { "Black" }}" else "ChessDude_Analysis"
        return (listOf(players,component(tags["Event"]),component(tags["Date"]).replace('.','-')).filter { it.isNotEmpty() }.joinToString("_").take(180))+".pgn"
    }
    fun validRating(text:String)=text.isBlank() || (text.trim().matches(Regex("[0-9]{1,4}")) && text.trim().toInt() in 0..4000)
}
