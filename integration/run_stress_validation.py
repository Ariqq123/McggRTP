#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import hashlib
from pathlib import Path

from run_live_validation import (
    PAPER1_PORT,
    PAPER2_PORT,
    PROBE_PAPER,
    PROXY_PORT,
    ROOT,
    WORK_ROOT,
    PaperServerSpec,
    audit_logs,
    install_node_dependencies,
    prepare_paper_server,
    prepare_velocity,
    start_server,
    stop_processes,
    wait_for_log,
    wait_for_port,
)


def main() -> int:
    if not PROBE_PAPER.exists():
        raise SystemExit("probe-paper bootstrap is missing")

    bot_count = int(os.environ.get("MCGGRTP_STRESS_BOT_COUNT", "50"))
    mode = os.environ.get("MCGGRTP_STRESS_MODE", "local")

    if WORK_ROOT.exists():
        shutil.rmtree(WORK_ROOT)
    WORK_ROOT.mkdir(parents=True)

    prepare_velocity()
    paper_servers = [
        PaperServerSpec("paper1", "survival-1", PAPER1_PORT, "S1"),
        PaperServerSpec("paper2", "survival-2", PAPER2_PORT, "S2"),
    ]
    for server in paper_servers:
        prepare_paper_server(server)
        write_ops_file(WORK_ROOT / server.dirname / "ops.json", bot_count)

    processes: list[subprocess.Popen[str]] = []
    try:
        processes.append(start_server("velocity", ["java", "-Xms384M", "-Xmx384M", "-jar", "velocity.jar"], WORK_ROOT / "velocity"))
        for server in paper_servers:
            processes.append(start_server(server.dirname, ["java", "-Xms768M", "-Xmx768M", "-jar", "server.jar", "--nogui"], WORK_ROOT / server.dirname))

        wait_for_port(PROXY_PORT, "proxy")
        wait_for_log(WORK_ROOT / "paper1" / "logs" / "latest.log", "Done (", "paper1")
        wait_for_log(WORK_ROOT / "paper2" / "logs" / "latest.log", "Done (", "paper2")

        install_node_dependencies()
        result = run_bot_stress(bot_count, mode)
        result["logAudit"] = audit_logs()
        report_path = WORK_ROOT / "stress-report.json"
        report_path.write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(json.dumps(result, indent=2))
        return 0 if result.get("ok") else 1
    finally:
        stop_processes(processes)


def run_bot_stress(bot_count: int, mode: str) -> dict:
    completed = subprocess.run(
        ["node", "rtp_stress.js"],
        cwd=ROOT / "integration",
        check=False,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "MCGGRTP_PROXY_HOST": "127.0.0.1",
            "MCGGRTP_PROXY_PORT": str(PROXY_PORT),
            "MCGGRTP_STRESS_BOT_COUNT": str(bot_count),
            "MCGGRTP_STRESS_MODE": mode,
        },
    )
    lines = [line for line in completed.stdout.splitlines() if line.strip()]
    if not lines:
        raise RuntimeError(f"Node stress runner produced no output.\nSTDERR:\n{completed.stderr}")
    result = json.loads(lines[-1])
    if completed.stderr:
        result["stderr"] = completed.stderr
    result["exitCode"] = completed.returncode
    return result


def write_ops_file(path: Path, bot_count: int) -> None:
    ops = [
        {
            "uuid": offline_uuid(f"Stress{index:02d}"),
            "name": f"Stress{index:02d}",
            "level": 4,
            "bypassesPlayerLimit": False,
        }
        for index in range(1, bot_count + 1)
    ]
    path.write_text(json.dumps(ops, indent=2), encoding="utf-8")


def offline_uuid(username: str) -> str:
    digest = bytearray(hashlib.md5(("OfflinePlayer:" + username).encode("utf-8")).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    hexed = digest.hex()
    return f"{hexed[:8]}-{hexed[8:12]}-{hexed[12:16]}-{hexed[16:20]}-{hexed[20:32]}"


if __name__ == "__main__":
    sys.exit(main())
