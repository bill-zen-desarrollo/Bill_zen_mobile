# Bill_zen_mobile
# BillZen Authenticator (`Bill_zen_mobile`)

![Platform](https://img.shields.io/badge/Plataforma-Android%208.0%2B%20(API%2026)-0A6F52?style=flat-square&logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Lenguaje-Kotlin%202.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Standard](https://img.shields.io/badge/Est%C3%A1ndar-RFC%206238%20(TOTP)-D4A94C?style=flat-square)
![Organization](https://img.shields.io/badge/Organizaci%C3%B3n-ZenTech%20Solutions-171B24?style=flat-square)

Aplicación móvil nativa para Android que actúa como autenticador de doble factor (**2FA / TOTP**) para el sistema contable y de facturación electrónica **BillZen ERP**.

Diseñada bajo los estándares criptográficos **RFC 4648 (Base32)** y **RFC 6238 (TOTP — HMAC-SHA1)**, permite vincular empresas por su **NIT** mediante el escaneo de un código QR y generar códigos temporales de 6 dígitos cada 30 segundos **100 % sin conexión a internet (offline)**.

---

## 1. Arquitectura del Ecosistema BillZen

El sistema **BillZen** de **ZenTech Solutions** está distribuido en tres repositorios dentro de la organización `bill-zen-desarrollo`:

| Repositorio | Componente | Tecnologías Principales | Rol en la Autenticación 2FA |
| :--- | :--- | :--- | :--- |
| **`bill_zen_users`** | Portal Web y API Cloud | HTML/CSS/JS, FastAPI, SQLAlchemy, PostgreSQL (Neon.tech), Vercel | Genera la semilla Base32 por empresa (`POST /api/2fa/setup`) y valida los códigos TOTP (`POST /api/2fa/verify`). |
| **`bill_zen_ERP`** | Software Contable de Escritorio | Python | Muestra el código QR de vinculación (`otpauth://`) y solicita el código de 6 dígitos al iniciar sesión. |
| **`Bill_zen_mobile`** | **App Móvil (este repositorio)** | **Kotlin, Jetpack Compose, CameraX, ML Kit, Android Keystore** | **Escanea el QR, almacena la semilla cifrada en hardware y calcula el código de 6 dígitos cada 30 s offline.** |

### Flujo de Vinculación y Uso

1. **Solicitud de vinculación:** El usuario inicia el proceso en **BillZen ERP** (escritorio), el cual solicita al backend la URI estándar:
   ```text
   otpauth://totp/BillZen:{NIT}?secret={BASE32_SECRET}&issuer=BillZen&algorithm=SHA1&digits=6&period=30
   ```
2. **Escaneo QR:** Con **BillZen Authenticator**, el usuario escanea el código QR mostrado en pantalla.
3. **Almacenamiento seguro:** La app valida la URI, decodifica la semilla Base32 y la guarda cifrada con `AES-256-GCM` respaldada por el **Android Keystore**.
4. **Generación offline:** Cada 30 segundos (sincronizados con el tiempo Unix UTC), el motor criptográfico local calcula el código de 6 dígitos mediante `HMAC-SHA1` y truncamiento dinámico.

---

## 2. Especificaciones Técnicas

| Parámetro | Valor / Detalle |
| :--- | :--- |
| **Nombre de la aplicación** | BillZen Authenticator (`billzenERP.apk`) |
| **Application ID / Package** | `co.billzen.authenticator` |
| **SDK Mínimo (`minSdk`)** | API 26 (Android 8.0 Oreo — >97 % de cobertura de dispositivos) |
| **SDK Objetivo (`targetSdk`)** | API 35 (Android 15) |
| **Interfaz de Usuario** | Jetpack Compose + Material Design 3 |
| **Algoritmo TOTP** | RFC 6238 (`HMAC-SHA1`, ventana de 30 s, 6 dígitos) |
| **Codificación de Semilla** | RFC 4648 (`Base32` sin dependencia de librerías externas) |
| **Almacenamiento Local** | `EncryptedSharedPreferences` (`androidx.security:security-crypto`) |
| **Escáner QR** | CameraX + Google ML Kit Barcode Scanning (procesamiento 100 % en el dispositivo) |

---

## 3. Estructura del Proyecto

```text
Bill_zen_mobile/
├── app/
│   ├── build.gradle.kts                     # Configuración del módulo, SDKs y dependencias
│   └── src/main/
│       ├── AndroidManifest.xml              # Configuración de la app, permisos y allowBackup=false
│       ├── java/co/billzen/authenticator/
│       │   ├── MainActivity.kt              # Actividad principal, FLAG_SECURE y UI inicial
│       │   ├── core/                        # Motor criptográfico, parsers y almacenamiento seguro
│       │   │   ├── Base32.kt                # Decodificador y validador RFC 4648
│       │   │   ├── TotpGenerator.kt         # (Próximo) Generador RFC 6238 HMAC-SHA1
│       │   │   ├── OtpAuthParser.kt         # (Próximo) Validador de URIs otpauth://totp/...
│       │   │   └── SecureAccountStore.kt    # (Próximo) Persistencia cifrada multi-empresa (NIT)
│       │   └── ui/
│       │       └── theme/
│       │           ├── Color.kt             # Paleta corporativa BillZen (Dark, Emerald, Gold, Cream)
│       │           ├── Theme.kt             # Esquema de color Material 3
│       │           └── Type.kt              # Definición tipográfica
│       └── res/                             # Recursos gráficos, iconos adaptativos y textos XML
├── build.gradle.kts                         # Configuración raíz de Gradle (Kotlin DSL)
├── gradle/libs.versions.toml                # Catálogo centralizado de versiones (Version Catalog)
└── settings.gradle.kts                      # Definición de repositorios y módulos
```

---

## 4. Medidas de Seguridad Aplicadas

- **Protección contra capturas de pantalla (`FLAG_SECURE`):** La ventana principal activa `WindowManager.LayoutParams.FLAG_SECURE`, bloqueando capturas de pantalla, grabaciones de video y previsualización del contenido en el selector de aplicaciones recientes de Android.
- **Cero tráfico de red para generación de códigos:** Los códigos temporales se calculan exclusivamente en memoria local a partir de la semilla compartida.
- **Desactivación de copias de seguridad en la nube (`allowBackup=false`):** Evita que las semillas 2FA salgan del dispositivo mediante respaldos automáticos de ADB o Google Drive.
- **Portapapeles protegido:** Al copiar un código, se marca como contenido sensible (`ClipDescription.EXTRA_IS_SENSITIVE`) para ocultarlo de la vista previa del portapapeles en Android 13+.
- **Cifrado respaldado por hardware:** Uso de claves maestras gestionadas por el chip de seguridad del dispositivo (*Android Keystore System*).

---

## 5. Estado del Desarrollo (Roadmap MVP)

- [x] Inicialización del proyecto Android (Kotlin + Jetpack Compose)
- [x] Definición de paleta de colores corporativa (`Color.kt`) y tema Material 3 (`Theme.kt`)
- [x] Pantalla de inicio (`MainActivity.kt`) con `FLAG_SECURE`, encabezado `BillZen` y estado vacío (`EmptyAccountsCard`)
- [ ] Módulo `core/Base32.kt`: decodificador y validador RFC 4648 *(en progreso)*
- [ ] Módulo `core/TotpGenerator.kt`: algoritmo RFC 6238 + pruebas unitarias con vectores oficiales
- [ ] Componente visual de tarjeta de empresa con temporizador circular de 30 s
- [ ] Módulo `core/OtpAuthParser.kt`: lectura y validación de URI `otpauth://`
- [ ] Módulo `core/SecureAccountStore.kt`: guardado cifrado con `EncryptedSharedPreferences`
- [ ] Escáner QR con CameraX + ML Kit e ingreso manual de clave secreta
- [ ] Icono adaptativo oficial de BillZen y generación del artefacto `billzenERP.apk`

---

## 6. Requisitos para Compilar en Entorno Local

1. **Android Studio** (versión Ladybug 2024.2.1 o superior recomendada).
2. **JDK 17** (incluido por defecto en Android Studio).
3. **Android SDK 35** instalado desde el *SDK Manager*.
4. Clonar el repositorio:
   ```bash
   git clone https://github.com/bill-zen-desarrollo/Bill_zen_mobile.git
   ```
5. Abrir la carpeta en Android Studio, esperar a que finalice **Gradle Sync** y ejecutar en un dispositivo físico o emulador (`Medium Phone API 35+`).

---

## 7. Autores

Desarrollado por el equipo de ingeniería de **ZenTech Solutions** (Colombia):

- **Camilo (Cerezo)** — Co-Founder & Developer
- **Camilo Alarcón** — Co-Founder & Developer

---
*© ZenTech Solutions — Todos los derechos reservados.*