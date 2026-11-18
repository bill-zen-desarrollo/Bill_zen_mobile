package co.billzen.authenticator

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.billzen.authenticator.core.CashierSaleSummary
import co.billzen.authenticator.core.DailyAuditSnapshot
import co.billzen.authenticator.core.LowStockAlertItem
import co.billzen.authenticator.core.MobileAuditClient
import co.billzen.authenticator.core.MobileLoginResult
import co.billzen.authenticator.core.PersistedMobileSession
import co.billzen.authenticator.core.QrPairingPackage
import co.billzen.authenticator.core.SecurityAuditLogItem
import co.billzen.authenticator.core.StockMovementItem
import co.billzen.authenticator.ui.theme.BillZenAmber
import co.billzen.authenticator.ui.theme.BillZenEmerald
import co.billzen.authenticator.ui.theme.BillZenGold
import co.billzen.authenticator.ui.theme.BillZenMint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class ErpSection(val title: String) {
    DASHBOARD("Dashboard"),
    ACCOUNTING("Contabilidad"),
    TOKEN_2FA("Autenticador")
}

data class KpiMetric(
    val title: String,
    val formattedValue: String,
    val deltaPercentage: String,
    val isPositiveTrend: Boolean
)

data class CategorySaleSlice(
    val categoryName: String,
    val percentage: Float,
    val formattedAmount: String,
    val color: Color
)

data class TopProductRank(
    val productName: String,
    val unitsSold: Int,
    val relativeProgress: Float, // 0.0f .. 1.0f respecto al producto #1
    val formattedRevenue: String
)

enum class ExpensePaymentStatus(val label: String) {
    PAID("PAGADO"),
    PENDING("PENDIENTE")
}

data class ExpenseRecord(
    val id: String,
    val date: String,
    val concept: String,
    val supplier: String,
    val paymentMethod: String,
    val formattedAmount: String,
    val status: ExpensePaymentStatus
)

data class AccountingSummary(
    val netSales: String,
    val grossProfit: String,
    val operatingExpenses: String,
    val accountsPayable: String
)

data class ErpUiState(
    val adminName: String = "Camilo",
    val companyName: String = "ZenTech Solutions S.A.S.",
    val companyNit: String = "900.123.456-7",
    val selectedSection: ErpSection = ErpSection.DASHBOARD,
    val kpiMetrics: List<KpiMetric> = emptyList(),
    val categorySales: List<CategorySaleSlice> = emptyList(),
    val topProducts: List<TopProductRank> = emptyList(),
    val accountingSummary: AccountingSummary = AccountingSummary(
        netSales = "$ 0",
        grossProfit = "$ 0",
        operatingExpenses = "$ 0",
        accountsPayable = "$ 0"
    ),
    val expenses: List<ExpenseRecord> = emptyList(),
    val isLiveConnected: Boolean = false,
    val connectionModeLabel: String = "MODO DEMO LOCAL",
    val lastUpdatedAt: String = "",
    val cashierSales: List<CashierSaleSummary> = emptyList(),
    val stockMovements: List<StockMovementItem> = emptyList(),
    val lowStockAlerts: List<LowStockAlertItem> = emptyList(),
    val securityLogs: List<SecurityAuditLogItem> = emptyList(),
    val isLoadingAudit: Boolean = false,
    val syncErrorMessage: String? = null,
    val activeBaseUrl: String = "",
    val fallbackBaseUrl: String? = null,
    val sessionToken: String? = null,
    val dailyAuditSnapshot: DailyAuditSnapshot? = null
)

class ErpViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialMockState())
    val uiState: StateFlow<ErpUiState> = _uiState.asStateFlow()

    @Volatile
    private var currentBaseUrl: String = ""

    @Volatile
    private var currentFallbackUrl: String? = null

    @Volatile
    private var currentSessionToken: String? = null

    @Volatile
    private var onSessionRevokedCallback: (() -> Unit)? = null

    fun selectSection(section: ErpSection) {
        _uiState.update { it.copy(selectedSection = section) }
    }

    fun syncLinkedCompany(companyName: String, nit: String) {
        _uiState.update { state ->
            state.copy(
                adminName = if (state.isLiveConnected) state.adminName else companyName,
                companyName = companyName,
                companyNit = nit
            )
        }
    }

    fun restoreSavedSession(
        session: PersistedMobileSession,
        onSessionRevoked: () -> Unit = {}
    ) {
        currentBaseUrl = session.activeBaseUrl
        currentFallbackUrl = session.fallbackBaseUrl
        currentSessionToken = session.sessionToken
        onSessionRevokedCallback = onSessionRevoked

        val transportLabel = if (session.isRemoteTunnel) {
            "READ_ONLY • Túnel HTTPS Cloudflare"
        } else {
            "READ_ONLY • Red Local LAN"
        }

        _uiState.update { state ->
            state.copy(
                adminName = session.adminName.ifBlank { state.adminName },
                companyName = session.companyName.ifBlank { state.companyName },
                companyNit = session.nit.ifBlank { state.companyNit },
                isLiveConnected = true,
                connectionModeLabel = transportLabel,
                activeBaseUrl = session.activeBaseUrl,
                fallbackBaseUrl = session.fallbackBaseUrl,
                sessionToken = session.sessionToken,
                syncErrorMessage = null
            )
        }

        refreshDailyAudit(
            baseUrl = session.activeBaseUrl,
            fallbackUrl = session.fallbackBaseUrl,
            sessionToken = session.sessionToken,
            onUnauthorized = onSessionRevoked
        )
    }

    fun clearSyncError() {
        _uiState.update { it.copy(syncErrorMessage = null) }
    }

    /**
     * Autentica al Administrador contra `POST /api/movil/login` en `BillZen.exe` usando el paquete QR
     * escaneado (`QrPairingPackage`), almacena el `session_token` y sincroniza inmediatamente
     * la auditoría del día (`GET /api/movil/auditoria-dia`) en `Dispatchers.IO`.
     */
    fun loginWithQrPackage(
        pkg: QrPairingPackage,
        username: String,
        password: String,
        onResult: ((MobileLoginResult) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isLoadingAudit = true,
                    syncErrorMessage = null
                )
            }

            val loginResult = MobileAuditClient.loginAdmin(
                pkg = pkg,
                username = username,
                password = password
            )

            when (loginResult) {
                is MobileLoginResult.Success -> {
                    currentBaseUrl = loginResult.activeBaseUrl
                    currentFallbackUrl = loginResult.fallbackBaseUrl
                    currentSessionToken = loginResult.sessionToken

                    val transportLabel = if (loginResult.isRemoteTunnel) {
                        "READ_ONLY • Túnel HTTPS Cloudflare"
                    } else {
                        "READ_ONLY • Red Local LAN"
                    }

                    _uiState.update { state ->
                        state.copy(
                            adminName = loginResult.usuario.nombreCompleto.ifBlank { loginResult.usuario.username },
                            companyName = pkg.empresa.ifBlank { state.companyName },
                            companyNit = pkg.nit.ifBlank { state.companyNit },
                            isLiveConnected = true,
                            connectionModeLabel = transportLabel,
                            activeBaseUrl = loginResult.activeBaseUrl,
                            fallbackBaseUrl = loginResult.fallbackBaseUrl,
                            sessionToken = loginResult.sessionToken,
                            syncErrorMessage = null
                        )
                    }

                    val auditResult = MobileAuditClient.fetchDailyAudit(
                        baseUrl = loginResult.activeBaseUrl,
                        fallbackUrl = loginResult.fallbackBaseUrl,
                        sessionToken = loginResult.sessionToken
                    )

                    auditResult.fold(
                        onSuccess = { snapshot ->
                            currentBaseUrl = snapshot.activeBaseUrl.ifBlank { loginResult.activeBaseUrl }
                            applyDailyAuditSnapshot(snapshot)
                        },
                        onFailure = { error ->
                            _uiState.update { state ->
                                state.copy(
                                    isLoadingAudit = false,
                                    syncErrorMessage = error.localizedMessage
                                        ?: "Sesión iniciada, pero no se pudo descargar la auditoría del día."
                                )
                            }
                        }
                    )
                }

                is MobileLoginResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            isLoadingAudit = false,
                            syncErrorMessage = loginResult.errorMessage
                        )
                    }
                }
            }

            if (onResult != null) {
                withContext(Dispatchers.Main) {
                    onResult(loginResult)
                }
            }
        }
    }

    /**
     * Descarga y actualiza en tiempo real el snapshot `GET /api/movil/auditoria-dia` desde `BillZen.exe`
     * ejecutándose en `Dispatchers.IO` y formateando todos los KPIs a pesos colombianos (`$ X.XXX.XXX`).
     */
    fun refreshDailyAudit(
        baseUrl: String? = null,
        fallbackUrl: String? = null,
        sessionToken: String? = null,
        onUnauthorized: (() -> Unit)? = null,
        onComplete: ((Result<DailyAuditSnapshot>) -> Unit)? = null
    ) {
        val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() }
            ?: currentBaseUrl.ifBlank { _uiState.value.activeBaseUrl }
        val resolvedFallbackUrl = fallbackUrl?.takeIf { it.isNotBlank() }
            ?: currentFallbackUrl ?: _uiState.value.fallbackBaseUrl
        val resolvedToken = sessionToken?.takeIf { it.isNotBlank() }
            ?: currentSessionToken ?: _uiState.value.sessionToken.orEmpty()

        if (resolvedBaseUrl.isBlank() || resolvedToken.isBlank()) {
            val missingErr = IllegalStateException("No hay una sesión remota activa para sincronizar con BillZen ERP.")
            _uiState.update {
                it.copy(
                    isLoadingAudit = false,
                    syncErrorMessage = missingErr.message
                )
            }
            onComplete?.invoke(Result.failure(missingErr))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isLoadingAudit = true,
                    syncErrorMessage = null
                )
            }

            val result = MobileAuditClient.fetchDailyAudit(
                baseUrl = resolvedBaseUrl,
                fallbackUrl = resolvedFallbackUrl,
                sessionToken = resolvedToken
            )

            result.fold(
                onSuccess = { snapshot ->
                    currentBaseUrl = snapshot.activeBaseUrl.ifBlank { resolvedBaseUrl }
                    currentFallbackUrl = resolvedFallbackUrl
                    currentSessionToken = resolvedToken
                    applyDailyAuditSnapshot(snapshot)
                },
                onFailure = { error ->
                    val errText = error.message.orEmpty().lowercase()
                    val isRevokedOrUnauthorized = error is SecurityException ||
                        errText.contains("401") ||
                        errText.contains("403") ||
                        errText.contains("expir") ||
                        errText.contains("revocad") ||
                        errText.contains("inválid") ||
                        errText.contains("invalida") ||
                        errText.contains("no autorizad")

                    if (isRevokedOrUnauthorized) {
                        currentSessionToken = null
                        _uiState.update { state ->
                            state.copy(
                                isLoadingAudit = false,
                                isLiveConnected = false,
                                sessionToken = null,
                                connectionModeLabel = "SESIÓN REVOCADA O EXPIRADA",
                                syncErrorMessage = error.localizedMessage
                                    ?: "La sesión móvil fue cerrada o expiró en BillZen ERP (HTTP 401)."
                            )
                        }
                        val revokeAction = onUnauthorized ?: onSessionRevokedCallback
                        revokeAction?.invoke()
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                isLoadingAudit = false,
                                syncErrorMessage = error.localizedMessage
                                    ?: "Error de red al sincronizar la auditoría del día."
                            )
                        }
                    }
                }
            )

            if (onComplete != null) {
                withContext(Dispatchers.Main) {
                    onComplete(result)
                }
            }
        }
    }

    /**
     * Aplica un `DailyAuditSnapshot` al `ErpUiState`, convirtiendo automáticamente `kpis_dia`
     * al formato de pesos colombianos (`$ X.XXX.XXX`) para alimentar `DashboardScreen` y `AccountingScreen`.
     */
    fun applyDailyAuditSnapshot(snapshot: DailyAuditSnapshot) {
        val kpis = snapshot.kpisDia
        val formattedTotalVendido = MobileAuditClient.formatCopCurrency(kpis.totalVendidoHoy)
        val formattedVentasNetas = MobileAuditClient.formatCopCurrency(kpis.ventasNetasHoy)
        val formattedIva = MobileAuditClient.formatCopCurrency(kpis.ivaHoy)
        val formattedUtilidadNeta = MobileAuditClient.formatCopCurrency(kpis.utilidadNetaHoy)
        val formattedGastos = MobileAuditClient.formatCopCurrency(kpis.gastosHoy)
        val formattedCostoVentas = MobileAuditClient.formatCopCurrency(kpis.costoVentasHoy)
        val formattedMontoAnulado = MobileAuditClient.formatCopCurrency(kpis.montoAnuladoHoy)

        val liveKpiMetrics = listOf(
            KpiMetric(
                title = "Ventas de hoy",
                formattedValue = formattedTotalVendido,
                deltaPercentage = "${kpis.facturasEmitidasHoy} facturas emitidas",
                isPositiveTrend = kpis.totalVendidoHoy >= 0.0
            ),
            KpiMetric(
                title = "Ventas netas hoy",
                formattedValue = formattedVentasNetas,
                deltaPercentage = "IVA: $formattedIva",
                isPositiveTrend = kpis.ventasNetasHoy >= 0.0
            ),
            KpiMetric(
                title = "Utilidad neta hoy",
                formattedValue = formattedUtilidadNeta,
                deltaPercentage = "Gastos: $formattedGastos",
                isPositiveTrend = kpis.utilidadNetaHoy >= 0.0
            ),
            KpiMetric(
                title = "Anulaciones hoy",
                formattedValue = formattedMontoAnulado,
                deltaPercentage = "${kpis.facturasAnuladasHoy} facturas anuladas",
                isPositiveTrend = kpis.facturasAnuladasHoy == 0
            )
        )

        val liveAccountingSummary = AccountingSummary(
            netSales = formattedVentasNetas,
            grossProfit = formattedUtilidadNeta,
            operatingExpenses = formattedGastos,
            accountsPayable = formattedCostoVentas
        )

        val sliceColors = listOf(BillZenEmerald, BillZenMint, BillZenGold, BillZenAmber)
        val totalCajerosAmount = snapshot.ventasPorCajero.sumOf { it.montoTotal }.coerceAtLeast(1.0)
        val mappedCategorySlices = if (snapshot.ventasPorCajero.isNotEmpty()) {
            snapshot.ventasPorCajero.take(4).mapIndexed { index, cajero ->
                val pct = (cajero.montoTotal / totalCajerosAmount).toFloat().coerceIn(0.05f, 1.0f)
                CategorySaleSlice(
                    categoryName = cajero.displayName,
                    percentage = pct,
                    formattedAmount = cajero.formattedAmount,
                    color = sliceColors[index % sliceColors.size]
                )
            }
        } else {
            null
        }

        val saleMovements = snapshot.ultimosMovimientosStock.filter {
            it.tipo.equals("VENTA", ignoreCase = true) || it.cantidad != 0.0
        }
        val maxMovedQty = saleMovements.maxOfOrNull { kotlin.math.abs(it.cantidad) }?.coerceAtLeast(1.0) ?: 1.0
        val mappedTopProducts = if (saleMovements.isNotEmpty()) {
            saleMovements.take(4).map { mov ->
                val absQty = kotlin.math.abs(mov.cantidad)
                TopProductRank(
                    productName = mov.producto.ifBlank { mov.codigo },
                    unitsSold = absQty.roundToInt(),
                    relativeProgress = (absQty / maxMovedQty).toFloat().coerceIn(0.08f, 1.0f),
                    formattedRevenue = "Stock: ${mov.stockFinal.roundToInt()}"
                )
            }
        } else {
            null
        }

        val modeLabel = buildString {
            append(snapshot.modoConexion.ifBlank { "READ_ONLY" })
            append(" • ")
            append(if (snapshot.isRemoteTunnel) "Túnel HTTPS Cloudflare" else "Red Local LAN")
        }

        _uiState.update { state ->
            state.copy(
                companyName = snapshot.razonSocial.ifBlank { state.companyName },
                companyNit = snapshot.nit.ifBlank { state.companyNit },
                kpiMetrics = liveKpiMetrics,
                categorySales = mappedCategorySlices ?: state.categorySales,
                topProducts = mappedTopProducts ?: state.topProducts,
                accountingSummary = liveAccountingSummary,
                isLiveConnected = true,
                connectionModeLabel = modeLabel,
                lastUpdatedAt = snapshot.generadoEn.ifBlank { kpis.fecha },
                cashierSales = snapshot.ventasPorCajero,
                stockMovements = snapshot.ultimosMovimientosStock,
                lowStockAlerts = snapshot.alertasStockBajo,
                securityLogs = snapshot.bitacoraSeguridad,
                isLoadingAudit = false,
                syncErrorMessage = null,
                activeBaseUrl = snapshot.activeBaseUrl.ifBlank { state.activeBaseUrl },
                dailyAuditSnapshot = snapshot
            )
        }
    }

    private fun createInitialMockState(): ErpUiState {
        return ErpUiState(
            adminName = "Camilo",
            companyName = "ZenTech Solutions S.A.S.",
            companyNit = "900.123.456-7",
            kpiMetrics = listOf(
                KpiMetric(
                    title = "Ventas de hoy",
                    formattedValue = "$ 2.450.000",
                    deltaPercentage = "+14.2% vs ayer",
                    isPositiveTrend = true
                ),
                KpiMetric(
                    title = "Ventas del mes",
                    formattedValue = "$ 48.920.000",
                    deltaPercentage = "+8.7% vs mes ant.",
                    isPositiveTrend = true
                ),
                KpiMetric(
                    title = "Utilidad del mes",
                    formattedValue = "$ 16.340.000",
                    deltaPercentage = "+5.4% margen neto",
                    isPositiveTrend = true
                ),
                KpiMetric(
                    title = "Ticket promedio",
                    formattedValue = "$ 185.000",
                    deltaPercentage = "-2.1% esta semana",
                    isPositiveTrend = false
                )
            ),
            categorySales = listOf(
                CategorySaleSlice("Licencias y SaaS", 0.42f, "$ 20.5M", BillZenEmerald),
                CategorySaleSlice("Hardware POS", 0.28f, "$ 13.7M", BillZenMint),
                CategorySaleSlice("Consultoría TI", 0.18f, "$ 8.8M", BillZenGold),
                CategorySaleSlice("Soporte y Otros", 0.12f, "$ 5.9M", BillZenAmber)
            ),
            topProducts = listOf(
                TopProductRank("Plan BillZen Plus Anual", 142, 1.0f, "$ 17.040.000"),
                TopProductRank("Terminal Lector Código QR", 98, 0.69f, "$ 11.760.000"),
                TopProductRank("Impresora Térmica 80mm", 76, 0.53f, "$ 9.120.000"),
                TopProductRank("Paquete 1.000 Facturas DIAN", 64, 0.45f, "$ 6.400.000")
            ),
            accountingSummary = AccountingSummary(
                netSales = "$ 48.920.000",
                grossProfit = "$ 31.180.000",
                operatingExpenses = "$ 14.840.000",
                accountsPayable = "$ 6.290.000"
            ),
            expenses = listOf(
                ExpenseRecord(
                    id = "EGR-1041",
                    date = "16 Nov 2026",
                    concept = "Infraestructura Cloud y Base de Datos",
                    supplier = "Neon Tech / Vercel Inc.",
                    paymentMethod = "Tarjeta Corporativa",
                    formattedAmount = "$ 1.250.000",
                    status = ExpensePaymentStatus.PAID
                ),
                ExpenseRecord(
                    id = "EGR-1042",
                    date = "15 Nov 2026",
                    concept = "Renovación Certificado Firma Digital DIAN",
                    supplier = "Andes SCD S.A.",
                    paymentMethod = "Transferencia PSE",
                    formattedAmount = "$ 890.000",
                    status = ExpensePaymentStatus.PAID
                ),
                ExpenseRecord(
                    id = "EGR-1043",
                    date = "14 Nov 2026",
                    concept = "Importación Lote Terminales POS",
                    supplier = "Distribuidora Mayorista S.A.S.",
                    paymentMethod = "Crédito 30 días",
                    formattedAmount = "$ 4.450.000",
                    status = ExpensePaymentStatus.PENDING
                ),
                ExpenseRecord(
                    id = "EGR-1044",
                    date = "12 Nov 2026",
                    concept = "Honorarios Revisión Fiscal Mensual",
                    supplier = "Consultores Contables Asociados",
                    paymentMethod = "Transferencia Bancaria",
                    formattedAmount = "$ 1.840.000",
                    status = ExpensePaymentStatus.PENDING
                )
            )
        )
    }
}
