package com.example.marketplace.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val Padrao = Typography()

// Títulos com mais peso para dar hierarquia visual às telas
val Typography = Typography(
    headlineLarge = Padrao.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Padrao.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Padrao.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Padrao.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Padrao.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Padrao.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Padrao.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)
