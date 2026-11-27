#!/usr/bin/env python3
"""Write one WireGuard config per tunnel in app/src/main/assets/vpn-routes.json.

Needs two files in ~/.config/bravia-vpn/ (kept out of the repo):
  private.key    the TV's WireGuard private key (never leaves this machine except inside
                 the configs you import on the TV)
  servers.json   your VPN provider's WireGuard servers, one per location used in
                 vpn-routes.json; see scripts/servers.example.json

Endpoints are written as IP addresses: at boot WireGuard starts before Wi-Fi and can't
resolve hostnames.

    scripts/vpn-configs.py            # writes ~/.config/bravia-vpn/<tunnel>.conf
"""
import json, os, socket, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ROUTES = json.loads((ROOT / "app/src/main/assets/vpn-routes.json").read_text())
OUT = pathlib.Path.home() / ".config/bravia-vpn"


def main():
    key = (OUT / "private.key").read_text().strip()
    cfg = json.loads((OUT / "servers.json").read_text())
    port = cfg.get("port", 51820)
    groups = {g["id"]: g for g in ROUTES["groups"]}
    for name, t in ROUTES["tunnels"].items():
        assert len(name) <= 15, name
        server = cfg["servers"][t["location"]]
        host, pub = server["host"], server["public_key"]
        ips = sorted({a[4][0] for a in socket.getaddrinfo(host, port, socket.AF_INET)})
        apps = [a for g in t["groups"] for a in groups[g]["apps"]]
        conf = f"""[Interface]
PrivateKey = {key}
Address = {cfg["address"]}
DNS = {cfg["dns"]}
IncludedApplications = {", ".join(apps)}

[Peer]
PublicKey = {pub}
AllowedIPs = 0.0.0.0/0
Endpoint = {ips[0]}:{port}
# host: {host} (other IPs: {", ".join(ips[1:]) or "none"})
PersistentKeepalive = 25
"""
        path = OUT / f"{name}.conf"
        path.write_text(conf)
        os.chmod(path, 0o600)
        print(f"{path}  {t['location']:10s} {ips[0]:16s} {len(apps)} apps")


if __name__ == "__main__":
    main()
