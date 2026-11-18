package co.billzen.authenticator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.billzen.authenticator.core.LowStockAlertItem
import co.billzen.authenticator.core.SecurityAuditLogItem
import co.billzen.authenticator.core.StockMovementItem
import co.billzen.authenticator.ui.theme.BillZenAmber
import co.billzen.authenticator.ui.theme.BillZenDark
import co.billzen.authenticator.ui.theme.BillZenEmerald
import co.billzen.authenticator.ui.theme.BillZenError
import co.billzen.authenticator.ui.theme.BillZenGold
import co.billzen.authenticator.ui.theme.BillZenLightBorder
import co.billzen.authenticator.ui.theme.BillZenTextSecondary
import co.billzen.authenticator.ui.theme.BillZenWhiteCard

private const val MAX_VISIBLE_SECURITY_LOGS = 10

@Composable
fun AccountingScreen(
    uiState: ErpUiState,
    modifier: Modifier = Modifier
) {
    val summary = uiState.accountingSummary
    val isLive = uiState.isLiveConnected

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        item {
            Text(
                text = "Contabilidad y Finanzas",
                color = BillZenDark,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isLive) {
                    "Resumen del día y control de inventario"
                } else {
                    "Balance operativo y control de obligaciones con proveedores"
                },
                color = BillZenTextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AccountingSummaryCard(
                        label = "Ventas Netas",
                        amount = summary.netSales,
                        accentColor = BillZenEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    AccountingSummaryCard(
                        label = if (isLive) "Utilidad Neta" else "Utilidad Bruta",
                        amount = summary.grossProfit,
                        accentColor = BillZenGold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AccountingSummaryCard(
                        label = if (isLive) "Gastos del Día" else "Gastos Operativos",
                        amount = summary.operatingExpenses,
                        accentColor = BillZenAmber,
                        modifier = Modifier.weight(1f)
                    )
                    AccountingSummaryCard(
                        label = if (isLive) "Costo de Ventas" else "Cuentas por Pagar",
                        amount = summary.accountsPayable,
                        accentColor = BillZenError,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (uiState.lowStockAlerts.isNotEmpty()) {
            item(key = "low_stock_alerts") {
                LowStockAlertsCard(alerts = uiState.lowStockAlerts)
            }
        }

        if (isLive) {
            item(key = "stock_movements_header") {
                Text(
                    text = "Movimientos de inventario hoy",
                    color = BillZenDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (uiState.stockMovements.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aún no hay movimientos de inventario registrados hoy.",
                        color = BillZenTextSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(uiState.stockMovements, key = { "mov_${it.idMovimiento}" }) { movement ->
                StockMovementRowCard(movement = movement)
            }
        } else {
            item(key = "expenses_header") {
                Text(
                    text = "Egresos y Cuentas por Pagar",
                    color = BillZenDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(uiState.expenses, key = { it.id }) { record ->
                ExpenseRowCard(record = record)
            }
        }

        if (uiState.securityLogs.isNotEmpty()) {
            item(key = "security_logs") {
                SecurityLogCard(
                    logs = uiState.securityLogs.take(MAX_VISIBLE_SECURITY_LOGS)
                )
            }
        }
    }
}

@Composable
private fun AccountingSummaryCard(
    label: String,
    amount: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BillZenWhiteCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BillZenLightBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                color = BillZenTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = amount,
                color = accentColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LowStockAlertsCard(alerts: List<LowStockAlertItem>) {
    Surface(
        color = BillZenWhiteCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, BillZenAmber),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Alertas de stock bajo",
                color = BillZenDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Productos en o por debajo del stock mínimo",
                color = BillZenTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                alerts.forEach { alert ->
                    val stockColor = if (alert.stock <= 0.0) BillZenError else BillZenAmber

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = alert.producto.ifBlank { alert.codigo },
                                color = BillZenDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Código: ${alert.codigo}",
                                color = BillZenTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = stockColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Stock ${formatQuantity(alert.stock)} / Mín. ${formatQuantity(alert.stockMinimo)}",
                                color = stockColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockMovementRowCard(movement: StockMovementItem) {
    val badgeColor = movementTypeColor(movement.tipo)

    Surface(
        color = BillZenWhiteCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BillZenLightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movement.producto.ifBlank { movement.codigo },
                        color = BillZenDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Código: ${movement.codigo}",
                        color = BillZenTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = movement.tipo.uppercase(),
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = listOf(movement.fecha, movement.usuario)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = BillZenTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${formatQuantity(movement.cantidad)} uds · Stock ${formatQuantity(movement.stockFinal)}",
                    color = BillZenDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun SecurityLogCard(logs: List<SecurityAuditLogItem>) {
    Surface(
        color = BillZenWhiteCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BillZenLightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Bitácora de seguridad",
                color = BillZenDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Eventos recientes registrados en BillZen ERP",
                color = BillZenTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                logs.forEach { log ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = log.accion,
                            color = BillZenDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (log.detalle.isNotBlank()) {
                            Text(
                                text = log.detalle,
                                color = BillZenTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = listOf(log.fecha, log.usuario)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            color = BillZenTextSecondary.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun movementTypeColor(tipo: String): Color {
    val normalized = tipo.trim().uppercase()
    return when {
        normalized.startsWith("VENTA") -> BillZenEmerald
        normalized.startsWith("ENTRADA") -> BillZenGold
        normalized.startsWith("ANUL") -> BillZenError
        else -> BillZenAmber
    }
}

private fun formatQuantity(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.forLanguageTag("es-CO"), "%.2f", value)
    }
}

@Composable
private fun ExpenseRowCard(record: ExpenseRecord) {
    val isPaid = record.status == ExpensePaymentStatus.PAID
    val badgeBgColor = if (isPaid) BillZenEmerald.copy(alpha = 0.14f) else BillZenError.copy(alpha = 0.14f)
    val badgeTextColor = if (isPaid) BillZenEmerald else BillZenError

    Surface(
        color = BillZenWhiteCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BillZenLightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.concept,
                        color = BillZenDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Proveedor: ${record.supplier}",
                        color = BillZenTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    color = badgeBgColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = record.status.label,
                        color = badgeTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${record.date} · ${record.paymentMethod}",
                    color = BillZenTextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = record.formattedAmount,
                    color = BillZenDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
