# RawBT Clone

This is an Android application that acts as a printer bridge for thermal Bluetooth printers, similar to the original RAWBT app. It supports ESC/POS commands and can receive print jobs from other applications via Android Intents or an HTTP server.

## 🚀 Features

-   **Bluetooth Connectivity**: Connects to classic Bluetooth (SPP) thermal printers.
-   **ESC/POS Support**: A comprehensive `EscPosBuilder` for generating print commands.
-   **Intent API**: Allows other apps (like those made with React Native) to send print jobs.
-   **Local HTTP Server**: An optional background service that listens for print jobs on `http://127.0.0.1:8080/print`.
-   **Simple UI**: For scanning, pairing, and selecting a printer.

## 🛠️ How to Build an APK

1.  **Using Android Studio**:
    *   Open Android Studio.
    *   Select "Open an Existing Project".
    *   Navigate to this project's root directory and click "OK".
    *   Android Studio will automatically sync the Gradle project.
    *   From the top menu, go to `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`.

2.  **Using `gradlew` (Command Line)**:
    *   Navigate to the project's root directory in your terminal.
    *   To build a debug APK: `./gradlew assembleDebug`
    *   The generated APK will typically be found at `app/build/outputs/apk/debug/app-debug.apk`.

3.  **Locate the APK**:
    *   After a successful build (either via Android Studio or `gradlew`), the APK file (`app-debug.apk` for debug builds) will be located in the `app/build/outputs/apk/debug/` directory.

## 📱 How to Use

### 1. From React Native (or any other app) via Intent

You can trigger a print job by sending a `Broadcast Intent`.

-   **Action**: `com.rawbtclone.PRINT`
-   **Extras**:
    -   `type`: (String) `text` or `json`.
    -   `data`: (String) The content to print.

#### Example: Plain Text

```javascript
import { NativeModules } from 'react-native';

const { IntentLauncher } = NativeModules;

const printText = () => {
  IntentLauncher.sendBroadcast({
    action: 'com.rawbtclone.PRINT',
    extras: [
      { key: 'type', value: 'text' },
      { key: 'data', value: 'Hello World!
This is a test print.' },
    ],
  });
};
```
*(Note: You might need a small native module in your React Native app to send broadcast intents like `react-native-send-intent` or similar).*

#### Example: JSON Data

The JSON data should be an array of command objects.

```javascript
const printReceipt = () => {
  const printData = [
    { type: 'text', text: 'My Store', align: 'center', bold: true, size: 'double' },
    { type: 'text', text: '123 Main St', align: 'center', newline: true },
    { type: 'text', text: '--------------------------------', newline: true },
    { type: 'text', text: 'Item A', align: 'left' },
    { type: 'text', text: '10.00', align: 'right', newline: true },
    { type: 'qr', text: 'your-data-here', align: 'center', newline: true },
    { type: 'cut' },
  ];

  IntentLauncher.sendBroadcast({
    action: 'com.rawbtclone.PRINT',
    extras: [
      { key: 'type', value: 'json' },
      { key: 'data', value: JSON.stringify(printData) },
    ],
  });
};
```

### 2. Via HTTP Server

The app runs a local server in the background. You can send a POST request to it.

-   **URL**: `http://127.0.0.1:8080/print`
-   **Method**: `POST`
-   **Body**: The same JSON payload as used in the Intent API.

#### Example: `curl`

```bash
curl -X POST 
  http://127.0.0.1:8080/print 
  -H 'Content-Type: application/json' 
  -d '[
    {"type": "text", "text": "HTTP Print Test", "align": "center", "bold": true},
    {"type": "text", "text": "This is a test from curl.", "align": "center", "newline": true},
    {"type": "cut"}
  ]'
```

## 📂 Project Structure

```
.
├── app
│   ├── build.gradle
│   └── src
│       └── main
│           ├── AndroidManifest.xml
│           ├── java/com/rawbtclone
│           │   ├── MainActivity.kt
│           │   ├── bluetooth/
│           │   ├── models/
│           │   ├── receivers/
│           │   ├── services/
│           │   └── utils/
│           └── res/
│               ├── layout/
│               └── values/
├── build.gradle
└── settings.gradle
```
