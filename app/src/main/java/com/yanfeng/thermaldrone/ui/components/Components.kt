package com.yanfeng.thermaldrone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yanfeng.thermaldrone.ui.theme.WarnAmber

/** Failsafe / status banner. */
@Composable
fun WarningBanner(text: String, critical: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (critical) MaterialTheme.colorScheme.error else WarnAmber)
            .padding(8.dp)
    ) {
        Text(text, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
