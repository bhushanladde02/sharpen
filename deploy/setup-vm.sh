#!/usr/bin/env bash
# One-time setup of an Ubuntu VM (tested on Oracle Cloud Always Free, Ampere A1, Ubuntu 22.04/24.04).
# Run as the default user:  bash setup-vm.sh
set -euo pipefail

sudo apt-get update -y
sudo apt-get install -y ca-certificates curl git ufw

# Docker Engine + compose plugin (official repository)
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

# Firewall: SSH, HTTP, HTTPS. Oracle also needs the same ports opened in the VCN security list (console).
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable

# Oracle's Ubuntu images ship iptables rules that block 80/443 even with ufw open; remove that reject rule.
if sudo iptables -L INPUT -n | grep -q "reject-with icmp-host-prohibited"; then
  sudo iptables -I INPUT 5 -p tcp --dport 80 -j ACCEPT
  sudo iptables -I INPUT 5 -p tcp --dport 443 -j ACCEPT
  sudo apt-get install -y iptables-persistent >/dev/null 2>&1 || true
  sudo netfilter-persistent save >/dev/null 2>&1 || true
fi

echo
echo "Done. Log out and back in (docker group), then:"
echo "  git clone https://github.com/bhushanladde02/sharpen.git && cd sharpen"
echo "  cp deploy/.env.example deploy/.env && nano deploy/.env"
echo "  docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build"
