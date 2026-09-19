#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# PiyushOS Brain - server start (phone me hi)
# Istemal:  bash termux/run.sh
# Rokne ke liye: Ctrl+C
# ============================================================
set -e

# phone ko sleep mat hone do (server chalte waqt)
termux-wake-lock 2>/dev/null || true

cd "$(dirname "$0")/.."

# dono ek hi phone me hain isliye 127.0.0.1 (same phone) use karo
export HOST=127.0.0.1

echo "🧠 PiyushOS Brain start ho raha hai: ws://127.0.0.1:${PORT:-8787}"
echo "   PiyushOS app me IP=127.0.0.1, Port=8787 daal ke CONNECT dabao."
echo "   (Is terminal ko band mat karna jab tak app use kar rahe ho)"
echo ""

python -m brain.main
