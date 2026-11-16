package co.billzen.authenticator

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import co.billzen.authenticator.ui.theme.BillZenAmber
import co.billzen.authenticator.ui.theme.BillZenEmerald
import co.billzen.authenticator.ui.theme.BillZenGold
import co.billzen.authenticator.ui.theme.BillZenMint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
enum class ErpSection(val title: String, val iconSymbol: String) {
    DASHBOARD("Dashboard", "📊"),
    ACCOUNTING("Contabilidad", "📑"),
    TOKEN_2FA("Token 2FA", "🔐")
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
    val expenses: List<ExpenseRecord> = emptyList()
)
class ErpViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(createInitialMockState())
    val uiState: StateFlow<ErpUiState> = _uiState.asStateFlow()
    fun selectSection(section: ErpSection) {
        _uiState.update { it.copy(selectedSection = section) }
    }
    fun syncLinkedCompany(companyName: String, nit: String) {
        _uiState.update {
            it.copy(
                adminName = companyName,
                companyNit = nit
            )
        }
    }
    private fun createInitialMockState(): ErpUiState {
        return ErpUiState(
            adminName = "Camilo",
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