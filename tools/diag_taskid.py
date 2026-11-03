"""一次性诊断脚本：用 Python 侧实现复现 App 的「解析不到 taskId」问题。

目的只有一个——看清服务端第 2 步到底返回了什么结构。
输出写入 diag.json（含个人信息，分析完即删）。
"""
import io
import json
import os
import sys

REPO = os.environ.get("AHUT_REPO", "").strip()
if not REPO:
    print("请先设置 AHUT_REPO 指向原 Python 仓库（ahut_wqqd）所在目录")
    raise SystemExit(1)
sys.path.insert(0, REPO)
import main_sync as m  # noqa: E402

STUDENT_ID = int(os.environ.get("ID", "0") or 0)
if not STUDENT_ID:
    print("请先设置 ID 环境变量为学号")
    raise SystemExit(1)

out = {}
u = m.User(student_Id=STUDENT_ID)

# step 0：登录换 token（debug=True 只绕过本地时间判断，服务端仍然会拦）
out["step0"] = m.sign_in_by_step(u, 0, debug=True)
out["token_len"] = len(u.token or "")

if u.token:
    url = m.WEB_DICT["task_id_api"]
    status, res = m.http_request_json(u, "GET", url, headers=m.generate_header(u, url))
    out["http_status"] = status
    out["task_result"] = res
    # 把 records 的结构单独摘出来，方便对比
    data = res.get("data") if isinstance(res, dict) else None
    out["data_type"] = type(data).__name__
    if isinstance(data, dict):
        out["data_keys"] = list(data.keys())
        recs = data.get("records")
        out["records_type"] = type(recs).__name__
        out["records_len"] = len(recs) if isinstance(recs, list) else None
        if isinstance(recs, list) and recs:
            out["first_record_keys"] = list(recs[0].keys()) if isinstance(recs[0], dict) else type(recs[0]).__name__
            out["first_taskId"] = recs[0].get("taskId") if isinstance(recs[0], dict) else None

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir, "diag.json")
with io.open(OUT, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=2)

print("DONE")
