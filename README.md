# Meta FRC Project

**Team:** FRC 11269 "Batteries Not Included"  
**Season Target:** Offseason build (2026) → ready for 2027 Kickoff  
**Status:** Planning — Revision 2  

---

## Overview

This project implements a wired VR-assisted robot localization system forked from QuestNav, fused with PhotonVision AprilTag detection, plus a separate flat/VR practice recording system.

### Three Tracks

- **Track A — Competition Vision (Mandatory):** Standard USB camera(s) + coprocessor + PhotonVision + AprilTags
- **Track B — Meta FRC Localization (Flagship):** Quest 3S wired via USB-C-to-Ethernet, running QuestNav, publishing 6DOF pose at up to 120 Hz over NT4, fused with PhotonVision in WPILib pose estimator
- **Track C — Practice & Scrimmage Recording:** Second Quest (or same unit off-robot) recording flat + VR gameplay footage for film review

---

## Repository Structure

```
meta-frc/
├── unity/              # Track B: QuestNav Unity application (forked)
├── java-robot/         # Track B: WPILib robot code with pose fusion
├── track-c-recorder/   # Track C: Bridge service for recording sessions
├── hardware/           # Mount designs, wiring diagrams, BOM
├── docs/               # Documentation, setup guides, rules compliance
└── README.md           # This file
```

---

## Legal Compliance

**CRITICAL:** This project strictly adheres to FRC Rule R707 (or equivalent in current season manual):

- ✅ Track B uses **wired-only** communication (USB-C-to-Ethernet adapter)
- ✅ Quest headset Wi-Fi and Bluetooth are **disabled** via ADB commands that persist through reboot
- ✅ Touch Plus controllers are **NOT** used on-robot (controller-to-headset link is inherently wireless)
- ✅ Track C recording equipment is **never attached to or communicating with the ROBOT** during matches
- ✅ Robot passes inspection and functions correctly with Track B powered off

See `docs/rules-compliance.md` for detailed reasoning and checklist.

---

## Quick Start

### Prerequisites

1. **Unity Development Environment:**
   - Unity 6 (6000.0.29f1) LTS
   - Android Build Support + OpenJDK + Android SDK & NDK Tools
   - Meta XR All-in-One SDK (from Unity Asset Store)
   - Meta XR Interaction SDK Essentials (from Unity Asset Store)
   - Meta MR Utility Kit (from Unity Asset Store)
   - MessagePack plugin (.unitypackage from GitHub releases)

2. **Robot Development Environment:**
   - WPILib 2027 (or current season version)
   - Java JDK 17+
   - Gradle

3. **Hardware:**
   - Meta Quest 3S
   - USB-C-to-Ethernet adapter (power-passthrough capable, see tested list)
   - 5V regulator or battery bank (Redux Zinc-V recommended)
   - 3D-printed mount (designs in `hardware/`)

### Phase 0: Fork & Bring-up

1. Clone this repository
2. Open `unity/` folder in Unity Hub
3. Import MessagePack plugin via `Assets > Import Package > Custom Package`
4. Switch build platform to Android
5. Run Meta Project Setup Tool until "XR Ready for Android"
6. Build `.apk` with **Development Build** checked
7. Install APK on Quest 3S
8. Disable Wi-Fi and Bluetooth on headset:
   ```bash
   adb shell svc wifi disable
   adb shell svc bluetooth disable
   ```
9. Connect via USB-C-to-Ethernet adapter
10. Verify pose streaming to NT4 on test roboRIO

### Phase 1: Pose Fusion

See `java-robot/README.md` for WPILib integration instructions.

---

## Phased Roadmap

| Phase | Goal | Exit Criteria |
|-------|------|---------------|
| 0 | Fork & bring-up | Headset streams pose to NT4 over wired connection |
| 1 | Pose fusion | Fused pose tracks correctly through tag-visible → occluded → visible cycle |
| 2 | Robot mounting | Headset mounted rigidly, survives driving/impacts |
| 3 | Drift correction | AprilTag-based re-initialization implemented (upstream PR candidate) |
| 4 | Track C recording | Flat + VR recorder working at practice |
| 5 | Scrimmage validation | Live testing at scrimmage, accuracy logged |
| 6 | Competition hardening | Passes inspection, works with Track B disabled |

---

## Component Breakdown

### Track B: QuestNav App (Unity)

- Forked from `QuestNav/QuestNav` (MIT licensed)
- Reuses pose-streaming core (`MotionStreamer.cs`, NT4 publish at 90-120 Hz)
- Extension work: AprilTag-assisted re-initialization for drift correction

### Track B: Robot Code (WPILib/Java)

- NT4 client consuming QuestNav pose topic
- `SwerveDrivePoseEstimator` or `DifferentialDrivePoseEstimator` fusing:
  - QuestNav pose (high-rate, low-latency, can drift)
  - PhotonVision AprilTag pose (lower-rate, drift-free ground truth)

### Track C: Bridge Recorder

- Runs wirelessly against handheld/tripod Quest (off-robot only)
- Records flat MP4 + stereo SBS VR footage
- Session manifest (JSON: session ID, timestamps, camera IDs, dropped frames)

---

## Bill of Materials

| Item | Purpose | Notes |
|------|---------|-------|
| Meta Quest 3S | Track B localization; Track C recording | QuestNav recommends 3S for cost/performance |
| USB-C to Ethernet adapter | Wired link for Track B | Must support power passthrough (see tested list) |
| 5V regulator / battery bank | Headset power | Redux Zinc-V plug-and-play recommended |
| 3D-printed mount | Rigid headset mounting | Designs in `hardware/` |
| Global-shutter USB camera | Track A vision | OV9281-class |
| Raspberry Pi 5 / Orange Pi 5 | PhotonVision coprocessor | Track A |
| Pit laptop / mini PC | Track C bridge + recorder | Linux |
| External SSD | Track C storage | Stereo video is large |

---

## License

This project is MIT licensed. See [LICENSE](LICENSE).

QuestNav is also MIT licensed — we retain their copyright notice and credit them in our README.

---

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly (especially for Track B: verify no wireless paths enabled)
5. Submit a pull request

**Note:** If you implement the AprilTag-based re-initialization feature (Phase 3), please consider upstreaming it to the main QuestNav repository as a community contribution.

---

## Contact & Support

- Team Discord: [link]
- Email: [team email]
- Q&A Questions Submitted: See `docs/qanda-log.md`

---

## Revision History

- **Revision 2** (Current): Supersedes original plan. Clarifies R707 blanket ban, documents QuestNav as foundation, explicitly rejects wireless Quest-as-camera and Touch Plus controller paths.
- **Revision 1**: Original "FRC VR Vision & Session Recording System" plan.
