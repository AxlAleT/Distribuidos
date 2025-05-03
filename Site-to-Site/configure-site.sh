#!/bin/bash
# Site setup: WAN=eth0 (DHCP), LAN=eth1 static 192.168.1.1/24
# Replace with the other real public IP and your PSK:
REMOTE_PUB_IP="203.0.113.2"
PSK="supersecretpresharedkey"

# Update and upgrade the system
apt update
apt upgrade -y

# Install SSH server (for remote management)
apt install -y openssh-server
systemctl enable ssh
systemctl start ssh

# Install necessary packages for routing
apt install -y \
  iproute2 \
  iptables \
  net-tools \
  tcpdump \
  traceroute \
  dnsutils \
  iputils-ping \
  curl \
  wget

# Enable IP forwarding
echo "net.ipv4.ip_forward=1" > /etc/sysctl.d/99-router.conf
sysctl -p /etc/sysctl.d/99-router.conf

# Install strongSwan for IPsec support
apt install -y \
  strongswan \
  strongswan-pki \
  libcharon-extra-plugins \
  libcharon-extauth-plugins \
  libstrongswan-extra-plugins

# Configure network interfaces
cat > /etc/network/interfaces.d/eth0 << EOF
auto eth0
iface eth0 inet dhcp
EOF

cat > /etc/network/interfaces.d/eth1 << EOF
auto eth1
iface eth1 inet static
    address 192.168.1.1
    netmask 255.255.255.0
EOF

# Setup NAT for internal network
cat > /etc/iptables.rules << EOF
*nat
:PREROUTING ACCEPT [0:0]
:INPUT ACCEPT [0:0]
:OUTPUT ACCEPT [0:0]
:POSTROUTING ACCEPT [0:0]
-A POSTROUTING -o eth0 -j MASQUERADE
COMMIT

*filter
:INPUT DROP [0:0]
:FORWARD DROP [0:0]
:OUTPUT ACCEPT [0:0]
-A INPUT -i lo -j ACCEPT
-A INPUT -i eth1 -j ACCEPT
-A INPUT -i eth0 -p icmp -j ACCEPT
-A INPUT -i eth0 -m state --state RELATED,ESTABLISHED -j ACCEPT
-A INPUT -i eth0 -p udp --dport 500 -j ACCEPT
-A INPUT -i eth0 -p udp --dport 4500 -j ACCEPT
-A INPUT -i eth0 -p tcp --dport 22 -j ACCEPT
-A FORWARD -m state --state RELATED,ESTABLISHED -j ACCEPT
-A FORWARD -i eth1 -o eth0 -j ACCEPT
-A FORWARD -i eth0 -o eth1 -m state --state RELATED,ESTABLISHED -j ACCEPT
COMMIT
EOF

# Create script to load iptables rules on boot
cat > /etc/network/if-pre-up.d/iptables << EOF
#!/bin/sh
/sbin/iptables-restore < /etc/iptables.rules
EOF

chmod +x /etc/network/if-pre-up.d/iptables

# IPsec configuration
mkdir -p /etc/ipsec.d/certs
mkdir -p /etc/ipsec.d/private

cat > /etc/ipsec.conf << EOF
config setup
    charondebug="ike 2, knl 2, cfg 2, net 2, esp 2, dmn 2, mgr 2"

conn %default
    keyexchange=ikev2
    ike=aes256-sha256-modp2048
    esp=aes256-sha256-modp2048
    mobike=no

conn site-to-site
    left=%defaultroute
    leftsubnet=192.168.1.0/24
    leftid=@site1
    leftauth=psk

    right=${REMOTE_PUB_IP}
    rightsubnet=192.168.2.0/24
    rightid=@site2
    rightauth=psk

    auto=start
EOF

cat > /etc/ipsec.secrets << EOF
@site1 @site2 : PSK "${PSK}"
EOF

chmod 600 /etc/ipsec.secrets

# Create a basic service status script
cat > /usr/local/bin/router-status << EOF
#!/bin/bash
echo "=== Router Status ==="
echo "--- Network Interfaces ---"
ip a
echo
echo "--- Routing Table ---"
ip route
echo
echo "--- IPsec Status ---"
ipsec statusall
echo
echo "--- Current Connections ---"
netstat -tunlp
EOF

chmod +x /usr/local/bin/router-status

# Optional - Create a minimal DHCP server for the LAN
apt install -y isc-dhcp-server

cat > /etc/dhcp/dhcpd.conf << EOF
default-lease-time 600;
max-lease-time 7200;

subnet 192.168.1.0 netmask 255.255.255.0 {
  range 192.168.1.100 192.168.1.200;
  option routers 192.168.1.1;
  option domain-name-servers 8.8.8.8, 8.8.4.4;
}
EOF

# Configure DHCP server to run on LAN interface
sed -i 's/INTERFACESv4=""/INTERFACESv4="eth1"/' /etc/default/isc-dhcp-server

# Enable and start services
systemctl enable strongswan
systemctl enable isc-dhcp-server

echo "Site1 configured. Reboot to apply."