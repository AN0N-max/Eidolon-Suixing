import importlib.util
import ssl
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    "probe", Path(__file__).parents[1] / "tools/tts_probe.py"
)
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class Socket:
    closed = False

    def close(self):
        self.closed = True


def test_dns_failure_is_safe_and_never_sends_http(monkeypatch):
    def fail(*args, **kwargs):
        raise OSError("sensitive provider detail")

    monkeypatch.setattr(probe.socket, "getaddrinfo", fail)
    result = probe.probe()
    assert result["reachable"] is False and result["paidRequest"] is False
    assert result["stages"] == [{"stage": "dns", "ok": False}]
    assert "sensitive" not in str(result)


def test_invalid_proxy_never_resolves_or_exposes_credentials(monkeypatch):
    monkeypatch.setattr(
        probe.socket,
        "getaddrinfo",
        lambda *a, **k: (_ for _ in ()).throw(AssertionError("network called")),
    )
    for value in (
        "http://user:private@localhost:8080",
        "file:///tmp/x",
        "http://localhost:8080/path",
        "socks5://localhost:8080",
    ):
        result = probe.probe(value)
        assert (
            not result["reachable"] and result["stages"][0]["stage"] == "configuration"
        )
        assert "private" not in str(result)


def test_direct_tls_verifies_host_without_http_requests(monkeypatch):
    socket = Socket()
    monkeypatch.setattr(probe.socket, "getaddrinfo", lambda *a, **k: [])
    monkeypatch.setattr(probe.socket, "create_connection", lambda *a, **k: socket)

    class TLS:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            socket.close()

        def version(self):
            return "TLSv1.3"

    class Context:
        def wrap_socket(self, sock, server_hostname):
            assert sock is socket and server_hostname == "api.fish.audio"
            return TLS()

    monkeypatch.setattr(probe.ssl, "create_default_context", lambda: Context())
    assert probe.probe()["reachable"] is True and socket.closed


def test_tls_certificate_failure_closes_socket(monkeypatch):
    socket = Socket()
    monkeypatch.setattr(probe.socket, "getaddrinfo", lambda *a, **k: [])
    monkeypatch.setattr(probe.socket, "create_connection", lambda *a, **k: socket)

    class Context:
        def wrap_socket(self, *a, **k):
            raise ssl.SSLCertVerificationError()

    monkeypatch.setattr(probe.ssl, "create_default_context", lambda: Context())
    result = probe.probe()
    assert result["stages"][-1] == {"stage": "tls", "ok": False}
    assert socket.closed


def test_proxy_uses_connect_only_to_fixed_host_and_verifies_tls(monkeypatch):
    socket = Socket()
    calls = []
    monkeypatch.setattr(probe.socket, "getaddrinfo", lambda *a, **k: [])

    class Connection:
        sock = socket

        def __init__(self, host, port, timeout):
            calls.append((host, port, timeout))

        def set_tunnel(self, host, port):
            calls.append((host, port))

        def connect(self):
            calls.append("connect")

        def close(self):
            socket.close()

    class TLS:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            socket.close()

        def version(self):
            return "TLSv1.3"

    class Context:
        def wrap_socket(self, sock, server_hostname):
            assert sock is socket and server_hostname == "api.fish.audio"
            return TLS()

    monkeypatch.setattr(probe.http.client, "HTTPConnection", Connection)
    monkeypatch.setattr(probe.ssl, "create_default_context", lambda: Context())
    result = probe.probe("http://127.0.0.1:7890")
    assert result["reachable"] and result["route"] == "explicit-proxy"
    assert calls == [("127.0.0.1", 7890, 8), ("api.fish.audio", 443), "connect"]
    assert socket.closed
