#!/usr/bin/env python3
"""Builds the Block Dungeons template: an isometric diorama of real blocks (item icons are isometric already), with
mobs on it, under a utility bar laid out as Minecraft Dungeons' own. The page around the generated parts is
mcdungeons.template.html beside this script; edit that, then run from the repository root:

    python3 tools/templates/mcdungeons.py
"""
import os
import random

S = 40                      # item box size, px
W = S * 0.884               # cube width inside the box
LIFT = S * 0.541            # one block of height
random.seed(7)

N = 9
height = [[0] * N for _ in range(N)]
kind = [['grass_block'] * N for _ in range(N)]
# A raised bank at the back left, a path across, a pond corner of mossy stone, a wall at the back.
for i in range(N):
    for j in range(N):
        if i <= 2 and j <= 4:
            height[i][j] = 1
        if i == 0 and j <= 2:
            height[i][j] = 2
        if abs(i - j) <= 0 and 2 < i < 8:
            kind[i][j] = 'dirt_path'
        if j - i == 1 and 3 < i < 7:
            kind[i][j] = 'coarse_dirt'
        if i >= 6 and j >= 6:
            kind[i][j] = 'mossy_cobblestone'
extra = []  # (i, j, level, block) placed on top
extra += [(1, 6, 0, 'oak_leaves'), (1, 7, 0, 'oak_leaves'), (2, 7, 0, 'oak_leaves'), (1, 6, 1, 'oak_leaves')]
extra += [(0, 5, 0, 'oak_log'), (0, 5, 1, 'oak_log'), (0, 5, 2, 'oak_leaves'), (0, 4, 2, 'oak_leaves'), (0, 6, 2, 'oak_leaves')]
extra += [(7, 1, 0, 'mossy_stone_bricks'), (8, 1, 0, 'mossy_stone_bricks'), (8, 2, 0, 'cracked_stone_bricks'), (8, 1, 1, 'mossy_stone_bricks')]
extra += [(5, 8, 0, 'red_mushroom_block'), (3, 1, 0, 'hay_block'), (4, 0, 0, 'hay_block')]


def at(i, j, level):
    """The centre of the item box for the cube at lattice (i, j) and height level, relative to the scene centre."""
    x = (i - j) * W / 2
    y = (i + j - (N - 1)) * W / 4 - level * LIFT
    return x, y


cubes = []
for i in range(N):
    for j in range(N):
        for h in range(height[i][j] + 1):
            cubes.append((i + j, h, i, j, 'dirt' if h < height[i][j] and kind[i][j] == 'grass_block' else kind[i][j]))
for i, j, l, b in extra:
    cubes.append((i + j, height[i][j] + 1 + l, i, j, b))
cubes.sort()
out = []
for depth, h, i, j, b in cubes:
    x, y = at(i, j, h)
    out.append(f'<item class="b" id="minecraft:{b}" style="left: {x - S / 2:.1f}px; top: {y - S / 2:.1f}px"></item>')


def stand(i, j, w, hgt):
    """left/top for an entity box w x hgt whose feet stand on the top face of tile (i, j)."""
    x, y = at(i, j, height[i][j])
    top_face = y - LIFT / 2
    return x - w / 2, top_face - hgt * 0.92


mobs = [('player', 4, 4, 30, 48, '-mc-yaw: 30deg', 'hero'), ('minecraft:zombie', 5, 2, 30, 48, '-mc-yaw: 140deg', ''),
        ('minecraft:skeleton', 3, 6, 30, 48, '-mc-yaw: -40deg', ''), ('minecraft:chicken', 5, 6, 18, 18, '-mc-yaw: 160deg', ''),
        ('minecraft:creeper', 7, 4, 26, 40, '-mc-yaw: 100deg', '')]
ents = []
for t, i, j, w, h, yaw, cls in mobs:
    lx, ly = stand(i, j, w, h)
    tag = '<entity player' if t == 'player' else f'<entity type="{t}"'
    bar = '' if cls == 'hero' else '<i class="hp"><b></b></i>'
    ents.append(f'<div class="mob {cls}" style="left: {lx:.1f}px; top: {ly:.1f}px; width: {w}px; height: {h}px">{bar}'
                f'{tag} style="width: {w}px; height: {h}px; {yaw}"></entity></div>')
loot_x, loot_y = at(5, 5, height[5][5])

page = open(os.path.join(os.path.dirname(__file__), 'mcdungeons.template.html'), encoding='utf-8').read()
page = page.replace('@@BLOCKS@@', '\n    '.join(out)).replace('@@MOBS@@', '\n    '.join(ents))
page = page.replace('@@LOOTX@@', f'{loot_x - 9:.1f}').replace('@@LOOTY@@', f'{loot_y - LIFT / 2 - 22:.1f}')
open('kit/src/main/resources/assets/oriel/ui/styles/mcdungeons.html',
     'w', encoding='utf-8', newline='\n').write(page)
print(len(out), 'blocks')
