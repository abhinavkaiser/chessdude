package org.offlinechess.core

import com.github.bhlangonijr.chesslib.*
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList

object Chess {
    const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    fun board(fen: String): Board {
        val f = fen.trim().split(Regex("\\s+"))
        require(f.size == 6) { "FEN must contain six fields" }
        require(f[0].split('/').size == 8 && f[0].split('/').all { row -> row.all { it in "12345678prnbqkPRNBQK" } && row.sumOf { if(it.isDigit()) it.digitToInt() else 1 } == 8 }) { "Invalid FEN board" }
        require(f[0].split('/').first().none { it in "pP" } && f[0].split('/').last().none { it in "pP" }) { "Pawns cannot occupy the first or eighth rank" }
        require(f[0].count { it == 'K' } == 1 && f[0].count { it == 'k' } == 1) { "FEN requires one king of each color" }
        require(f[1] in listOf("w", "b") && (f[2] == "-" || f[2].all { it in "KQkq" })) { "Invalid side or castling rights" }
        require(f[3] == "-" || f[3].matches(Regex("[a-h][36]"))) { "Invalid en passant square" }
        require((f[4].toIntOrNull() ?: -1) >= 0 && (f[5].toIntOrNull() ?: 0) > 0) { "Invalid FEN counters" }
        return Board().apply { loadFromFen(f.joinToString(" ")) }.also {
            val w = it.getKingSquare(Side.WHITE).ordinal
            val b = it.getKingSquare(Side.BLACK).ordinal
            require(kotlin.math.abs(w % 8 - b % 8) > 1 || kotlin.math.abs(w / 8 - b / 8) > 1) { "Kings cannot be adjacent" }
            require(it.squareAttackedBy(it.getKingSquare(it.sideToMove.flip()), it.sideToMove) == 0L) { "The non-moving king is in check" }
        }
    }
    fun move(fen: String, uci: String): Pair<String, String> {
        val b = board(fen)
        val m = b.legalMoves().firstOrNull { it.toString() == uci } ?: error("Illegal move: $uci")
        val list = MoveList(fen).apply { add(m) }
        val san = list.toSanArray().first()
        check(b.doMove(m, true))
        return san to b.fen
    }
    fun san(fen: String, text: String): Triple<String,String,String> {
        val list = MoveList(fen)
        list.addSanMove(text.replace('0','O'), true, true)
        require(list.size == 1) { "Invalid SAN: $text" }
        val m = list.first()
        val (san, next) = move(fen, m.toString())
        return Triple(m.toString(), san, next)
    }
    fun pvSan(fen: String, moves: List<String>): String = runCatching {
        val list = MoveList(fen)
        val b = board(fen)
        moves.forEach { u ->
            val m = b.legalMoves().firstOrNull { it.toString() == u } ?: return@forEach
            list.add(m); b.doMove(m, true)
        }
        list.toSanWithMoveNumbers()
    }.getOrDefault(moves.joinToString(" "))
    fun drawStatus(board: Board): String? = when {
        board.isMated -> "Checkmate"
        board.isStaleMate -> "Stalemate"
        board.isRepetition(5) -> "Draw: fivefold repetition"
        board.halfMoveCounter >= 150 -> "Draw: 75-move rule"
        board.isRepetition(3) -> "Threefold repetition: draw claim available"
        board.halfMoveCounter >= 100 -> "50-move rule: draw claim available"
        board.isInsufficientMaterial -> "Draw: insufficient material"
        else -> null
    }
}
