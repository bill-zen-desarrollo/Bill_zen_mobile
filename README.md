# BillZen Authenticator (`Bill_zen_mobile`)

![Platform](https://img.shields.io/badge/Plataforma-Android%208.0%2B%20(API%2026)-0A6F52?style=flat-square&logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Lenguaje-Kotlin%202.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Standard](https://img.shields.io/badge/Est%C3%A1ndar-RFC%206238%20(TOTP)-D4A94C?style=flat-square)
![Organization](https://img.shields.io/badge/Organizaci%C3%B3n-ZenTech%20Solutions-171B24?style=flat-square)

Aplicación móvil nativa para Android que actúa como autenticador de doble factor (**2FA / TOTP**) para el sistema contable y de facturación electrónica **BillZen ERP**.

Diseñada bajo los estándares criptográficos **RFC 4648 (Base32)** y **RFC 6238 (TOTP — HMAC-SHA1)**, permite vincular múltiples empresas por su **NIT** mediante el escaneo de un código QR o clave manual y generar códigos temporales de 6 dígitos cada 30 segundos **100 % sin conexión a internet (offline)**.

---

## 1. Arquitectura del Ecosistema BillZen

El sistema **BillZen** de **ZenTech Solutions** está distribuido en tres repositorios dentro de la organización `bill-zen-desarrollo`:

| Repositorio | Componente | Tecnologías Principales | Rol en la Autenticación 2FA |
| :--- | :--- | :--- | :--- |
| **`bill_zen_users`** | Portal Web y API Cloud | HTML/CSS/JS, FastAPI, SQLAlchemy, PostgreSQL (Neon.tech), Vercel | Genera la semilla Base32 por empresa (`POST /api/2fa/setup`) y valida los códigos TOTP (`POST /api/2fa/verify`). |
| **`bill_zen_ERP`** | Software Contable de Escritorio | Python | Muestra el código QR de vinculación (`otpauth://`) y solicita el código de 6 dígitos al iniciar sesión. |
| **`Bill_zen_mobile`** | **App Móvil (este repositorio)** | **Kotlin, Jetpack Compose, CameraX, ML Kit, Android Keystore** | **Escanea el QR, almacena la semilla cifrada en hardware y calcula el código de 6 dígitos cada 30 s offline.** |

---

## 2. Especificaciones Técnicas

| Parámetro | Valor / Detalle |
| :--- | :--- |
| **Nombre de la aplicación** | BillZen Authenticator (`billzenERP.apk`) |
| **Application ID / Package** | `co.billzen.authenticator` |
| **SDK Mínimo (`minSdk`)** | API 26 (Android 8.0 Oreo — >97 % de cobertura de dispositivos) |
| **SDK Objetivo (`targetSdk`)** | API 37 |
| **Interfaz de Usuario** | Jetpack Compose + Material Design 3 |
| **Algoritmo TOTP** | RFC 6238 (`HMAC-SHA1`, ventana de 30 s, 6 dígitos) |
| **Codificación de Semilla** | RFC 4648 (`Base32` implementado de forma nativa) |
| **Almacenamiento Local** | Cifrado `AES-256-GCM` respaldado por hardware `AndroidKeyStore` |
| **Escáner QR** | CameraX (`1.4.1`) + Google ML Kit Barcode Scanning (`17.3.0`) 100 % en el dispositivo |

---

## 3. Estructura del Proyecto Implementada

```text
Bill_zen_mobile/
├── app/
│   ├── build.gradle.kts                     # Configuración del módulo, CameraX, ML Kit y archivesName="billzenERP"
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml          # Permiso CAMERA y allowBackup="false"
│       │   ├── java/co/billzen/authenticator/
│       │   │   ├── MainActivity.kt          # Pantalla principal, tarjetas TOTP con reloj circular 30s y modales
│       │   │   ├── QrScannerDialog.kt       # Visor de cámara en vivo con CameraX + ML Kit Barcode Scanning
│       │   │   ├── core/                    # Motor criptográfico, parser y almacenamiento seguro
│       │   │   │   ├── Base32.kt            # Decodificador y validador RFC 4648
│       │   │   │   ├── TotpGenerator.kt     # Motor RFC 6238 (HMAC-SHA1, truncamiento dinámico, progreso 30s)
│       │   │   │   ├── OtpAuthParser.kt     # Analizador y validador de URIs otpauth://totp/...
│       │   │   │   └── SecureAccountStore.kt# Persistencia cifrada AES-256-GCM con AndroidKeyStore
│       │   │   └── ui/theme/
│       │   │       ├── Color.kt             # Paleta corporativa BillZen (Dark, Emerald, Mint, Gold, Amber, Cream)
│       │   │       ├── Theme.kt             # Esquema de color Material 3
│       │   │       └── Type.kt              # Definición tipográfica
│       │   └── res/                         # Recursos gráficos y textos XML
│       └── test/java/co/billzen/authenticator/
│           └── ExampleUnitTest.kt           # Pruebas unitarias con vectores oficiales RFC 4648 y RFC 6238
├── build.gradle.kts                         # Configuración raíz de Gradle (Kotlin DSL)
├── gradle/libs.versions.toml                # Catálogo centralizado de versiones
└── settings.gradle.kts                      # Definición de repositorios y módulos
```

---

## 4. Medidas de Seguridad Aplicadas

1. **Protección contra capturas de pantalla (`FLAG_SECURE`):** Bloquea capturas de pantalla, grabaciones de video y vista previa en el selector de apps recientes.
2. **Cifrado autenticado en hardware (`AndroidKeyStore` + `AES/GCM/NoPadding`):** Las semillas 2FA se guardan cifradas con una llave maestra AES de 256 bits no exportable alojada en el chip de seguridad del dispositivo.
3. **Desactivación de copias de seguridad en la nube (`allowBackup="false"`):** Configurado en `AndroidManifest.xml` para evitar extracción de semillas vía respaldos de ADB o nube.
4. **Portapapeles protegido (`EXTRA_IS_SENSITIVE`):** Al tocar una tarjeta para copiar el código de 6 dígitos, se marca como contenido sensible en Android 13+ para ocultarlo de la vista previa del sistema.
5. **Confirmación de seguridad al desvincular:** Modal de confirmación obligatorio antes de eliminar cualquier NIT vinculado.

---

## 5. Estado del Desarrollo (Roadmap MVP)

- [x] Inicialización del proyecto Android (Kotlin + Jetpack Compose)
- [x] Definición de paleta de colores corporativa (`Color.kt`) y tema Material 3 (`Theme.kt`)
- [x] Módulo `core/Base32.kt`: decodificador y validador RFC 4648
- [x] Módulo `core/TotpGenerator.kt`: algoritmo RFC 6238 + pruebas unitarias con vectores oficiales (`t=59s -> 287082`)
- [x] Módulo `core/OtpAuthParser.kt`: lectura y validación de URI `otpauth://totp/BillZen:{NIT}?secret=...`
- [x] Módulo `core/SecureAccountStore.kt`: guardado cifrado multi-empresa con `AndroidKeyStore` (`AES-256-GCM`)
- [x] Tarjeta de empresa con código de 6 dígitos en vivo, anillo circular de 30 s (verde menta / alerta ámbar en últimos 5 s) y copia sensible al portapapeles
- [x] Escáner QR en vivo con `CameraX` + `Google ML Kit` (`QrScannerDialog.kt`) e ingreso manual de clave/URI
- [x] Configuración de salida `base.archivesName.set("billzenERP")` y `allowBackup="false"`
- [ ] Personalización del ícono adaptativo con el logo "B" de BillZen y publicación de `billzenERP.apk` en GitHub Releases

---

## 6. Autores

Desarrollado por el equipo de ingeniería de **ZenTech Solutions** (Colombia):

- **Camilo (Cerezo)** — Co-Founder & Developer
- **Camilo Alarcón** — Co-Founder & Developer

---
*© ZenTech Solutions — Todos los derechos reservados.*