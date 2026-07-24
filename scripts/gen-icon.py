#!/usr/bin/env python3
# gen_icon.py — jw-gasul 파비콘/앱아이콘 래스터화 스크립트(외부 라이브러리 없이 PNG/ICO 생성).
# SVG와 동일한 기하(비계 프레임 마크)를 SDF로 그려 4x 슈퍼샘플링 후 다운샘플한다.
import math
import struct
import zlib

# 색상 (Tailwind slate-800 / teal-400) — 네비 배경과 대시보드 강조색에 맞춤
BG = (30, 41, 59)       # #1e293b
FG = (45, 212, 191)     # #2dd4bf

VB = 64.0               # 기준 viewBox
RADIUS = 14.0           # 배경 라운드 코너
STROKE = 5.0            # 마크 선 두께
SS = 4                  # 슈퍼샘플 배율

# 비계 프레임: 수직 기둥 2 + 수평 발판 2 + 대각 가새 1 (favicon.svg와 동일 기하)
SEGMENTS = [
    ((20, 12), (20, 52)),   # 왼쪽 기둥
    ((44, 12), (44, 52)),   # 오른쪽 기둥
    ((13, 24), (51, 24)),   # 위 발판 (양쪽으로 살짝 돌출)
    ((13, 42), (51, 42)),   # 아래 발판
    ((20, 42), (44, 24)),   # 대각 가새
]

# 16px 이하 전용 단순화 기하 — 대각 가새를 빼고 선을 굵혀 뭉개짐을 막는다
SEGMENTS_SMALL = [
    ((20, 12), (20, 52)),
    ((44, 12), (44, 52)),
    ((12, 24), (52, 24)),
    ((12, 42), (52, 42)),
]
STROKE_SMALL = 7.0
SMALL_MAX = 16          # 이 크기 이하는 단순화 기하 사용


def sd_round_rect(px, py, half, r):
    """중심 기준 라운드 사각형의 부호거리(음수=내부)"""
    qx = abs(px) - (half - r)
    qy = abs(py) - (half - r)
    return math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - r


def sd_segment(px, py, a, b):
    """선분까지의 거리(round cap 캡슐용)"""
    ax, ay = a
    bx, by = b
    vx, vy = px - ax, py - ay
    wx, wy = bx - ax, by - ay
    denom = wx * wx + wy * wy
    t = 0.0 if denom == 0 else max(0.0, min(1.0, (vx * wx + vy * wy) / denom))
    return math.hypot(vx - wx * t, vy - wy * t)


def render(size):
    """size x size RGBA 픽셀 바이트를 만든다(슈퍼샘플 후 평균). 작은 크기는 단순화 기하 사용"""
    small = size <= SMALL_MAX
    segments = SEGMENTS_SMALL if small else SEGMENTS
    stroke = STROKE_SMALL if small else STROKE
    n = size * SS
    scale = VB / n
    half = VB / 2.0
    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            r = g = b = a = 0
            for sy in range(SS):
                for sx in range(SS):
                    ux = (x * SS + sx + 0.5) * scale
                    uy = (y * SS + sy + 0.5) * scale
                    if sd_round_rect(ux - half, uy - half, half, RADIUS) > 0:
                        continue  # 배경 라운드 사각형 밖 = 투명
                    mark = min(sd_segment(ux, uy, p, q) for p, q in segments) <= stroke / 2.0
                    c = FG if mark else BG
                    r += c[0]
                    g += c[1]
                    b += c[2]
                    a += 255
            total = SS * SS
            k = a // 255  # 커버된 샘플 수
            if k == 0:
                row += bytes(4)
            else:
                # 색은 커버된 샘플 평균, 알파는 전체 샘플 대비 커버율(스트레이트 알파)
                row += bytes((r // k, g // k, b // k, a // total))
        rows.append(bytes(row))
    return rows


def png_bytes(rows, width, height=None):
    """RGBA 행 배열을 PNG 바이트로 인코딩(정사각이면 height 생략 가능)"""
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height or width, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


# 메신저 링크 미리보기(Open Graph) 카드 이미지 규격 — 1200x630이 사실상 표준
OG_W, OG_H, OG_MARK = 1200, 630, 384
OG_BG = (15, 23, 42)    # slate-900 — 아이콘 배경(slate-800)보다 어둡게 깔아 대비를 준다


def render_og():
    """OG 카드 이미지: 어두운 배경 중앙에 앱 아이콘을 얹은 1200x630 RGBA 행 배열"""
    mark = render(OG_MARK)
    ox, oy = (OG_W - OG_MARK) // 2, (OG_H - OG_MARK) // 2
    rows = []
    for y in range(OG_H):
        row = bytearray()
        for x in range(OG_W):
            r, g, b = OG_BG
            if oy <= y < oy + OG_MARK and ox <= x < ox + OG_MARK:
                i = (x - ox) * 4
                src = mark[y - oy]
                a = src[i + 3]
                if a:  # 아이콘 알파로 배경 위에 합성
                    r = (src[i] * a + r * (255 - a)) // 255
                    g = (src[i + 1] * a + g * (255 - a)) // 255
                    b = (src[i + 2] * a + b * (255 - a)) // 255
            row += bytes((r, g, b, 255))
        rows.append(bytes(row))
    return rows


def ico_bytes(entries):
    """[(size, png)] 목록을 ICO 컨테이너로 묶는다(PNG 임베드 방식)"""
    header = struct.pack("<HHH", 0, 1, len(entries))
    offset = 6 + 16 * len(entries)
    dirs, blobs = b"", b""
    for size, png in entries:
        dirs += struct.pack("<BBBBHHII", size & 0xFF, size & 0xFF, 0, 0, 1, 32, len(png), offset)
        offset += len(png)
        blobs += png
    return header + dirs + blobs


if __name__ == "__main__":
    import sys
    out = sys.argv[1].rstrip("/")
    pngs = {}
    for s in (16, 32, 48, 180, 192, 512):
        pngs[s] = png_bytes(render(s), s)
        print(f"rendered {s}x{s} ({len(pngs[s])} bytes)")
    for s in (180, 192, 512):
        with open(f"{out}/icon-{s}.png", "wb") as f:
            f.write(pngs[s])
    with open(f"{out}/favicon.ico", "wb") as f:
        f.write(ico_bytes([(s, pngs[s]) for s in (16, 32, 48)]))
    with open(f"{out}/og-image.png", "wb") as f:
        f.write(png_bytes(render_og(), OG_W, OG_H))
    print(f"rendered og-image {OG_W}x{OG_H}")
    print("done")
