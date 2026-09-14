from __future__ import annotations

import json
import pytest
from pathlib import Path

from xray_fluent.application.node_runtime_service import is_native_singbox_only_node
from xray_fluent.application.worker_service import _node_supports_test
from xray_fluent.engines.singbox.config_builder import build_singbox_outbound
from xray_fluent.engines.singbox.runtime_planner import parse_singbox_document, plan_singbox_runtime
from xray_fluent.link_parser import parse_links_text, validate_node_outbound
from xray_fluent.models import RoutingSettings
from xray_fluent.node_transport import node_transport
from xray_fluent.openvpn_normalization import normalize_openvpn_outbound
from xray_fluent.native_test_config import build_native_test_config
from xray_fluent.qml_app.bridge.node_edit_helpers import (
    MANUAL_NODE_PROTOCOLS,
    build_node_updates,
    load_node_edit_fields,
    new_node_edit_fields,
)


def _base_config() -> dict:
    return {
        "inbounds": [{"type": "tun", "tag": "tun-in"}],
        "outbounds": [
            {"type": "direct", "tag": "proxy"},
            {"type": "direct", "tag": "direct"},
            {"type": "block", "tag": "block"},
        ],
        "route": {"rules": [], "final": "direct"},
        "dns": {"servers": [{"tag": "bootstrap-dns", "type": "udp", "server": "1.1.1.1"}]},
    }


def test_openvpn_migration_preserves_legacy_default_and_rejects_unknown_cipher():
    legacy = {"type": "openvpn", "servers": [{"server": "vpn.example", "server_port": 443}]}
    normalize_openvpn_outbound(legacy)
    assert legacy["network"] == "tcp"
    assert legacy["servers"][0]["network"] == "tcp"
    with pytest.raises(ValueError, match="Unsupported OpenVPN data cipher"):
        normalize_openvpn_outbound({"type": "openvpn", "cipher": "invalid-cipher"})


def test_full_legacy_openvpn_profile_is_migrated_for_ping_without_mutating_source():
    payload = {
        "outbounds": [{"type": "openvpn", "tag": "vpn", "proto": "udp",
                       "servers": [{"server": "vpn.example", "server_port": 1194}],
                       "tls": {"ca": "CA"}}],
        "route": {"final": "vpn"},
    }
    nodes, errors = parse_links_text(json.dumps(payload))
    assert not errors
    config = build_native_test_config(nodes[0], 19876)
    assert config["endpoints"][0]["type"] == "openvpn-client"
    assert config["endpoints"][0]["system"] is False
    assert not any(item.get("type") == "openvpn" for item in config["outbounds"])
    assert payload["outbounds"][0]["type"] == "openvpn"


def test_ovpn_inline_profile_imports_supported_extended_fields() -> None:
    nodes, errors = parse_links_text(
        """client
proto tcp-client
remote vpn-one.example.com 1194
remote vpn-two.example.com 443 tcp-client
data-ciphers AES-256-GCM:AES-128-GCM
auth SHA256
auth-user-pass
verify-x509-name vpn.example.com name
key-direction 1
ping 10
ping-restart 60
connect-retry 5
dhcp-option DNS 10.8.0.1
<auth-user-pass>
alice
secret
</auth-user-pass>
<ca>
-----BEGIN CERTIFICATE-----
CA
-----END CERTIFICATE-----
</ca>
<tls-crypt-v2>
-----BEGIN OpenVPN tls-crypt-v2 client key-----
KEY
-----END OpenVPN tls-crypt-v2 client key-----
</tls-crypt-v2>
"""
    )

    assert errors == []
    node = nodes[0]
    assert node.scheme == "openvpn"
    assert node.server == "vpn-one.example.com"
    assert node.port == 1194
    assert node_transport(node) == "TCP"
    assert is_native_singbox_only_node(node) is True
    assert _node_supports_test(node, "ping", ping_method="tcping") is True
    # Native sing-box profiles are tested through an isolated loopback mixed
    # inbound; the worker does not create TUN or modify the system proxy.
    assert _node_supports_test(node, "speed") is True
    outbound = build_singbox_outbound(node)
    assert outbound["type"] == "openvpn-client"
    assert outbound["servers"] == [
        {"server": "vpn-one.example.com", "server_port": 1194, "network": "tcp"},
        {"server": "vpn-two.example.com", "server_port": 443, "network": "tcp"},
    ]
    assert outbound["network"] == "tcp"
    assert outbound["data_ciphers"] == ["AES-256-GCM"]
    assert outbound["auth"] == "SHA256"
    assert outbound["username"] == "alice"
    assert outbound["password"] == "secret"
    assert outbound["tls"]["control_wrap"]["type"] == "tls_crypt_v2"
    assert outbound["ping_interval"] == "10s"
    assert outbound["ping_restart"] == "60s"
    assert outbound["tls"]["server_name_type"] == "name"
    assert node.outbound["_dns"] == ["10.8.0.1"]
    assert validate_node_outbound(node) is None


def test_ovpn_file_embeds_sibling_resources(tmp_path: Path) -> None:
    (tmp_path / "ca.crt").write_text("CA DATA\n", encoding="utf-8")
    (tmp_path / "client.crt").write_text("CERT DATA\n", encoding="utf-8")
    (tmp_path / "client.key").write_text("KEY DATA\n", encoding="utf-8")
    (tmp_path / "ta.key").write_text("TLS AUTH DATA\n", encoding="utf-8")
    (tmp_path / "auth.txt").write_text("user\npass\n", encoding="utf-8")
    profile = tmp_path / "provider.ovpn"
    profile.write_text(
        """client
remote 198.51.100.10 443
proto tcp-client
ca ca.crt
cert client.crt
key client.key
tls-auth ta.key 1
auth-user-pass auth.txt
""",
        encoding="utf-8",
    )

    nodes, errors = parse_links_text(str(profile), allow_file_reference=True)

    assert errors == []
    node = nodes[0]
    native = node.outbound["singbox"]
    assert node.name == "provider"
    assert native["proto"] == "tcp"
    assert native["tls_auth"] == "TLS AUTH DATA\n"
    assert native["key_direction"] == 1
    assert native["username"] == "user"
    assert native["password"] == "pass"
    assert native["tls"] == {
        "certificate": "CERT DATA\n",
        "key": "KEY DATA\n",
        "ca": "CA DATA\n",
    }
    assert not any(key.endswith("_path") for key in native)


def test_ovpn_file_cannot_read_resources_outside_profile_directory(tmp_path: Path) -> None:
    profile_dir = tmp_path / "profile"
    profile_dir.mkdir()
    (tmp_path / "outside-ca.crt").write_text("PRIVATE DATA", encoding="utf-8")
    profile = profile_dir / "unsafe.ovpn"
    profile.write_text(
        "client\nproto tcp-client\nremote vpn.example.com 1194\nca ../outside-ca.crt\n",
        encoding="utf-8",
    )

    nodes, errors = parse_links_text(str(profile), allow_file_reference=True)

    assert nodes == []
    assert errors and "inside the profile directory" in errors[0]


def test_openvpn_native_json_and_urltest_auto_are_preserved() -> None:
    single_nodes, single_errors = parse_links_text(json.dumps({
        "type": "openvpn",
        "tag": "provider-openvpn",
        "servers": [{"server": "single.example.com", "server_port": 1194}],
        "proto": "udp",
        "tls": {"ca": "CA"},
    }))
    assert single_errors == []
    assert single_nodes[0].scheme == "openvpn"
    assert single_nodes[0].server == "single.example.com"

    config = {
        "remarks": "OpenVPN AUTO",
        "outbounds": [
            {"type": "openvpn", "tag": "ovpn-1", "servers": [{"server": "one.example.com", "server_port": 1194}], "proto": "udp"},
            {"type": "openvpn", "tag": "ovpn-2", "servers": [{"server": "two.example.com", "server_port": 443}], "proto": "tcp"},
            {"type": "urltest", "tag": "auto", "outbounds": ["ovpn-1", "ovpn-2"], "url": "https://www.gstatic.com/generate_204", "interval": "3m"},
        ],
        "route": {"final": "auto"},
    }

    nodes, errors = parse_links_text(json.dumps(config))

    assert errors == []
    assert len(nodes) == 1
    node = nodes[0]
    assert node.scheme == "auto"
    assert node.name == "OpenVPN AUTO"
    assert node.server == "one.example.com"
    assert node.outbound["protocol"] == "singbox_config"
    stored = node.outbound["singbox_config"]
    stored_openvpn = [item for item in stored["outbounds"] if item.get("tag", "").startswith("ovpn-")]
    assert [item["type"] for item in stored_openvpn] == ["openvpn", "openvpn"]
    assert [item["name"] for item in stored_openvpn] == ["openvpn0", "openvpn1"]
    assert all(item["system"] is False for item in stored_openvpn)
    assert stored["route"]["final"] == "auto"


def test_current_openvpn_endpoint_json_is_preserved() -> None:
    payload = {
        "endpoints": [{
            "type": "openvpn-client",
            "tag": "provider-openvpn",
            "network": "udp",
            "servers": [{"server": "udp.example.com", "server_port": 1194}],
            "tls": {"certificate": "CA"},
        }],
        "outbounds": [{"type": "direct", "tag": "direct"}],
        "route": {"final": "provider-openvpn"},
    }

    nodes, errors = parse_links_text(json.dumps(payload))

    assert errors == []
    assert nodes[0].scheme == "openvpn"
    assert nodes[0].server == "udp.example.com"
    assert nodes[0].port == 1194
    stored = nodes[0].outbound["singbox_config"]
    assert stored["endpoints"][0]["type"] == "openvpn-client"


def test_openvpn_runtime_bootstraps_every_remote(monkeypatch) -> None:
    nodes, errors = parse_links_text(
        "client\nremote first.example.com 1194\nremote 198.51.100.20 443\nproto tcp-client\n"
        "<ca>\n-----BEGIN CERTIFICATE-----\nCA\n-----END CERTIFICATE-----\n</ca>\n"
    )
    assert errors == []
    monkeypatch.setattr(
        "xray_fluent.engines.singbox.runtime_planner._resolve_endpoint_addresses",
        lambda host: ["203.0.113.10"] if host == "first.example.com" else [],
    )
    document = parse_singbox_document(Path("default.json"), json.dumps(_base_config()))

    config = plan_singbox_runtime(
        document,
        nodes[0],
        routing=RoutingSettings(mode="global", tun_default_outbound="proxy"),
    ).singbox_config

    outbound = next(item for item in config["endpoints"] if item.get("tag") == "proxy")
    tun = next(item for item in config["inbounds"] if item.get("type") == "tun")
    assert outbound["type"] == "openvpn-client"
    assert outbound["domain_resolver"] == "bootstrap-dns"
    assert "203.0.113.10/32" in tun["route_exclude_address"]
    assert "198.51.100.20/32" in tun["route_exclude_address"]
    assert any("first.example.com" in rule.get("domain", []) for rule in config["route"]["rules"])


def test_openvpn_udp_profile_builds_native_endpoint() -> None:
    nodes, errors = parse_links_text(
        "client\nremote vpn.example.com 1194\nproto udp\nmssfix 0\nexplicit-exit-notify\n"
        "<ca>\n-----BEGIN CERTIFICATE-----\nCA\n-----END CERTIFICATE-----\n</ca>\n"
    )

    assert errors == []
    outbound = build_singbox_outbound(nodes[0])
    assert outbound["type"] == "openvpn-client"
    assert outbound["network"] == "udp"
    assert outbound["mss_fix_disabled"] is True
    assert outbound["explicit_exit_notify"] == 1
    assert outbound["servers"] == [
        {"server": "vpn.example.com", "server_port": 1194, "network": "udp"}
    ]


def test_openvpn_mixed_connection_blocks_keep_each_transport() -> None:
    nodes, errors = parse_links_text(
        "client\nremote-random\n"
        "<connection>\nremote tcp.example.com 443\nproto tcp-client\n</connection>\n"
        "<connection>\nremote udp.example.com 1194\nproto udp\n</connection>\n"
        "<ca>\n-----BEGIN CERTIFICATE-----\nCA\n-----END CERTIFICATE-----\n</ca>\n"
    )

    assert errors == []
    outbound = build_singbox_outbound(nodes[0])
    assert outbound["remote_random"] is True
    assert [(item["server"], item["network"]) for item in outbound["servers"]] == [
        ("tcp.example.com", "tcp"),
        ("udp.example.com", "udp"),
    ]


def test_openvpn_editor_round_trip_and_manual_protocol() -> None:
    assert "openvpn" in MANUAL_NODE_PROTOCOLS
    fields = new_node_edit_fields("openvpn", "Manual")
    keys = {item["key"] for item in fields["protocolFields"]}
    assert {"openvpnServersJson", "tlsCrypt", "tlsAuth", "certificate", "privateKey", "caCertificate"} <= keys

    updates = build_node_updates(
        type("Draft", (), {
            "name": "",
            "group": "Manual",
            "scheme": "openvpn",
            "server": "",
            "port": 0,
            "outbound": {"protocol": "openvpn"},
        })(),
        {
            "name": "My OpenVPN",
            "group": "Manual",
            "server": "vpn.example.com",
            "port": "1194",
            "openvpnProto": "udp",
            "openvpnCipher": "AES-256-GCM",
            "username": "user",
            "password": "pass",
            "tlsCrypt": "STATIC KEY",
            "caCertificate": "CA",
            "verifyX509Name": "vpn.example.com",
            "verifyX509NameMode": "exact",
        },
    )

    native = updates["outbound"]["singbox"]
    assert native["servers"] == [{"server": "vpn.example.com", "server_port": 1194}]
    assert native["tls_crypt"] == "STATIC KEY"
    assert native["tls"]["ca"] == "CA"
    loaded = load_node_edit_fields(type("Saved", (), {**updates, "scheme": "openvpn", "server": "vpn.example.com", "port": 1194})())
    assert loaded["username"] == "user"
    assert loaded["caCertificate"] == "CA"
