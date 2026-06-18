#!/usr/bin/env python3
"""Bake one frame of HourglassDrawable into a static Android vector drawable.

Replicates the exact geometry in app/.../HourglassDrawable.kt (conical bulbs, sqrt sand
surface, stream, caps) at a given `progress`, so the launcher / notification icon is an
identical still of the live draining animation rather than a hand-drawn lookalike.

Usage:
  py tools/gen_hourglass_frame.py --progress 0.68 --side 24 --color "#FFFFFFFF" --out OUT.xml
"""
import argparse
import math

# Mirrors HourglassMath.kt
START_FILL = 0.80
END_FILL = 0.06


def surface(progress):
    p = max(0.0, min(1.0, progress))
    return math.sqrt(END_FILL + (START_FILL - END_FILL) * p)


def f(x):
    return f"{x:.3f}".rstrip("0").rstrip(".")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--progress", type=float, required=True)
    ap.add_argument("--side", type=float, default=24.0)
    ap.add_argument("--color", default="#FFFFFFFF")
    ap.add_argument("--out", required=True)
    # Optional: emit into a larger viewport with the glyph scaled+translated inside a group,
    # so it fits an adaptive-icon safe zone (e.g. --canvas 108 --scale 2.5 --translate 24).
    ap.add_argument("--canvas", type=float, default=None)
    ap.add_argument("--scale", type=float, default=1.0)
    ap.add_argument("--translate", type=float, default=0.0)
    a = ap.parse_args()

    S = a.side
    cx = S / 2
    cap_inset = S * 0.14
    side_inset = S * 0.20
    glass_top = cap_inset
    glass_bottom = S - cap_inset
    neck_y = (glass_top + glass_bottom) / 2
    half_w = S / 2 - side_inset
    neck_half = S * 0.045
    half_cap = S / 2 - S * 0.14
    glass_stroke = S * 0.06
    stream_stroke = S * 0.045

    s = surface(a.progress)
    top_surface_y = neck_y - s * (neck_y - glass_top)
    bottom_surface_y = neck_y + s * (glass_bottom - neck_y)
    w_top = half_w + (neck_half - half_w) * (1 - s)   # half-width at the top sand surface
    w_bot = neck_half + (half_w - neck_half) * s       # half-width at the bottom sand surface

    paths = []
    c = a.color

    # Top sand (only if the band is thicker than HourglassDrawable's 0.5px cutoff).
    if neck_y - top_surface_y > 0.5:
        paths.append(
            f'    <path android:fillColor="{c}" android:pathData="'
            f'M{f(cx - w_top)},{f(top_surface_y)} L{f(cx + w_top)},{f(top_surface_y)} '
            f'L{f(cx + neck_half)},{f(neck_y)} L{f(cx - neck_half)},{f(neck_y)} Z" />'
        )
    # Bottom sand.
    if glass_bottom - bottom_surface_y > 0.5:
        paths.append(
            f'    <path android:fillColor="{c}" android:pathData="'
            f'M{f(cx - w_bot)},{f(bottom_surface_y)} L{f(cx + w_bot)},{f(bottom_surface_y)} '
            f'L{f(cx + half_w)},{f(glass_bottom)} L{f(cx - half_w)},{f(glass_bottom)} Z" />'
        )
    # Falling stream.
    paths.append(
        f'    <path android:strokeColor="{c}" android:strokeWidth="{f(stream_stroke)}" '
        f'android:strokeLineCap="round" android:fillColor="#00000000" '
        f'android:pathData="M{f(cx)},{f(neck_y)} L{f(cx)},{f(bottom_surface_y)}" />'
    )
    # Glass outline (top + bottom trapezoids).
    stroke_attrs = (
        f'android:strokeColor="{c}" android:strokeWidth="{f(glass_stroke)}" '
        f'android:strokeLineCap="round" android:strokeLineJoin="round" '
        f'android:fillColor="#00000000"'
    )
    paths.append(
        f'    <path {stroke_attrs} android:pathData="'
        f'M{f(cx - half_w)},{f(glass_top)} L{f(cx + half_w)},{f(glass_top)} '
        f'L{f(cx + neck_half)},{f(neck_y)} L{f(cx - neck_half)},{f(neck_y)} Z" />'
    )
    paths.append(
        f'    <path {stroke_attrs} android:pathData="'
        f'M{f(cx - neck_half)},{f(neck_y)} L{f(cx + neck_half)},{f(neck_y)} '
        f'L{f(cx + half_w)},{f(glass_bottom)} L{f(cx - half_w)},{f(glass_bottom)} Z" />'
    )
    # Caps.
    paths.append(
        f'    <path {stroke_attrs} android:pathData="'
        f'M{f(cx - half_cap)},{f(glass_top)} L{f(cx + half_cap)},{f(glass_top)}" />'
    )
    paths.append(
        f'    <path {stroke_attrs} android:pathData="'
        f'M{f(cx - half_cap)},{f(glass_bottom)} L{f(cx + half_cap)},{f(glass_bottom)}" />'
    )

    body = "\n".join(paths)
    canvas = a.canvas if a.canvas else S
    if a.scale != 1.0 or a.translate != 0.0 or canvas != S:
        body = (
            f'    <group android:scaleX="{f(a.scale)}" android:scaleY="{f(a.scale)}"\n'
            f'        android:translateX="{f(a.translate)}" android:translateY="{f(a.translate)}">\n'
            f"{body}\n"
            "    </group>"
        )
    xml = (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{f(canvas)}dp" android:height="{f(canvas)}dp"\n'
        f'    android:viewportWidth="{f(canvas)}" android:viewportHeight="{f(canvas)}">\n'
        f"{body}\n"
        "</vector>\n"
    )
    with open(a.out, "w", encoding="utf-8") as fh:
        fh.write(xml)
    print("wrote", a.out, f"(surface={s:.3f}, top~{int(round(s*100))}% bottom~{int(round((1-s)*100))}%)")


if __name__ == "__main__":
    main()
