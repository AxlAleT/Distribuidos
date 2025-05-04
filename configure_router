#!/bin/bash
# Site-to-Site IPSec Router Configuration Script
# ---------------------------------------------
# This script configures a Debian-based router with an IPSec tunnel between two sites.
# Edit the variables below before running the script on each site's router.

# CONFIGURATION VARIABLES - EDIT THESE FOR EACH SITE
# --------------------------------------------------
# WAN Interface (connected to ISP)
WAN_IFACE="eth0"
# LAN Interface (connected to local network)
LAN_IFACE="eth1"

# Site identification
SITE_ID="site1"                  # Use "site1" for first router, "site2" for second router
SITE_NAME="Site 1"               # Human-readable site name

# Local Network Configuration
LAN_IP="192.168.1.1"             # Router's IP on local network (site1=192.168.1.1, site2=192.168.2.1)
LAN_SUBNET="192.168.1.0/24"      # Local subnet (site1=192.168.1.0/24, site2=192.168.2.0/24)
LAN_NETMASK="255.255.255.0"      # Subnet mask
DHCP_RANGE_START="192.168.1.100" # Start of DHCP range
DHCP_RANGE_END="192.168.1.200"   # End of DHCP range

# Remote Site Configuration
REMOTE_SITE_ID="site2"           # Remote site ID (site1 points to site2, and vice versa)
REMOTE_PUB_IP="203.0.113.2"      # Public IP of the remote site's router
REMOTE_SUBNET="192.168.2.0/24"   # Remote site's subnet

# IPSec Configuration
PSK="supersecretpresharedkey"    # Pre-shared key (should be the same on both routers)

# DNS Servers
PRIMARY_DNS="8.8.8.8"
SECONDARY_DNS="8.8.4.4"

# --------------------------------------
# SCRIPT BEGINS HERE - No need to modify below this line unless customizing functionality
# --------------------------------------

echo "Starting router configuration for ${SITE_NAME} (${SITE_ID})"
echo "Local network: ${LAN_SUBNET}, Remote network: ${REMOTE_SUBNET}"
echo "Remote site public IP: ${REMOTE_PUB_IP}"

# Configure network interfaces
echo "Configuring network interfaces..."
cat > /etc/network/interfaces.d/${WAN_IFACE} << EOF
auto ${WAN_IFACE}
iface ${WAN_IFACE} inet dhcp
EOF

cat > /etc/network/interfaces.d/${LAN_IFACE} << EOF
auto ${LAN_IFACE}
iface ${LAN_IFACE} inet static
    address ${LAN_IP}
    netmask ${LAN_NETMASK}
EOF

# Setup NAT and firewall rules
echo "Configuring firewall rules..."
cat > /etc/iptables.rules << EOF
*nat
:PREROUTING ACCEPT [0:0]
:INPUT ACCEPT [0:0]
:OUTPUT ACCEPT [0:0]
:POSTROUTING ACCEPT [0:0]
-A POSTROUTING -o ${WAN_IFACE} -j MASQUERADE
COMMIT

*filter
:INPUT DROP [0:0]
:FORWARD DROP [0:0]
:OUTPUT ACCEPT [0:0]
-A INPUT -i lo -j ACCEPT
-A INPUT -i ${LAN_IFACE} -j ACCEPT
-A INPUT -i ${WAN_IFACE} -p icmp -j ACCEPT
-A INPUT -i ${WAN_IFACE} -m state --state RELATED,ESTABLISHED -j ACCEPT
-A INPUT -i ${WAN_IFACE} -p udp --dport 500 -j ACCEPT
-A INPUT -i ${WAN_IFACE} -p udp --dport 4500 -j ACCEPT
-A INPUT -i ${WAN_IFACE} -p tcp --dport 22 -j ACCEPT
-A FORWARD -m state --state RELATED,ESTABLISHED -j ACCEPT
-A FORWARD -i ${LAN_IFACE} -o ${WAN_IFACE} -j ACCEPT
-A FORWARD -i ${WAN_IFACE} -o ${LAN_IFACE} -m state --state RELATED,ESTABLISHED -j ACCEPT
COMMIT
EOF

# Create script to load iptables rules on boot
echo "Setting up iptables persistence..."
cat > /etc/network/if-pre-up.d/iptables << EOF
#!/bin/sh
/sbin/iptables-restore < /etc/iptables.rules
EOF

chmod +x /etc/network/if-pre-up.d/iptables

# Save current iptables rules
iptables-restore < /etc/iptables.rules
mkdir -p /etc/iptables
iptables-save > /etc/iptables/rules.v4

# IPsec configuration
echo "Configuring IPsec tunnel..."
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
    leftsubnet=${LAN_SUBNET}
    leftid=@${SITE_ID}
    leftauth=psk
    
    right=${REMOTE_PUB_IP}
    rightsubnet=${REMOTE_SUBNET}
    rightid=@${REMOTE_SITE_ID}
    rightauth=psk
    
    auto=start
EOF

cat > /etc/ipsec.secrets << EOF
@${SITE_ID} @${REMOTE_SITE_ID} : PSK "${PSK}"
EOF

chmod 600 /etc/ipsec.secrets

# Create a basic service status script
echo "Creating router status script..."
cat > /usr/local/bin/router-status << EOF
#!/bin/bash
echo "=== Router Status for ${SITE_NAME} ==="
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

# Configure DHCP server for the LAN
echo "Configuring DHCP server..."
cat > /etc/dhcp/dhcpd.conf << EOF
default-lease-time 600;
max-lease-time 7200;

subnet ${LAN_SUBNET%/*} netmask ${LAN_NETMASK} {
  range ${DHCP_RANGE_START} ${DHCP_RANGE_END};
  option routers ${LAN_IP};
  option domain-name-servers ${PRIMARY_DNS}, ${SECONDARY_DNS};
}
EOF

# Configure DHCP server to run on LAN interface
sed -i "s/INTERFACESv4=\"\"/INTERFACESv4=\"${LAN_IFACE}\"/" /etc/default/isc-dhcp-server

# Enable services
echo "Enabling services..."
systemctl enable strongswan
systemctl enable isc-dhcp-server

echo "---------------------------------------"
echo "Router configuration complete for ${SITE_NAME} (${SITE_ID})!"
echo "IPsec tunnel configured to ${REMOTE_SITE_ID} at ${REMOTE_PUB_IP}"
echo "Local network: ${LAN_SUBNET}"
echo "Remote network: ${REMOTE_SUBNET}"
echo
echo "Please reboot the system to apply all changes."
echo "After reboot, check tunnel status with: router-status"
echo "---------------------------------------"
