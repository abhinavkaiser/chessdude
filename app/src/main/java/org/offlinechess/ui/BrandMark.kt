package org.offlinechess.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.offlinechess.R

@Composable
fun BrandMark(size:Dp=40.dp) {
    Image(painterResource(R.drawable.chess_dude_mark),"Chess Dude knight logo",Modifier.size(size).background(Color(0xFF183D30),RoundedCornerShape(24)))
}
