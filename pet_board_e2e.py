#!/usr/bin/env python3
"""pet-board E2E：通过业务应用 SSE 代理调用 DSH Agent，验证 5 个工具全链路。"""
import json, subprocess, sys

AGENT = "boarding-copilot"
URL = "http://127.0.0.1:18113/api/assistant/stream"

CASES = [
    ("T1 房型查询", "宠物店寄养有什么房型？标准犬舍多少钱一天？简洁回答", ["标准犬舍", "88"]),
    ("T2 寄养师查询", "宠物店哪位寄养师擅长猫咪？评分多少？简洁回答", ["小满", "猫咪"]),
    ("T3 预约寄养", "我是测试客户林悦，电话13300007777，我家金毛叫旺财，想寄养在宠物店标准犬舍 3 天，周六开始，帮我预约，告诉我订单号和价格", ["P4", "旺财"]),
    ("T4 订单查询", "查一下宠物寄养订单 P4001，谁家的宠物？简洁回答", ["纪先生", "雪球"]),
    ("T5 运营统计", "宠物店今天运营情况怎么样？多少寄养中订单？简洁回答", ["寄养", "营收"]),
]

def ask(message, timeout=170):
    payload = json.dumps({"message": message}, ensure_ascii=False)
    try:
        out = subprocess.run(
            ["curl", "-s", "--noproxy", "*", "-N", "-X", "POST", URL,
             "-H", "Content-Type: application/json", "-d", payload,
             "--max-time", str(timeout)],
            capture_output=True, text=True, timeout=timeout + 10).stdout
    except Exception as e:
        return "", f"curl 异常: {e}"
    text = []
    ev = ""
    for line in out.splitlines():
        line = line.rstrip("\r")
        if line.startswith("event:"):
            ev = line[6:].strip()
        elif line.startswith("data:"):
            s = line[5:].strip()
            if not s or s == "[DONE]" or ev != "chunk":
                continue
            try:
                j = json.loads(s)
                c = j.get("content", "")
                if c:
                    text.append(c)
            except Exception:
                pass
            ev = ""
    return "".join(text), out

def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    cases = CASES if not only else [c for c in CASES if c[0].startswith(only)]
    passed, failed = 0, []
    for name, q, keys in cases:
        reply, raw = ask(q)
        ok = all(k in reply for k in keys)
        print(f"[{'PASS' if ok else 'FAIL'}] {name}\n  Q: {q}\n  A: {reply[:200]}")
        if ok:
            passed += 1
        else:
            failed.append(name)
            if not reply:
                print(f"  raw 首行: {raw.splitlines()[:3] if raw else '(空)'}")
    print(f"\n===== pet-board E2E: {passed}/{len(cases)} PASS =====")
    if failed:
        print("失败用例:", ", ".join(failed))
        sys.exit(1)

if __name__ == "__main__":
    main()
