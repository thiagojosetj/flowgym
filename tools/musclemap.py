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
#
# Each entry is (side, paths, paired).
#
# `paired` is why there are 38 entries and not 70-odd paths: the figure is symmetric about x = 50,
# so a muscle that exists on both sides is drawn ONCE on the left and the renderer mirrors it.
# Hand-drawing the right side too would guarantee the two halves drifting apart over time, and a
# lopsided highlight reads as a mistake in the anatomy rather than in the drawing.
#
# The 13 group images are not here either: a group is the union of its subgroups, composed by the
# generator, so "Peito" always lights exactly what its three parts light.

REGIONS = {
    # ---------------------------------------------------------------- chest (front)
    "chest.upper": ("front", ["M34 44c5-2.4 10.4-3.6 16-3.6v8.4c-4.6 0-9.2.8-13.6 2.4z"], False),
    "chest.middle": ("front", ["M33.4 53.6c5.2-2 10.8-3 16.6-3v10.4c-5.2 0-10.2.8-15 2.4z"], False),
    "chest.lower": ("front", ["M33.6 64.6c5-1.8 10.6-2.8 16.4-2.8v8.6c-5 0-9.8.8-14.4 2.2z"], False),

    # ---------------------------------------------------------------- back
    "back.lats": ("back", ["M31.4 56l4-1.6 5.8 25.6c1 4.4 2.6 8 5 11.4l-9 2c-2.6-3.6-4.2-7.4-5-12z"],
                  True),
    "back.upper_traps": ("back", ["M46.4 33.6c-6 .4-11.8 1.8-16.6 4-2.2 1-3.4 2.4-3.8 4.4l6.6 2.6"
                                  "c3.8-2.6 8.6-4.2 13.8-4.6z"], True),
    "back.mid_lower_traps": ("back", ["M48.6 52v34l-6.4-4.6-5.4-19.4z"], True),
    "back.rhomboids": ("back", ["M48.6 50.4v16l-9.6-5-2.4-9.4z"], True),
    "back.teres_major": ("back", ["M33.4 57.6l7.4 3.4 1.8 7.6-8.4-2.6z"], True),

    # ---------------------------------------------------------------- shoulders
    "shoulders.front_delt": ("front", ["M28.6 39.4c3.8 1 5.8 3.6 6.2 7.8l.6 7-10.6 1.6-1.2-9.6c"
                                       "-.4-3.8 1.2-6 5-6.8z"], True),
    "shoulders.side_delt": ("front", ["M24.8 42.6c2.4-.4 3.6.8 3.4 3.6l-.8 12.8-6.6-1.2.6-11.2c"
                                      ".2-2.6 1.2-3.8 3.4-4z"], True),
    "shoulders.rear_delt": ("back", ["M28.6 40c3.6 1 5.6 3.4 6 7.4l.6 6.4-10 1.4-1-9c-.4-3.6 1-5.6"
                                     " 4.4-6.2z"], True),
    "shoulders.rotator_cuff": ("back", ["M33.6 45.4l6.6 2.2 1 7-7.6-1.8z"], True),

    # ---------------------------------------------------------------- arms: biceps
    "biceps.long_head": ("front", ["M26.4 48l4.2-.6-.8 12.4c-.2 2.8-.7 4.8-1.8 7.4l-2.2 5.2-5-2 "
                                   "2.4-5.8c.9-2.2 1.3-3.8 1.5-6.2z"], True),
    "biceps.short_head": ("front", ["M22.6 49.6l3.2.2-.8 10.8c-.2 2.6-.6 4.4-1.6 6.8l-1.8 4.4"
                                    "-3.8-1.6 1.8-4.6c.8-2 1.1-3.4 1.3-5.6z"], True),
    "biceps.brachialis": ("front", ["M21.4 64.6l4.4 1.4-2.4 7.2-4.4-1.8z"], True),

    # ---------------------------------------------------------------- arms: triceps
    "triceps.long_head": ("back", ["M28.8 47.4l3.6-.4-.9 13c-.2 2.8-.6 4.8-1.7 7.4l-2.2 5.2-4.4"
                                   "-1.8 2.2-5.4c.9-2.2 1.2-3.8 1.4-6z"], True),
    "triceps.lateral_head": ("back", ["M24.6 50l3 .4-.8 11c-.2 2.6-.6 4.4-1.5 6.6l-1.6 4-3.6-1.4"
                                      " 1.6-4.2c.8-2 1-3.4 1.2-5.4z"], True),
    "triceps.medial_head": ("back", ["M25.6 63.6l4.2 1.2-2.2 7-4.2-1.6z"], True),

    # ---------------------------------------------------------------- forearms
    "forearms.flexors": ("front", ["M21 74.6l5 2-5.6 14c-1 2.6-1.6 4.4-2 7l-1.4 9-5-1 1.6-9.6c.5"
                                   "-3 1.1-5.2 2.2-8z"], True),
    "forearms.extensors": ("back", ["M21 74.6l5 2-5.6 14c-1 2.6-1.6 4.4-2 7l-1.4 9-5-1 1.6-9.6c.5"
                                    "-3 1.1-5.2 2.2-8z"], True),
    "forearms.brachioradialis": ("front", ["M23.4 71.4l4.4 1.8-3.6 9-4.4-1.8z"], True),

    # ---------------------------------------------------------------- quadriceps (front)
    "quads.rectus_femoris": ("front", ["M44.6 114l4 1.2-.6 20c-.1 3.4-.4 6-1.1 9.2l-1.4 6.4-5"
                                       "-1.2 1.4-6.6c.7-3 1-5.4 1.1-8.8z"], True),
    "quads.vastus_lateralis": ("front", ["M39.6 116l4 1-1 18.4c-.2 3.2-.5 5.6-1.2 8.6l-1.2 5.4"
                                         "-4.6-1.2 1.2-5.6c.7-2.8 1-5 1.2-8z"], True),
    "quads.vastus_medialis": ("front", ["M47.4 138l4 1-.4 10.6c-.1 2.8-.4 4.8-1 7.4l-1.2 5-4.6"
                                        "-1.2 1.2-5.2c.6-2.4.9-4.2 1-6.8z"], True),
    "quads.vastus_intermedius": ("front", ["M43.4 120.6l3.4.8-.8 17-3.6-.8z"], True),

    # ---------------------------------------------------------------- hamstrings (back)
    "hamstrings.biceps_femoris": ("back", ["M39.6 118l4.2 1-1 19c-.2 3.2-.5 5.6-1.2 8.6l-1.2 5.2"
                                           "-4.6-1.2 1.2-5.4c.7-2.8 1-5 1.2-8z"], True),
    "hamstrings.semis": ("back", ["M45 118.6l4 1-.8 18.6c-.1 3.2-.4 5.6-1 8.6l-1.1 5-4.6-1.2 1.1"
                                  "-5.2c.6-2.8.9-5 1-8z"], True),

    # ---------------------------------------------------------------- glutes (back)
    "glutes.maximus": ("back", ["M38.6 100.4c4 .6 7.6 1 11.4 1v16.4c-4.4 0-8.4-.5-12.8-1.4z"], True),
    "glutes.medius": ("back", ["M36.6 92.6c3.2.8 6 1.4 9 1.8l-.4 7.4c-3.4-.4-6.4-1-9.6-1.9z"], True),
    "glutes.minimus": ("back", ["M37.6 88.4c2.6.7 5 1.2 7.4 1.5l-.3 4.4c-2.8-.4-5.2-.9-7.8-1.6z"],
                       True),

    # ---------------------------------------------------------------- adductors (front)
    "adductors.magnus": ("front", ["M48.4 114.6l1.6.2v22l-4-.8 1-13.6z"], True),
    "adductors.longus_brevis": ("front", ["M48.6 113.4l1.4.2v12.8l-3.4-.6.6-7.8z"], True),

    # ---------------------------------------------------------------- calves (back)
    "calves.gastrocnemius": ("back", ["M41.6 150l5 1-1.6 9c-.5 2.8-.7 5-.7 8l.1 9-6 .4-.1-9.6c0"
                                      "-3.2.2-5.6.8-8.6z"], True),
    "calves.soleus": ("back", ["M40.6 170l5 .6-.3 11c-.1 2.4-.2 4.2-.2 6.4l-4.8.3-.1-6.8c0-2.4 "
                               ".1-4.4.4-6.6z"], True),

    # ---------------------------------------------------------------- abdomen (front)
    "abs.rectus": ("front", ["M44.6 72h10.8v34h-10.8z"], False),
    "abs.obliques": ("front", ["M37.6 72.6l5.6 1-1 32-6-1.6z"], True),
    "abs.transverse": ("front", ["M41 92.6h18v12h-18z"], False),

    # ---------------------------------------------------------------- lower back
    "lower_back.erectors": ("back", ["M44.6 70l4.4.6v34l-5-.8z"], True),
}

# Groups are composed, never drawn: a group lights exactly what its subgroups light, and gaining a
# subgroup updates its group for free.
GROUP_SIDE = {
    "chest": "front", "back": "back", "shoulders": "front", "biceps": "front", "triceps": "back",
    "forearms": "front", "quads": "front", "hamstrings": "back", "glutes": "back",
    "adductors": "front", "calves": "back", "abs": "front", "lower_back": "back",
}


def group_region(group_code):
    """Every path of every subgroup of this group, on the group's own side."""
    side = GROUP_SIDE[group_code]
    paths, paired = [], []
    for code, (region_side, region_paths, is_paired) in sorted(REGIONS.items()):
        if code.split(".")[0] != group_code:
            continue
        # A group shows one side; a subgroup drawn on the other is still worth lighting, since the
        # alternative is a group that looks emptier than the sum of its parts.
        for path in region_paths:
            paths.append(path)
            paired.append(is_paired)
    return side, paths, paired
