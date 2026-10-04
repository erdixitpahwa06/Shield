# SHIELD-DP (Android, Kotlin + Jetpack Compose + WireGuard)

Free VPN client. No signup, no subscription, no ads. Real WireGuard tunnel via the
official `com.wireguard.android:tunnel` library.

## Build the APK
1. Install Android Studio (Koala or newer) and open this folder.
2. Let Gradle sync (needs internet the first time).
3. Build > Build Bundle(s) / APK(s) > Build APK(s). Output:
   `app/build/outputs/apk/debug/app-debug.apk` (debug-signed, installs directly).
4. Copy it to your phone and install (allow "unknown sources").

## Use it
- Servers tab > **Mumbai** or **Hyderabad** > Set up > paste your client .conf (or import the file) > Save.
- Select the server, go to Shield, tap the circle.
- Add more servers any time with "Add server".

## Free server on Oracle Cloud (Always Free), India
1. Sign up at oracle.com/cloud/free. Pick home region **India West (Mumbai)** or **India South (Hyderabad)**.
   The home region cannot be changed later. Confirm current Always Free limits on Oracle's page.
2. Create an Ubuntu 22.04 VM (Ampere A1 / Arm shape). Keep the public IP.
3. In the VM's subnet Security List add an **Ingress rule: UDP, port 51820, source 0.0.0.0/0**.
4. SSH in and run:

```bash
sudo apt update && sudo apt install -y wireguard
sudo sysctl -w net.ipv4.ip_forward=1
echo "net.ipv4.ip_forward=1" | sudo tee /etc/sysctl.d/99-wg.conf
umask 077
wg genkey | tee server.key | wg pubkey > server.pub
wg genkey | tee client.key | wg pubkey > client.pub
ip route | grep default     # note the interface name, e.g. ens3 or enp0s6
```

5. Create `/etc/wireguard/wg0.conf` (replace values; use your interface name instead of ens3):

```
[Interface]
Address = 10.8.0.1/24
ListenPort = 51820
PrivateKey = <contents of server.key>
PostUp = iptables -I INPUT -p udp --dport 51820 -j ACCEPT; iptables -I FORWARD -i wg0 -j ACCEPT; iptables -I FORWARD -o wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o ens3 -j MASQUERADE
PostDown = iptables -D INPUT -p udp --dport 51820 -j ACCEPT; iptables -D FORWARD -i wg0 -j ACCEPT; iptables -D FORWARD -o wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o ens3 -j MASQUERADE

[Peer]
PublicKey = <contents of client.pub>
AllowedIPs = 10.8.0.2/32
```

6. `sudo systemctl enable --now wg-quick@wg0`
7. Client config to paste into the app:

```
[Interface]
PrivateKey = <contents of client.key>
Address = 10.8.0.2/32
DNS = 1.1.1.1

[Peer]
PublicKey = <contents of server.pub>
Endpoint = <VM_PUBLIC_IP>:51820
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
```

For each extra phone/friend, generate another key pair and add another [Peer] with a new
10.8.0.x address.

## Notes
- Kill switch: Android only allows this through system settings (Always-on VPN + Block without VPN). The app links to it.
- "No logs" is only true if your server is configured that way. Stock WireGuard keeps no traffic logs.
- Not included yet: QR import, speed test, usage history chart. Easy to add later.
