
# Technical Design Document: Pametni Audio Navigacioni Sistem (BLE Beacon)

---

## Product Overview

### Purpose

Blind and visually impaired individuals frequently face unique challenges navigating urban environments, especially in micro-locations where precision is vital (e.g., crosswalks, stairs, entrances, poles, bus stops). GPS-based solutions lack the necessary accuracy in these contexts, particularly indoors or where precise, immediate contextual awareness is required.

**This system addresses these issues by:**

* Using Bluetooth Low Energy (BLE) beacons to communicate rich, compact metadata about nearby critical points and obstacles directly to users' smartphones, within the limitations of BLE protocol.
* Empowering users with timely, context-aware audio notifications via Text-To-Speech (TTS), enabling independent and safe mobility.

**Example User Scenarios:**

* A visually impaired person approaches a crosswalk beacon. Their receiver device immediately emits a TTS audio alert: "Pešački prelaz ispred vas."
* Another user approaches a flight of stairs; before reaching the first step, the receiver announces: "Pažnja, stepenice."

### Target Audience

**Primary User Roles:**

1. **Beacon Operator**
  * Installs and configures beacon devices (Android smartphones primarily; laptops are a secondary, less reliable option—see Architecture).
  * Needs: Simple beacon setup, local data persistence, reliable broadcast with minimal technical complexity.


2. **End User (Blind or Visually Impaired Person)**
  * Relies on a smartphone in receiver mode for real-time, non-intrusive audio notifications about immediate surroundings.
  * Needs: Offline functionality, fast and reliable signal detection, customizable audio alerts, stability, and intuitive interaction.

**Pain Points Addressed:**

* Eliminates reliance on inaccurate GPS or internet connectivity.
* Lower risk of accidents or missed waypoints.
* Empowers blind and visually impaired users with greater self-reliance in cities.

### Expected Outcomes

**Tangible Benefits:**

* Technological independence for receivers (no network dependency).
* Measurable improvement in micro-location awareness.
* Safer and faster navigation for the visually impaired.

**Intangible Benefits:**

* Enhanced dignity and autonomy for users.
* Public demonstration of inclusive urban innovation.

**Key Success Metrics & KPIs:**

* **Detection Latency:** TTS fires within <2 seconds of entering beacon proximity.
* **Offline Capability:** 100% core functionality without internet.
* **Stability:** App never crashes due to BLE signal loss or OS-level Bluetooth changes.
* **Demo Readiness:** 3–5 urban demo beacon points, each reliably recognized.

**Short-Term Impact:**

* Functional MVP deployed at a hackathon, with robust demo situations across multiple site types (crosswalk, stairs, pole, entrance, bus stop).

**Long-Term Impact:**

* Citywide deployment with an admin dashboard, networked monitoring, and cloud-based configuration expansion.

---

## Architecture

### High-Level Architecture

The system is a **single Android application** built with Kotlin and Jetpack Compose, offering **two operational modes:**

* **Beacon Mode**
  * Launches BLE Advertiser API to broadcast compact, encoded metadata about the current urban point. Due to BLE advertisement payload limitations (\~31 bytes), only highly compact fields (beaconId, pointType, priority, messageCode) are broadcast, not full text descriptions.
  * Data encoded in manufacturer-specific BLE advertisement packets.
  * Local configuration/state persisted with DataStore.


* **Receiver Mode**
  * Activates BLE Scanner API to detect advertisements in the local vicinity.
  * Parses received BLE data, extracts meaningful metadata, evaluates proximity (using stabilized RSSI logic), applies cooldown logic, and triggers TTS audio output if all conditions are met.
  * Updates local state and cooldown logs using DataStore.

**Key Characteristics:**

* **Offline-first:** No backend (e.g., REST, GraphQL, WebSocket) required for MVP; all data local.
* **Communication:** Pure one-way BLE broadcast (beacon → receiver).
* **Persistence:** DataStore for lightweight, key-value entity storage.

> **Device Requirements:**  
>
> **Primary beacon devices are Android smartphones.** While laptops may theoretically operate as beacons, BLE advertising support is dependent on OS-level features (macOS, Windows), hardware drivers, and background process limitations. This makes laptops unreliable for live demo purposes; they should only be used as a secondary, optional experiment and not as the core demo units.

Robustness & Error Handling

* **Retry Logic:** If BLE scanning stops unexpectedly (OS-level interrupts, background kills), the app will attempt to restart scanning after a short delay (e.g., 2–5 seconds), ensuring robustness in variable runtime conditions.
* **Bluetooth State Handling:** If Bluetooth is turned off while scanning, the app will show a user-friendly prompt and gracefully pause operations (no crash or data loss).
* **Invalid Packet Handling:** Any BLE advertisement not matching the expected protocol ID or missing required fields is silently discarded; malformed payloads are ignored without triggering any state changes or app instability.
* **TTS Fallback:** If the device's TTS service fails to initialize or becomes unavailable, the app will continue operation, log the error, and skip audio playback for that event, rather than crashing.

---

### Why BLE-Based Micro-Location is Superior to GPS for This Use Case

GPS typically achieves accuracy in the range of 3–5 meters outdoors and degrades drastically indoors or near large buildings. This margin is far too wide to reliably distinguish between a crosswalk, a staircase, or a narrow entrance—especially in dense urban areas, or at indoor transition points where even a one-meter error can mean missing a vital crossing or obstacle.

BLE beacons enable proximity detection within a precise range of physical distances (commonly 1–3 meters), allowing the system to provide contextual, location-specific audio alerts with accuracy and real-time responsiveness inaccessible to GPS-based systems. BLE also operates fully offline, requiring no network connection, no GPS lock, and no dependency on satellite visibility—critical for consistent performance both outdoors and indoors. This ensures low-latency, reliable, and privacy-preserving service for users anywhere, anytime, making BLE the preferred technology for these safety-critical micro-location scenarios.

---

### Data Structures & Algorithms

BLE Payload Size Limitation

Bluetooth Low Energy advertisements support a **maximum payload of \~31 bytes**, a hard constraint defined by the BLE protocol itself. Attempting to transmit long or verbose strings (e.g., full TTS messages) within this space is both technically unreliable and incompatible with cross-device BLE standards.

**Design Solution:**  

The beacon **does not transmit the full 'message' string**. Instead, it transmits a compact `messageCode: Short`, aiming for a 2-byte integer. The receiving application locally maintains a mapping table from `(pointType, messageCode)` to the appropriate, natural-language TTS message. This keeps the BLE packet small, reliable, and future-proof, while preserving the flexibility to support many message types and languages in-app.

**This is a carefully chosen compromise:**  

It adheres to the Model B principle of broadcasting rich, structured metadata (not just an ID), but is fully aligned with the technical constraints of BLE advertisement hardware and standards.

Example: messageCode Mapping Table

| messageCode | Sample TTS Output – Serbian |
| --- | --- |
| 1 | "Pešački prelaz ispred vas." |
| 2 | "Pažnja, stepenice." |
| 3 | "Pažnja, stub u blizini." |
| 4 | "Ulaz u objekat sa desne strane." |
| 5 | "Autobusko stajalište ispred vas." |

Local tables in the receiver app can be easily expanded, internationalized, or customized per deployment.

Core Data Structures

| Data Structure | Rationale |
| --- | --- |
| BeaconConfig | Encapsulates all local state and broadcast fields. |
| DetectedBeaconEvent | Ensures detailed logs for debugging and demo. |
| ReceiverState | Centralizes scan state and last audio replay. |
| CooldownEntry | Prevents excessive repeat notifications. |

**Updated Data Model for BLE Payload:**

* Only the following fields are encoded and transmitted:
  * beaconId
  * pointType (as compact integer/enum)
  * priority (as compact integer/enum)
  * messageCode

**Advanced BLE Payload Encoding:**  

Compact binary encoding or protocol buffers (if space allows) are used to structure these four fields into the 31-byte packet, maximizing efficiency and minimizing the parsing workload at the receiver.

RSSI-based Proximity: Enhanced Stability

**RSSI (Received Signal Strength Indicator) is inherently unstable**, responding sensitively to environmental factors like interference, obstacles, and device orientation. Relying on a single threshold (e.g., -75 dBm) leads to noisy or accidental triggers.

**Algorithmic Solution:**  

The receiver must observe **N consecutive packets with RSSI above the configured threshold** (e.g., at least 2 or 3 valid readings), before confirming beacon proximity and triggering a TTS announcement. This debounce/hysteresis step ensures stability while still offering fast reaction times.

**Summary of RSSI Handling:**

* Each detected BLE packet is compared to the RSSI threshold.
* Only if (>N) consecutive packets for the same beacon exceed the threshold is the user alerted.
* This approach filters out outliers and environmental jitter, improving practical reliability.

Key Algorithms

1. **BLE Payload Encoding/Decoding:** Highly compact representation (integer codes) for all beacon fields.
2. **Stabilized RSSI Proximity Detection:** N consecutive valid RSSI readings above threshold are required for event trigger.
3. **Cooldown Timer:** Per-beacon, with local persistence, to avoid announcement spam.
4. **(pointType, messageCode) to TTS Mapping:** Fast, local translation to natural voice message; easily updatable for new use cases.

---

### System Interfaces

* **Android BLE Advertiser API:** For BLE beacon broadcasting of the compact 4-field payload.
* **Android BLE Scanner API:** For receiving only protocol-matching, well-structured packets.
* **Android TextToSpeech API:** Converts messageCode/pointType mapped text into clear spoken announcements.
* **Android DataStore:** For all application, state, and config persistence.
* **Internal Module Workflow:**
  * UI Layer → ViewModel → BLE Service (Advertiser/Scanner) → TTS Service

---

### User Interface

**Three Core Screens (Aligned to Data Model):**

1. **Mode Selection Screen**
  * Highly accessible layout: "Beacon Mode" and "Receiver Mode" buttons.
  * Compliant with accessibility best practices.


2. **Beacon Config Screen**
  * Edit fields:
    * Label (string, local only; for operator's reference, *not* included in BLE packet)
    * PointType (dropdown; enum selection)
    * Priority (dropdown; enum selection)
    * Message Code (dropdown; select from list: e.g., 1 = crosswalk, 2 = stairs, etc.)
  * Clear visual display of currently configured message code, label, and status.
  * Start/Stop Broadcasting toggle.
  * **Clarification:** The label/description is for operator usability and demo context only; only messageCode (plus beaconId, pointType, priority) is broadcast in the BLE payload.


3. **Receiver Screen**
  * Status: "Scanning"/"Not Scanning"
  * Display of last detected point:
    * Label (if available locally)
    * Type and priority
    * Decoded message (using local mapping table; e.g., "Pešački prelaz ispred vas.")
  * Start/Stop Scanning button, large and accessible

**Accessibility by design:**

* Large fonts and touch targets
* High color contrast
* TalkBack compatibility via Compose accessibility modifiers

---

## Data Model

### Entities

```
BeaconConfig
  - beaconId: String (UUID format, unique)
  - label: String (for local use only)
  - pointType: String (enum, e.g., CROSSWALK)
  - priority: String (enum)
  - messageCode: Short (int code, in BLE packet)
  - isActive: Boolean
  - lastUpdatedAt: Long (timestamp)

```

DetectedBeaconEvent

* beaconId: String
* detectedAt: Long (timestamp)
* rssi: Int
* pointType: String
* priority: String
* messageCode: Short
* wasAnnounced: Boolean

ReceiverState

* scanningEnabled: Boolean
* lastDetectedBeaconId: String? (nullable)
* lastDetectedMessageCode: Short? (nullable)
* lastAnnouncementAt: Long? (nullable)

CooldownEntry

* beaconId: String
* lastTriggeredAt: Long (timestamp)

```

```

PointType Enum

* CROSSWALK
* TRAFFIC_LIGHT
* STAIRS
* ENTRANCE
* BUS_STOP
* POLE
* ELEVATOR
* WORKS
* COUNTER
* DOOR
* OBSTACLE
* OTHER

Priority Enum

* LOW
* MEDIUM
* HIGH
* CRITICAL

### Relationships and Storage

* See above — entity relationships persist, but message field is replaced by messageCode throughout.
* Persistence: **DataStore for configs and cooldowns; Room (optional) for event logs.**

### Data Flow

**Beacon Mode:**

1. User selects "Beacon Mode" and completes config form.
2. Config is saved to DataStore.
3. BLE advertisement packet is encoded with beaconId, pointType, priority, messageCode (no long strings).
4. Advertising begins; nRF Connect (or similar) can be used for verification.

**Receiver Mode:**

1. User chooses "Receiver Mode," starts scanning.
2. Scanner listens for matching packets.
3. For each detected beacon:
  * Parses and validates structure.
  * Collects N consecutive RSSI readings > threshold.
  * Checks cooldown for that beaconId/messageCode.
  * If all criteria met, looks up messageCode in local table (with pointType if needed) and plays corresponding TTS message.
  * Updates state and UI with event details.

---

## Testing Plan

### Testing Strategy

* **Unit Tests:**
  * BLE packet encode/decode and messageCode mapping.
  * RSSI hysteresis logic.
  * Cooldown timer behavior.
  * JUnit4 + Kotlin.


* **Integration Tests:**
  * BLE advertising and scanning between two physical Android devices (no emulators).


* **End-to-End (E2E):**
  * Walkthrough: beacon setup, scanning, proximity approach, TTS, cooldown effect.
  * Five demo point types for MVP.


* **Performance:**
  * Announce within <2 seconds on valid proximity.

### Tools

* **JUnit4**, physical Android devices, and nRF Connect for packet inspection.

### Core Test Cases

See previous table, updated to match new fields (messageCode everywhere message previously appeared).

---

## Deployment Plan

### Environment

* Android Studio, Kotlin 1.9+, two physical Android smartphones with BLE (API 26+ recommended).
* nRF Connect to verify advertisement payload (should display beaconId, pointType, priority, and messageCode).

### Process

* Build and install APK on both devices.
* Configure beacon with correct messageCode(s); verify BLE packets contain expected fields.
* Receiver scans, receives, and accurately decodes messageCode to TTS output.

### Rollback

* Restore prior APK and/or reset DataStore as required. Minimal risk due to simple data structures.

### Verification

* Confirm each messageCode triggers correct local TTS string on receiver. All fields match specification.

---

## Security & Performance

### Security

* No cloud, accounts, or credentials for MVP.
* All beacon packets are public (no sensitive user data).
* Input validation on beacon setup; code enforcement for messageCode validity.
* Only standard permissions requested.

### Performance

* BLE/TTS reaction <2 seconds.
* App remains responsive and memory/battery efficient.
* BLE and TTS resources released when modes are deactivated.

### Observability

* Structured Logcat logs for BLE, exceptions, and state transitions.
* In-app (or logcat) debug UI may optionally display last N events.

---

**Summary:**  

This design delivers a robust, BLE payload-compliant, demo-ready MVP for "Pametni Audio Navigacioni Sistem." Compact and realistic BLE payloads, reliable local lookup with messageCode, stabilized RSSI proximity, natively accessible UI, and bulletproof BLE handling make this solution both credible and ready for hackathon demonstration as a foundation for future city-scale smart navigation for the blind and visually impaired.