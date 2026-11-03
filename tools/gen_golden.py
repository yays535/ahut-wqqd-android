# -*- coding: utf-8 -*-
"""
从 Python 参考实现生成「黄金测试向量」，供 Kotlin 侧逐字节对齐。

用法：python tools/gen_golden.py <path-to-ahut_wqqd-repo> <output-json>
原理：直接 import 原仓库的 main_sync.py，调用其签名函数，把输入与期望输出落成 JSON。
"""

import json
import os
import sys
from datetime import datetime, timedelta, timezone


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    repo, out_path = sys.argv[1], sys.argv[2]
    sys.path.insert(0, os.path.abspath(repo))

    import main_sync as core

    bj = timezone(timedelta(hours=8))

    def ts(y, mo, d, h, mi, s):
        return int(datetime(y, mo, d, h, mi, s, tzinfo=bj).timestamp() * 1000)

    vectors = {}

    # 1) 密码 MD5
    pwds = ["Ahgydx@920", "", "123456", "p@ss word", "中文密码测试",
            "a" * 100, "Pwd!@#$%^&*()_+-=[]{}", "demo-password"]
    vectors["password_md5"] = [
        {"pwd": p, "expected": core.password_md5(p)} for p in pwds
    ]

    # 2) URL 签名 generate_sign
    # 该函数内部用 time.time() 取当前毫秒，结果不可复现。
    # 这里把 time.time 临时钉死到某个整秒，使 md5 链完全确定；
    # 同时仍走 main_sync 的真实实现，而不是在本脚本里重写算法。
    import time as _time

    fixed_ts_ms = 1793615951000
    original_time = _time.time
    _time.time = lambda: fixed_ts_ms / 1000.0
    try:
        urls = [
            core.WEB_DICT["task_id_api"],
            core.WEB_DICT["sign_in_api"],
            core.WEB_DICT["apiLog_api"],
            core.WEB_DICT["sign_in_result_api"].format(TASK_ID=1893, date_str="2026-11-02"),
            "https://xskq.ahut.edu.cn/api/flySource-yxgl/dormSignTask/getTaskByIdForApp?taskId=1&signDate=2026-11-02",
        ]
        tokens = ["eyJhbGciOiJIUzI1NiJ9.test.token", "a", "x" * 200,
                  "cf7d8e9a-1234-5678-9abc-def012345678"]
        sign_vectors = []
        for u in urls:
            for t in tokens:
                sign_vectors.append({
                    "url": u, "token": t, "ts_ms": fixed_ts_ms,
                    "expected": core.generate_sign(u, t),
                })
    finally:
        _time.time = original_time
    vectors["generate_sign"] = sign_vectors

    # 3) 签到 signCode（时间串签名）：覆盖周一/周日、月末月初、闰日、跨年
    stamps = [
        ts(2026, 11, 2, 18, 26, 41),
        ts(2026, 11, 2, 21, 30, 40),
        ts(2026, 1, 1, 0, 0, 0),
        ts(2026, 12, 31, 23, 59, 59),
        ts(2028, 2, 29, 12, 0, 0),
        ts(2026, 3, 1, 0, 0, 1),
        ts(2026, 10, 5, 7, 8, 9),
        ts(2027, 6, 30, 21, 30, 0),
    ]
    vectors["sign_code"] = [
        {"ts_ms": s, "expected": core.generate_signCode(s)} for s in stamps
    ]

    # 4) stuTaskId（紧凑 JSON + 浮点字符串格式）
    # 含两类：直接给的坐标；以及模拟「宿舍坐标 + 随机偏移」后产生的浮点和
    sums = []
    for base, jit in [(31.2345678, 0.0034567), (31.2345678, -0.0034567),
                      (31.2345, 0.0000001), (31.2, 0.0099999)]:
        sums.append((base + round(jit, 6), 117.1234567 + round(-jit, 6), round(28.34, 2)))
    pairs = [
        (31.234567, 117.123456, 28.34),
        (0.0, 0.0, 25.0),
        (31.2, 117.0, 30.0),
        (31.2380245, 117.1265432, 27.5),
        (-0.5, 179.999999, 35.0),
    ] + sums
    # 学校的 taskId 是 32 位十六进制字符串（实测 ebf794a5...），不是数字，
    # 所以签名 JSON 里必须是 "taskId":"..." 带引号的形式。
    task_ids = ["ebf794a5c9f6bff2575114ada2339658", "1893", ""]
    vectors["stu_task_id"] = [
        {
            "lat": repr(lat), "lng": repr(lng), "acc": repr(acc),
            "date": "2026-11-02", "taskId": tid, "fileId": "",
            "expect_strs": [str(lat), str(lng), str(acc)],
            "expected": core.generate_stuTaskId(lat, lng, acc, "2026-11-02", tid),
        }
        for lat, lng, acc in pairs
        for tid in task_ids
    ]

    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(vectors, f, ensure_ascii=False, indent=1)
    print(f"已写入 {out_path}")
    print(f"  密码 {len(vectors['password_md5'])} 条 / URL签名 {len(vectors['generate_sign'])} 条 / "
          f"signCode {len(vectors['sign_code'])} 条 / stuTaskId {len(vectors['stu_task_id'])} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
