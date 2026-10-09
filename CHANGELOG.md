# Cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [1.0.0] - 2026-10-09

### Agregado
- Motor de fórmulas determinista (NNLS Lawson–Hanson ponderado, ácido por bicarbonatos,
  tanques A/B/ácido, solubilidad, EC por cationes, balance de masa) con pruebas JUnit 5.
- Datos semilla: 15 cultivos con rangos v2 del proyecto Soluciones Nutritivas, 17 insumos
  LATAM editables y recetas Hoagland, La Molina y MasterBlend. Lo que no tiene fuente queda
  como `TODO_FUENTE` ("dato pendiente").
- Asistente de fórmula en 2 pantallas, pedido en lenguaje natural (offline por reglas,
  Gemini Nano o nube con consentimiento), PDF/CSV y fórmulas guardadas.
- Sistemas con foto, cultivos por reservorio, calendario, compatibilidad y temporizador.
- Bitácora con registro rápido, gráficos Vico con zona óptima, explicación de deriva y
  calculadoras (pH, EC/ppm 500/700, ácido, reposición).
- Alertas offline cada 6 h (temperatura > 22 °C, pH/EC fuera de rango o con tendencia a
  salir en 24 h), recordatorios y latido de sensores (15 min).
- Diagnóstico por foto (CameraX) en 3 niveles: LiteRT local, Gemini Nano y Claude por proxy.
- Fichas MIP, compras y costos en soles, sensores BLE GATT y MQTT, widget Glance.
- Privacidad (Ley 29733), borrar todos los datos, respaldo JSON, APhid Pro (Play Billing).
- Idiomas: es-PE (por defecto), es-419, pt-BR y en.
