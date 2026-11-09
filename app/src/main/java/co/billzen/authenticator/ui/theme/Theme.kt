package co.billzen.authenticator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BillZenColorScheme = darkColorScheme(
    primary = BillZenEmerald,
    onPrimary = Color.White,
    secondary = BillZenMint,
    onSecondary = BillZenDark,
    background = BillZenDark,
    onBackground = BillZenTextPrimary,
    surface = BillZenSurface,
    onSurface = BillZenTextPrimary,
    error = BillZenError,
)
@Composable
fun BillzenAuntenticatorTheme(
    content:@Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BillZenColorScheme,
        typography = Typography,
        content = content,
    )
}
