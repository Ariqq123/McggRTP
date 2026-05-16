#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import shutil
import socket
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parent.parent
WORK_ROOT = ROOT / ".integration" / "runtime"
DOWNLOADS = ROOT / ".integration" / "downloads"
PROBE_PAPER = ROOT / ".integration" / "probe-paper"

PROXY_PORT = 25575
PAPER1_PORT = 25566
PAPER2_PORT = 25567
OFFLINE_PORT = 25568

SECRET = "mcggrtp-integration-secret"

PAPER_MESSAGE_TEMPLATE = {
    "prefix": "&8[&aMcggRTP&8] ",
    "no-permission": "&cYou do not have permission.",
    "cooldown": "&cYou must wait &e{time}&c before using RTP again.",
    "teleport-failed": "&cCould not find a safe location. Try again later.",
    "sending-server": "&7Sending you to &e{server}&7...",
    "server-offline": "&cThat server is offline.",
    "world-unavailable": "&cThat world is not available for RTP.",
    "reload-local": "&aPaper config reloaded.",
    "reload-started": "&7Reloading proxy config...",
    "reload-complete": "&aReload complete.",
    "reload-failed": "&cReload failed.",
    "server-player-count": "&7Players online: &f{count}",
    "server-click": "&7Click to RTP via &f{server}",
    "server-locked": "&cYou do not have permission for this server.",
    "server-unavailable-lore": "&cThis server is offline.",
    "server-open-lore": "&aAvailable now.",
    "dimension-locked": "&cYou do not have permission for this dimension.",
}


@dataclass(frozen=True)
class PaperServerSpec:
    dirname: str
    server_id: str
    port: int
    tag: str


def main() -> int:
    if not PROBE_PAPER.exists():
        raise SystemExit("probe-paper bootstrap is missing")

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
        processes.append(start_server("velocity", ["java", "-Xms256M", "-Xmx256M", "-jar", "velocity.jar"], WORK_ROOT / "velocity"))
        for server in paper_servers:
            processes.append(start_server(server.dirname, ["java", "-Xms512M", "-Xmx512M", "-jar", "server.jar", "--nogui"], WORK_ROOT / server.dirname))

        wait_for_port(PROXY_PORT, "proxy")
        wait_for_log(WORK_ROOT / "paper1" / "logs" / "latest.log", "Done (", "paper1")
        wait_for_log(WORK_ROOT / "paper2" / "logs" / "latest.log", "Done (", "paper2")

        install_node_dependencies()
        result = run_bot_validation()
        result["logAudit"] = audit_logs()
        report_path = WORK_ROOT / "validation-report.json"
        report_path.write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(json.dumps(result, indent=2))
        return 0 if result.get("ok") else 1
    finally:
        stop_processes(processes)


def prepare_velocity() -> None:
    velocity_dir = WORK_ROOT / "velocity"
    velocity_dir.mkdir(parents=True)
    shutil.copy2(DOWNLOADS / "velocity-3.4.0-SNAPSHOT-559.jar", velocity_dir / "velocity.jar")
    (velocity_dir / "forwarding.secret").write_text(SECRET, encoding="utf-8")
    (velocity_dir / "velocity.toml").write_text(
        f"""
config-version = "2.7"
bind = "127.0.0.1:{PROXY_PORT}"
motd = "<green>McggRTP Integration"
show-max-players = 16
online-mode = false
force-key-authentication = false
prevent-client-proxy-connections = false
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"
announce-forge = false
kick-existing-players = false
ping-passthrough = "DISABLED"
sample-players-in-ping = false
enable-player-address-logging = true

[servers]
survival-1 = "127.0.0.1:{PAPER1_PORT}"
survival-2 = "127.0.0.1:{PAPER2_PORT}"
survival-3 = "127.0.0.1:{OFFLINE_PORT}"
try = ["survival-1"]

[forced-hosts]

[advanced]
compression-threshold = 256
compression-level = -1
login-ratelimit = 0
connection-timeout = 3000
read-timeout = 30000
haproxy-protocol = false
tcp-fast-open = false
bungee-plugin-message-channel = true
show-ping-requests = false
failover-on-unexpected-server-disconnect = true
announce-proxy-commands = true
log-command-executions = false
log-player-connections = true
accepts-transfers = false
enable-reuse-port = false
command-rate-limit = 0
forward-commands-if-rate-limited = true
kick-after-rate-limited-commands = 0
tab-complete-rate-limit = 0
kick-after-rate-limited-tab-completes = 0

[query]
enabled = false
port = {PROXY_PORT}
map = "Velocity"
show-plugins = false
""".strip()
        + "\n",
        encoding="utf-8",
    )

    plugin_dir = velocity_dir / "plugins" / "mcggrtp"
    plugin_dir.mkdir(parents=True)
    shutil.copy2(ROOT / "velocity" / "build" / "libs" / "McggRTP-velocity.jar", velocity_dir / "plugins" / "McggRTP-velocity.jar")
    write_yaml(plugin_dir / "config.yml", velocity_plugin_config())


def prepare_paper_server(server: PaperServerSpec) -> None:
    target = WORK_ROOT / server.dirname
    shutil.copytree(PROBE_PAPER, target, dirs_exist_ok=True)
    for path in [target / "logs", target / "plugins", target / "cache", target / ".console_history"]:
        if path.is_file():
            path.unlink()
        elif path.is_dir():
            shutil.rmtree(path)

    shutil.copy2(DOWNLOADS / "paper-1.21.5-114.jar", target / "server.jar")
    (target / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (target / "server.properties").write_text(
        f"""
server-port={server.port}
server-ip=127.0.0.1
online-mode=false
enforce-secure-profile=false
motd={server.server_id}
spawn-protection=0
view-distance=6
simulation-distance=4
""".lstrip(),
        encoding="utf-8",
    )

    paper_global_path = target / "config" / "paper-global.yml"
    with paper_global_path.open("r", encoding="utf-8") as handle:
        paper_global = yaml.safe_load(handle)
    paper_global["proxies"]["velocity"]["enabled"] = True
    paper_global["proxies"]["velocity"]["online-mode"] = False
    paper_global["proxies"]["velocity"]["secret"] = SECRET
    with paper_global_path.open("w", encoding="utf-8") as handle:
        yaml.safe_dump(paper_global, handle, sort_keys=False)

    plugin_root = target / "plugins"
    plugin_root.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "paper" / "build" / "libs" / "McggRTP-paper.jar", plugin_root / "McggRTP-paper.jar")
    data_dir = plugin_root / "McggRTP-Paper"
    data_dir.mkdir(parents=True, exist_ok=True)
    write_yaml(data_dir / "config.yml", paper_plugin_config(server.server_id))
    write_yaml(data_dir / "messages.yml", paper_messages(server.tag))


def velocity_plugin_config() -> dict:
    return {
        "settings": {
            "plugin-message-channel": "mcggrtp:main",
            "pending-expire-seconds": 5,
        },
        "cooldowns": {
            "enabled": True,
            "default-seconds": 3,
            "bypass-permission": "rtp.bypass.cooldown",
        },
        "servers": network_servers(),
        "dimensions": network_dimensions(["survival-1"], ["survival-1", "survival-2", "survival-3"]),
    }


def paper_plugin_config(server_id: str) -> dict:
    return {
        "gui": {
            "title": "&8Random Teleport",
            "size": 27,
            "filler": {
                "enabled": True,
                "material": "BLACK_STAINED_GLASS_PANE",
                "name": " ",
            },
        },
        "main-menu": {
            "overworld": dimension_option(11, "&aOverworld", "GRASS_BLOCK", "world", "rtp.dimension.overworld", "&7Click to choose an overworld server."),
            "nether": dimension_option(13, "&cNether", "NETHERRACK", "world_nether", "rtp.dimension.nether", "&7Click to choose a nether server."),
            "end": dimension_option(15, "&dThe End", "END_STONE", "world_the_end", "rtp.dimension.end", "&7Click to choose an end server."),
        },
        "server-menu": {
            "title": "&8Choose Server: {dimension}",
            "size": 27,
            "online-material": "LIME_WOOL",
            "offline-material": "BARRIER",
        },
        "sounds": {
            "menu-open": "BLOCK_NOTE_BLOCK_PLING",
            "menu-click": "BLOCK_NOTE_BLOCK_PLING",
            "denied": "ENTITY_VILLAGER_NO",
            "teleport-success": "ENTITY_ENDERMAN_TELEPORT",
        },
        "network": {
            "current-server": server_id,
            "dimensions": network_dimensions(["survival-1"], ["survival-1", "survival-2", "survival-3"]),
            "servers": {
                server_key: {
                    "display-name": server_value["display-name"],
                    "permission": server_value["permission"],
                }
                for server_key, server_value in network_servers().items()
            },
        },
        "rtp": {
            "cooldown-seconds": 3,
            "worlds": {
                "world": world_settings(128, 16, ["LAVA", "FIRE", "CACTUS", "MAGMA_BLOCK"]),
                "world_nether": world_settings(32, 32, ["LAVA", "FIRE", "MAGMA_BLOCK"], avoid_bedrock_roof=True),
                "world_the_end": world_settings(48, 24, []),
            },
        },
    }


def paper_messages(server_tag: str) -> dict:
    return {
        **PAPER_MESSAGE_TEMPLATE,
        "searching": f"&7[{server_tag}] Searching for a safe location...",
        "teleport-success": f"&a[{server_tag}] Teleported you to a random location.",
    }


def network_servers() -> dict:
    return {
        "survival-1": {"display-name": "&aSurvival 1", "enabled": True, "permission": "rtp.server.survival1"},
        "survival-2": {"display-name": "&aSurvival 2", "enabled": True, "permission": "rtp.server.survival2"},
        "survival-3": {"display-name": "&aSurvival 3", "enabled": True, "permission": "rtp.server.survival3"},
    }


def network_dimensions(single_server_dimension: list[str], overworld_servers: list[str]) -> dict:
    return {
        "overworld": {"servers": overworld_servers},
        "nether": {"servers": single_server_dimension},
        "end": {"servers": single_server_dimension},
    }


def dimension_option(slot: int, display_name: str, material: str, world_name: str, permission: str, lore_line: str) -> dict:
    return {
        "slot": slot,
        "display-name": display_name,
        "material": material,
        "world-name": world_name,
        "permission": permission,
        "lore": [lore_line],
    }


def world_settings(radius: int, max_attempts: int, unsafe_blocks: list[str], avoid_bedrock_roof: bool = False) -> dict:
    settings = {
        "enabled": True,
        "center-x": 0,
        "center-z": 0,
        "min-radius": radius,
        "max-radius": radius,
        "max-attempts": max_attempts,
    }
    if unsafe_blocks:
        settings["unsafe-blocks"] = unsafe_blocks
    else:
        settings["blacklisted-biomes"] = []
    if avoid_bedrock_roof:
        settings["avoid-bedrock-roof"] = True
    return settings


def write_yaml(path: Path, content: dict) -> None:
    with path.open("w", encoding="utf-8") as handle:
        yaml.safe_dump(content, handle, sort_keys=False)


def start_server(name: str, command: list[str], cwd: Path) -> subprocess.Popen[str]:
    log_path = cwd / "process.log"
    log_handle = log_path.open("w", encoding="utf-8")
    process = subprocess.Popen(
        command,
        cwd=cwd,
        stdin=subprocess.PIPE,
        stdout=log_handle,
        stderr=subprocess.STDOUT,
        text=True,
    )
    process._mcggrtp_log_handle = log_handle  # type: ignore[attr-defined]
    process._mcggrtp_name = name  # type: ignore[attr-defined]
    return process


def wait_for_port(port: int, label: str, timeout: float = 60.0) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        with socket.socket() as sock:
            sock.settimeout(0.5)
            if sock.connect_ex(("127.0.0.1", port)) == 0:
                return
        time.sleep(0.25)
    raise TimeoutError(f"Timed out waiting for {label} port {port}")


def wait_for_log(path: Path, needle: str, label: str, timeout: float = 120.0) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if path.exists() and needle in path.read_text(encoding="utf-8", errors="ignore"):
            return
        time.sleep(0.5)
    raise TimeoutError(f"Timed out waiting for {label} log marker {needle!r}")


def install_node_dependencies() -> None:
    if not (ROOT / "integration" / "node_modules").exists():
        subprocess.run(["npm", "install", "--silent"], cwd=ROOT / "integration", check=True)


def run_bot_validation() -> dict:
    completed = subprocess.run(
        ["node", "live_validation.js"],
        cwd=ROOT / "integration",
        check=False,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "MCGGRTP_PROXY_HOST": "127.0.0.1",
            "MCGGRTP_PROXY_PORT": str(PROXY_PORT),
            "MCGGRTP_BOT_NAME": "TestBot",
        },
    )
    lines = [line for line in completed.stdout.splitlines() if line.strip()]
    if not lines:
        raise RuntimeError(f"Node validator produced no output.\nSTDERR:\n{completed.stderr}")
    result = json.loads(lines[-1])
    if completed.stderr:
        result["stderr"] = completed.stderr
    result["exitCode"] = completed.returncode
    return result


def audit_logs() -> dict:
    velocity_log = (WORK_ROOT / "velocity" / "process.log").read_text(encoding="utf-8", errors="ignore")
    paper1_log = (WORK_ROOT / "paper1" / "process.log").read_text(encoding="utf-8", errors="ignore")
    paper2_log = (WORK_ROOT / "paper2" / "process.log").read_text(encoding="utf-8", errors="ignore")
    return {
        "paper1Join": "TestBot joined the game" in paper1_log,
        "sameServerCommands": paper1_log.count("TestBot issued server command: /rtp"),
        "netherAdvancement": "We Need to Go Deeper" in paper1_log,
        "endAdvancement": "The End?" in paper1_log,
        "proxyConnectedToSurvival2": "TestBot -> survival-2 has connected" in velocity_log,
        "paper2SawPlayer": "UUID of player TestBot" in paper2_log,
    }


def stop_processes(processes: list[subprocess.Popen[str]]) -> None:
    for process in reversed(processes):
        if process.poll() is None and process.stdin:
            try:
                process.stdin.write("stop\n")
                process.stdin.flush()
            except OSError:
                pass

    deadline = time.time() + 30
    for process in processes:
        while process.poll() is None and time.time() < deadline:
            time.sleep(0.25)
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
        log_handle = getattr(process, "_mcggrtp_log_handle", None)
        if log_handle is not None:
            log_handle.close()


if __name__ == "__main__":
    sys.exit(main())
