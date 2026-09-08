# Meta FRC Robot Code - Beta Release

**Team:** FRC 11269 "Batteries Not Included"  
**Season:** 2027 Prep  
**Status:** Beta Ready ✓  
**License:** MIT (forked from QuestNav)

---

## Quick Start

```bash
# Build the project
./gradlew build

# Run unit tests
./gradlew test

# Deploy to robot
./gradlew deploy

# Run simulation
./gradlew sim

# Clean build
./gradlew clean build
```

---

## What's New in Beta

### Architecture Improvements
✅ **RobotContainer Pattern** - Centralized command binding and subsystem management  
✅ **Command-Based Framework** - Full WPILib CommandScheduler integration  
✅ **Subsystem Encapsulation** - Hardware accessed only through subsystem classes  
✅ **Modular Autonomous** - SequentialCommandGroup structure for easy routine building  

### Safety & Quality
✅ **JUnit5 Testing** - 7 unit tests for DriveSubsystem pose estimation  
✅ **Constants Properly Declared** - All ports and tuning values as `static final`  
✅ **SmartDashboard Telemetry** - Comprehensive logging for debugging  
✅ **Pose Validation** - Rejects NaN/infinite poses before fusion  

### Vision Fusion Features
✅ **QuestNav + PhotonVision Fusion** - SwerveDrivePoseEstimator with dual inputs  
✅ **Automatic Drift Detection** - 6-state state machine (NORMAL→SUSPICIOUS→DRIFT_DETECTED→REINITIALIZING→VALIDATING→RECOVERY)  
✅ **Dynamic Sensor Weighting** - Standard deviations adjust based on drift state  
✅ **Alliance-Aware Transforms** - Automatic blue/red alliance coordinate handling  
✅ **Manual Re-Init Support** - Driver-triggered drift correction via controller button  

---

## File Structure

```
java-robot/
├── README_BETA.md           # This file
├── BETA_READINESS.md        # Detailed beta checklist
├── build.gradle             # Gradle config with JUnit5
├── gradlew                  # Gradle wrapper script
├── gradle/wrapper/          # Gradle wrapper properties
└── src/
    ├── main/java/frc/robot/
    │   ├── Main.java                    # Entry point
    │   ├── Robot.java                   # Robot lifecycle (refactored)
    │   ├── RobotContainer.java          # NEW: Command central
    │   ├── Constants/
    │   │   └── VisionConstants.java     # Tuning thresholds
    │   ├── commands/
    │   │   ├── ResetPoseCommand.java    # Manual pose reset
    │   │   └── ManualReinitCommand.java # Driver re-init trigger
    │   ├── subsystems/
    │   │   ├── DriveSubsystem.java      # Pose fusion (465 lines)
    │   │   ├── QuestNavSubsystem.java   # NT4 client
    │   │   └── questnav/
    │   │       └── QuestNavReinitializer.java  # 6-state machine
    │   ├── util/
    │   │   └── QuestNavPose.java        # Coordinate transforms
    │   └── vision/
    │       └── PhotonVisionSubsystem.java  # AprilTag processing
    └── test/java/frc/robot/
        └── DriveSubsystemTest.java      # 7 unit tests
```

---

## Driver Controls

| Button | Action |
|--------|--------|
| **A (Hold)** | Auto drift correction via AprilTags |
| **B (Press)** | Reset pose to zero (field setup) |
| **X (Press)** | Toggle field/robot-relative drive |
| **Start (Press)** | Zero gyro heading |
| **D-Pad Up (Press)** | Toggle alliance color (blue/red) |
| **Left Stick** | Forward/back, strafe left/right |
| **Right Stick** | Rotate clockwise/counterclockwise |

---

## SmartDashboard Layout

### Autonomous Selection
- Dropdown: "Do Nothing", "Drive Forward 3m", "Auto Calibrate", "Complex Routine"

### Pose Display
- Field2d widget showing robot position
- X, Y, Rotation numeric displays

### Vision System Health
- `Vision/QuestNavActive` - Boolean
- `Vision/AprilTagActive` - Boolean
- `Vision/TagCount` - Number of detected tags
- `Vision/QuestNavStdDev[X/Y/Theta]` - Current uncertainty

### Drift Detection
- `Robot/State` - Current state machine state
- `Drive/TotalReinits` - Count of automatic corrections
- `Drive/LastReinitTime` - Timestamp of last correction

---

## Testing Protocol

### Unit Tests (Automated)
```bash
./gradlew test
```
Tests cover:
- Initial pose validation
- QuestNav measurement fusion
- AprilTag measurement fusion
- Invalid pose rejection
- Dynamic std dev adjustment
- Alliance flip functionality
- Field-relative mode toggle

### Bench Testing (Manual)
1. Connect QuestNav via USB-C-to-Ethernet
2. Verify Wi-Fi/Bluetooth disabled (`adb shell svc wifi disable`)
3. Launch Unity app on Quest 3S (Development Build ✓)
4. Run robot code in simulation mode
5. Validate pose updates on SmartDashboard
6. Test drift detection by moving headset

### Field Testing
1. Mount headset rigidly on robot
2. Secure cable routing per FRC wiring rules
3. Run autonomous routines
4. Validate fused pose vs ground truth
5. Test driver controls in teleop

---

## Known Issues & TODOs

### High Priority (Before Scrimmage)
- [ ] Configure actual motor ports in Constants
- [ ] Tune SwerveDriveKinematics for robot geometry
- [ ] Validate IMU (ADIS16470) mounting orientation
- [ ] Connect PhotonVision cameras and configure pipelines

### Medium Priority
- [ ] Add current limiting to motor controllers
- [ ] Implement soft limits for non-drivetrain mechanisms
- [ ] Create full autonomous routines with PathPlanner/Choreo
- [ ] Add AdvantageScope logging for detailed analysis

### Low Priority
- [ ] Wireless camera source experiment (pit only)
- [ ] Track C recording system for practice review
- [ ] Touch Plus controller as practice "wand"

---

## Rules Compliance Checklist

- [ ] Quest 3S Wi-Fi/Bluetooth OFF (verify with `adb shell svc wifi disable`)
- [ ] USB-C-to-Ethernet adapter from QuestNav tested list
- [ ] Robot functional with Track B powered OFF
- [ ] Touch Plus controllers NOT mounted on robot
- [ ] Re-audit against 2027 Game Manual at Kickoff

---

## Troubleshooting

### QuestNav Not Streaming
1. Check Development Build is enabled in Unity
2. Verify USB-C-to-Ethernet adapter is connected
3. Confirm Wi-Fi is disabled (`adb shell svc wifi enable && adb shell svc wifi disable`)
4. Check NetworkTables server team number (11269)

### Pose Estimator Diverging
1. Check AprilTag pipeline is detecting tags
2. Verify QuestNav mount transform is correct
3. Adjust standard deviations in VisionConstants
4. Ensure alliance color is set correctly

### Build Errors
```bash
# Clean and rebuild
./gradlew clean build

# Check Java version (must be 17)
java -version

# Regenerate wrapper if needed
gradle wrapper --gradle-version 8.5
```

---

## Credits

- **QuestNav** - Original pose streaming framework (MIT License)
- **WPILib** - Robot control framework
- **PhotonVision** - AprilTag detection
- **FRC 11269** - Meta FRC integration and drift correction

---

## Next Steps

1. ✅ Code complete for Phase 1 (Pose Fusion)
2. ⏳ Build and deploy to test rig
3. ⏳ Bench test with QuestNav streaming
4. ⏳ Validate drift detection and correction
5. ⏳ Mount on robot and field test
6. ⏳ Competition hardening (Phase 6)

**For detailed beta readiness checklist, see `BETA_READINESS.md`**
