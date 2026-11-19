# Reglas R8/ProGuard de BillZen Mobile ERP (release)

# Modelos de dominio, cliente HTTP y capa de seguridad
-keep class co.billzen.authenticator.core.** { *; }

# Parser JSON nativo de Android
-keep class org.json.** { *; }

# CameraX + ML Kit (lector QR)
-keep class androidx.camera.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn org.json.**
-dontwarn androidx.camera.**
-dontwarn com.google.mlkit.**
