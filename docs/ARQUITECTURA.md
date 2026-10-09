# Arquitectura de APhid

Clean Architecture + MVVM con flujo unidireccional: cada pantalla expone un `StateFlow` de
estado inmutable y recibe eventos como llamadas al ViewModel. **Offline-first**: Room es la
fuente de verdad; la nube es opcional y está desactivada por defecto.

```
:app ──► :feature:* ──► :core:domain ──► :core:formula-engine ──► :core:model
  │            │              ▲
  │            ├► :core:designsystem (tema, íconos, cadenas en 4 idiomas)
  │            ├► :core:ai (OnDevice · GeminiNano · ClaudeProxy)
  │            └► :core:data (exportadores PDF/CSV)
  └► :core:data ─► :core:database (Room)   :core:notifications
```

| Módulo | Tipo | Responsabilidad |
|---|---|---|
| `:core:model` | Kotlin/JVM | Modelos serializables, semillas |
| `:core:formula-engine` | Kotlin/JVM | NNLS, tanques, ácido, solubilidad, EC |
| `:core:domain` | Kotlin/JVM | Interfaces de repositorio, casos de uso, reglas de deriva, alertas, calculadoras, validación IA, protocolo de sensores |
| `:core:database` | Android | Room (14 entidades), DAOs, assets semilla |
| `:core:data` | Android | Repositorios, DataStore, respaldo JSON, PDF/CSV, FileProvider |
| `:core:designsystem` | Android | Tema verde agua, íconos vectoriales, componentes, cadenas |
| `:core:notifications` | Android | Canales y notificaciones |
| `:core:ai` | Android | `AiProvider` y enrutador por niveles |
| `:feature:*` | Android | Pantallas + ViewModels por funcionalidad |

Los tres módulos Kotlin/JVM (≈ 40 % de la lógica) se prueban sin emulador.

## Navegación

Navigation Compose con rutas tipadas `@Serializable`. `NavigationSuiteScaffold`
(Material 3 Adaptive) muestra barra inferior en teléfonos y riel en tabletas con 5 pestañas:
Sistemas, Fórmula, Bitácora, Diagnóstico y Más.

## Inyección y trabajos

Hilt en todos los módulos (KSP). `AlertsWorker` (`@HiltWorker`) corre cada 6 h y, si hay
sensores, cada 15 min para vigilar el latido. El widget Glance se refresca cuando cambian la
última lectura o la próxima tarea.

## IA

`AiRouter` intenta primero lo local: parser por reglas → Gemini Nano (si `checkStatus()` es
`AVAILABLE`) → Claude vía proxy solo con consentimiento. Toda respuesta pasa por
`AiSchemaValidator`; si no cumple el esquema se descarta. La IA nunca calcula gramos.

## Build

Gradle 9.8 + AGP 9.4 (Kotlin integrado), Kotlin 2.4, KSP 2.3, Version Catalog y
convenciones en `build-logic/`. `compileSdk`/`targetSdk` 36, `minSdk` 26, JDK 17.
