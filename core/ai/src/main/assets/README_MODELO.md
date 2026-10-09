# Modelo de diagnóstico on-device (intercambiable)

Coloca aquí `diagnosis_model.tflite` (LiteRT/TFLite) para activar el nivel 1 de diagnóstico.

- Entrada: imagen RGB `[1, alto, ancho, 3]`, `float32` normalizada 0–1 **o** `uint8` 0–255.
- Salida: `[1, N]` con probabilidades (o logits) en el orden de `diagnosis_labels.txt`.
- APhid no incluye un modelo entrenado: sin el archivo, el nivel 1 se informa como
  "no instalado" y se usan los niveles 2 (Gemini Nano) o 3 (nube con consentimiento).
