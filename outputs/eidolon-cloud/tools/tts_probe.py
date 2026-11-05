"""DNS/TCP/TLS-only Fish reachability probe: never sends a Fish HTTP request/key."""

import argparse
import http.client
import json
import socket
import ssl
import time
from urllib.parse import urlsplit

HOST = "api.fish.audio"


def probe(proxy=None):
    report = {
        "target": HOST,
        "paidRequest": False,
        "route": "explicit-proxy" if proxy else "direct",
        "stages": [],
    }
    sock = None
    connection = None
    stage = "configuration"
    try:
        if proxy:
            parsed = urlsplit(proxy)
            if (
                parsed.scheme != "http"
                or not parsed.hostname
                or parsed.username
                or parsed.password
                or parsed.path not in {"", "/"}
                or parsed.query
                or parsed.fragment
            ):
                raise ValueError()
            host, port = parsed.hostname, parsed.port or 8080
        else:
            host, port = HOST, 443
        stage = "dns"
        began = time.monotonic()
        socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)
        report["stages"].append(
            {"stage": stage, "ok": True, "ms": round((time.monotonic() - began) * 1000)}
        )
        stage = "connect-tunnel" if proxy else "tcp"
        if proxy:
            connection = http.client.HTTPConnection(host, port, timeout=8)
            connection.set_tunnel(HOST, 443)
            connection.connect()
            sock = connection.sock
        else:
            sock = socket.create_connection((HOST, 443), timeout=8)
        report["stages"].append({"stage": stage, "ok": True})
        stage = "tls"
        with ssl.create_default_context().wrap_socket(
            sock, server_hostname=HOST
        ) as tls:
            sock = None
            report["stages"].append(
                {"stage": stage, "ok": True, "protocol": tls.version()}
            )
        report["reachable"] = True
    except (OSError, ValueError, http.client.HTTPException):
        report["stages"].append({"stage": stage, "ok": False})
        report["reachable"] = False
    finally:
        if sock is not None:
            sock.close()
        if connection is not None:
            connection.close()
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--proxy",
        help="Optional credential-free HTTP CONNECT proxy; only this probe uses it",
    )
    args = parser.parse_args()
    result = probe(args.proxy)
    print(json.dumps(result, indent=2))
    return 0 if result["reachable"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
