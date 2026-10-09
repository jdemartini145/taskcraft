# Formulario de Seguridad de los datos (Google Play)

| Pregunta | Respuesta |
|---|---|
| ¿La app recopila o comparte datos de usuario? | Sí, solo de forma opcional (diagnóstico en la nube) |
| ¿Los datos se cifran en tránsito? | Sí (HTTPS al proxy) |
| ¿El usuario puede pedir que se borren? | Sí ("Borrar todos mis datos"; en el servidor no se almacenan) |

| Tipo de dato | Recopilado | Compartido | Opcional | Finalidad |
|---|---|---|---|---|
| Fotos | Sí, solo si el usuario lo autoriza por foto | Sí, con Anthropic (procesador) | Sí | Funcionalidad de la app |
| Otro contenido generado por el usuario (notas de síntomas) | Igual que fotos | Igual que fotos | Sí | Funcionalidad de la app |
| Identificadores de dispositivo | No (token aleatorio por instalación, no persistente entre reinstalaciones) | No | — | Prevención de abusos |
| Ubicación, contactos, datos financieros, salud | No | No | — | — |
| Compras (APhid Pro) | Las procesa Google Play | — | Sí | — |

Permisos: `CAMERA` (diagnóstico), `POST_NOTIFICATIONS` (alertas), `BLUETOOTH_SCAN` con
`neverForLocation` y `BLUETOOTH_CONNECT` (sensores), `INTERNET` (opcional, nube/MQTT).
