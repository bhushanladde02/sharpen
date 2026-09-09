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

# Small machine (1 GB Micro): add a 2 GB swap file so memory spikes swap instead of killing the JVM.
mem_kb=$(grep MemTotal /proc/meminfo | awk '{print $2}')
if [ "$mem_kb" -lt 2500000 ] && ! swapon --show | grep -q swapfile; then
  sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
  echo 'vm.swappiness=20' | sudo tee /etc/sysctl.d/99-swap.conf >/dev/null && sudo sysctl -q -p /etc/sysctl.d/99-swap.conf
  echo "Added a 2 GB swap file (machine has $((mem_kb / 1024)) MB RAM)."
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
echo "  cp deploy/.env.example deploy/.env && nano deploy/.env    # on a 1 GB Micro add SMALL_VM=true"
echo "  APP_IMAGE=ghcr.io/bhushanladde02/sharpen:latest bash deploy/remote-deploy.sh   # pull the pipeline's image"
echo "  (or, on a machine with >= 4 GB: docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build)"
