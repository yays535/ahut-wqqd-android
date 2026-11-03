#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""用真实账号走前 5 步（不提交签到），核对每一步的真实返回结构与字段类型。

用法：ID=学号 PWD=密码 python tools/live_check.py
凭据只从环境变量读取，不落盘。
"""
import json
import os
import sys

REPO = os.environ.get("AHUT_REPO", "").strip()
if not REPO:
    print("请先设置 AHUT_REPO 指向原 Python 仓库（ahut_wqqd）所在目录")
    raise SystemExit(1)
sys.path.insert(0, REPO)

import main_sync as m  # noqa: E402


def mask(s, keep=6):
    return (s[:keep] + "…" + s[-4:]) if isinstance(s, str) and len(s) > keep + 6 else s


def main():
    sid = os.environ.get("ID", "").strip()
    pwd = os.environ.get("PWD", "").strip()
    if not sid or not pwd:
        print("缺少 ID / PWD 环境变量")
        return 1

    u = m.User(student_Id=int(sid), password=pwd)

    # step 0：登录
    r0 = m.sign_in_by_step(u, 0, debug=True)
    print("[0] token:", r0.get("success"), "| token=", mask(u.token))
    if not r0.get("success"):
        print("    登录失败：", r0.get("msg"))
        return 1

    # step 1：取 taskId
    status, task = m.http_request_json(
        u, "GET", m.WEB_DICT["task_id_api"],
        headers=m.generate_header(u, m.WEB_DICT["task_id_api"]),
    )
    rec = ((task or {}).get("data") or {}).get("records") or [{}]
    tid = (rec[0] or {}).get("taskId")
    print("[1] http:", status, "| code:", task.get("code"), "| 记录数:", len(rec))
    print("    taskId 类型:", type(tid).__name__, "| 值:", tid)
    print("    taskName:", (rec[0] or {}).get("taskName"))
    print("    时间窗:", (rec[0] or {}).get("signStartTime"), "-", (rec[0] or {}).get("signEndTime"))
    u.taskId = tid

    # step 2：微信配置（URL 里带 taskId）
    url2 = m.WEB_DICT["auth_check_api"].format(TASK_ID=u.taskId, STUDENT_ID=u.student_Id)
    s2, r2 = m.http_request_json(u, "GET", url2, headers=m.generate_header(u, url2))
    print("[2] 微信配置 http:", s2, "| code:", r2.get("code"), "| msg:", r2.get("msg"))

    # step 3：开时间窗
    s3, r3 = m.http_request_json(
        u, "POST", m.WEB_DICT["apiLog_api"],
        headers=m.generate_header(u, m.WEB_DICT["apiLog_api"]),
    )
    print("[3] 开时间窗 http:", s3, "| body:", json.dumps(r3, ensure_ascii=False)[:80])

    # step 4：宿舍位置
    url4 = m.WEB_DICT["get_location_api"].format(
        TASK_ID=u.taskId, date_str=m.get_time()["date"]
    )
    s4, r4 = m.http_request_json(u, "GET", url4, headers=m.generate_header(u, url4))
    vo = (r4.get("data") or {}).get("dormitoryRegisterVO") or {}
    print("[4] 位置 http:", s4, "| code:", r4.get("code"))
    print("    locationLat:", vo.get("locationLat"), "locationLng:", vo.get("locationLng"))
    print("    roomId:", vo.get("roomId"))
    u.latitude = float(vo.get("locationLat") or 0)
    u.longitude = float(vo.get("locationLng") or 0)
    u.room_id = vo.get("roomId") or ""

    # 不提交签到，只把将要发送的 stuTaskId 用固定坐标算出来，供 Kotlin 侧对齐
    lat, lng, acc = 31.716345, 117.120021, 28.47
    js = json.dumps({
        "latitude": str(lat),
        "longitude": str(lng),
        "locationAccuracy": str(acc),
        "signDate": m.get_time()["date"],
        "taskId": u.taskId,
        "fileId": "",
    }, separators=(",", ":"))
    print("[5] stuTaskId 输入 JSON:", js)
    print("    期望摘要:", m.generate_stuTaskId(lat, lng, acc, m.get_time()["date"], u.taskId))
    print("    提交体 taskId 片段:", json.dumps({"taskId": u.taskId}, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
