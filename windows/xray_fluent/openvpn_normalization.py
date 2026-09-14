from __future__ import annotations

from typing import Any


DATA_CIPHERS = {
    "AES-128-GCM", "AES-192-GCM", "AES-256-GCM",
    "AES-128-CBC", "AES-192-CBC", "AES-256-CBC",
    "CHACHA20-POLY1305",
}
AUTH_DIGESTS = {"MD5", "SHA1", "SHA256", "SHA384", "SHA512"}

_GO_TLS_CIPHER_SUITES = {
    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
    "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
    "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
}
_OPENSSL_TLS_ALIASES = {
    "ECDHE-ECDSA-AES128-SHA": "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
    "ECDHE-ECDSA-AES256-SHA": "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
    "ECDHE-RSA-AES128-SHA": "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
    "ECDHE-RSA-AES256-SHA": "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
    "ECDHE-ECDSA-AES128-GCM-SHA256": "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
    "ECDHE-ECDSA-AES256-GCM-SHA384": "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
    "ECDHE-RSA-AES128-GCM-SHA256": "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
    "ECDHE-RSA-AES256-GCM-SHA384": "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
    "ECDHE-RSA-CHACHA20-POLY1305": "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
    "ECDHE-ECDSA-CHACHA20-POLY1305": "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
}


def normalize_data_cipher(value: object) -> str:
    normalized = str(value or "").strip().upper()
    return normalized if normalized in DATA_CIPHERS else ""


def normalize_auth_digest(value: object) -> str:
    normalized = str(value or "").strip().upper()
    return normalized if normalized in AUTH_DIGESTS else ""


def normalize_tls_cipher_suite(value: object) -> str:
    normalized = str(value or "").strip().upper()
    if not normalized:
        return ""
    if normalized in _GO_TLS_CIPHER_SUITES:
        return normalized
    if normalized in _OPENSSL_TLS_ALIASES:
        return _OPENSSL_TLS_ALIASES[normalized]
    iana = normalized.replace("-", "_")
    return iana if iana in _GO_TLS_CIPHER_SUITES else ""


def normalize_tls_cipher_suites(value: object) -> list[str]:
    if isinstance(value, (list, tuple, set)):
        raw_values = [str(item) for item in value]
    elif value in (None, ""):
        raw_values = []
    else:
        raw_values = [str(value)]
    result: list[str] = []
    for raw in raw_values:
        for candidate in raw.split(":"):
            normalized = normalize_tls_cipher_suite(candidate)
            if normalized and normalized not in result:
                result.append(normalized)
    return result


def normalize_openvpn_outbound(outbound: dict[str, Any]) -> None:
    """Migrate stored/legacy OpenVPN data to the current endpoint schema.

    sing-box 1.14 moved OpenVPN from the old extended ``openvpn`` outbound to
    the native ``openvpn-client`` endpoint.  Lumen keeps the legacy shape in
    its database so older profiles remain editable, and converts only the
    disposable runtime copy here.
    """
    outbound_type = str(outbound.get("type") or "").strip().lower()
    if outbound_type not in {"openvpn", "openvpn-client"}:
        return

    legacy = outbound_type == "openvpn"
    proto = _normalize_openvpn_network(outbound.pop("proto", None) or outbound.get("network") or ("tcp" if legacy else "udp"))
    outbound["type"] = "openvpn-client"
    outbound["network"] = proto
    outbound["system"] = False
    outbound["name"] = str(outbound.get("name") or "openvpn0")

    servers = outbound.get("servers")
    if isinstance(servers, list):
        normalized_servers: list[dict[str, Any]] = []
        for raw_server in servers:
            if not isinstance(raw_server, dict):
                continue
            server = dict(raw_server)
            remote_network = server.pop("proto", None) or server.get("network") or proto
            server["network"] = _normalize_openvpn_network(remote_network)
            normalized_servers.append(server)
        outbound["servers"] = normalized_servers

    raw_cipher = str(outbound.pop("cipher", "") or "").strip() if legacy else ""
    if legacy and raw_cipher:
        cipher = normalize_data_cipher(raw_cipher)
        if cipher:
            outbound["data_ciphers"] = [cipher]
            outbound["data_ciphers_fallback"] = cipher
        else:
            raise ValueError(f"Unsupported OpenVPN data cipher `{raw_cipher}`")
    elif "data_ciphers" in outbound:
        raw_ciphers = outbound.get("data_ciphers")
        candidates = raw_ciphers if isinstance(raw_ciphers, list) else str(raw_ciphers or "").split(":")
        ciphers = [normalize_data_cipher(value) for value in candidates]
        ciphers = [value for value in ciphers if value]
        if not ciphers and raw_ciphers not in (None, "", []):
            raise ValueError("Unsupported OpenVPN data cipher")
        outbound["data_ciphers"] = ciphers
    raw_auth = str(outbound.get("auth") or "").strip()
    auth = normalize_auth_digest(raw_auth)
    if auth:
        outbound["auth"] = auth
    elif raw_auth:
        raise ValueError(f"Unsupported OpenVPN auth digest `{raw_auth}`")

    tls = outbound.get("tls")
    if isinstance(tls, dict):
        tls = dict(tls)
        if legacy:
            ca = tls.pop("ca", None)
            ca_path = tls.pop("ca_path", None)
            client_certificate = tls.pop("certificate", None)
            client_certificate_path = tls.pop("certificate_path", None)
            client_key = tls.pop("key", None)
            client_key_path = tls.pop("key_path", None)
            if ca:
                tls["certificate"] = ca
            if ca_path:
                tls["certificate_path"] = ca_path
            if client_certificate:
                tls["client_certificate"] = client_certificate
            if client_certificate_path:
                tls["client_certificate_path"] = client_certificate_path
            if client_key:
                tls["client_key"] = client_key
            if client_key_path:
                tls["client_key_path"] = client_key_path
            verify_name = tls.pop("verify_x509_name", None)
            if verify_name:
                tls["server_name"] = verify_name
            verify_mode = str(tls.pop("verify_x509_name_mode", "") or "").strip().lower()
            if verify_mode == "exact":
                verify_mode = "name"
            if verify_mode:
                if verify_mode not in {"subject", "name", "name-prefix"}:
                    raise ValueError(f"OpenVPN verify-x509-name mode `{verify_mode}` is not supported")
                tls["server_name_type"] = verify_mode
            raw_suites = tls.pop("cipher_suites", None)
            suites = normalize_tls_cipher_suites(raw_suites)
            if suites:
                tls["cipher"] = ":".join(suites)
            elif raw_suites not in (None, "", [], (), set()):
                raise ValueError("Unsupported OpenVPN TLS cipher suite")

            control_type = ""
            control_key = ""
            control_path = ""
            if str(outbound.get("tls_auth") or outbound.get("tls_auth_path") or "").strip():
                control_type = "tls_auth"
                control_key = str(outbound.pop("tls_auth", "") or "")
                control_path = str(outbound.pop("tls_auth_path", "") or "")
            elif str(outbound.get("tls_crypt") or outbound.get("tls_crypt_path") or "").strip():
                control_type = "tls_crypt_v2" if outbound.pop("tls_crypt_v2", False) else "tls_crypt"
                control_key = str(outbound.pop("tls_crypt", "") or "")
                control_path = str(outbound.pop("tls_crypt_path", "") or "")
            if control_type:
                control_wrap: dict[str, Any] = {"type": control_type}
                if control_key:
                    control_wrap["key"] = control_key
                elif control_path:
                    control_wrap["key_path"] = control_path
                direction = outbound.pop("key_direction", None)
                if control_type == "tls_auth" and str(direction) in {"0", "1"}:
                    control_wrap["direction"] = "server" if str(direction) == "0" else "client"
                tls["control_wrap"] = control_wrap
        tls.pop("kernel_tx", None)
        tls.pop("kernel_rx", None)
        outbound["tls"] = tls

    # These belonged to the pre-1.14 extension and are rejected by the native endpoint.
    for key in (
        "tls_auth", "tls_auth_path", "tls_crypt", "tls_crypt_path", "tls_crypt_v2",
        "key_direction", "key_password", "reconnect_delay", "allowed_ips",
        "lumen_requires_user_auth",
    ):
        outbound.pop(key, None)


def _normalize_openvpn_network(value: object) -> str:
    network = str(value or "udp").strip().lower()
    if network in {"udp", "udp4", "udp6"}:
        return network
    if network in {"tcp", "tcp-client"}:
        return "tcp"
    if network in {"tcp4", "tcp4-client"}:
        return "tcp4"
    if network in {"tcp6", "tcp6-client"}:
        return "tcp6"
    raise ValueError(f"Unsupported OpenVPN transport `{network}`")


def openvpn_private_key_is_encrypted(outbound: dict[str, Any]) -> bool:
    tls = outbound.get("tls") if isinstance(outbound.get("tls"), dict) else {}
    private_key = str(tls.get("key") or tls.get("client_key") or "").upper()
    return "ENCRYPTED PRIVATE KEY" in private_key or (
        "PROC-TYPE:" in private_key and "ENCRYPTED" in private_key
    )
