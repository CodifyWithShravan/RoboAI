# RoboAI - Advanced Android AI Assistant 🤖

RoboAI is a cutting-edge Android application that transforms your device into an intelligent, emotionally responsive robot. Powered by **TensorFlow Lite**, **MediaPipe**, and **Sensor Fusion**, RoboAI perceives the world through vision, sound, and movement, reacting with a dynamic, vector-based facial interface.

<div align="center">
  <h3>👁️ See • 👂 Hear • 🧠 Think • ❤️ Feel</h3>
</div>

## ✨ Key Features

### 🎭 Expressive RoboFace
A fully procedural, canvas-drawn robot face that reacts in real-time.
- **6+ Emotions**: Neutral, Happy, Angry, Surprised, Sad, Listening, and Sleep.
- **Dynamic Animations**: Smooth transitions, blinking, looking around, and audio-reactive mouth visualization.
- **Vector Graphics**: Crisps rendering at any resolution using Jetpack Compose Canvas.

### 🧠 TFLite AI Brain
Run powerful machine learning models directly on-device.
- **Emotion Recognition**: Analyzes facial landmarks to detect user emotions (Happy, Angry, Surprised, etc.).
- **Hardware Acceleration**: Switch between **CPU**, **GPU**, and **NNAPI** delegates for optimal performance.
- **Smart Optimization**: Automatically pauses inference when the robot sleeps to conserve battery.
- **Mock Fallback**: Includes a robust simulation mode for testing UI flows even without a custom model file.

### 📱 Sensor Fusion
The robot is aware of its physical environment.
- **Tilt Control**: Eyes naturally follow the device's orientation (gravity-aware).
- **Shake Detection**: Violently shaking the device makes the robot **Angry**.
- **Proximity Awareness**: Covering the top sensor puts the robot to **Sleep**; uncovering or tapping wakes it up instantly.

### 🔊 Audio Intelligence
- **Loud Noise Trigger**: Sudden loud sounds startle the robot into a **Surprised** state.
- **Speech Visualization**: The mouth transforms into an audio equalizer when speech is detected.

## 🏗️ Architecture

RoboAI behaves like a living creature thanks to a robust **Finite State Machine (FSM)** architecture.

- **Stack**: 100% Kotlin, Jetpack Compose, CameraX, MediaPipe, TensorFlow Lite.
- **Pattern**: MVVM (Model-View-ViewModel) with Unidirectional Data Flow (UDF).
- **Core Components**:
  - `RoboStats`: Sealed class defining all possible emotional states.
  - `RoboSignal`: Event system handling inputs from sensors, AI, and audio.
  - `RoboViewModel`: The central brain processing signals and managing state transitions.
  - `TFLiteClassifier`: Coroutine-managed background inference engine.

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug or later.
- Android Device (Min SDK 24).
- Physical device recommended for Camera/Sensor testing.

### Installation
1.  **Clone the repository**:
    ```bash
    git clone https://github.com/yourusername/RoboAI.git
    ```
2.  **Open in Android Studio**:
    Sync Gradle project.
3.  **(Optional) Add Custom Model**:
    Place your quantized TFLite model (`robo_classifier.tflite`) in `app/src/main/assets/`. If omitted, the app runs in **Mock Mode** (Simulation).
4.  **Run**:
    Deploy to your Android device. Grant **Camera** and **Microphone** permissions.

## 🎮 Interaction Guide

| Interaction | Action | Robot Reaction |
|---|---|---|
| **Smile** | Smile at the camera | **Happy** (Yellow/Green, Bouncing) |
| **Shake** | Shake the phone | **Angry** (Red, Pulsing) |
| **Surprise** | Open mouth wide / Clap | **Surprised** (Orange, Wide Eyes) |
| **Sleep** | Cover top proximity sensor | **Sleep** (Dimmed, Eyes Closed, AI Paused) |
| **Wake** | Tap screen / Uncover sensor | **Wake** (Resumes Activity) |
| **Talk** | Speak to the robot | **Listening** (Purple, Equalizer Mouth) |

## 🐞 Debugging

Tap the **"RoboAI"** title at the top of the screen to open the **Debug Overlay**:
- View real-time **Inference Latency** (ms).
- See the raw **User Emotion** and confidence score.
- Manually switch AI Delegates (**CPU**, **GPU**, **NNAPI**).
- Toggle **Landmark Overlay** to see what the robot sees.

## 📄 License
This project is open-source and available under the MIT License.
