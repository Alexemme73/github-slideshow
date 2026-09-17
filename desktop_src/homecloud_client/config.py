from __future__ import annotations

import json
import os
import platform
import socket
import uuid
from pathlib import Path
from threading import RLock
from typing import Any

from . import APP_NAME, VERSION


def app_data_dir() -> Path:
    system = platform.system().lower()
    if system == "windows":
        base = Path(os.environ.get("LOCALAPPDATA") or Path.home() / "AppData" / "Local")
        path = base / "HomeCloudClient"
    else:
        base = Path(os.environ.get("XDG_CONFIG_HOME") or Path.home() / ".config")
        path = base / "homecloud-client"
    path.mkdir(parents=True, exist_ok=True)
    try:
        path.chmod(0o700)
    except OSError:
        pass
    return path


CONFIG_PATH = app_data_dir() / "config.json"
DB_PATH = app_data_dir() / "sync_state.sqlite3"
LOG_PATH = app_data_dir() / "homecloud-client.log"


DEFAULTS: dict[str, Any] = {
    "version": VERSION,
    "server_url": "",
    "token": "",
    "username": "",
    "display_name": "",
    "device_id": "",
    "device_name": "",
    "auto_sync": True,
    "wifi_only": True,
    "sync_photos": True,
    "sync_videos": True,
    "photos_path": "",
    "videos_path": "",
    "custom_folders": [],
    "min_battery_percent": 25,
    "sync_interval_minutes": 15,
    "theme": "system",
    "start_with_os": False,
    "paused": False,
    "last_sync_ts": 0.0,
    "last_status": "Pronto per la sincronizzazione",
    "last_error": "",
    "last_uploaded": 0,
    "last_checked": 0,
}


class ConfigStore:
    def __init__(self, path: Path = CONFIG_PATH):
        self.path = path
        self._lock = RLock()
        self._data = self._read()
        changed = False
        if not self._data.get("device_id"):
            self._data["device_id"] = f"desktop-{uuid.uuid4()}"
            changed = True
        if not self._data.get("device_name"):
            self._data["device_name"] = f"{socket.gethostname()} · {platform.system()}"
            changed = True
        if changed:
            self.save()

    def _read(self) -> dict[str, Any]:
        data = dict(DEFAULTS)
        try:
            if self.path.exists():
                loaded = json.loads(self.path.read_text(encoding="utf-8"))
                if isinstance(loaded, dict):
                    data.update(loaded)
        except Exception:
            pass
        if not isinstance(data.get("custom_folders"), list):
            data["custom_folders"] = []
        try:
            data["min_battery_percent"] = max(5, min(80, int(data.get("min_battery_percent", 25))))
        except Exception:
            data["min_battery_percent"] = 25
        try:
            data["sync_interval_minutes"] = int(data.get("sync_interval_minutes", 15))
        except Exception:
            data["sync_interval_minutes"] = 15
        return data

    def save(self) -> None:
        with self._lock:
            self._data["version"] = VERSION
            temp = self.path.with_suffix(".tmp")
            temp.write_text(json.dumps(self._data, ensure_ascii=False, indent=2), encoding="utf-8")
            try:
                temp.chmod(0o600)
            except OSError:
                pass
            temp.replace(self.path)
            try:
                self.path.chmod(0o600)
            except OSError:
                pass

    def get(self, key: str, default: Any = None) -> Any:
        with self._lock:
            return self._data.get(key, default)

    def set(self, key: str, value: Any, save: bool = True) -> None:
        with self._lock:
            self._data[key] = value
        if save:
            self.save()

    def update(self, values: dict[str, Any], save: bool = True) -> None:
        with self._lock:
            self._data.update(values)
        if save:
            self.save()

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            return json.loads(json.dumps(self._data))

    def clear_account(self) -> None:
        with self._lock:
            for key in ("token", "username", "display_name"):
                self._data[key] = ""
            self._data["last_status"] = "Account disconnesso"
            self._data["last_error"] = ""
        self.save()
