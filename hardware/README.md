# Hardware Guide — Meta FRC

**Track:** B (Competition) & C (Practice)  
**Season:** 2027  

---

## Bill of Materials

### Essential Components (Track B)

| Item | Purpose | Recommended Model | Qty | Estimated Cost |
|------|---------|-------------------|-----|----------------|
| Meta Quest 3S | Localization headset | Meta Quest 3S 128GB | 1 | $299 |
| USB-C to Ethernet Adapter | Wired network link | Anker USB-C to Ethernet + Power Passthrough | 1 | $35-45 |
| 5V Regulator / Power Bank | Headset power | Redux Robotics Zinc-V | 1 | $25 |
| 3D-Printed Mount | Rigid headset mounting | QuestNav Printables design | 1 | ~$5 (filament) |
| USB-C Cable | Data + power | Anker USB-C to USB-C, 6ft | 1 | $15 |
| Ethernet Cable | Network connection | Cat6 patch cable, various lengths | 2 | $10 |

**Total Track B:** ~$390-400

### Optional / Redundant (Track B)

| Item | Purpose | Notes |
|------|---------|-------|
| Second USB-C to Ethernet adapter | Backup for competition | Many fail unexpectedly |
| Extra USB-C cables | Spares | Failure points under vibration |
| Velcro straps / zip ties | Cable management | Secure all connections |
| Foam padding | Impact absorption | Protect headset during contact |

### Track C Components (Separate from Robot)

| Item | Purpose | Notes |
|------|---------|-------|
| Second Quest 3S (optional) | Dedicated recording headset | Can use same as Track B between matches |
| Wi-Fi 6E router | Low-latency streaming | TP-Link Archer AXE300 or similar |
| External SSD (1TB+) | Recording storage | Samsung T7, SanDisk Extreme |
| Tripod mount | Fixed camera position | Standard 1/4"-20 thread adapter |
| Handheld grip | Operator filming | Quest 2/3 controller mount or custom handle |

**Total Track C:** ~$350-500 (if buying second Quest)

---

## Tested USB-C to Ethernet Adapters

⚠️ **CRITICAL:** Not all USB-C to Ethernet adapters work with Quest. Many boot-loop or don't pass power correctly.

### Known Working (as of QuestNav documentation):

| Adapter | Chipset | Power Passthrough | Notes |
|---------|---------|-------------------|-------|
| Anker USB-C to Ethernet + PD | Realtek RTL8153 | ✅ Yes, USB-C PD | Most reliable option |
| UGREEN USB-C to Gigabit Ethernet | ASIX AX88179 | ✅ Yes, via separate USB-C port | Requires dual-port model |
| Belkin USB-C to Ethernet + Charge | Realtek | ✅ Yes | Expensive but reliable |

### Known Issues:

| Adapter | Issue | Avoid |
|---------|-------|-------|
| Generic no-name adapters | Boot-loop, don't enumerate | Any without brand/reviews |
| USB-PD only power banks | Some trigger >5V, cause boot-loop | Check compatibility |
| HDMI Link adapters | Wrong direction (headset → display) | Not for this use case |

### Verification Steps:

Before competition:

1. Plug adapter into Quest 3S
2. Connect Ethernet cable (link lights should activate)
3. Connect power via adapter's passthrough port
4. Put on headset (or use ADB)
5. Verify Ethernet connection in Settings → Wi-Fi (should show "Ethernet connected")
6. Test NT4 communication

---

## Power Solutions

### Option 1: RoboRIO USB Port (Simplest)

**Pros:**
- No additional hardware
- Stable 5V supply
- Always on when robot powered

**Cons:**
- Limited current (500mA-900mA depending on RIO generation)
- Won't charge headset, only sustain
- May not be enough if headset battery is very low

**Implementation:**
```
RoboRIO USB Port → USB-A to USB-C cable → Quest 3S
```

Use high-quality USB-A to USB-C cable (Anker, Belkin). Keep length under 6ft to minimize voltage drop.

### Option 2: Redux Robotics Zinc-V (Recommended)

**Pros:**
- Designed for FRC USB devices
- Compliant with USB rules out of box
- Stable 5V @ 3A
- CAN bus controlled (can switch off)

**Cons:**
- Additional cost
- Takes up CAN IDs

**Wiring:**
```
Robot 12V → Zinc-V → USB-A port → USB-A to USB-C → Quest
            (regulated 5V)
```

Configure via CAN in robot code:
```java
ZincV zinc = new ZincV(CAN_ID);
zinc.enableUSB(true);  // Enable during match
zinc.enableUSB(false); // Disable when not needed
```

### Option 3: USB Battery Bank (Portable)

**Pros:**
- Independent of robot power
- Can pre-charge headset before matches
- Portable for testing

**Cons:**
- Must avoid USB-PD (Power Delivery) negotiation
- Adds weight
- Needs separate charging

**Recommended Banks:**
| Model | Capacity | Output | Notes |
|-------|----------|--------|-------|
| Anker PowerCore 10000 | 10000mAh | 5V/2.4A | Reliable, compact |
| RAVPower 20000mAh | 20000mAh | 5V/2.4A (via USB-A) | Longer runtime |

**⚠️ WARNING:** Avoid USB-PD battery banks! They negotiate voltage above 5V (9V, 12V, etc.) which can cause Ethernet adapter boot-looping or damage.

**Workaround:** Use USB-A to USB-C cable (not USB-C to USB-C). This forces bank into 5V mode.

### Option 4: Custom 5V Regulator (Advanced)

For teams comfortable with electrical integration:

**Components:**
- Grapple MitoCANdria or Pololu D36V50F5 regulator
- USB breakout board with sense resistors
- Fuse (per R607 requirements)

**Wiring Diagram:**
```
Robot 12V → Regulator → USB Breakout → USB-C → Quest
          (5V output)   (with D+/D- sense resistors)
```

**Critical:** Include 22Ω resistors on D+ and D- lines so Quest recognizes valid USB charger. Without these, headset may not accept power.

---

## 3D-Printed Mounts

### QuestNav Official Mount

QuestNav publishes a mount design on Printables:
- https://www.printables.com/designs/[questnav-mount-id] (check their GitHub for current link)

**Features:**
- Rigid attachment point
- Cable routing channels
- Low profile (minimizes snag risk)
- Tested at competitions

**Printing Recommendations:**
- Material: PETG or ABS (PLA may crack under impact)
- Infill: 40-50%
- Wall thickness: 3-4 perimeters
- Orientation: Print flat on build plate for strength

### Custom Mount Design Considerations

If designing your own:

1. **Mounting Location:**
   - Top of robot (good visibility, exposed to impacts)
   - Inside bumpers (protected, limited FOV)
   - Behind intake (very protected, may be occluded)

2. **Orientation:**
   - Forward-facing (standard)
   - Angled downward (sees floor tags better)
   - 360° mount (adjustable, more complex)

3. **Cable Management:**
   - Route USB-C cable away from moving parts
   - Strain relief near headset
   - Zip tie points every 6 inches

4. **Quick-Release:**
   - Consider thumbscrew mount for rapid removal
   - Useful between matches if using same Quest for Track C

---

## Wiring Compliance

### FRC Rules Reference

- **R607:** Electrical components must be fused
- **R609:** USB devices must comply with USB spec
- **R707:** No wireless communication (Track B specific)
- **R711:** Cable management, strain relief

### Best Practices

1. **Secure All Connections:**
   - Zip tie USB-C cable at both ends
   - Use right-angle connectors where possible
   - Apply hot glue or epoxy to critical joints

2. **Protect from Impact:**
   - Route cables inside frame/bumpers
   - Use spiral wrap or loom tubing
   - Add foam padding around headset

3. **Prevent Snagging:**
   - No loose cables hanging
   - Keep cables tight to frame
   - Use cable guides/channels

4. **Label Everything:**
   - Tag both ends of Ethernet cable
   - Mark power source (RIO vs regulator)
   - Document in wiring diagram

---

## Testing Checklist

### Pre-Match (Every Event)

- [ ] Ethernet adapter link lights active
- [ ] Quest boots with Wi-Fi/BT disabled (verify via ADB)
- [ ] NT4 pose data publishing (check Shuffleboard/AdvantageScope)
- [ ] Headset battery level adequate (or external power connected)
- [ ] Mount secure, no wobble
- [ ] Cables secured, no snag hazards
- [ ] Robot functional with Track B powered off

### Post-Match

- [ ] Inspect mount for cracks/damage
- [ ] Check cable connections still tight
- [ ] Verify headset survived impacts
- [ ] Review pose data logs for anomalies

---

## Troubleshooting Hardware

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| No Ethernet link | Bad adapter or cable | Try different adapter/cable |
| Adapter boot-loops | Power issue (PD negotiation) | Use USB-A cable, check 5V regulator |
| Quest won't charge | Missing D+/D- resistors | Add sense resistors or use compliant adapter |
| Pose data intermittent | Loose connection | Reseat all cables, add strain relief |
| Mount cracked mid-match | Weak print orientation | Reprint with better infill/orientation |
| Tracking loss | Dirty lenses/cameras | Clean before each match |

---

## Ordering Links

*(Note: Prices and availability change — verify before ordering)*

- **Meta Quest 3S:** https://www.meta.com/experiences/quest/
- **Anker USB-C to Ethernet:** Amazon B07ZVXQJ3B
- **Redux Zinc-V:** https://reduxrobotics.com/zinc-v
- **Anker PowerCore 10000:** Amazon B07QXV6N1B
- **Cat6 Ethernet Cables:** Monoprice, Amazon Basics
- **PETG Filament:** eSun, Hatchbox, Prusament

---

## Revision History

- **Revision 1** (Current): Initial hardware guide based on project plan and QuestNav documentation
