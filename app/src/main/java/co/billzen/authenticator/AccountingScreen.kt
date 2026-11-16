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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.billzen.authenticator.ui.theme.BillZenAmber
import co.billzen.authenticator.ui.theme.BillZenDark
import co.billzen.authenticator.ui.theme.BillZenEmerald
import co.billzen.authenticator.ui.theme.BillZenError
import co.billzen.authenticator.ui.theme.BillZenGold
import co.billzen.authenticator.ui.theme.BillZenLightBorder
import co.billzen.authenticator.ui.theme.BillZenTextSecondary
import co.billzen.authenticator.ui.theme.BillZenWhiteCard

@Composable
fun AccountingScreen(
    summary: AccountingSummary,
    expenses: List<ExpenseRecord>,
    modifier: Modifier = Modifier
) {
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
                text = "Balance operativo y control de obligaciones con proveedores",
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
                        label = "Utilidad Bruta",
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
                        label = "Gastos Operativos",
                        amount = summary.operatingExpenses,
                        accentColor = BillZenAmber,
                        modifier = Modifier.weight(1f)
                    )
                    AccountingSummaryCard(
                        label = "Cuentas por Pagar",
                        amount = summary.accountsPayable,
                        accentColor = BillZenError,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            Text(
                text = "Egresos y Cuentas por Pagar",
                color = BillZenDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(expenses, key = { it.id }) { record ->
            ExpenseRowCard(record = record)
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
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = amount,
                color = accentColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
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

                // Etiqueta de Estado (PAGADO en verde / PENDIENTE en rojo)
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