#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
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

    processes: list[subprocess.Popen[str]] = []
    try:
        velocity_command = ["java", "-Xms384M", "-Xmx384M"]
        if os.environ.get("MCGGRTP_VELOCITY_PACKET_DECODE_LOGGING", "false").lower() == "true":
            velocity_command.append("-Dvelocity.packet-decode-logging=true")
        velocity_command.extend(["-jar", "velocity.jar"])
        processes.append(start_server("velocity", velocity_command, WORK_ROOT / "velocity"))
        for server in paper_servers:
            processes.append(start_server(server.dirname, ["java", "-Xms768M", "-Xmx768M", "-jar", "server.jar", "--nogui"], WORK_ROOT / server.dirname))

        wait_for_port(PROXY_PORT, "proxy")
        wait_for_log(WORK_ROOT / "paper1" / "logs" / "latest.log", "Done (", "paper1")
        wait_for_log(WORK_ROOT / "paper2" / "logs" / "latest.log", "Done (", "paper2")

        install_node_dependencies()
        result = run_bot_stress(bot_count, mode)
        result["serverPerformance"] = collect_server_performance(processes[1:])
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


def collect_server_performance(paper_processes: list[subprocess.Popen[str]]) -> dict:
    for process in paper_processes:
        if process.poll() is None and process.stdin:
            process.stdin.write("tps\n")
            process.stdin.write("mspt\n")
            process.stdin.flush()

    import time
    time.sleep(2)

    performance = {}
    for dirname in ("paper1", "paper2"):
        log_path = WORK_ROOT / dirname / "process.log"
        lines = log_path.read_text(encoding="utf-8", errors="ignore").splitlines()
        tps_line = clean_console_line(last_matching(lines, "TPS from last 1m, 5m, 15m:"))
        mspt_header_index = last_matching_index(lines, "Server tick times")
        mspt_lines = []
        if mspt_header_index is not None:
            for line in lines[mspt_header_index:mspt_header_index + 6]:
                cleaned = clean_console_line(line)
                if cleaned and ("Server tick times" in cleaned or "◴" in cleaned):
                    mspt_lines.append(cleaned)
        performance[dirname] = {
            "tps": tps_line,
            "mspt": " ".join(line for line in mspt_lines if line),
        }
    return performance


def last_matching(lines: list[str], needle: str) -> str | None:
    for line in reversed(lines):
        if needle in line:
            return line
    return None


def last_matching_index(lines: list[str], needle: str) -> int | None:
    for index in range(len(lines) - 1, -1, -1):
        if needle in lines[index]:
            return index
    return None


def clean_console_line(line: str | None) -> str | None:
    if line is None:
        return None
    without_ansi = re.sub(r"\x1b\[[0-9;]*m", "", line)
    without_prompt = without_ansi.replace("\r", "").replace(">", "").strip()
    return re.sub(r"\s+", " ", without_prompt)


if __name__ == "__main__":
    sys.exit(main())
