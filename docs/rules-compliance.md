# FRC Rules Compliance

**Rule:** R707 (or equivalent in current season Game Manual)  
**Status:** CRITICAL — must be re-verified at 2027 Kickoff

---

## The Rule

> **R707:** ROBOTs may not utilize wireless communication to or from the ROBOT, except for:
> - Communication with the authorized field access point (via the provided radio)
> - Event-provided location tags (e.g., AprilTags, QR codes)
> - Wireless charging (if applicable per other rules)

This is a **blanket ban**, not a bandwidth limitation. Any wireless communication to/from/within the ROBOT is prohibited regardless of data rate.

---

## What's Legal and Why

### ✅ Track A: USB Camera + PhotonVision (Mandatory Baseline)

**Verdict:** Legal, mandatory  
**Reasoning:** Standard wired USB camera(s) connected to coprocessor running PhotonVision. All communication is wired (USB, Ethernet). No wireless components involved.

### ✅ Track B: Quest 3S Wired via USB-C-to-Ethernet (Flagship Feature)

**Verdict:** Legal on-robot, in-match  
**Reasoning:**
1. **No wireless communication:** Quest connects via USB-C-to-Ethernet adapter with power passthrough. All data travels over physical cable to robot network.
2. **Radios disabled:** Wi-Fi and Bluetooth are disabled via ADB commands that persist through reboot:
   ```bash
   adb shell svc wifi disable
   adb shell svc bluetooth disable
   ```
3. **Precedent:** QuestNav (`QuestNav/QuestNav`) has been competition-tested by multiple teams with this exact configuration. Their documentation explicitly cites R707 as the reason for the wired requirement.
4. **Functionality:** Robot remains fully operational with Track B powered off/disconnected (required for inspection).

**Setup Requirements:**
- Use only tested USB-C-to-Ethernet adapters (see `hardware/adapters-tested.md`)
- Verify Wi-Fi/BT disabled state persists through reboot before each event
- Document setup for inspectors (bring this README + QuestNav precedent)
- Consider submitting Q&A question ahead of Week 1

### ❌ Wireless Quest-as-Camera-Source for PhotonVision

**Verdict:** Rejected for competition use  
**Reasoning:**
1. **Direct R707 violation:** Any wireless transmission from Quest to robot network is prohibited, regardless of bandwidth.
2. **No native UVC-out:** Meta doesn't support Quest as plug-and-play USB camera. Their official USB-C video tooling (HDMI Link, Passthrough over Link) goes the opposite direction or is Unity Editor dev-preview only.
3. **Custom pipeline required:** Would need fully custom capture pipeline for something QuestNav's pose stream already covers for localization purposes.
4. **Not worth the risk:** Even if wired workaround found, doesn't offer advantage over QuestNav's proven pose-streaming approach.

**Status:** Downgraded to optional stretch experiment, pit/practice only, never on competition robot.

### ❌ Touch Plus Controllers Mounted on Robot

**Verdict:** Rejected for competition use  
**Reasoning:**
1. **Inherently wireless:** Controller-to-headset link is wireless with no wired fallback. Unlike the headset's Wi-Fi/BT (which have Ethernet alternative), controllers have no data port at all.
2. **Battery slot is power-only:** The controller's battery compartment provides power but doesn't enable wired data communication.
3. **No workaround exists:** Powering externally doesn't remove the wireless hop — the controller still communicates wirelessly to the headset.

**Legal Use Case:** Off-robot only, at practice/scrimmage, as handheld "wand" for marking waypoints or tagging VR recording moments. Not part of ROBOT during matches → R707 doesn't apply.

### ✅ Second Quest Recording Scrimmages (Track C)

**Verdict:** Legal  
**Reasoning:** Recording equipment is handheld/tripod-mounted, never attached to or communicating with the ROBOT during matches. R707 applies only to ROBOT construction — sideline equipment is unconstrained.

**Use Cases:**
- Handheld operator filming practice matches
- Tripod-mounted fixed viewpoint recording
- Wireless streaming to pit laptop for live review
- All legal because not part of ROBOT

### ✅ Extra IMU on Competition Robot (Recommended Redundancy)

**Verdict:** Legal, recommended  
**Reasoning:** Standard Pigeon 2.0 / NavX / ADIS16470 on CAN or SPI, fully wired, WPILib-supported. Use this instead of controllers for any "second tracked point" need.

---

## Pre-Event Checklist

Before each competition:

- [ ] **Verify Wi-Fi disabled:** `adb shell svc wifi status` should report disabled
- [ ] **Verify Bluetooth disabled:** `adb shell svc bluetooth status` should report disabled
- [ ] **Reboot test:** Power cycle headset, confirm Wi-Fi/BT remain disabled
- [ ] **Ethernet link test:** Confirm stable connection via USB-C-to-Ethernet adapter
- [ ] **Robot functional without Track B:** Disconnect/disable Track B, verify robot operates normally
- [ ] **Documentation ready:** Bring printed copy of this compliance doc + QuestNav precedent
- [ ] **Rules re-audit:** Check current season Game Manual for R707 renumbering/text changes

---

## Q&A Question Draft

Submit to FIRST Q&A portal before Week 1:

> **Question:** Our team is implementing a wired VR headset localization system based on the open-source QuestNav project. The Quest 3S headset is connected to our robot network via a USB-C-to-Ethernet adapter with power passthrough. We have disabled Wi-Fi (`adb shell svc wifi disable`) and Bluetooth (`adb shell svc bluetooth disable`) via ADB commands that persist through reboot. The headset publishes 6DOF pose data over NetworkTables (NT4) via the wired Ethernet connection. No wireless communication to/from the robot occurs. Is this configuration legal under R707?
>
> **Context:** The QuestNav project (`github.com/QuestNav/QuestNav`) has been competition-tested by multiple teams in previous seasons with this exact wired configuration. Their documentation explicitly states the wired requirement is due to R707 compliance.

---

## Inspection Talking Points

When presenting to inspectors:

1. **"This is QuestNav, which has been competition-tested"** — Bring up their GitHub, show photos/videos of it running at events.

2. **"All communication is wired"** — Show the USB-C-to-Ethernet adapter, demonstrate Ethernet link lights active.

3. **"Radios are disabled and stay disabled"** — Run the ADB status commands live, show they persist through reboot.

4. **"Robot works without it"** — Power off Track B, demonstrate autonomous/teleop still functions with Track A vision alone.

5. **"We understand R707"** — Reference this document, explain why we rejected wireless alternatives.

---

## Risk Mitigation

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Wi-Fi/BT setting doesn't persist after OS update | Medium | Re-verify after every headset OS update; keep ADB commands documented as pre-match checklist |
| Inspector unfamiliar with QuestNav precedent | Low-Medium | Bring printed documentation, GitHub screenshots, competition photos |
| Rules renumbered/changed at Kickoff 2027 | Medium | Re-audit against 2027 Game Manual immediately at Kickoff; update this doc accordingly |
| USB-Ethernet adapter fails mid-match | Medium | Have backup adapter; design mount for quick swap; ensure Track A vision works independently |

---

## References

- FRC Game Manual (current season) — Section 8, Robot Rules, R707
- QuestNav Documentation: https://github.com/QuestNav/QuestNav
- QuestNav Rules Compliance Discussion: https://github.com/QuestNav/QuestNav/issues/[issue-number]
- FIRST Q&A Portal: https://frc-qa.firstinspires.org/

---

## Revision History

- **Revision 2** (Current): Explicitly documents rejected paths (wireless camera, Touch Plus controllers) with reasoning to prevent mid-season re-proposal.
- **Revision 1**: Initial compliance documentation.
