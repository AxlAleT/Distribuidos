#!/bin/bash
# Site-to-Site IPSec Router Dependencies Installation
# --------------------------------------------------
# This script installs all necessary packages for a Debian-based router
# with IPSec tunnel capabilities

echo "Starting router dependencies installation..."

# Update and upgrade the system
echo "Updating system packages..."
apt update
apt upgrade -y

# Install SSH server (for remote management)
echo "Installing SSH server..."
apt install -y openssh-server
systemctl enable ssh
systemctl start ssh

# Install necessary packages for routing
echo "Installing networking utilities..."
apt install -y \
  iproute2 \
  iptables \
  iptables-persistent \
  net-tools \
  tcpdump \
  traceroute \
  dnsutils \
  iputils-ping \
  curl \
  wget

# Enable IP forwarding
echo "Enabling IP forwarding..."
echo "net.ipv4.ip_forward=1" > /etc/sysctl.d/99-router.conf
sysctl -p /etc/sysctl.d/99-router.conf

# Install strongSwan for IPsec support
echo "Installing strongSwan for IPsec..."
apt install -y \
  strongswan \
  strongswan-pki \
  libcharon-extra-plugins \
  libcharon-extauth-plugins \
  libstrongswan-extra-plugins

# Install DHCP server for the LAN
echo "Installing DHCP server..."
apt install -y isc-dhcp-server

echo "---------------------------------------"
echo "Router dependencies installation complete!"
echo "You can now run the router configuration script."
echo "---------------------------------------"
