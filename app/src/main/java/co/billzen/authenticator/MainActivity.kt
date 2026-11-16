package co.billzen.authenticator

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import co.billzen.authenticator.core.Base32
import co.billzen.authenticator.core.OtpAuthParser
import co.billzen.authenticator.core.SecureAccountStore
import co.billzen.authenticator.core.TotpAccount
import co.billzen.authenticator.core.TotpGenerator
import co.billzen.authenticator.ui.theme.BillZenAmber
import co.billzen.authenticator.ui.theme.BillZenBorder
import co.billzen.authenticator.ui.theme.BillZenCard
import co.billzen.authenticator.ui.theme.BillZenDark
import co.billzen.authenticator.ui.theme.BillZenEmerald
import co.billzen.authenticator.ui.theme.BillZenError
import co.billzen.authenticator.ui.theme.BillZenGold
import co.billzen.authenticator.ui.theme.BillZenMint
import co.billzen.authenticator.ui.theme.BillZenSurface
import co.billzen.authenticator.ui.theme.BillZenTextPrimary
import co.billzen.authenticator.ui.theme.BillZenTextSecondary
import co.billzen.authenticator.ui.theme.BillzenAuntenticatorTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bloquea capturas y grabaciones de pantalla por seguridad 2FA
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val accountStore = SecureAccountStore(applicationContext)

        enableEdgeToEdge()
        setContent {
            BillzenAuntenticatorTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BillZenTextPrimary
                ) { innerPadding ->
                    BillZenHomeScreen(
                        accountStore = accountStore,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun BillZenHomeScreen(
    accountStore: SecureAccountStore? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var accounts by remember { mutableStateOf(accountStore?.getAccounts() ?: emptyList()) }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var accountPendingDelete by remember { mutableStateOf<TotpAccount?>(null) }


    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(200L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        BillZenHeader(
            hasAccounts = accounts.isNotEmpty(),
            onScanQrClick = { showQrScanner = true },
            onManualAddClick = { showManualDialog = true }
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (accounts.isEmpty()) {
            EmptyAccountsCard(
                onScanQrClick = { showQrScanner = true },
                onManualLinkClick = { showManualDialog = true },
                onLoadDemoClick = {
                    val demoAccount = TotpAccount(
                        nit = "900.123.456-7",
                        companyName = "ZenTech Solutions S.A.S.",
                        secret = "JBSWY3DPEHPK3PXP"
                    )
                    accounts = accountStore?.saveAccount(demoAccount) ?: listOf(demoAccount)
                    Toast.makeText(context, "Empresa de prueba vinculada", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(accounts, key = { it.nit }) { account ->
                    TotpAccountCard(
                        account = account,
                        currentTimeMillis = currentTimeMillis,
                        onCopyCode = { code ->
                            copySensitiveCode(context, code)
                        },
                        onDeleteRequest = {
                            accountPendingDelete = account
                        }
                    )
                }
            }
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onSwitchToManual = {
                showQrScanner = false
                showManualDialog = true
            },
            onQrCodeDetected = { rawQr ->
                try {
                    val parsedAccount = OtpAuthParser.parse(rawQr)
                    accounts = accountStore?.saveAccount(parsedAccount) ?: (accounts + parsedAccount)
                    showQrScanner = false
                    Toast.makeText(
                        context,
                        "NIT ${parsedAccount.nit} vinculado por QR",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(
                        context,
                        e.message ?: "El código QR no es válido para BillZen 2FA",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    if (showManualDialog) {
        AddAccountDialog(
            onDismiss = { showManualDialog = false },
            onSaveAccount = { newAccount ->
                accounts = accountStore?.saveAccount(newAccount) ?: (accounts + newAccount)
                showManualDialog = false
                Toast.makeText(
                    context,
                    "NIT ${newAccount.nit} vinculado correctamente",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    accountPendingDelete?.let { target ->
        DeleteConfirmDialog(
            account = target,
            onDismiss = { accountPendingDelete = null },
            onConfirmDelete = {
                accounts = accountStore?.removeAccount(target.nit)
                    ?: accounts.filterNot { it.nit == target.nit }
                accountPendingDelete = null
                Toast.makeText(context, "Empresa desvinculada", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun BillZenHeader(
    hasAccounts: Boolean = false,
    onScanQrClick: () -> Unit = {},
    onManualAddClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = BillZenDark,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                ) {
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
            fontSize = 28.sp
        )

        if (hasAccounts) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onManualAddClick,
                    border = BorderStroke(1.dp, BillZenDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "+ Clave",
                        color = BillZenDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onScanQrClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BillZenEmerald,
                        contentColor = BillZenTextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Escanear QR",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyAccountsCard(
    onScanQrClick: () -> Unit = {},
    onManualLinkClick: () -> Unit = {},
    onLoadDemoClick: () -> Unit = {}
) {
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
                text = "TU PORTABILIDAD A LA MANO",
                color = BillZenTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Escanea el código QR mostrado en BillZen ERP",
                color = BillZenTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onScanQrClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillZenEmerald,
                    contentColor = BillZenTextPrimary
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

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onManualLinkClick,
                border = BorderStroke(1.dp, BillZenBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ingresar clave o URI manualmente",
                    color = BillZenTextPrimary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onLoadDemoClick) {
                Text(
                    text = "Cargar empresa demo (RFC 6238)",
                    color = BillZenGold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun TotpAccountCard(
    account: TotpAccount,
    currentTimeMillis: Long,
    onCopyCode: (String) -> Unit,
    onDeleteRequest: () -> Unit
) {
    val rawCode = remember(account.secret, currentTimeMillis / 1000L) {
        TotpGenerator.generateCode(
            base32Secret = account.secret,
            timestampMillis = currentTimeMillis,
            periodSeconds = account.periodSeconds,
            digits = account.digits
        )
    }
    val formattedCode = if (rawCode.length == 6) {
        "${rawCode.substring(0, 3)} ${rawCode.substring(3, 6)}"
    } else {
        rawCode
    }

    val secondsLeft = TotpGenerator.secondsRemaining(currentTimeMillis, account.periodSeconds)
    val progress = TotpGenerator.progressRemaining(currentTimeMillis, account.periodSeconds)
    val isExpiringSoon = secondsLeft <= 5
    val accentColor = if (isExpiringSoon) BillZenAmber else BillZenMint

    Surface(
        color = BillZenCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (isExpiringSoon) BillZenAmber else BillZenBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopyCode(rawCode) }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.companyName,
                        color = BillZenTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "NIT: ${account.nit}",
                        color = BillZenGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                TextButton(onClick = onDeleteRequest) {
                    Text(
                        text = "Desvincular",
                        color = BillZenError,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedCode,
                    color = accentColor,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )

                // Anillo de cuenta regresiva de 30s
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        color = accentColor,
                        trackColor = BillZenBorder,
                        strokeWidth = 4.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        text = "${secondsLeft}s",
                        color = BillZenTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Toca la tarjeta para copiar el código",
                color = BillZenTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSaveAccount: (TotpAccount) -> Unit
) {
    var nitInput by remember { mutableStateOf("") }
    var companyInput by remember { mutableStateOf("") }
    var secretOrUriInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = BillZenTextPrimary,
        unfocusedTextColor = BillZenTextPrimary,
        focusedBorderColor = BillZenMint,
        unfocusedBorderColor = BillZenBorder,
        focusedLabelColor = BillZenMint,
        unfocusedLabelColor = BillZenTextSecondary
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BillZenSurface,
        titleContentColor = BillZenTextPrimary,
        textContentColor = BillZenTextSecondary,
        title = {
            Text(
                text = "Vincular empresa manualmente",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Pega la URI otpauth:// generada por el ERP o escribe los datos manualmente:",
                    fontSize = 13.sp,
                    color = BillZenTextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nitInput,
                    onValueChange = {
                        nitInput = it
                        errorMessage = null
                    },
                    label = { Text("NIT de la empresa (ej. 900123456-7)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = companyInput,
                    onValueChange = {
                        companyInput = it
                        errorMessage = null
                    },
                    label = { Text("Razón social (opcional)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = secretOrUriInput,
                    onValueChange = {
                        secretOrUriInput = it
                        errorMessage = null
                    },
                    label = { Text("Semilla Base32 o URI otpauth://") },
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = BillZenError,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedInput = secretOrUriInput.trim()
                    try {
                        val account = if (trimmedInput.startsWith("otpauth://", ignoreCase = true)) {
                            OtpAuthParser.parse(trimmedInput)
                        } else {
                            require(nitInput.isNotBlank()) { "Ingresa el NIT de la empresa." }
                            require(Base32.isValid(trimmedInput)) {
                                "La clave secreta no es un código Base32 válido (usa letras A-Z y números 2-7)."
                            }
                            TotpAccount(
                                nit = nitInput.trim(),
                                companyName = companyInput.trim().ifEmpty { "Empresa BillZen" },
                                secret = trimmedInput.uppercase().replace(" ", "").replace("-", "")
                            )
                        }
                        onSaveAccount(account)
                    } catch (e: IllegalArgumentException) {
                        errorMessage = e.message ?: "Datos de vinculación inválidos."
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillZenEmerald,
                    contentColor = BillZenTextPrimary
                )
            ) {
                Text("Guardar y vincular")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = BillZenTextSecondary)
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    account: TotpAccount,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BillZenSurface,
        titleContentColor = BillZenTextPrimary,
        textContentColor = BillZenTextSecondary,
        title = {
            Text(
                text = "¿Desvincular empresa?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Se eliminará la semilla 2FA de ${account.companyName} (NIT ${account.nit}) de este dispositivo. Para volver a iniciar sesión necesitarás vincularla nuevamente."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillZenError,
                    contentColor = BillZenTextPrimary
                )
            ) {
                Text("Sí, desvincular")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = BillZenTextSecondary)
            }
        }
    )
}

private fun copySensitiveCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Código BillZen 2FA", code)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Código $code copiado al portapapeles", Toast.LENGTH_SHORT).show()
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