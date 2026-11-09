package co.billzen.authenticator

import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.billzen.authenticator.ui.theme.BillZenBorder
import co.billzen.authenticator.ui.theme.BillZenCard
import co.billzen.authenticator.ui.theme.BillZenDark
import co.billzen.authenticator.ui.theme.BillZenEmerald
import co.billzen.authenticator.ui.theme.BillZenGold
import co.billzen.authenticator.ui.theme.BillZenMint
import co.billzen.authenticator.ui.theme.BillZenTextSecondary
import co.billzen.authenticator.ui.theme.BillZenTextPrimary
import co.billzen.authenticator.ui.theme.BillzenAuntenticatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContent {
            BillzenAuntenticatorTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BillZenTextPrimary
                ) { innerPadding ->
                    BillZenHomeScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}  //ERROR

@Composable
fun BillZenHomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        //Logo de Billzen
        BillZenHeader()

        Spacer(modifier = Modifier.height(28.dp))

        //Bienvenida a billzen
        EmptyAccountsCard()
    }
}

@Composable
fun BillZenHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = BillZenDark,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    ){
                        append("Bill")
                    }
                    withStyle(
                        SpanStyle(
                            color = BillZenGold,
                            fontWeight = FontWeight.Light,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif
                        )
                    ) {
                        append("zen")
                    }
                },
                fontSize = 26.sp
            )
        }
    }


@Composable
fun EmptyAccountsCard() {
    Surface(
        color = BillZenCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BillZenBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Protege tu empresa con 2FA",
                color = BillZenTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Escanea el código QR mostrado en Billzen ERP de escritorio para vincular tu NIT y generar códigos de 6 dígitos cada 30 segundos.",
                color = BillZenTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {},

                    colors = ButtonDefaults.buttonColors(
                        containerColor = BillZenEmerald,
                        contentColor = BillZenTextPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                    ) {
                    Text(
                        text = "Escanear QR de vinculación",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                }
        }
    }
    @Preview(showBackground = true)
    @Composable
    fun BillZenHomePreview() {
        BillzenAuntenticatorTheme {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = BillZenTextPrimary
            ) { innerPadding ->
                BillZenHomeScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }