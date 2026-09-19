# 🧠 PiyushOS — Aapka Personal AI Butler for Android

> "PPT bana do", "Excel me budget bana do", "3D model chahiye rocket ka", "LinkedIn profile likh do",
> **"WhatsApp me Rahul ko msg bhejo"**, **"PowerPoint app me live slides banao"**, **"slide 2 ka title badlo"** — bas bolo, **woh karega**.

PiyushOS ek AI agent hai jo do hisse se banta hai:

1. **🧠 Brain** (aapke PC/Laptop par chalta Python server) — aapki command samajhta hai, kaam ki planning karta hai, aur files (PPT/Excel/3D model/LinkedIn text) bana kar phone par bhejta hai
2. **📱 Android App** (aapke phone par) — chat/voice UI deta hai aur phone ko control karta hai (app kholna, tap, swipe, typing, screenshot)

```
 ┌────────────┐   voice/text command    ┌──────────────────────────┐
 │   PHONE    │ ──────────────────────► │        BRAIN (PC)        │
 │ (PiyushOS  │                         │  NVIDIA NIM AI model     │
 │   app)     │ ◄────────────────────── │  (nemotron-3-ultra)      │
 └────────────  reply + files + TTS    │                          │
       ▲                                │  Tools:                  │
       │                                │  • PPT banana (python-pptx)
  Accessibility service                 │  • Excel (openpyxl)      │
  (tap/swipe/type/app)                  │  • 3D model (trimesh)    │
       │                                │  • LinkedIn/resume text  │
  MediaProjection                       │  • phone control cmds    │
  (screenshot)                          └──────────────────────────┘
```

## ✨ Kya-kya karta hai

| Aap bolo (Hinglish) | Wo karega |
|---|---|
| "Ek 10 slide ki PPT banao AI ke baare me" | Professional PPTX banayega + phone par bhejega |
| **"Slide 2 ka title badlo" / "nayi slide add karo"** | PPT EDIT karke dobara bhejega |
| "Excel me mera mahinayana budget banao" | Formatted Excel sheet (headers, filter, colors) |
| **"B3 me 5000 daalo" / "naya row add karo"** | Excel EDIT karke dobara bhejega |
| "Ek rocket ka 3D model banao red me" | GLB (dekhne ke liye) + STL (3D printing ke liye) |
| "LinkedIn profile likho mere liye" | Profile likhega, phone par bhejega + clipboard par copy |
| **"WhatsApp me Rahul ko msg bhejo: kal milte hain"** | WhatsApp kholke, Rahul dhundh ke, message BHEJ dega |
| **"Instagram par aaj ki post like karo"** / "YouTube me X search karo" | App ke andar navigate karke kaam karega |
| **"Phone ke PowerPoint app me 3 slides banao"** | PowerPoint APP me LIVE slides banayega (aapke samne) |
| "Instagram kholo" / "WhatsApp kholo" | App khol dega |
| Koi bhi kahani/email/bio/project description | Likh kar phone par bhejega |

**Voice + Text dono chalti hai** — mic button dabao ya type karo. Reply sunane ke liye bhi TTS on hai.

---

## 🎮 Pura App Control (GUI Agent) — "Astra" wala feature

Yeh PiyushOS ka sabse bada power hai. Jab aap kisi app ke **andar** kuch karne kehte ho, to agent yeh loop chalata hai:

```
┌─────────────────────────────────────────────────────────┐
│ 1. APP Kholo        (whatsapp / instagram / powerpoint) │
│ 2. SCREEN DEkho     (UI tree + screenshot + OCR)        │
│ 3. AI SOCHE         (kaunsa element? kya action?)       │
│ 4. ACTION Lo        (tap / type / swipe / back)         │
│ 5. DOBARA SCREEN    ──→ 2 pe wapas (jab tak kaam na ho) │
└─────────────────────────────────────────────────────────┘
```

**Screen kaise samajhta hai (3 layers):**
1. **UI Tree** — Accessibility service current app ke har element ka text + position (pixels) + clickable/editable status bhejti hai. Yeh sabse fast aur reliable hai
2. **Screenshot + Vision** — AI ko screen ki photo bhi dikhti hai (agar model vision support kare)
3. **OCR (ML Kit)** — jab UI tree me text kam ho (maps/games jaise canvas apps), to on-device OCR se text nikaalta hai

**Example — "WhatsApp me Rahul ko msg bhejo: kal milte hain":**
```
App kholo: whatsapp
Screen dekho → [Search box, Rahul (chat list), ...]
Tap: Search box → Type: "Rahul"
Screen dekho → [Rahul ka chat dikh raha hai]
Tap: Rahul → Type: "kal milte hain" → Tap: Send ✅
```

**PowerPoint LIVE mode:**
- "Phone ke PowerPoint app me 3 slides banao: AI ke 3 use cases" — wo PowerPoint app kholke, template chunke, har slide me title + bullets **aapke samne type karke** banayega
- Default me AI **PPTX file** banata hai (fast + clean). Live mode tab jab aap specifically bolo

**Limitations (honest baat):**
- Text-based UI me (WhatsApp, Gmail, Settings, YouTube, PowerPoint) bahut accha chalta hai
- Canvas/visual-heavy apps (games, kuch maps views) me OCR help karta hai, par wahan accuracy kam ho sakti hai
- Har action ek-ek step me hota hai (1-3 second per step) — 5-10 step ka kaam ~1 minute le sakta hai
- Apps ke UI update hote hain; agar koi app ka design badal jaye to AI ko dobara dekh ke padhna padta hai

---

## 📁 Project Structure

```
piyushos/
├── README.md                  ← yeh file
├── .env.example               ← config template (copy karke .env banao)
├── brain/                     ← 🧠 AI Brain (Python server)
│   ├── main.py                ← FastAPI + WebSocket server
│   ├── agent.py               ← agent loop (LLM + tools)
│   ├── llm.py                 ← NVIDIA NIM client + mock mode
│   ├── config.py              ← .env se settings
│   ├── ws/bridge.py           ← phone ke saath communication
│   ├── registry.py            ← pichli files ka yaad (editing ke liye)
│   ├── tools/
│   │   ├── pptx_tool.py       ← PPT banana
│   │   ├── xlsx_tool.py       ← Excel banana
│   │   ├── edit_tool.py       ← PPT/Excel EDIT (customization)
│   │   ├── model3d_tool.py    ← 3D models
│   │   ├── gui_tool.py        ← 🎮 GUI AGENT (app ke andar kaam)
│   │   ├── share_tool.py      ← LinkedIn/resume text
│   │   └── device_tools.py    ← tap/swipe/type/screenshot commands
│   ├── generated/             ← ban hui files yahan aati hain
│   └── dev/
│       ├── test_tools.py      ← tools ka quick test
│       └── test_e2e.py        ← fake phone se E2E test
└── android/                   ← 📱 Android App (Kotlin + Compose)
    └── app/src/main/java/com/piyushos/app/
        ├── MainActivity.kt            ← chat UI + voice + TTS + screen commands
        ├── ChatScreen.kt              ← Compose UI
        ├── SocketClient.kt            ← WebSocket client
        ├── ShareActivity.kt           ← file share karna
        ├── accessibility/PhoneControllerService.kt  ← tap/swipe/type + UI TREE engine
        ├── media/ProjectionService.kt ← screenshot engine
        ├── media/ScreenOcr.kt         ← on-device OCR (ML Kit)
        └── files/FileSaver.kt         ← files save + notification
```

---

## 🚀 SETUP PART 1 — AI Brain (PC/Laptop)

### 1. Python install karo
Python **3.10+** chahiye: https://www.python.org/downloads/
(Windows me install karte waqt **"Add to PATH"** tick zaroor karo)

### 2. Folder me jao + environment banao

```bash
cd piyushos
python -m venv .venv

# Linux/Mac:
source .venv/bin/activate
# Windows:
.venv\Scripts\activate
```

### 3. Dependencies install karo

```bash
pip install -r brain/requirements.txt
```

### 4. .env banao

```bash
cp .env.example .env      # Windows: copy .env.example .env
```

`.env` ko kholo aur apni **NVIDIA NIM API key** daalo:
- Key kahan milegi: **https://build.nvidia.com** → login → **API Keys** → Create API Key
- `LLM_API_KEY=nvapi-xxxxxxxx` yeh daalo
- Model pehle se set hai: `nvidia/nemotron-3-ultra-550b-a55b`

### 5. Test karo (bina API key ke)

```bash
# Linux/Mac
LLM_MOCK=1 python -m brain.main
# Windows (PowerShell)
$env:LLM_MOCK=1; python -m brain.main
```

Mock mode me dimaag fake hai par pura pipeline (phone connect, commands, files) test hota hai.

### 6. LIVE mode me chalao

```bash
python -m brain.main
```

Screen par yeh dikhega:

```
🧠 PiyushOS Brain - aapka personal AI butler server
  Model : nvidia/nemotron-3-ultra-550b-a55b
  Port  : 8787
  Token : piyush123
Android app me yahi IP : Port : Token daal ke connect karo.
```

> ⚠️ **PC ka IP dhundo** (phone aur PC ek hi WiFi par hone chahiye):
> - **Windows**: CMD me `ipconfig` → IPv4 Address (jaise `192.168.29.1`)
> - **Linux/Mac**: terminal me `ifconfig` ya `ip addr`
>
> Agar connect na ho to Windows Firewall me Python ke liye **Inbound Rule** allow karna (port 8787, TCP).

---

## 📱 SETUP PART 2 — Android App

### 1. App build karo

**Tarika A (aasan) — Android Studio:**
1. [Android Studio](https://developer.android.com/studio) install karo (agar nahi hai)
2. `piyushos/android` folder ko **Open** karo → sync hone do
3. Phone ko USB se lagao (Developer Options me **USB Debugging** on) → **Run ▶** button dabao

**Tarika B — APK banao:**
1. Android Studio me: **Build → Build Bundle(s)/APK(s) → Build APK(s)**
2. APK `app/build/outputs/apk/debug/app-debug.apk` me milegi
3. Phone par bhejo (WhatsApp/Drive) aur install karo

### 2. App kholo + Connect karo

1. **IP** daalo (PC ka, jaise `192.168.29.1`)
2. **Port** `8787`
3. **Token** `piyush123` (jo `.env` me `DEVICE_TOKEN` hai)
4. **CONNECT** dabao → "✅ Connect ho gaya" dikhega

### 3. Accessibility ON karo (phone control ke liye)

1. App me **🦾 Accessibility ON** dabao
2. Settings khulengi → **Accessibility → PiyushOS** → **ON** karo
3. "Allow" / "Access allowed" dabao

> Yeh permission isi kaam aati hai: aapke commands par tap/swipe/typing karna. Koi data kisi aur ke paas nahi jaata.

### 4. Screenshot ON karo (AI ko screen dikhane ke liye)

1. App me **📸 Screenshot ON** dabao
2. Phone kaagaz dikhayega "Is app ko screen dikhane ki permission do?" → **START** dabao

> Phone restart hone par yeh ek baar dobara dena padta hai.

### 5. Party shuru! 🎉

Type karo ya mic dabao:
- *"Ek 8 slide ki PPT banao renewable energy par"*
- *"Ab slide 3 ka title badlo: Future of Energy"*
- *"Excel me 5 dostu ka khata banao"*
- *"B3 me 5000 daalo"*
- *"House ka 3D model banao"*
- *"Mera LinkedIn about section likho — AI engineer hoon Sitapur se"*
- *"Instagram kholo"*
- *"WhatsApp me Rahul ko msg bhejo: kal milte hain"*
- *"Phone ke PowerPoint app me 3 slides banao AI ke baare me"*

Files **Downloads → PiyushOS** folder me aa jati hain, aur notification me **Share** ka button hota hai (WhatsApp/Email par bhej sakte ho).

> 💡 **GUI commands (app ke andar kaam) ke liye Accessibility ON hona zaroori hai.** Screenshot permission ho to AI ko screen photo bhi dikhti hai — bina uske bhi UI tree se zyada tar kaam chalta hai.

---

## 🧪 Phone ke bina test karna

Server chala ke doosre terminal me:

```bash
python -m brain.dev.test_e2e      # fake phone se 5 commands ka E2E test
```

Aur sirf tools ka test:

```bash
python -m brain.dev.test_tools    # PPT/Excel/3D models banake verify karta hai
```

---

## 🔧 Troubleshooting

| Problem | Solution |
|---|---|
| App connect nahi hoti | (1) PC + phone **ek hi WiFi** par? (2) PC ka IP sahi? (3) Windows Firewall me port **8787** allow? (4) Server terminal me "📱 Phone connect hua" toh dikha? |
| "Ghalat token" | `.env` ka `DEVICE_TOKEN` aur app ka token match karo |
| AI reply nahi karta / error | `.env` me `LLM_API_KEY` sahi hai? `LLM_MOCK=1` se pehle test karo. Server log dekho |
| "tools not supported" jaisa error | Kuch purane models tool-calling nahi karte. `.env` me model badlo, e.g. `LLM_MODEL=nvidia/llama-3.1-nemotron-70b-instruct` |
| Screenshot nahi aa raha | "Enable Screenshots" dobara dabao (phone restart ke baad token expire hota hai) |
| Typing kaam nahi karta | Pehle AI se bolo input field par tap karo, phir type karo |
| App nahi khulti | AI ko exact app naam bolo (jaise "chrome" na bol ke "google chrome") |
| GUI task ruk gaya / galti ho gayi | Dobara bolo — AI naya screen dekh ke fresh sochta hai. Complex kaam chhote hisson me bolo |
| GUI task slow lag raha | Har step me 1-3 second lagte hain — screen dekhna + sochna + action. Yeh normal hai |
| Banking app me kuch nahi hota | **Android ki security** — banking/payments apps par automation block hota hai. Yeh normal hai, koi bug nahi |
| 3D model kahan dekho | Phone: "3D Model Viewer" / "Scene Viewer" app (Play Store). PC: Windows 3D Viewer ya https://gltf-viewer.donmccurdy.com |

---

## 🔒 Safety & Privacy

- Server aapke **apne PC** par chalta hai — phone se sirf commands jaati hain aapke hi network me
- Kisi bhi third-party server par screen/data **nahi** jaata
- Token (`DEVICE_TOKEN`) change karke kisi aur ke connect hone se rok sakte ho
- AI sirf tabhi phone control karta hai jab aapne bola hai

## 🗺️ Aage ke ideas

- **Better OCR**: ML Kit ke alawa screen regions ka targeted OCR
- **Root mode**: rooted phone par ADB/shell se aur gahra control (har app, har corner)
- **Zyada 3D shapes**: Blender connect karke koi bhi custom model
- **Multi-device**: ek PC se kai phones
- **Memory**: agent ko yaad rahe ki aapko kya pasand hai (aapke contacts, frequent tasks)
- **Self-learning**: jo GUI tasks baar-baar kaam karte hain unko shortcuts banana

---

**Banaya gaya: Piyush ke liye, PiyushOS se 💙**
