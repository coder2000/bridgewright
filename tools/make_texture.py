"""Draws the Bridge Wand item texture (16x16 PNG) without any image libraries."""
import struct, sys, zlib

T = (0, 0, 0, 0)
STICK_D, STICK_L = (73, 54, 21, 255), (137, 103, 39, 255)
PLANK_D, PLANK, PLANK_L = (104, 78, 47, 255), (162, 130, 78, 255), (196, 160, 98, 255)
POST = (84, 64, 38, 255)

px = [[T] * 16 for _ in range(16)]

# Diagonal handle, bottom-left to the middle.
for i in range(9):
    x, y = 1 + i, 14 - i
    px[y][x] = STICK_L
    px[y][x + 1] = STICK_D

# A small arched bridge as the head: deck of planks with two railing posts.
deck = [(7, 6), (8, 5), (9, 4), (10, 4), (11, 4), (12, 4), (13, 5), (14, 6)]
for x, y in deck:
    px[y][x] = PLANK_L
    px[y + 1][x] = PLANK
    px[y + 2][x] = PLANK_D
for x, y in [(8, 5), (13, 5)]:
    px[y - 1][x] = POST
    px[y - 2][x] = POST
for x in range(8, 14):
    px[2][x] = POST

def png(rows):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in r) for r in rows)
    def chunk(t, d):
        c = t + d
        return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

with open(sys.argv[1], "wb") as f:
    f.write(png(px))
