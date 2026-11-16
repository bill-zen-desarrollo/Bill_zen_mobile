package co.billzen.authenticator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BillZenErpLightColorScheme = lightColorScheme(
    primary = BillZenEmerald,
    onPrimary = Color.White,
    secondary = BillZenGold,
    onSecondary = BillZenDark,
    tertiary = BillZenMint,
    background = BillZenCream,
    onBackground = BillZenDark,
    surface = BillZenWhiteCard,
    onSurface = BillZenDark,
    surfaceVariant = BillZenCream,
    onSurfaceVariant = BillZenTextSecondary,
    outline = BillZenLightBorder,
    error = BillZenError,
    onError = Color.White
)
@Composable
fun BillzenAuntenticatorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BillZenErpLightColorScheme,
        typography = Typography,
        content = content
    )
}
