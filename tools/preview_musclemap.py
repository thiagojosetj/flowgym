#!/usr/bin/env python3
"""Renders the muscle map as one SVG sheet, for reviewing the artwork.

    python3 tools/preview_musclemap.py [saida.svg]

This is a review aid and is not part of the build: the app ships the VectorDrawable XML, which
comes from the same definitions in musclemap.py, so a change to the artwork shows up in both.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import musclemap as m

# Flat colours, only for the review sheet. In the app the silhouette and the highlight are tinted
# by the theme, so they follow light and dark mode instead of being baked in.
BODY = "#C9CEDA"
HINT = "#AEB5C4"
HIGHLIGHT = "#2E6BE6"
SCALE = 0.56
COLUMNS = 7


def figure(x, y, side, region_paths, paired, label):
    hint = m.FRONT_HINT if side == "front" else m.BACK_HINT
    out = [f'<g transform="translate({x},{y}) scale({SCALE})">']
    out += [f'<path d="{p}" fill="{BODY}"/>' for p in m.FRONT_BODY]
    out += [f'<path d="{p}" fill="none" stroke="{HINT}" stroke-width="1.6" '
            f'stroke-linecap="round"/>' for p in hint]
    for path, is_paired in zip(region_paths, paired):
        out.append(f'<path d="{path}" fill="{HIGHLIGHT}"/>')
        if is_paired:
            # Mirrored about the figure's axis rather than drawn again, so the two halves of a
            # muscle can never drift apart.
            out.append(f'<g transform="translate({m.VIEW_W},0) scale(-1,1)">'
                       f'<path d="{path}" fill="{HIGHLIGHT}"/></g>')
    out.append("</g>")
    cx = x + (m.VIEW_W * SCALE) / 2
    base = y + m.VIEW_H * SCALE
    out.append(f'<text x="{cx}" y="{base + 16}" text-anchor="middle" '
               f'font-family="system-ui,sans-serif" font-size="11" fill="#444">{label}</text>')
    out.append(f'<text x="{cx}" y="{base + 29}" text-anchor="middle" '
               f'font-family="system-ui,sans-serif" font-size="9" fill="#888">'
               f'{"frente" if side == "front" else "costas"}</text>')
    return out


def main(destination):
    cells = [("front", [], [], "silhueta (frente)"), ("back", [], [], "silhueta (costas)")]
    for code in sorted(m.GROUP_SIDE):
        side, paths, paired = m.group_region(code)
        cells.append((side, paths, paired, f"[grupo] {code}"))
    cells += [(side, paths, [is_paired] * len(paths), code)
              for code, (side, paths, is_paired) in sorted(m.REGIONS.items())]

    cell_w = m.VIEW_W * SCALE + 26
    cell_h = m.VIEW_H * SCALE + 42
    rows = (len(cells) + COLUMNS - 1) // COLUMNS
    width = COLUMNS * cell_w + 20
    height = rows * cell_h + 50

    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
           f'viewBox="0 0 {width} {height}">',
           f'<rect width="{width}" height="{height}" fill="#FBFBFD"/>',
           f'<text x="16" y="26" font-family="system-ui,sans-serif" font-size="14" fill="#222" '
           f'font-weight="600">FlowGym — mapa muscular: {len(m.GROUP_SIDE)} grupos + '
           f'{len(m.REGIONS)} subgrupos</text>']
    for index, (side, paths, paired, label) in enumerate(cells):
        row, column = divmod(index, COLUMNS)
        svg += figure(20 + column * cell_w, 42 + row * cell_h, side, paths, paired, label)
    svg.append("</svg>")

    with open(destination, "w", encoding="utf-8") as handle:
        handle.write("\n".join(svg))
    print(f"{destination}: {len(m.GROUP_SIDE)} grupos + {len(m.REGIONS)} subgrupos, "
          f"{os.path.getsize(destination)} bytes")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "musclemap-preview.svg")
