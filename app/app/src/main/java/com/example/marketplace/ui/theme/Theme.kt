package com.example.marketplace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Verde40,
    onPrimary = Color.White,
    primaryContainer = VerdeContainer90,
    onPrimaryContainer = OnVerdeContainer10,
    secondary = Azul40,
    onSecondary = Color.White,
    secondaryContainer = AzulContainer90,
    onSecondaryContainer = OnAzulContainer10,
    tertiary = Laranja40,
    onTertiary = Color.White,
    tertiaryContainer = LaranjaContainer90,
    onTertiaryContainer = OnLaranjaContainer10,
    background = FundoClaro,
    surface = FundoClaro,
    surfaceVariant = SuperficieVariante90,
    onSurfaceVariant = OnSuperficieVariante30,
    outline = Contorno50,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainer = Color(0xFFEAEFE7),
    surfaceContainerHigh = Color(0xFFE5EAE2),
    surfaceContainerHighest = Color(0xFFDFE4DC)
)

private val DarkColorScheme = darkColorScheme(
    primary = Verde80,
    onPrimary = Color(0xFF003914),
    primaryContainer = VerdeContainer30,
    onPrimaryContainer = VerdeContainer90,
    secondary = Azul80,
    onSecondary = Color(0xFF033541),
    secondaryContainer = AzulContainer30,
    onSecondaryContainer = AzulContainer90,
    tertiary = Laranja80,
    onTertiary = Color(0xFF532200),
    tertiaryContainer = LaranjaContainer30,
    onTertiaryContainer = LaranjaContainer90,
    background = FundoEscuro,
    surface = FundoEscuro,
    surfaceVariant = SuperficieVariante30,
    onSurfaceVariant = OnSuperficieVariante80,
    outline = Contorno60,
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631)
)

private val FormasApp = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MarketPlaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Cores fixas (sem dynamic color) para o app ter identidade própria em qualquer aparelho
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = FormasApp,
        content = content
    )
}
