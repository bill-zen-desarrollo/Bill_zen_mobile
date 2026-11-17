package co.billzen.authenticator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.billzen.authenticator.ui.theme.BillZenDark
import co.billzen.authenticator.ui.theme.BillZenEmerald
import co.billzen.authenticator.ui.theme.BillZenError
import co.billzen.authenticator.ui.theme.BillZenGold
import co.billzen.authenticator.ui.theme.BillZenLightBorder
import co.billzen.authenticator.ui.theme.BillZenTextSecondary
import co.billzen.authenticator.ui.theme.BillZenWhiteCard

/**
 * Pantalla principal "Dashboard" del BillZen Mobile ERP.
 *
 * Incluye:
 * 1. TopBar con el logo corporativo "BillZen" y saludo personalizado "Hola, {nombre}".
 * 2. Carrusel horizontal con las 4 tarjetas de KPIs financieros e indicador de subida/bajada.
 * 3. Gráfico circular (Donut Chart) para "Ventas por categoría".
 * 4. Gráfico de barras horizontales para "Productos más vendidos".
 */
@Composable
fun DashboardScreen(
    uiState: ErpUiState,
    deviceUuidShort: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        DashboardTopBar(
            adminName = uiState.adminName,
            companyNit = uiState.companyNit,
            deviceUuidShort = deviceUuidShort
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Indicadores Financieros",
            color = BillZenDark,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Fila scrollable horizontalmente con las 4 tarjetas de KPIs
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            uiState.kpiMetrics.forEach { metric ->
                KpiMetricCard(metric = metric)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Gráfico 1: Donut Chart - Ventas por Categoría
        CategoryDonutChartCard(slices = uiState.categorySales)

        Spacer(modifier = Modifier.height(18.dp))

        // Gráfico 2: Barras Horizontales - Productos más vendidos
        TopProductsBarChartCard(products = uiState.topProducts)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DashboardTopBar(
    adminName: String,
    companyNit: String,
    deviceUuidShort: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
                        append("Zen")
                    }
                },
                fontSize = 28.sp
            )

            // Insignia ejecutiva compacta en una sola línea horizontal
            Surface(
                color = BillZenEmerald.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, BillZenEmerald.copy(alpha = 0.35f))
            ) {
                Text(
                    text = if (companyNit.isNotBlank()) "NIT: $companyNit" else "ID: $deviceUuidShort",
                    color = BillZenEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Hola, $adminName",
            color = BillZenTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun KpiMetricCard(metric: KpiMetric) {
    val trendColor = if (metric.isPositiveTrend) BillZenEmerald else BillZenError
    val trendArrow = if (metric.isPositiveTrend) "▲" else "▼"

    Surface(
        color = BillZenWhiteCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BillZenLightBorder),
        modifier = Modifier.width(210.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = metric.title,
                color = BillZenTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = metric.formattedValue,
                color = BillZenDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = trendColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "$trendArrow ${metric.deltaPercentage}",
                    color = trendColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
@Composable
private fun CategoryDonutChartCard(slices: List<CategorySaleSlice>) {
    Surface(
        color = BillZenWhiteCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BillZenLightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Ventas por categoría",
                color = BillZenDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Distribución porcentual del mes actual",
                color = BillZenTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(140.dp)
                ) {
                    Canvas(modifier = Modifier.size(120.dp)) {
                        var startAngle = -90f
                        for (slice in slices) {
                            val sweepAngle = slice.percentage * 360f
                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle - 3f, // Pequeña separación visual entre arcos
                                useCenter = false,
                                style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt)
                            )
                            startAngle += sweepAngle
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total",
                            color = BillZenTextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "$ 48.9M",
                            color = BillZenDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    slices.forEach { slice ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(slice.color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = slice.categoryName,
                                    color = BillZenDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "${(slice.percentage * 100).toInt()}%",
                                color = BillZenTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun TopProductsBarChartCard(products: List<TopProductRank>) {
    Surface(
        color = BillZenWhiteCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BillZenLightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Productos más vendidos",
                color = BillZenDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ranking por unidades y facturación acumulada",
                color = BillZenTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                products.forEach { item ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.productName,
                                color = BillZenDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${item.unitsSold} uds · ${item.formattedRevenue}",
                                color = BillZenTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Barra horizontal proporcional
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(BillZenLightBorder)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = item.relativeProgress.coerceIn(0.05f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BillZenEmerald)
                            )
                        }
                    }
                }
            }
        }
    }
}
