#!/usr/bin/env python3
"""Dump the current Android UI and print nodes as: text | desc | center | flags.

Usage:
    python tools/uiq.py            # all non-empty nodes
    python tools/uiq.py 添加        # filter by substring in text/desc
"""
import os
import re
import subprocess
import sys

ADB = os.environ.get("ADB", "adb")


def adb(*args: str) -> str:
    out = subprocess.run([ADB, *args], capture_output=True)
    return out.stdout.decode("utf-8", "replace")


def main() -> None:
    keyword = sys.argv[1] if len(sys.argv) > 1 else None
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")

    for node in re.findall(r"<node [^>]*>", xml):
        text = re.search(r'text="([^"]*)"', node)
        desc = re.search(r'content-desc="([^"]*)"', node)
        bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if not bounds:
            continue
        text = text.group(1) if text else ""
        desc = desc.group(1) if desc else ""
        if not text and not desc:
            continue
        if keyword and keyword not in text and keyword not in desc:
            continue
        x1, y1, x2, y2 = (int(v) for v in bounds.groups())
        flags = []
        if 'clickable="true"' in node:
            flags.append("click")
        if 'enabled="false"' in node:
            flags.append("DISABLED")
        if 'checked="true"' in node:
            flags.append("checked")
        if 'selected="true"' in node:
            flags.append("selected")
        if 'long-clickable="true"' in node:
            flags.append("long")
        print(f"{(text or '-'):<34} | desc={(desc or '-'):<14} | "
              f"({(x1 + x2) // 2:4d},{(y1 + y2) // 2:4d}) | {' '.join(flags)}")


if __name__ == "__main__":
    main()
