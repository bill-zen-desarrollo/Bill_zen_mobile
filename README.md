# BillZen Mobile ERP & Authenticator (`Bill_zen_mobile`)

![Platform](https://img.shields.io/badge/Plataforma-Android%208.0%2B%20(API%2026)-0A6F52?style=flat-square&logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Lenguaje-Kotlin%202.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Architecture](https://img.shields.io/badge/Arquitectura-MVVM%20%2B%20StateFlow-D4A94C?style=flat-square)
![Organization](https://img.shields.io/badge/Organizaci%C3%B3n-ZenTech%20Solutions-171B24?style=flat-square)

Aplicación móvil nativa para Android de uso exclusivo para el **Administrador / Dueño de la empresa**, que funciona como espejo ejecutivo del software de escritorio **BillZen ERP (`BillzenERP.exe`)** e integra el autenticador de doble factor (**2FA / TOTP — RFC 6238**).

Al abrir la aplicación por primera vez, solicita escanear el código QR de vinculación del ERP de escritorio. Una vez vinculada la empresa por su **NIT**, desbloquea el **Dashboard Financiero**, el módulo de **Contabilidad y Cuentas por Pagar** y el **Generador de Tokens 2FA offline**.

---

## 1. Arquitectura del Ecosistema BillZen (`bill-zen-desarrollo`)

| Repositorio | Componente | Tecnologías Principales | Rol en el Ecosistema |
| :--- | :--- | :--- | :--- |
| **`bill_zen_users`** | Portal Web y API Cloud | HTML/CSS/JS, FastAPI, SQLAlchemy, PostgreSQL (Neon.tech), Vercel | Registro de empresas, validación de sesión única por dispositivo (`Device Binding`), endpoints 2FA y descarga oficial de `billzenERP.apk`. |
| **`bill_zen_ERP`** | Software Contable de Escritorio | Python (`BillzenERP.exe`) | Operación contable completa, facturación electrónica y generación de QR de vinculación. |
| **`Bill_zen_mobile`** | **App Móvil Ejecutiva (este repo)** | **Kotlin, Jetpack Compose, MVVM (`StateFlow`), CameraX, ML Kit, Android Keystore** | **Dashboard de KPIs financieros, control de contabilidad/egresos, Device Binding por hardware y token TOTP 2FA.** |

---

## 2. Estructura Modular del Proyecto

```text
Bill_zen_mobile/
├── app/
│   ├── build.gradle.kts                     # Configuración del módulo, CameraX, ML Kit y archivesName="billzenERP"
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml          # Permiso CAMERA y allowBackup="false"
│       │   ├── java/co/billzen/authenticator/
│       │   │   ├── MainActivity.kt          # Flujo raíz: Onboarding por QR -> Panel de Administrador (3 secciones)
│       │   │   ├── ErpViewModel.kt          # Arquitectura MVVM con StateFlow y modelos de dominio fuertemente tipados
│       │   │   ├── DashboardScreen.kt       # KPIs financieros, Donut Chart en Canvas y barras de productos top
│       │   │   ├── AccountingScreen.kt      # Resumen contable y tabla dinámica de Egresos / Cuentas por Pagar
│       │   │   ├── QrScannerDialog.kt       # Lector QR en vivo con CameraX + Google ML Kit
│       │   │   ├── core/                    # Capa de seguridad, criptografía y persistencia en hardware
│       │   │   │   ├── SecurityDeviceManager.kt # Device Binding (UUID único cifrado en AndroidKeyStore + validación HTTP 403)
│       │   │   │   ├── SecureAccountStore.kt    # Almacén cifrado AES-256-GCM de empresas vinculadas por NIT
│       │   │   │   ├── TotpGenerator.kt         # Motor criptográfico RFC 6238 (HMAC-SHA1)
│       │   │   │   ├── OtpAuthParser.kt         # Validador y parser de URIs otpauth://totp/...
│       │   │   │   └── Base32.kt                # Decodificador RFC 4648
│       │   │   └── ui/theme/
│       │   │       ├── Color.kt             # Paleta corporativa BillZen (Light Theme Crema 0xFFF5F3EF, Emerald, Gold)
│       │   │       ├── Theme.kt             # Esquema Material 3 Light corporativo
│       │   │       └── Type.kt              # Definición tipográfica
│       │   └── res/                         # Recursos gráficos y textos XML
│       └── test/java/co/billzen/authenticator/
│           └── ExampleUnitTest.kt           # Certificación de vectores oficiales RFC 4648 y RFC 6238
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 3. Arquitectura de Seguridad

1. **Sesión Única Estricta por Hardware (`Device Binding` — `SecurityDeviceManager.kt`):** Genera un UUID v4 criptográfico único por instalación, cifrado con `AES-256-GCM` dentro del `AndroidKeyStore`, y prepara el payload de verificación contra el backend (manejando rechazo `HTTP 403 Forbidden` si existe otra sesión activa).
2. **Puerta de Enlace por QR (`InitialQrOnboardingScreen`):** Ningún módulo administrativo es accesible hasta vincular la empresa mediante el QR del ERP de escritorio.
3. **Protección contra Capturas (`FLAG_SECURE`):** Bloquea capturas y grabaciones de pantalla sobre indicadores financieros y códigos 2FA.
4. **Cero Respaldo en Nube (`allowBackup="false"`):** Impide la extracción de semillas y llaves de sesión mediante backups externos.

---

## 4. Autores

Desarrollado por **ZenTech Solutions** (Colombia):
- **Camilo (Cerezo)** — Co-Founder & Developer
- **Camilo Alarcón** — Co-Founder & Developer