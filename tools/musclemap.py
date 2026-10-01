"""Single source of truth for the muscle map artwork.

Every path here is drawn for this project. Nothing is traced from, or derived from, any other
product's artwork or any copyrighted anatomical plate; the figure is a schematic, not an atlas.

The same definitions produce the preview SVG (for reviewing the style) and the Android
VectorDrawable XML (what the app ships), so the two can never drift.

Canvas: 100 x 240, figure centred on x = 50.
"""

VIEW_W, VIEW_H = 100.0, 240.0

# --------------------------------------------------------------------------- silhouette

def _body(mirror_hint):
    """The standing figure. `mirror_hint` adds the cue that says which way it is facing."""
    parts = [
        # head
        "M50 6c6.2 0 11 5.1 11 11.8S56.2 29.6 50 29.6s-11-5.1-11-11.8S43.8 6 50 6z",
        # neck
        "M45.6 28.8h8.8v7.4h-8.8z",
        # torso: shoulders -> armpit -> waist -> hip
        "M50 34c7.4 0 15.2 1.5 20.4 4.3 2.6 1.4 3.6 3.4 3.8 6.2l1 16.4c.2 3.4-.6 5.4-2.2 "
        "8.2l-2.4 4.2c-1 1.8-1.4 3.4-1.6 5.6l-2 24c-.3 3.6-.6 6-1.4 9.2l-1.6 6.4c-.5 2-1.6 "
        "3-3.6 3h-21.6c-2 0-3.1-1-3.6-3l-1.6-6.4c-.8-3.2-1.1-5.6-1.4-9.2l-2-24c-.2-2.2-.6-3.8"
        "-1.6-5.6l-2.4-4.2c-1.6-2.8-2.4-4.8-2.2-8.2l1-16.4c.2-2.8 1.2-4.8 3.8-6.2C34.8 35.5 "
        "42.6 34 50 34z",
        # arms
        "M27.4 39.4c2.4.6 3.4 2.4 3.2 5.2l-1.2 17.6c-.2 2.6-.6 4.4-1.6 6.8l-6.6 16c-1 2.4-1.6"
        " 4.2-2 6.8l-2.4 15.6c-.4 2.6-1.8 3.8-4 3.4-2.2-.4-3.2-2-2.8-4.6l2.4-16c.5-3.2 1.2-5.6"
        " 2.4-8.6l6.2-15.2c.8-2 1.1-3.4 1.2-5.6l1.2-18c.2-2.8 1.6-4 4-3.4z",
        "M72.6 39.4c2.4-.6 3.8.6 4 3.4l1.2 18c.1 2.2.4 3.6 1.2 5.6l6.2 15.2c1.2 3 1.9 5.4 2.4"
        " 8.6l2.4 16c.4 2.6-.6 4.2-2.8 4.6-2.2.4-3.6-.8-4-3.4l-2.4-15.6c-.4-2.6-1-4.4-2-6.8l"
        "-6.6-16c-1-2.4-1.4-4.2-1.6-6.8l-1.2-17.6c-.2-2.8.8-4.6 3.2-5.2z",
        # legs
        "M46.4 110.2c1.8 0 2.8 1 3 3l1.2 20c.2 3.4 0 5.8-.6 9l-3.4 18c-.6 3.2-.8 5.6-.8 9l.2 "
        "24c0 2.8-1.4 4.2-4 4.2s-4-1.4-4-4.2l-.2-24.4c0-3.6.2-6.2.8-9.6l3.2-17.4c.5-2.8.7-4.8"
        ".6-7.8l-1-20.8c-.1-2 .9-3 2.6-3z",
        "M53.6 110.2c1.7 0 2.7 1 2.6 3l-1 20.8c-.1 3 .1 5 .6 7.8l3.2 17.4c.6 3.4.8 6 .8 9.6l"
        "-.2 24.4c0 2.8-1.4 4.2-4 4.2s-4-1.4-4-4.2l.2-24c0-3.4-.2-5.8-.8-9l-3.4-18c-.6-3.2-.8"
        "-5.6-.6-9l1.2-20c.2-2 1.2-3 3-3z",
        # feet
        "M38.6 194.6c3.4 0 5 1.6 5.2 4.4l.2 3.2c.1 1.8-.8 2.6-2.6 2.6h-9.6c-1.8 0-2.4-1-1.6"
        "-2.6l3.4-6c.8-1.2 2.2-1.6 5-1.6z",
        "M61.4 194.6c2.8 0 4.2.4 5 1.6l3.4 6c.8 1.6.2 2.6-1.6 2.6h-9.6c-1.8 0-2.7-.8-2.6-2.6l"
        ".2-3.2c.2-2.8 1.8-4.4 5.2-4.4z",
    ]
    return parts + mirror_hint


# The cue that tells front from back without a caption: a collarbone on the front, the spine
# and the shoulder blades on the back. Thin, so it never competes with the highlight.
FRONT_HINT = [
    "M38 44c4-1.6 8-2.4 12-2.4s8 .8 12 2.4",
]
BACK_HINT = [
    "M50 40v58",
    "M38 48l5 12", "M62 48l-5 12",
]

FRONT_BODY = _body([])
BACK_BODY = _body([])

# --------------------------------------------------------------------------- regions
# side: "front" or "back". Each path is the muscle region painted over the figure.

REGIONS = {
    "chest.upper":    ("front", ["M34 44c5-2.4 10.4-3.6 16-3.6s11 1.2 16 3.6l-1.4 8c-4.6-2-9.6"
                                 "-3-14.6-3s-10 1-14.6 3z"]),
    "back.lats":      ("back",  ["M31 56l4-2 6 26c1 4.4 2.6 8 5 11.4l-9 2c-2.6-3.6-4.2-7.4-5"
                                 "-12z",
                                 "M69 56l-4-2-6 26c-1 4.4-2.6 8-5 11.4l9 2c2.6-3.6 4.2-7.4 5"
                                 "-12z"]),
    "shoulders.rear_delt": ("back", ["M28.6 40c3.6 1 5.6 3.4 6 7.4l.6 6.4-10 1.4-1-9c-.4-3.6"
                                     " 1-5.6 4.4-6.2z",
                                     "M71.4 40c3.4.6 4.8 2.6 4.4 6.2l-1 9-10-1.4.6-6.4c.4-4 "
                                     "2.4-6.4 6-7.4z"]),
    "quads.vastus_medialis": ("front", ["M49 134l1.4 1 .6 14c.1 3.4-.2 6-1 9.2l-1.6 6.6-5.4"
                                        "-1.4 1.8-7.4c.7-3 1-5.2.9-8.2z"]),
    "biceps.long_head": ("front", ["M26.4 48l4.2-.6-.8 12.4c-.2 2.8-.7 4.8-1.8 7.4l-2.2 5.2"
                                   "-5-2 2.4-5.8c.9-2.2 1.3-3.8 1.5-6.2z"]),
    "calves.gastrocnemius": ("back", ["M41.6 150l5 1-1.6 9c-.5 2.8-.7 5-.7 8l.1 9-6 .4-.1-9.6"
                                      "c0-3.2.2-5.6.8-8.6z",
                                      "M58.4 150l-5 1 1.6 9c.5 2.8.7 5 .7 8l-.1 9 6 .4.1-9.6"
                                      "c0-3.2-.2-5.6-.8-8.6z"]),
}
