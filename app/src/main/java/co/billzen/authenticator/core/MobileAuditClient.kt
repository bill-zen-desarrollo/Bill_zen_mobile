package co.billzen.authenticator.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

data class QrPairingPackage(
    val nit: String,
    val empresa: String,
    val remoteUrl: String,
    val localUrl: String,
    val pairingToken: String
) {
    val hasValidRemoteEndpoint: Boolean
        get() = MobileAuditClient.isValidApiEndpoint(remoteUrl)

    val hasValidLocalEndpoint: Boolean
        get() = MobileAuditClient.isValidApiEndpoint(localUrl)

    fun candidateBaseUrls(): List<String> {
        val candidates = mutableListOf<String>()
        if (hasValidRemoteEndpoint) {
            candidates.add(MobileAuditClient.normalizeBaseUrl(remoteUrl))
        }
        if (hasValidLocalEndpoint) {
            val normalizedLocal = MobileAuditClient.normalizeBaseUrl(localUrl)
            if (normalizedLocal !in candidates) {
                candidates.add(normalizedLocal)
            }
        }
        return candidates
    }
}

data class AuthenticatedAdminUser(
    val idUsuario: Int,
    val username: String,
    val nombreCompleto: String,
    val idRol: Int
)

sealed class MobileLoginResult(
    open val ok: Boolean,
    open val sessionToken: String?,
    open val tokenType: String,
    open val expiresInSeconds: Long,
    open val activeBaseUrl: String?,
    open val fallbackBaseUrl: String?,
    open val isRemoteTunnel: Boolean,
    open val usuario: AuthenticatedAdminUser?,
    open val httpStatusCode: Int,
    open val errorMessage: String?
) {
    data class Success(
        override val sessionToken: String,
        override val tokenType: String = "Bearer",
        override val expiresInSeconds: Long = 43200L,
        override val activeBaseUrl: String,
        override val fallbackBaseUrl: String?,
        override val isRemoteTunnel: Boolean,
        override val usuario: AuthenticatedAdminUser,
        override val httpStatusCode: Int = HttpURLConnection.HTTP_OK
    ) : MobileLoginResult(
        ok = true,
        sessionToken = sessionToken,
        tokenType = tokenType,
        expiresInSeconds = expiresInSeconds,
        activeBaseUrl = activeBaseUrl,
        fallbackBaseUrl = fallbackBaseUrl,
        isRemoteTunnel = isRemoteTunnel,
        usuario = usuario,
        httpStatusCode = httpStatusCode,
        errorMessage = null
    )

    data class Failure(
        override val errorMessage: String,
        override val httpStatusCode: Int = 0
    ) : MobileLoginResult(
        ok = false,
        sessionToken = null,
        tokenType = "Bearer",
        expiresInSeconds = 0L,
        activeBaseUrl = null,
        fallbackBaseUrl = null,
        isRemoteTunnel = false,
        usuario = null,
        httpStatusCode = httpStatusCode,
        errorMessage = errorMessage
    )
}

data class DailyKpisSnapshot(
    val fecha: String,
    val totalVendidoHoy: Double,
    val ventasNetasHoy: Double,
    val ivaHoy: Double,
    val facturasEmitidasHoy: Int,
    val facturasAnuladasHoy: Int,
    val montoAnuladoHoy: Double,
    val costoVentasHoy: Double,
    val gastosHoy: Double,
    val utilidadNetaHoy: Double
)

data class CashierSaleSummary(
    val username: String,
    val usuario: String,
    val facturasEmitidas: Int,
    val montoTotal: Double
) {
    val displayName: String
        get() = usuario.ifBlank { username }

    val formattedAmount: String
        get() = MobileAuditClient.formatCopCurrency(montoTotal)
}

data class StockMovementItem(
    val idMovimiento: Int,
    val fecha: String,
    val tipo: String,
    val codigo: String,
    val producto: String,
    val cantidad: Double,
    val stockFinal: Double,
    val referencia: String,
    val usuario: String
)

data class LowStockAlertItem(
    val idProducto: Int,
    val codigo: String,
    val producto: String,
    val stock: Double,
    val stockMinimo: Double
)

data class SecurityAuditLogItem(
    val idAuditoria: Int,
    val fecha: String,
    val usuario: String,
    val accion: String,
    val detalle: String
)

data class DailyAuditSnapshot(
    val ok: Boolean,
    val modoConexion: String,
    val nit: String,
    val razonSocial: String,
    val generadoEn: String,
    val kpisDia: DailyKpisSnapshot,
    val ventasPorCajero: List<CashierSaleSummary>,
    val ultimosMovimientosStock: List<StockMovementItem>,
    val alertasStockBajo: List<LowStockAlertItem>,
    val bitacoraSeguridad: List<SecurityAuditLogItem>,
    val activeBaseUrl: String = "",
    val isRemoteTunnel: Boolean = false
) {
    val fecha: String get() = kpisDia.fecha
    val totalVendidoHoy: Double get() = kpisDia.totalVendidoHoy
    val ventasNetasHoy: Double get() = kpisDia.ventasNetasHoy
    val ivaHoy: Double get() = kpisDia.ivaHoy
    val facturasEmitidasHoy: Int get() = kpisDia.facturasEmitidasHoy
    val facturasAnuladasHoy: Int get() = kpisDia.facturasAnuladasHoy
    val montoAnuladoHoy: Double get() = kpisDia.montoAnuladoHoy
    val costoVentasHoy: Double get() = kpisDia.costoVentasHoy
    val gastosHoy: Double get() = kpisDia.gastosHoy
    val utilidadNetaHoy: Double get() = kpisDia.utilidadNetaHoy
}

object MobileAuditClient {

    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val DOWNLOAD_PLACEHOLDER_FRAGMENT = "vercel.app/#descargar"

    fun parseQrPayload(rawQr: String): QrPairingPackage {
        val trimmed = rawQr.trim()
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) {
            "El código QR no contiene un paquete JSON de vinculación BillZen."
        }

        val json = try {
            JSONObject(trimmed)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Formato JSON inválido en el código QR de vinculación.", e)
        }

        val nit = json.optString("nit", "").trim()
        val empresa = json.optString("empresa", "").trim().ifBlank { "Empresa BillZen" }
        val remoteUrl = json.optString("remote_url", "").trim()
        val localUrl = json.optString("local_url", "").trim()
        val pairingToken = json.optString("pairing_token", "").trim()

        require(pairingToken.isNotBlank() && pairingToken.contains(".")) {
            "El código QR no incluye un pairing_token firmado válido de BillZen ERP."
        }

        val hasRemote = isValidApiEndpoint(remoteUrl)
        val hasLocal = isValidApiEndpoint(localUrl)
        require(hasRemote || hasLocal) {
            "El código QR no contiene ninguna dirección de conexión válida (remote_url / local_url)."
        }

        return QrPairingPackage(
            nit = nit.ifBlank { "SIN-NIT" },
            empresa = empresa,
            remoteUrl = remoteUrl,
            localUrl = localUrl,
            pairingToken = pairingToken
        )
    }

    fun loginAdmin(
        pkg: QrPairingPackage,
        username: String,
        password: String
    ): MobileLoginResult {
        val cleanUser = username.trim()
        if (cleanUser.isBlank() || password.isBlank()) {
            return MobileLoginResult.Failure(
                errorMessage = "Debes ingresar el usuario y la contraseña de Administrador.",
                httpStatusCode = 400
            )
        }

        val candidates = pkg.candidateBaseUrls()
        if (candidates.isEmpty()) {
            return MobileLoginResult.Failure(
                errorMessage = "No hay URL de conexión disponible en el código QR escaneado.",
                httpStatusCode = 0
            )
        }

        val requestPayload = JSONObject().apply {
            put("pairing_token", pkg.pairingToken)
            put("username", cleanUser)
            put("password", password)
        }.toString()

        var lastNetworkError = "No fue posible conectar con BillZen ERP."

        for ((index, candidateBaseUrl) in candidates.withIndex()) {
            val endpoint = "$candidateBaseUrl/api/movil/login"
            try {
                val response = executeJsonRequest(
                    url = endpoint,
                    method = "POST",
                    requestBody = requestPayload,
                    bearerToken = null
                )

                val bodyJson = parseJsonSafely(response.body)

                if (response.statusCode == HttpURLConnection.HTTP_OK && bodyJson?.optBoolean("ok", false) == true) {
                    val sessionToken = bodyJson.optString("session_token", "").trim()
                    if (sessionToken.isBlank()) {
                        return MobileLoginResult.Failure(
                            errorMessage = "El servidor no devolvió un session_token válido.",
                            httpStatusCode = response.statusCode
                        )
                    }
                    val userObj = bodyJson.optJSONObject("usuario")
                    val adminUser = AuthenticatedAdminUser(
                        idUsuario = userObj?.optInt("id_usuario", 1) ?: 1,
                        username = userObj?.optString("username", cleanUser)?.ifBlank { cleanUser } ?: cleanUser,
                        nombreCompleto = userObj?.optString("nombre_completo", cleanUser)?.ifBlank { cleanUser } ?: cleanUser,
                        idRol = userObj?.optInt("id_rol", 1) ?: 1
                    )
                    val fallbackUrl = candidates.firstOrNull { it != candidateBaseUrl }
                    return MobileLoginResult.Success(
                        sessionToken = sessionToken,
                        tokenType = bodyJson.optString("token_type", "Bearer"),
                        expiresInSeconds = bodyJson.optLong("expires_in", 43200L),
                        activeBaseUrl = candidateBaseUrl,
                        fallbackBaseUrl = fallbackUrl,
                        isRemoteTunnel = isCloudflareOrRemoteHttps(candidateBaseUrl),
                        usuario = adminUser,
                        httpStatusCode = response.statusCode
                    )
                }

                if (response.statusCode in listOf(400, 401, 403)) {
                    val serverError = bodyJson?.optString("error", "")?.trim().orEmpty()
                    val detailMessage = serverError.ifBlank {
                        when (response.statusCode) {
                            401 -> "Credenciales de Administrador inválidas (HTTP 401)."
                            403 -> "Acceso denegado o token de emparejamiento inválido (HTTP 403)."
                            else -> "Solicitud rechazada por BillZen ERP (HTTP ${response.statusCode})."
                        }
                    }
                    return MobileLoginResult.Failure(
                        errorMessage = detailMessage,
                        httpStatusCode = response.statusCode
                    )
                }

                val serverMsg = bodyJson?.optString("error", "")?.trim().orEmpty()
                lastNetworkError = if (serverMsg.isNotBlank()) {
                    serverMsg
                } else {
                    "Respuesta HTTP ${response.statusCode} desde $candidateBaseUrl"
                }
                if (index < candidates.lastIndex) {
                    continue
                }
            } catch (e: Exception) {
                lastNetworkError = "Sin respuesta en $candidateBaseUrl (${e.localizedMessage ?: "error de red"})."
                if (index < candidates.lastIndex) {
                    continue
                }
            }
        }

        return MobileLoginResult.Failure(
            errorMessage = "No se pudo establecer conexión con BillZen ERP (Remoto ni LAN): $lastNetworkError",
            httpStatusCode = 0
        )
    }

    fun fetchDailyAudit(
        baseUrl: String,
        fallbackUrl: String? = null,
        sessionToken: String
    ): Result<DailyAuditSnapshot> {
        val cleanToken = sessionToken.trim()
        if (cleanToken.isBlank()) {
            return Result.failure(IllegalArgumentException("No hay un session_token activo para consultar la auditoría."))
        }

        val candidateUrls = buildList {
            if (isValidApiEndpoint(baseUrl)) add(normalizeBaseUrl(baseUrl))
            if (!fallbackUrl.isNullOrBlank() && isValidApiEndpoint(fallbackUrl)) {
                val normalizedFallback = normalizeBaseUrl(fallbackUrl)
                if (normalizedFallback !in this) add(normalizedFallback)
            }
        }

        if (candidateUrls.isEmpty()) {
            return Result.failure(IllegalArgumentException("No se proporcionó una URL base válida para la auditoría."))
        }

        var lastException: Exception = IllegalStateException("No fue posible consultar /api/movil/auditoria-dia.")

        for ((index, candidateBase) in candidateUrls.withIndex()) {
            val endpoint = "$candidateBase/api/movil/auditoria-dia"
            try {
                val response = executeJsonRequest(
                    url = endpoint,
                    method = "GET",
                    requestBody = null,
                    bearerToken = cleanToken
                )

                val bodyJson = parseJsonSafely(response.body)

                if (response.statusCode == HttpURLConnection.HTTP_OK && bodyJson != null && bodyJson.optBoolean("ok", true)) {
                    val snapshot = parseDailyAuditSnapshot(
                        json = bodyJson,
                        activeBaseUrl = candidateBase
                    )
                    return Result.success(snapshot)
                }

                if (response.statusCode in listOf(401, 403)) {
                    val errText = bodyJson?.optString("error", "")?.trim().orEmpty().ifBlank {
                        "La sesión móvil expiró o fue revocada en BillZen ERP (HTTP ${response.statusCode})."
                    }
                    return Result.failure(SecurityException(errText))
                }

                val errText = bodyJson?.optString("error", "")?.trim().orEmpty().ifBlank {
                    "Error HTTP ${response.statusCode} al consultar la auditoría del día."
                }
                lastException = IllegalStateException(errText)
                if (index < candidateUrls.lastIndex) {
                    continue
                }
            } catch (e: Exception) {
                lastException = e
                if (index < candidateUrls.lastIndex) {
                    continue
                }
            }
        }

        return Result.failure(lastException)
    }

    internal fun parseDailyAuditSnapshot(
        json: JSONObject,
        activeBaseUrl: String
    ): DailyAuditSnapshot {
        val empresaObj = json.optJSONObject("empresa")
        val kpisObj = json.optJSONObject("kpis_dia")
        val cajerosArr = json.optJSONArray("ventas_por_cajero") ?: JSONArray()
        val stockObj = json.optJSONObject("movimientos_stock_hoy")
        val movimientosArr = stockObj?.optJSONArray("ultimos_movimientos") ?: JSONArray()
        val alertasArr = stockObj?.optJSONArray("alertas_stock_bajo") ?: JSONArray()
        val bitacoraArr = json.optJSONArray("bitacora_seguridad") ?: JSONArray()

        val kpis = DailyKpisSnapshot(
            fecha = kpisObj?.optString("fecha", "").orEmpty(),
            totalVendidoHoy = kpisObj?.optDouble("total_vendido_hoy", 0.0) ?: 0.0,
            ventasNetasHoy = kpisObj?.optDouble("ventas_netas_hoy", 0.0) ?: 0.0,
            ivaHoy = kpisObj?.optDouble("iva_hoy", 0.0) ?: 0.0,
            facturasEmitidasHoy = kpisObj?.optInt("facturas_emitidas_hoy", 0) ?: 0,
            facturasAnuladasHoy = kpisObj?.optInt("facturas_anuladas_hoy", 0) ?: 0,
            montoAnuladoHoy = kpisObj?.optDouble("monto_anulado_hoy", 0.0) ?: 0.0,
            costoVentasHoy = kpisObj?.optDouble("costo_ventas_hoy", 0.0) ?: 0.0,
            gastosHoy = kpisObj?.optDouble("gastos_hoy", 0.0) ?: 0.0,
            utilidadNetaHoy = kpisObj?.optDouble("utilidad_neta_hoy", 0.0) ?: 0.0
        )

        val ventasPorCajero = buildList {
            for (i in 0 until cajerosArr.length()) {
                val item = cajerosArr.optJSONObject(i) ?: continue
                add(
                    CashierSaleSummary(
                        username = item.optString("username", ""),
                        usuario = item.optString("usuario", ""),
                        facturasEmitidas = item.optInt("facturas_emitidas", 0),
                        montoTotal = item.optDouble("monto_total", 0.0)
                    )
                )
            }
        }

        val ultimosMovimientos = buildList {
            for (i in 0 until movimientosArr.length()) {
                val item = movimientosArr.optJSONObject(i) ?: continue
                add(
                    StockMovementItem(
                        idMovimiento = item.optInt("id_movimiento", i + 1),
                        fecha = item.optString("fecha", ""),
                        tipo = item.optString("tipo", "MOVIMIENTO"),
                        codigo = item.optString("codigo", ""),
                        producto = item.optString("producto", ""),
                        cantidad = item.optDouble("cantidad", 0.0),
                        stockFinal = item.optDouble("stock_final", 0.0),
                        referencia = item.optString("referencia", ""),
                        usuario = item.optString("usuario", "")
                    )
                )
            }
        }

        val alertasStockBajo = buildList {
            for (i in 0 until alertasArr.length()) {
                val item = alertasArr.optJSONObject(i) ?: continue
                add(
                    LowStockAlertItem(
                        idProducto = item.optInt("id_producto", i + 1),
                        codigo = item.optString("codigo", ""),
                        producto = item.optString("producto", ""),
                        stock = item.optDouble("stock", 0.0),
                        stockMinimo = item.optDouble("stock_minimo", 0.0)
                    )
                )
            }
        }

        val bitacoraSeguridad = buildList {
            for (i in 0 until bitacoraArr.length()) {
                val item = bitacoraArr.optJSONObject(i) ?: continue
                add(
                    SecurityAuditLogItem(
                        idAuditoria = item.optInt("id_auditoria", i + 1),
                        fecha = item.optString("fecha", ""),
                        usuario = item.optString("usuario", ""),
                        accion = item.optString("accion", ""),
                        detalle = item.optString("detalle", "")
                    )
                )
            }
        }

        return DailyAuditSnapshot(
            ok = json.optBoolean("ok", true),
            modoConexion = json.optString("modo_conexion", "READ_ONLY"),
            nit = empresaObj?.optString("nit", "").orEmpty(),
            razonSocial = empresaObj?.optString("razon_social", "").orEmpty(),
            generadoEn = json.optString("generado_en", ""),
            kpisDia = kpis,
            ventasPorCajero = ventasPorCajero,
            ultimosMovimientosStock = ultimosMovimientos,
            alertasStockBajo = alertasStockBajo,
            bitacoraSeguridad = bitacoraSeguridad,
            activeBaseUrl = activeBaseUrl,
            isRemoteTunnel = isCloudflareOrRemoteHttps(activeBaseUrl)
        )
    }

    fun formatCopCurrency(amount: Double): String {
        val rounded = if (amount.isNaN() || amount.isInfinite()) 0L else amount.roundToLong()
        val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
            isGroupingUsed = true
        }
        return if (rounded < 0L) {
            "-$ ${formatter.format(-rounded)}"
        } else {
            "$ ${formatter.format(rounded)}"
        }
    }

    internal fun isValidApiEndpoint(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val cleaned = rawUrl.trim().lowercase(Locale.ROOT)
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) return false
        if (cleaned.contains(DOWNLOAD_PLACEHOLDER_FRAGMENT) || cleaned.contains("#descargar")) return false
        return true
    }

    internal fun isCloudflareOrRemoteHttps(baseUrl: String): Boolean {
        val lower = baseUrl.trim().lowercase(Locale.ROOT)
        return lower.contains(".trycloudflare.com") || lower.startsWith("https://")
    }

    internal fun normalizeBaseUrl(rawUrl: String): String {
        return rawUrl.trim().trimEnd('/')
    }

    private data class RawHttpResponse(
        val statusCode: Int,
        val body: String
    )

    private fun executeJsonRequest(
        url: String,
        method: String,
        requestBody: String?,
        bearerToken: String?
    ): RawHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (requestBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }

        return try {
            if (requestBody != null) {
                connection.outputStream.use { out ->
                    out.write(requestBody.toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream: InputStream? = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val bodyText = stream?.use { readStreamText(it) }.orEmpty()
            RawHttpResponse(statusCode = status, body = bodyText)
        } finally {
            connection.disconnect()
        }
    }

    private fun readStreamText(stream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        return sb.toString()
    }

    private fun parseJsonSafely(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return null
        return try {
            JSONObject(trimmed)
        } catch (_: JSONException) {
            null
        }
    }
}
