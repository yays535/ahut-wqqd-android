import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")

def adb(*args, timeout=60):
    return subprocess.run([ADB] + list(args), capture_output=True, text=True,
                          timeout=timeout, encoding="utf-8", errors="replace").stdout

def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml")

def selected():
    xml = dump()
    hour = minute = None
    for m in re.finditer(r'text="(\d\d)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        val, x1, y1, x2, y2 = m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5))
        if y2 - y1 < 45:
            continue
        if x1 < 330:
            hour = int(val)
        else:
            minute = int(val)
    return hour, minute

def swipe(col_x, px):
    # 正数 = 向前（数值变大）：手指向上滑
    if px > 0:
        y0, y1 = 930, max(660, 930 - px)
    else:
        y0, y1 = 660, min(950, 660 - px)
    adb("shell", "input", "swipe", str(col_x), str(y0), str(col_x), str(y1), "700")
    time.sleep(0.8)

def main():
    target_h, target_m = int(sys.argv[1]), int(sys.argv[2])
    for _ in range(60):
        h, mi = selected()
        if h is None or mi is None:
            print("读取失败")
            return 1
        dh = target_h - h
        # 滚轮不是环形列表：只能朝目标方向一路滚过去，不能靠越界回绕
        dm = target_m - mi
        print(f"当前 {h:02d}:{mi:02d} 需要 {dh:+d} 时 {dm:+d} 分")
        if dh == 0 and dm == 0:
            adb("shell", "input", "tap", "574", "1125")
            print("已确认")
            return 0
        if dh != 0:
            swipe(270, max(-280, min(280, dh * 84)))
        elif dm != 0:
            swipe(450, max(-280, min(280, dm * 84)))
    print("超出迭代次数")
    return 1

if __name__ == "__main__":
    sys.exit(main())
