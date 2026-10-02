#!/usr/bin/env python3
"""Checks a folder of exercise illustrations against the catalogue.

    python3 tools/check_exercise_art.py "E:\\Downloads\\Imagens-flow"

Answers the mechanical half of "which ones are wrong" - the half nobody should have to check by
eye: which codes are missing, which files belong to no exercise, which are not square, and which
have no transparency. It says nothing about whether a drawing is any good.

Reads the PNG header directly, so it needs no libraries beyond the standard one.
"""
import json
import os
import struct
import sys

CATALOG = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "android-app", "app", "src", "main", "assets", "catalog",
                       "catalog.json")

# PNG colour types that carry an alpha channel. 3 (palette) can be transparent through a tRNS
# chunk, which is checked separately.
ALPHA_TYPES = {4, 6}


def png_header(path):
    """(width, height, colour_type, has_trns) or None when this is not a PNG."""
    with open(path, "rb") as handle:
        if handle.read(8) != b"\x89PNG\r\n\x1a\n":
            return None
        length, chunk = struct.unpack(">I4s", handle.read(8))
        if chunk != b"IHDR":
            return None
        width, height, _depth, colour_type = struct.unpack(">IIBB", handle.read(10))
        handle.read(length - 10 + 4)

        has_trns = False
        while True:
            head = handle.read(8)
            if len(head) < 8:
                break
            size, name = struct.unpack(">I4s", head)
            if name == b"tRNS":
                has_trns = True
                break
            if name == b"IDAT":
                break
            handle.read(size + 4)
        return width, height, colour_type, has_trns


def main(folder):
    with open(CATALOG, encoding="utf-8") as handle:
        codes = [exercise["code"] for exercise in json.load(handle)["exercises"]]

    present, problems, extra = {}, [], []
    for entry in sorted(os.listdir(folder)):
        path = os.path.join(folder, entry)
        if not os.path.isfile(path):
            continue
        stem, extension = os.path.splitext(entry)
        if stem not in codes:
            extra.append(entry)
            continue
        present[stem] = entry

        if extension.lower() != ".png":
            problems.append(f"{entry}: nao e PNG (a transparencia se perde em JPG)")
            continue
        header = png_header(path)
        if header is None:
            problems.append(f"{entry}: nao e um PNG valido")
            continue
        width, height, colour_type, has_trns = header
        if width != height:
            problems.append(f"{entry}: {width}x{height}, nao e quadrado")
        if width < 512:
            problems.append(f"{entry}: {width}px, pequeno demais (minimo 512)")
        if colour_type not in ALPHA_TYPES and not has_trns:
            problems.append(f"{entry}: sem canal de transparencia (fundo solido)")

    missing = [code for code in codes if code not in present]

    print(f"pasta: {folder}")
    print(f"exercicios no catalogo: {len(codes)}")
    print(f"encontrados: {len(present)}")
    print()
    if missing:
        print(f"FALTANDO ({len(missing)}) - precisa gerar:")
        for code in missing:
            print(f"  {code}.png")
        print()
    if extra:
        print(f"SOBRANDO ({len(extra)}) - nome nao bate com nenhum exercicio, APAGAR ou RENOMEAR:")
        for entry in extra:
            print(f"  {entry}")
        print()
    if problems:
        print(f"PROBLEMAS ({len(problems)}) - gerar de novo:")
        for problem in problems:
            print(f"  {problem}")
        print()
    if not missing and not extra and not problems:
        print("Tudo certo no que da para verificar por arquivo.")
        print("O que esta imagem MOSTRA continua sendo julgamento humano.")
    return 1 if (missing or extra or problems) else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("uso: python3 tools/check_exercise_art.py <pasta>")
    sys.exit(main(sys.argv[1]))
