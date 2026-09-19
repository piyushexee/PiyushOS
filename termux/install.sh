#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# PiyushOS Brain - Termux one-time setup (PHONE me, bina PC ke)
# Istemal:  bash termux/install.sh   (repo folder se chalao)
# ============================================================
set -e

echo "📦 Step 1/4: Termux packages install/update ho rahe hain..."
pkg update -y
pkg install -y python libxml2 libxslt git

echo "📦 Step 2/4: Python libraries install ho rahi hain (pehli baar thoda time lagta hai)..."
pip install -r brain/requirements.txt 2>&1 | tail -5

# Agar pydantic-core / numpy wheels phone ke liye available na hon to rust compiler se build karo
if ! python -c "import pydantic_core, numpy" 2>/dev/null; then
    echo "⚙️ kuch libraries ka wheel nahi mila - rust se build karta hu (thoda time lagega)..."
    pkg install -y rust
    pip install -r brain/requirements.txt
fi

echo "📦 Step 3/4: .env ban raha hai..."
if [ ! -f .env ]; then
    cp .env.example .env
    echo ""
    echo "============================================================"
    echo "🔑 AB .env EDIT KARO apni NVIDIA NIM API key ke saath:"
    echo "   nano .env"
    echo "   (LLM_API_KEY= wali line me apni key daalo, save karo: Ctrl+O, Enter, Ctrl+X)"
    echo "   API key yahan se milegi: https://build.nvidia.com  -> 'Get API Key'"
    echo "============================================================"
else
    echo "✅ .env pehle se hai - API key check kar lena: nano .env"
fi

echo "📦 Step 4/4: Test - server start ho raha hai?..."
python -c "
import sys
sys.path.insert(0, '.')
from brain.config import settings
from brain.llm import llm_chat
print('✅ Brain ready! Model:', settings.llm_model, '| Vision:', settings.vision_enabled)
"

echo ""
echo "🎉 Setup done! Ab server chalan ke liye:"
echo "   bash termux/run.sh"
echo ""
echo "⚠️  Zaroori: PiyushOS app chalate waqt Termux me server ON rakhna"
echo "⚠️  App me IP = 127.0.0.1 daalo (kyunki dono ek hi phone me hain)"
