package co.billzen.authenticator

import android.app.Activity
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
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.billzen.authenticator.core.Base32
import co.billzen.authenticator.core.BiometricAuthManager
import co.billzen.authenticator.core.MobileAuditClient
import co.billzen.authenticator.core.MobileLoginResult
import co.billzen.authenticator.core.OtpAuthParser
import co.billzen.authenticator.core.PersistedMobileSession
import co.billzen.authenticator.core.QrPairingPackage
import co.billzen.authenticator.core.SecureAccountStore
import co.billzen.authenticator.core.SecurityDeviceManager
import co.billzen.authenticator.core.TotpAccount
import co.billzen.authenticator.core.TotpGenerator
import co.billzen.authenticator.ui.theme.BillZenAmber
import co.billzen.authenticator.ui.theme.BillZenBorder
import co.billzen.authenticator.ui.theme.BillZenCard
import co.billzen.authenticator.ui.theme.BillZenCream
import co.billzen.authenticator.ui.theme.BillZenDark
import co.billzen.authenticator.ui.theme.BillZenEmerald
import co.billzen.authenticator.ui.theme.BillZenError
import co.billzen.authenticator.ui.theme.BillZenGold
import co.billzen.authenticator.ui.theme.BillZenLightBorder
import co.billzen.authenticator.ui.theme.BillZenMint
import co.billzen.authenticator.ui.theme.BillZenSurface
import co.billzen.authenticator.ui.theme.BillZenTextPrimary
import co.billzen.authenticator.ui.theme.BillZenTextSecondary
import co.billzen.authenticator.ui.theme.BillZenWhiteCard
import co.billzen.authenticator.ui.theme.BillzenAuntenticatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val erpViewModel: ErpViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val accountStore = SecureAccountStore(applicationContext)

        enableEdgeToEdge()
        setContent {
            BillzenAuntenticatorTheme {
                BillZenRootFlow(
                    viewModel = erpViewModel,
                    accountStore = accountStore
                )
            }
        }
    }
}

@Composable
fun BillZenRootFlow(
    viewModel: ErpViewModel,
    accountStore: SecureAccountStore? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var accounts by remember { mutableStateOf<List<TotpAccount>>(emptyList()) }
    var isInitialLoadDone by remember { mutableStateOf(false) }
    var deviceUuidShort by remember { mutableStateOf("--------") }
    var isSessionUnlocked by remember { mutableStateOf(false) }
    var shouldAutoTriggerBiometric by remember { mutableStateOf(true) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var pendingQrPackage by remember { mutableStateOf<QrPairingPackage?>(null) }
    var qrLoginError by remember { mutableStateOf<String?>(null) }
    var accountPendingDelete by remember { mutableStateOf<TotpAccount?>(null) }

    LaunchedEffect(Unit) {
        val (loadedAccounts, loadedUuidShort, savedSession) = withContext(Dispatchers.IO) {
            val storedAccounts = accountStore?.getAccounts() ?: emptyList()
            val shortUuid = try {
                SecurityDeviceManager(context).getOrCreateBoundDeviceUuid().take(8).uppercase()
            } catch (_: Exception) {
                "--------"
            }
            val validSession = accountStore?.getValidMobileSession()
            Triple(storedAccounts, shortUuid, validSession)
        }
        accounts = loadedAccounts
        deviceUuidShort = loadedUuidShort
        isInitialLoadDone = true
        if (savedSession != null) {
            viewModel.restoreSavedSession(savedSession) {
                accountStore?.clearMobileSession()
            }
        }
    }

    DisposableEffect(lifecycleOwner, accounts.isNotEmpty()) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && accounts.isNotEmpty()) {
                isSessionUnlocked = false
                shouldAutoTriggerBiometric = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val primaryAccount = accounts.firstOrNull()
    LaunchedEffect(primaryAccount) {
        if (primaryAccount != null) {
            viewModel.syncLinkedCompany(
                companyName = primaryAccount.companyName,
                nit = primaryAccount.nit
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BillZenCream,
        bottomBar = {
            if (isInitialLoadDone && accounts.isNotEmpty() && isSessionUnlocked) {
                NavigationBar(
                    containerColor = BillZenWhiteCard,
                    contentColor = BillZenDark
                ) {
                    ErpSection.entries.forEach { section ->
                        val isSelected = uiState.selectedSection == section
                        val itemColor = if (isSelected) BillZenEmerald else BillZenTextSecondary
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.selectSection(section) },
                            icon = {
                                ErpNavIcon(
                                    section = section,
                                    color = itemColor
                                )
                            },
                            label = {
                                Text(
                                    text = section.title,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BillZenEmerald,
                                selectedTextColor = BillZenEmerald,
                                indicatorColor = BillZenEmerald.copy(alpha = 0.14f),
                                unselectedIconColor = BillZenTextSecondary,
                                unselectedTextColor = BillZenTextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isInitialLoadDone) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    BillZenLogoHeader()
                }
            } else if (accounts.isEmpty()) {
                InitialQrOnboardingScreen(
                    onScanQrClick = { showQrScanner = true },
                    onSaveManualAccount = { newAccount ->
                        accounts = accountStore?.saveAccount(newAccount) ?: (accounts + newAccount)
                        isSessionUnlocked = true
                        viewModel.selectSection(ErpSection.DASHBOARD)
                        Toast.makeText(
                            context,
                            "NIT ${newAccount.nit} vinculado correctamente",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onLoadDemoClick = {
                        val demoAccount = TotpAccount(
                            nit = "900.123.456-7",
                            companyName = "ZenTech Solutions S.A.S.",
                            secret = "JBSWY3DPEHPK3PXP"
                        )
                        accounts = accountStore?.saveAccount(demoAccount) ?: listOf(demoAccount)
                        isSessionUnlocked = true
                        viewModel.selectSection(ErpSection.DASHBOARD)
                        Toast.makeText(
                            context,
                            "Empresa vinculada. Bienvenido al Panel de Administrador.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            } else if (!isSessionUnlocked) {
                BiometricLockScreen(
                    companyName = primaryAccount?.companyName ?: "Empresa BillZen",
                    shouldAutoPrompt = shouldAutoTriggerBiometric,
                    onConsumeAutoPrompt = { shouldAutoTriggerBiometric = false },
                    onUnlockSuccess = {
                        isSessionUnlocked = true
                        val savedSession = accountStore?.getValidMobileSession()
                        if (savedSession != null) {
                            viewModel.restoreSavedSession(savedSession) {
                                accountStore.clearMobileSession()
                            }
                        }
                    },
                    onScanQrClick = { showQrScanner = true }
                )
            } else {
                when (uiState.selectedSection) {
                    ErpSection.DASHBOARD -> {
                        DashboardScreen(
                            uiState = uiState,
                            deviceUuidShort = deviceUuidShort,
                            onRefreshClick = {
                                viewModel.refreshDailyAudit(
                                    onUnauthorized = { accountStore?.clearMobileSession() }
                                )
                            }
                        )
                    }
                    ErpSection.ACCOUNTING -> {
                        AccountingScreen(uiState = uiState)
                    }
                    ErpSection.TOKEN_2FA -> {
                        SecurityTokenSectionScreen(
                            accounts = accounts,
                            onScanQrClick = { showQrScanner = true },
                            onManualAddClick = { showManualDialog = true },
                            onCopyCode = { code -> copySensitiveCode(context, code) },
                            onDeleteRequest = { target -> accountPendingDelete = target }
                        )
                    }
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
                val pairingPackage = runCatching { MobileAuditClient.parseQrPayload(rawQr) }.getOrNull()
                if (pairingPackage != null) {
                    showQrScanner = false
                    qrLoginError = null
                    pendingQrPackage = pairingPackage
                } else {
                    try {
                        val parsedAccount = OtpAuthParser.parse(rawQr)
                        accounts = accountStore?.saveAccount(parsedAccount) ?: (accounts + parsedAccount)
                        isSessionUnlocked = true
                        showQrScanner = false
                        viewModel.selectSection(ErpSection.DASHBOARD)
                        Toast.makeText(
                            context,
                            "NIT ${parsedAccount.nit} vinculado. Abriendo BillZen ERP...",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (_: IllegalArgumentException) {
                        Toast.makeText(
                            context,
                            "Este código QR no pertenece a BillZen ERP.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    pendingQrPackage?.let { pkg ->
        AdminQrLoginDialog(
            pkg = pkg,
            isLoading = uiState.isLoadingAudit,
            errorMessage = qrLoginError,
            onDismiss = {
                pendingQrPackage = null
                qrLoginError = null
            },
            onLogin = { username, password ->
                qrLoginError = null
                viewModel.loginWithQrPackage(pkg, username, password) { result ->
                    when (result) {
                        is MobileLoginResult.Success -> {
                            val linkedAccount = TotpAccount(
                                nit = pkg.nit,
                                companyName = pkg.empresa,
                                secret = deriveLocalSecret(pkg.pairingToken)
                            )
                            val ttlSeconds = if (result.expiresInSeconds > 0L) {
                                result.expiresInSeconds
                            } else {
                                SecureAccountStore.DEFAULT_MOBILE_SESSION_TTL_SECONDS
                            }
                            val persistedSession = PersistedMobileSession(
                                nit = pkg.nit,
                                companyName = pkg.empresa,
                                adminName = result.usuario.nombreCompleto.ifBlank { result.usuario.username },
                                sessionToken = result.sessionToken,
                                activeBaseUrl = result.activeBaseUrl,
                                fallbackBaseUrl = result.fallbackBaseUrl,
                                isRemoteTunnel = result.isRemoteTunnel,
                                expiresAtEpochMillis = System.currentTimeMillis() + (ttlSeconds * 1000L)
                            )
                            accountStore?.saveMobileSession(persistedSession)
                            accounts = accountStore?.saveAccount(linkedAccount) ?: (accounts + linkedAccount)
                            isSessionUnlocked = true
                            pendingQrPackage = null
                            viewModel.selectSection(ErpSection.DASHBOARD)
                            Toast.makeText(
                                context,
                                "Sesión iniciada. Datos de ${pkg.empresa} actualizados.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is MobileLoginResult.Failure -> {
                            qrLoginError = result.errorMessage
                        }
                    }
                }
            }
        )
    }

    if (showManualDialog) {
        AddAccountDialog(
            onDismiss = { showManualDialog = false },
            onSaveAccount = { newAccount ->
                accounts = accountStore?.saveAccount(newAccount) ?: (accounts + newAccount)
                isSessionUnlocked = true
                showManualDialog = false
                viewModel.selectSection(ErpSection.DASHBOARD)
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
                accountStore?.clearMobileSession()
                val updatedAccounts = accountStore?.removeAccount(target.nit)
                    ?: accounts.filterNot { it.nit == target.nit }
                accounts = updatedAccounts
                if (updatedAccounts.isEmpty()) {
                    isSessionUnlocked = false
                }
                accountPendingDelete = null
                Toast.makeText(context, "Empresa desvinculada del dispositivo", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun BiometricLockScreen(
    companyName: String,
    shouldAutoPrompt: Boolean,
    onConsumeAutoPrompt: () -> Unit,
    onUnlockSuccess: () -> Unit,
    onScanQrClick: () -> Unit = {}
) {
    val activity = LocalContext.current as? Activity
    val biometricManager = remember(activity) {
        activity?.let { BiometricAuthManager(it) }
    }

    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val triggerVerification = {
        if (biometricManager != null) {
            errorMessage = null
            biometricManager.authenticate(
                onSuccess = {
                    errorMessage = null
                    onUnlockSuccess()
                },
                onError = { message, unconfigured ->
                    if (unconfigured) {
                        onUnlockSuccess()
                    } else {
                        errorMessage = message
                    }
                }
            )
        } else {
            onUnlockSuccess()
        }
    }

    LaunchedEffect(shouldAutoPrompt) {
        if (shouldAutoPrompt) {
            delay(250L)
            onConsumeAutoPrompt()
            triggerVerification()
        }
    }

    val pillColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = BillZenDark,
        unfocusedTextColor = BillZenDark,
        focusedContainerColor = BillZenWhiteCard,
        unfocusedContainerColor = BillZenWhiteCard,
        focusedBorderColor = BillZenEmerald,
        unfocusedBorderColor = BillZenLightBorder,
        focusedPlaceholderColor = BillZenTextSecondary,
        unfocusedPlaceholderColor = BillZenTextSecondary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BillZenLogoHeader(centered = true, fontSizeSp = 34)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = companyName,
            color = BillZenTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = usernameInput,
            onValueChange = { usernameInput = it },
            placeholder = { Text("Usuario", fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = pillColors,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            placeholder = { Text("Contraseña", fontSize = 14.sp) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(28.dp),
            colors = pillColors,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = errorMessage!!,
                color = BillZenError,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "O ingresa con tu huella",
            color = BillZenTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        FingerprintCircleButton(onClick = triggerVerification)

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = triggerVerification,
            colors = ButtonDefaults.buttonColors(
                containerColor = BillZenEmerald,
                contentColor = BillZenTextPrimary
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "Ingresar",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onScanQrClick) {
            Text(
                text = "Escanear código QR",
                color = BillZenEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FingerprintCircleButton(onClick: () -> Unit) {
    Surface(
        color = BillZenWhiteCard,
        shape = CircleShape,
        border = BorderStroke(1.dp, BillZenLightBorder),
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = BillZenEmerald,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    style = stroke
                )
                drawArc(
                    color = BillZenEmerald,
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                    size = Size(size.width * 0.64f, size.height * 0.64f),
                    style = stroke
                )
                drawLine(
                    color = BillZenEmerald,
                    start = Offset(size.width * 0.50f, size.height * 0.42f),
                    end = Offset(size.width * 0.50f, size.height * 0.82f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun ErpNavIcon(
    section: ErpSection,
    color: Color
) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val w = size.width
        val h = size.height

        when (section) {
            ErpSection.DASHBOARD -> {
                drawLine(
                    color = color,
                    start = Offset(w * 0.12f, h * 0.86f),
                    end = Offset(w * 0.88f, h * 0.86f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.26f, h * 0.86f),
                    end = Offset(w * 0.26f, h * 0.52f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.86f),
                    end = Offset(w * 0.50f, h * 0.24f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.74f, h * 0.86f),
                    end = Offset(w * 0.74f, h * 0.38f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            ErpSection.ACCOUNTING -> {
                val docLeft = w * 0.18f
                val docTop = h * 0.12f
                val docWidth = w * 0.64f
                val docHeight = h * 0.76f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(docLeft, docTop),
                    size = Size(docWidth, docHeight),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.32f, h * 0.40f),
                    end = Offset(w * 0.68f, h * 0.40f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.32f, h * 0.60f),
                    end = Offset(w * 0.58f, h * 0.60f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            ErpSection.TOKEN_2FA -> {
                val shieldPath = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.84f, h * 0.24f)
                    lineTo(w * 0.84f, h * 0.52f)
                    cubicTo(
                        w * 0.84f, h * 0.72f,
                        w * 0.68f, h * 0.84f,
                        w * 0.50f, h * 0.90f
                    )
                    cubicTo(
                        w * 0.32f, h * 0.84f,
                        w * 0.16f, h * 0.72f,
                        w * 0.16f, h * 0.52f
                    )
                    lineTo(w * 0.16f, h * 0.24f)
                    close()
                }
                drawPath(
                    path = shieldPath,
                    color = color,
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.38f),
                    end = Offset(w * 0.50f, h * 0.60f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun InitialQrOnboardingScreen(
    onScanQrClick: () -> Unit,
    onSaveManualAccount: (TotpAccount) -> Unit = {},
    onLoadDemoClick: () -> Unit
) {
    var nitInput by remember { mutableStateOf("") }
    var secretInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val pillColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = BillZenDark,
        unfocusedTextColor = BillZenDark,
        focusedContainerColor = BillZenWhiteCard,
        unfocusedContainerColor = BillZenWhiteCard,
        focusedBorderColor = BillZenEmerald,
        unfocusedBorderColor = BillZenLightBorder,
        focusedPlaceholderColor = BillZenTextSecondary,
        unfocusedPlaceholderColor = BillZenTextSecondary
    )

    val tryManualConnect: () -> Boolean = {
        val trimmedSecret = secretInput.trim()
        if (trimmedSecret.isEmpty() && nitInput.isBlank()) {
            false
        } else {
            try {
                val account = if (trimmedSecret.startsWith("otpauth://", ignoreCase = true)) {
                    OtpAuthParser.parse(trimmedSecret)
                } else {
                    require(nitInput.isNotBlank())
                    require(Base32.isValid(trimmedSecret))
                    TotpAccount(
                        nit = nitInput.trim(),
                        companyName = "Empresa BillZen",
                        secret = trimmedSecret.uppercase().replace(" ", "").replace("-", "")
                    )
                }
                onSaveManualAccount(account)
                true
            } catch (_: IllegalArgumentException) {
                errorMessage = "Revisa el NIT y la clave ingresados."
                true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BillZenLogoHeader(centered = true, fontSizeSp = 34)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Conecta tu empresa",
            color = BillZenTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = nitInput,
            onValueChange = {
                nitInput = it
                errorMessage = null
            },
            placeholder = { Text("NIT de la empresa", fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = pillColors,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = secretInput,
            onValueChange = {
                secretInput = it
                errorMessage = null
            },
            placeholder = { Text("Contraseña", fontSize = 14.sp) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(28.dp),
            colors = pillColors,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = errorMessage!!,
                color = BillZenError,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "O ingresa con tu huella",
            color = BillZenTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        FingerprintCircleButton(onClick = { if (!tryManualConnect()) onScanQrClick() })

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { if (!tryManualConnect()) onScanQrClick() },
            colors = ButtonDefaults.buttonColors(
                containerColor = BillZenEmerald,
                contentColor = BillZenTextPrimary
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = if (nitInput.isNotBlank() && secretInput.isNotBlank()) "Ingresar" else "Escanear código QR",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onLoadDemoClick) {
            Text(
                text = "Ver demostración",
                color = BillZenTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SecurityTokenSectionScreen(
    accounts: List<TotpAccount>,
    onScanQrClick: () -> Unit,
    onManualAddClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    onDeleteRequest: (TotpAccount) -> Unit
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(200L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Autenticador",
                color = BillZenDark,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onScanQrClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillZenEmerald,
                    contentColor = BillZenTextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Escanear QR",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(accounts, key = { it.nit }) { account ->
                TotpAccountCard(
                    account = account,
                    currentTimeMillis = currentTimeMillis,
                    onCopyCode = onCopyCode,
                    onDeleteRequest = { onDeleteRequest(account) }
                )
            }

            item {
                OutlinedButton(
                    onClick = onManualAddClick,
                    border = BorderStroke(1.dp, BillZenDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "+ Vincular otro NIT manualmente",
                        color = BillZenDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun BillZenBrandMark(sizeDp: Int = 32) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF36A97F), Color(0xFF1F7A5B)),
                start = Offset.Zero,
                end = Offset(w, h)
            ),
            size = Size(w, h),
            cornerRadius = CornerRadius(w * 0.24f, h * 0.24f)
        )
        drawRoundRect(
            color = Color(0xFFFFFFFF),
            topLeft = Offset(w * 0.22f, h * 0.20f),
            size = Size(w * 0.22f, h * 0.60f),
            cornerRadius = CornerRadius(w * 0.11f, h * 0.11f)
        )
        drawRoundRect(
            color = Color(0xB8FFFFFF),
            topLeft = Offset(w * 0.50f, h * 0.20f),
            size = Size(w * 0.28f, h * 0.28f),
            cornerRadius = CornerRadius(w * 0.14f, h * 0.14f)
        )
        drawRoundRect(
            color = Color(0xFF5BEBD0),
            topLeft = Offset(w * 0.50f, h * 0.52f),
            size = Size(w * 0.28f, h * 0.28f),
            cornerRadius = CornerRadius(w * 0.14f, h * 0.14f)
        )
    }
}

@Composable
fun BillZenLogoHeader(
    centered: Boolean = false,
    fontSizeSp: Int = 28
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BillZenBrandMark(sizeDp = if (fontSizeSp >= 32) 36 else 30)
        Spacer(modifier = Modifier.width(10.dp))
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
                    append("Zen")
                }
            },
            fontSize = fontSizeSp.sp
        )
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
                color = BillZenTextPrimary.copy(alpha = 0.65f),
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
        unfocusedLabelColor = BillZenTextPrimary.copy(alpha = 0.7f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BillZenSurface,
        titleContentColor = BillZenTextPrimary,
        textContentColor = BillZenTextPrimary,
        title = {
            Text(
                text = "Conectar empresa",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Escribe el NIT y la clave que aparece en BillZen ERP.",
                    fontSize = 13.sp,
                    color = BillZenTextPrimary.copy(alpha = 0.8f)
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
                    label = { Text("Clave de vinculación") },
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
                    if (!trimmedInput.startsWith("otpauth://", ignoreCase = true) && nitInput.isBlank()) {
                        errorMessage = "Ingresa el NIT de la empresa."
                        return@Button
                    }
                    try {
                        val account = if (trimmedInput.startsWith("otpauth://", ignoreCase = true)) {
                            OtpAuthParser.parse(trimmedInput)
                        } else {
                            require(Base32.isValid(trimmedInput)) {
                                "La clave no es válida. Revísala e intenta de nuevo."
                            }
                            TotpAccount(
                                nit = nitInput.trim(),
                                companyName = companyInput.trim().ifEmpty { "Empresa BillZen" },
                                secret = trimmedInput.uppercase().replace(" ", "").replace("-", "")
                            )
                        }
                        onSaveAccount(account)
                    } catch (_: IllegalArgumentException) {
                        errorMessage = "La clave no es válida. Revísala e intenta de nuevo."
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
                Text("Cancelar", color = BillZenTextPrimary.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
fun AdminQrLoginDialog(
    pkg: QrPairingPackage,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onLogin: (username: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = BillZenTextPrimary,
        unfocusedTextColor = BillZenTextPrimary,
        focusedBorderColor = BillZenMint,
        unfocusedBorderColor = BillZenBorder,
        focusedLabelColor = BillZenMint,
        unfocusedLabelColor = BillZenTextPrimary.copy(alpha = 0.7f)
    )

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = BillZenSurface,
        titleContentColor = BillZenTextPrimary,
        textContentColor = BillZenTextPrimary,
        title = {
            Text(
                text = "Iniciar sesión de Administrador",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Surface(
                    color = BillZenCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BillZenBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = pkg.empresa,
                            color = BillZenTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "NIT: ${pkg.nit}",
                            color = BillZenGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario Administrador") },
                    singleLine = true,
                    enabled = !isLoading,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = BillZenError,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLogin(username, password) },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillZenEmerald,
                    contentColor = BillZenTextPrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = BillZenTextPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = "Iniciar sesión",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancelar", color = BillZenTextPrimary.copy(alpha = 0.7f))
            }
        }
    )
}

private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

private fun deriveLocalSecret(pairingToken: String): String {
    val builder = StringBuilder()
    var buffer = 0
    var bitsInBuffer = 0
    for (byte in pairingToken.toByteArray(Charsets.UTF_8)) {
        buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
        bitsInBuffer += 8
        while (bitsInBuffer >= 5) {
            bitsInBuffer -= 5
            builder.append(BASE32_ALPHABET[(buffer shr bitsInBuffer) and 0x1F])
        }
        buffer = buffer and ((1 shl bitsInBuffer) - 1)
    }
    if (bitsInBuffer > 0) {
        builder.append(BASE32_ALPHABET[(buffer shl (5 - bitsInBuffer)) and 0x1F])
    }
    return builder.toString().take(32).padEnd(16, 'A')
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
        textContentColor = BillZenTextPrimary,
        title = {
            Text(
                text = "¿Desvincular empresa?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Se cerrará la sesión de Administrador de ${account.companyName} (NIT ${account.nit}) y se eliminará la llave de acceso de este dispositivo."
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
                Text("Cancelar", color = BillZenTextPrimary.copy(alpha = 0.7f))
            }
        }
    )
}

private fun copySensitiveCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Código BillZen", code)
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
fun BillZenOnboardingPreview() {
    BillzenAuntenticatorTheme {
        BillZenRootFlow(viewModel = ErpViewModel())
    }
}
