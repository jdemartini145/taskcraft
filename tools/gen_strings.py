#!/usr/bin/env python3
"""Genera res/values*/strings.xml de :core:designsystem desde strings_table.py.

Idiomas: es-PE (por defecto, values/), es-419 (values-b+es+419), pt-BR (values-pt-rBR), en (values-en).
Uso: python3 tools/gen_strings.py
"""
import os
import sys
from xml.sax.saxutils import escape

sys.path.insert(0, os.path.dirname(__file__))
from strings_table import STRINGS  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "core", "designsystem", "src", "main", "res")
LOCALES = [("values", 0), ("values-b+es+419", 1), ("values-pt-rBR", 2), ("values-en", 3)]


def android_escape(s: str) -> str:
    s = escape(s)
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    if s.startswith("@") or s.startswith("?"):
        s = "\\" + s
    return s


def main():
    keys = set()
    for row in STRINGS:
        assert len(row) == 5, row
        assert row[0] not in keys, "duplicada: " + row[0]
        keys.add(row[0])
    for folder, idx in LOCALES:
        path = os.path.join(ROOT, folder)
        os.makedirs(path, exist_ok=True)
        with open(os.path.join(path, "strings.xml"), "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n')
            f.write("<!-- Generado por tools/gen_strings.py. No editar a mano. -->\n<resources>\n")
            for row in STRINGS:
                key, texts = row[0], row[1:]
                text = texts[idx] if texts[idx] is not None else texts[0]
                fmt = ' formatted="false"' if text.count("%") > 1 and "$" not in text else ""
                f.write(f'    <string name="{key}"{fmt}>{android_escape(text)}</string>\n')
            f.write("</resources>\n")
    print(f"{len(STRINGS)} cadenas x {len(LOCALES)} idiomas")


if __name__ == "__main__":
    main()
