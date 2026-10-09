# APhid

App Android nativa para cultivar en hidroponía en Latinoamérica. Pides una fórmula para tu
cultivo, etapa y volumen, y APhid entrega los **gramos exactos por solución madre**,
100 % offline, sin cuenta y en español.

> Este repositorio también conserva el prototipo web `index.html` (TaskCraft), ajeno a la app.

## Funciones

- **Fórmula en 2 pantallas**: cultivo, etapa, sistema, volumen, análisis de agua, insumos que
  tienes (con sugerencias), factor 50x/100x/200x → gramos por tanque, mL por litro, ppm contra
  meta, desvío, EC estimada, pH objetivo, costo en S/ y lista de compras. PDF, CSV y compartir.
- **Pedido en lenguaje natural** ("fórmula para fresa en fructificación, 200 L, agua de pozo"):
  la IA solo convierte texto en una solicitud validada; **los gramos los calcula el motor**.
- **Sistemas y cultivos**: DWC, NFT, Kratky, torre, goteo, flujo y reflujo, raíz flotante;
  calendario, cosecha estimada, compatibilidad de pH/EC y temporizador de ciclo.
- **Bitácora**: registro en segundos, gráficos con zona óptima y explicación de la deriva.
- **Alertas offline** cada 6 h: solución > 22 °C (pudrición de raíz), pH/EC fuera de rango o
  con tendencia a salir en 24 h, recordatorios y latido de sensores.
- **Diagnóstico por foto** (orientativo) en 3 niveles: LiteRT local, Gemini Nano, Claude con
  consentimiento por foto.
- Plagas (MIP), compras y costos, sensores ESP32 (BLE/MQTT), widget, respaldo JSON.

## Compilar

Requisitos: JDK 17 y Android SDK 36.

```bash
./gradlew assembleDebug testDebugUnitTest   # app debug + todas las pruebas unitarias
./gradlew assembleRelease                    # R8 + reducción de recursos
./gradlew detekt ktlintCheck lintDebug       # análisis estático
```

Para el diagnóstico en la nube, despliega `backend/claude-proxy` y compila con
`-Paphid.aiProxyUrl=https://...`. **No hay ninguna clave de API en el código ni en el APK.**

## Estructura

Ver [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) y las ecuaciones del motor en
[docs/MOTOR_FORMULAS.md](docs/MOTOR_FORMULAS.md). Sensores: [docs/SENSORES.md](docs/SENSORES.md).
Privacidad: [docs/PRIVACIDAD.md](docs/PRIVACIDAD.md) · Play: [docs/SEGURIDAD_DATOS_PLAY.md](docs/SEGURIDAD_DATOS_PLAY.md).

## Datos agronómicos

APhid no inventa valores. Las metas provienen de fuentes citadas (Hoagland y Arnon 1950;
Hochmuth, UF/IFAS HS796; Solución La Molina, UNALM; rangos v2 del proyecto Soluciones
Nutritivas). Lo que no tiene fuente aparece como **"dato pendiente"** (`TODO_FUENTE`) y puedes
usar una receta de referencia. Revisa los valores marcados "verificar" antes de producir.

## Cadenas e idiomas

Las cadenas de la UI se generan desde `tools/strings_table.py` con
`python3 tools/gen_strings.py` (es-PE por defecto, es-419, pt-BR, en).
