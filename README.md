# 🧠 PiyushOS — Aapka Personal AI Butler for Android

> "PPT bana do", "Excel me budget bana do", "3D model chahiye rocket ka", "LinkedIn profile likh do",
> **"WhatsApp me Rahul ko msg bhejo"**, **"PowerPoint app me live slides banao"**, **"slide 2 ka title badlo"** — bas bolo, **woh karega**.

PiyushOS ek AI agent hai jo do hisse se banta hai:

1. **🧠 Brain** (aapke PHONE par **Termux** me chalta Python server) — aapki command samajhta hai, kaam ki planning karta hai, aur files (PPT/Excel/3D model/LinkedIn text) bana kar phone par bhejta hai
2. **📱 Android App** (aapke phone par) — chat/voice UI deta hai aur phone ko control karta hai (app kholna, tap, swipe, typing, screenshot)

> 💻 **PC hai to bhi chalega** — brain PC par bhi chala sakte ho (neeche optional section me hai). Par poora system **sirf ek phone** par bhi chalta hai.

```
 ┌──────────────────────────────── PHONE ────────────────────────────────┐
 │  ┌─────────────┐  voice/text command  ┌────────────────────────────┐ │
 │  │  PiyushOS   │ ───────────────────► │   🧠 BRAIN (Termux)        │ │
 │  │     App     │ ◄─────────────────── │   NVIDIA NIM AI model      │ │
 │  └─────────────┘  reply + files + TTS │   (nemotron-3-ultra)       │ │
 │        ▲                              │                            │ │
 │        │                              │  Tools:                    │ │
 │  Accessibility service                │  • PPT banana (python-pptx)│ │
 │  (tap/swipe/type/app)                 │  • Excel (openpyxl)        │ │
 │        │                              │  • 3D model (trimesh)      │ │
 │  MediaProjection                      │  • LinkedIn/resume text    │ │
 │  (screenshot)                         │  • GUI agent + phone cmds  │ │
 │                                       └────────────────────────────┘ │
 └──────────────────────────────────────────────────────────────────────┘
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

**Koi bhi task, koi bhi app:** Agent pehle task ka **plan** banata hai (3-10 chhote steps), phir har step par screen dekh kar verify karta hai. App-specific "cheat sheets" (WhatsApp/Instagram/YouTube/Gmail/Maps/PowerPoint/Settings ke layout hints) isme built-in hain — isliye naye tasks par bhi zyada reliable hai.

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
├── termux/                    ← 📲 PHONE setup scripts (PC chahiye nahi)
│   ├── install.sh             ← one-time setup (Termux me chalao)
│   └── run.sh                 ← brain server start
└── android/                   ← 📱 Android App (Kotlin + Compose)
    └── app/src/main/java/com/piyushos/app/
        ├── MainActivity.kt            ← chat UI + voice + TTS + screen commands
        ├── ChatScreen.kt              ← Compose UI
        ├── CrashLogger.kt             ← crash log (PC ke bina debug)
        ├── SocketClient.kt            ← WebSocket client
        ├── ShareActivity.kt           ← file share karna
        ├── accessibility/PhoneControllerService.kt  ← tap/swipe/type + UI TREE engine
        ├── media/ProjectionService.kt ← screenshot engine
        ├── media/ScreenOcr.kt         ← on-device OCR (ML Kit)
        └── files/FileSaver.kt         ← files save + notification
```

---

## 📲 APK Direct Download (Android Studio ke bina!)

**GitHub Actions har push par APK khud bana deta hai.** Aapko Android Studio ki zaroorat hi nahi:

1. [github.com/piyushexee/PiyushOS/actions](https://github.com/piyushexee/PiyushOS/actions) kholo
2. **Android APK Build** workflow me latest **green run** pe click karo
3. Neeche **Artifacts** me `PiyushOS-Android-APK` dikhayega — uspe click karo → **download**
4. Zip kholege to `app-debug.apk` milega → phone par bhej ke install karo
   *(Settings me "Unknown sources" allow karna padega)*

> Naya code push karoge to GitHub Actions 5-10 min me naya APK bana dega. 🤖

---

## 🚀 SETUP PART 1 — AI Brain (PHONE me hi, bina PC ke! 📲)

Brain **Termux** me chalta hai — phone par ek Linux jaisa terminal. Pura system ek phone par hi chalta hai.

### 1. Termux install karo

1. **F-Droid** se **Termux** install karo (Play Store wali build purani hai, mat use karna):
   - F-Droid: https://f-droid.org/packages/com.termux/
   - (F-Droid app khud Play Store me hai)
2. (Optional) **Termux:API** bhi install karo — same F-Droid se

### 2. Repo phone par lao

Termux kholo aur yeh daalo:

```bash
pkg install -y git
git clone https://github.com/piyushexee/PiyushOS.git
cd PiyushOS
```

> (Agar repo ka zip download kiya hai, usse Termux ke home folder me extract karna — jahan `.git`/`brain` folder hai, wahin se aage badho)

### 3. One-time setup

```bash
bash termux/install.sh
```

Yeh khud karega: ✅ Termux me Python install → ✅ PiyushOS ki Python libraries install → ✅ `.env` file bana → ✅ test.
(Pehli baar 5-10 min lag sakte hain — libraries download hoti hain)

### 4. Apni API key daalo

```bash
nano .env
```

- `LLM_API_KEY=` wali line me apni **NVIDIA NIM key** daalo (`nvapi-...` se shuru hoti hai)
- Key kahan milegi: **https://build.nvidia.com** → login → **API Keys** → Create API Key
- Model pehle se set hai: `nvidia/nemotron-3-ultra-550b-a55b`
- Save: `Ctrl+O` → Enter | Exit: `Ctrl+X`

### 5. Brain chalao

```bash
bash termux/run.sh
```

Screen par dikhega:

```
🧠 PiyushOS Brain start ho raha hai: ws://127.0.0.1:8787
   PiyushOS app me IP=127.0.0.1, Port=8787 daal ke CONNECT dabao.
```

⚠️ **Is terminal ko band mat karo** jab tak app use kar rahe ho. Screen lock kar sakte ho (wake-lock laga deta hai).

### 6. Bina API key ke pehle test (optional)

```bash
LLM_MOCK=1 bash termux/run.sh
```

Mock mode me dimaag fake hai par pura pipeline (connect, commands, files) test ho jata hai.

> 🔋 **Battery masla na ho**: Settings → Apps → Termux → Battery → **Unrestricted** kar do.
>
> 💡 Phone ka mobile data/WiFi chalu raha karo — AI ko NVIDIA server se baat karni hai.

---

### 💻 Optional: PC/Laptop hai to wahan bhi chala sakte ho

```bash
# PC par (Python 3.10+ chahiye)
cd piyushos
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r brain/requirements.txt
cp .env.example .env             # fir .env me API key daalo
python -m brain.main
```

PC me chalane par phone + PC **ek hi WiFi** par hone chahiye, aur app me PC ka IP daalna hai
(Windows: CMD me `ipconfig` → IPv4 Address). Windows Firewall me port **8787** allow karna pad sakta hai.

---

## 📱 SETUP PART 2 — Android App

### 1. App phone par install karo

**Tarika A (sabse aasan) — GitHub Actions se APK:**
1. [github.com/piyushexee/PiyushOS/actions](https://github.com/piyushexee/PiyushOS/actions) → latest green run → **Artifacts** → `PiyushOS-Android-APK` → download
2. Phone par bhejo aur install karo

**Tarika B — Android Studio (developers ke liye):**
1. [Android Studio](https://developer.android.com/studio) me `android` folder **Open** karo
2. Phone ko USB se lagao (Developer Options me **USB Debugging** on) → **Run ▶** dabao

### 2. App kholo + Connect karo

1. **IP** daalo — Termux me brain chalane par: **`127.0.0.1`** (dono ek hi phone me hain)
   - (PC me brain chalate ho to PC ka IP, jaise `192.168.29.1`)
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
| **App khulte hi crash ho jaye** ("keeps stopping") | App dobara kholo — red **crash card** dikhega (crash ka reason likha hoga). **Copy crash log** dabao aur log bhej do — fix turant aayega. Naya APK download karke install karna bhi try karo |
| App connect nahi hoti | (1) Brain server (Termux) chal raha hai? (2) IP sahi? — Termux me **`127.0.0.1`**, PC me PC ka IP (3) Token match? (4) Termux terminal me "📱 Phone connect hua" toh dikha? |
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

- Brain server aapke **apne phone** (Termux) par chalta hai — phone se sirf commands jaati hain aapke hi device par
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
