🐍 PythonIDEv02 (Android Python IDE)

An Android-based Python IDE built with Sora CodeEditor and Chaquopy, providing syntax highlighting, a built-in Python runtime, and a lightweight coding environment directly on your phone.


---

✨ Features

📝 Code Editor powered by Sora

Syntax highlighting via TextMate grammars

Customizable themes (quietlight.json included)

Monospaced font, line numbers, and whitespace markers


🐍 Run Python Code

Integrated with Chaquopy

Executes code directly inside the app

Outputs captured in a scrollable console


⌨️ Custom Keyboard Bar for fast coding:

Quick insert of Tab, ", ', (), :, #, @, $, etc.

Wraps selections automatically


🎨 TextMate Theme + Grammar Support

Ships with quietlight.json theme

Grammar loaded from languages.json in assets/textmate/




---

📂 Project Structure

app/
 ├── src/main/assets/textmate/
 │    ├── quietlight.json        # Theme definition
 │    ├── languages.json         # Grammar definitions
 │    └── python.tmLanguage.json # Python TextMate grammar
 ├── java/com/endyaris/pythonidev02/
 │    └── SoraActivity.kt        # Main editor activity
 ├── res/layout/activity_sora.xml # UI layout with editor + console


---

🚀 Getting Started

1. Clone the repo:

git clone https://github.com/your-username/PythonIDEv02.git
cd PythonIDEv02


2. Open in Android Studio (or AndroidIDE).


3. Add Chaquopy to your build.gradle.kts:

plugins {
    id("com.chaquo.python") version "15.0.1"
}


4. Run on Android device.




---

🛠 Requirements

Android 7.0+

Gradle 8+

Kotlin DSL build system

Chaquopy for Python runtime



---

---

❤️ Support & Donations

If you like this project and want to support further development, you can donate Solana(SOL), Bitcoin(BTC) or Ethereum (ETH):

[![SOL]([https://solana.org/](https://solana.org/_next/static/media/logo.2e4a7507.svg))]Solana Address:
d5L3ihSWp8EDTVmeFFWFVgibpcS4snUBc5WrBxDp2sb

💳 Bitcoin Address: bc1qm664xtdn7ehr6r6tfp4ye8g7mfka5afhwqsun8

💳 Ethereum Address: 0x5fcfB93Bf2D06F9E339D26D557Fb39663b54e7D7

[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/donate/?hosted_button_id=YOUR_BUTTON_ID)

