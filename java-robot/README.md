# Meta FRC Robot Code - Pose Fusion Implementation

This directory contains the WPILib/Java robot code for fusing QuestNav 6DOF pose data with PhotonVision AprilTag measurements.

## Architecture

The pose fusion system combines three localization sources:

1. **Wheel Odometry** - High frequency, drifts over time
2. **QuestNav Pose** - 90-120 Hz, low latency, can drift (Track B)
3. **PhotonVision AprilTags** - Periodic, drift-free ground truth (Track A)

## Project Structure

```
java-robot/
├── build.gradle              # Gradle build configuration
├── settings.gradle           # Project settings
└── src/main/java/frc/robot/
    ├── Main.java             # Entry point
    ├── Robot.java            # Main robot class with fusion logic
    ├── subsystems/
    │   ├── DriveSubsystem.java       # Swerve drive with pose estimator
    │   └── QuestNavSubsystem.java    # NT4 client for QuestNav pose
    ├── vision/
    │   └── PhotonVisionSubsystem.java # AprilTag pose estimation
    └── util/
        └── QuestNavPose.java         # Coordinate transforms
```

## Key Components

### QuestNavSubsystem

Receives 6DOF pose from QuestNav via NetworkTables:
- Subscribes to `/questnav/pose` topic (struct Pose2d)
- Updates at 90-120 Hz
- Provides standard deviations for tuning trust levels
- Detects stale data (>100ms without update)

### PhotonVisionSubsystem

Receives AprilTag-based pose from PhotonVision coprocessor:
- Supports multiple cameras
- Uses MULTI_TAG_PNP strategy when possible
- Adjusts std devs based on tag count and ambiguity
- Provides drift-free ground truth corrections

### DriveSubsystem

Implements `SwerveDrivePoseEstimator` that fuses all inputs:
- Wheel odometry as baseline
- Vision measurements from QuestNav and PhotonVision
- Configurable standard deviations for each source
- Field-relative pose visualization

### QuestNavPose Utility

Handles coordinate transformations:
- Unity/QuestNav frame → FRC field coordinates
- Red/blue alliance flipping
- Robot mounting offset compensation
- Pose validation utilities

## Fusion Logic (Robot.java)

```java
// In robotPeriodic():

// 1. Fuse QuestNav (high-frequency, low-latency)
if (questNav.hasValidPose() && !questNav.isStale()) {
    Pose2d robotPose = QuestNavPose.applyRobotOffset(
        questNav.getEstimatedPose(),
        QuestNavPose.createMountTransform(0.3, 0.0, 0.5, 0.0)
    );
    drive.addQuestNavMeasurement(robotPose, questNav.getStandardDeviations());
}

// 2. Fuse PhotonVision (periodic, drift-free)
if (photon.hasValidPose() && !photon.isStale()) {
    drive.addPhotonVisionMeasurement(
        photon.getEstimatedPose(),
        photon.getStandardDeviations()
    );
}
```

## Tuning Guidelines

### Standard Deviations

Lower values = more trust in that measurement source.

**QuestNav (typical starting values):**
- Single tag visible: `[0.1, 0.1, 0.05]`
- No tags (VIO only): `[0.5, 0.5, 0.1]` (less trust due to drift)

**PhotonVision:**
- 2+ tags: `[0.1, 0.1, 0.1]` (high confidence)
- 1 tag: `[0.3, 0.3, 0.3]` (moderate confidence)
- No tags: `[0.5, 0.5, 0.5]` (very loose, won't correct)

**Odometry:**
- Typical: `[0.05, 0.05, 0.05]`

### Mount Transform

Adjust based on physical headset position:

```java
// createMountTransform(xOffset, yOffset, zOffset, yawDegrees)
Transform2d mount = QuestNavPose.createMountTransform(
    0.3,   // 30cm forward from robot center
    0.0,   // Centered left/right
    0.5,   // 50cm up
    0.0    // No rotation offset
);
```

## Testing Procedure

### Phase 1 Exit Criteria

1. ✅ Fused pose tracks correctly with AprilTags visible
2. ✅ Pose remains stable when tags are occluded (QuestNav VIO only)
3. ✅ Pose re-converges when tags become visible again
4. ✅ No divergence during tag-visible → occluded → visible cycle

### Bench Testing

1. Place robot at known field position
2. Verify QuestNav pose matches expected location
3. Verify PhotonVision detects tags correctly
4. Drive robot manually, observe fused pose on dashboard
5. Cover cameras, verify QuestNav maintains tracking
6. Uncover cameras, verify rapid re-convergence

## NetworkTables Topics

| Topic | Type | Direction | Description |
|-------|------|-----------|-------------|
| `/questnav/pose` | struct Pose2d | QuestNav → Robot | 6DOF pose from headset |
| `/photonvision/estimatedPose` | struct Pose2d | Coprocessor → Robot | AprilTag-based pose |

## Dependencies

- WPILib 2025.3.1
- PhotonLib 2025.3.1
- NetworkTables 4
- AdvantageKit (optional, for logging)

## Build & Deploy

```bash
./gradlew build
./gradlew deploy
```

## SmartDashboard Keys

- `QuestNav/TranslationX`, `QuestNav/TranslationY`, `QuestNav/Rotation`
- `QuestNav/HasValidPose`, `QuestNav/UpdateRateHz`
- `PhotonVision/TagsDetected`, `PhotonVision/Ambiguity`
- `Robot/X`, `Robot/Y`, `Robot/Rotation`
- `Field` (visual field display)
