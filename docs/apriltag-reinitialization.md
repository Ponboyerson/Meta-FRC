# Phase 3: AprilTag-Based Re-initialization for QuestNav

## Overview

This document describes the implementation of **AprilTag-based re-initialization** for correcting SLAM drift and tracking loss in the QuestNav Unity application. This is a key software contribution identified in the Meta FRC project plan and addresses an explicitly open problem in the upstream QuestNav project.

## Problem Statement

QuestNav's VIO (Visual Inertial Odometry) system provides high-frequency (90-120Hz) pose estimates but suffers from:

1. **Accumulated drift over time** - Small errors in velocity/position integration compound
2. **Tracking loss during occlusion** - When the headset can't see enough features
3. **No absolute reference** - VIO is relative, not anchored to field coordinates
4. **Reset required after removal** - If headset is taken off robot, SLAM resets to origin

Without correction, these issues make QuestNav unsuitable as the sole localization source for competition.

## Solution Architecture

The re-initialization system implements a **state machine** that:

1. **Monitors drift** between QuestNav pose and AprilTag pose continuously
2. **Detects suspicious behavior** using configurable thresholds
3. **Confirms drift** with hysteresis to avoid false positives
4. **Validates AprilTag solutions** using multi-tag confidence scoring
5. **Executes safe re-initialization** when conditions are met
6. **Recovers gracefully** if re-init fails or tracking is lost

### State Machine Diagram

```
NORMAL ──drift detected──> SUSPICIOUS ──time threshold──> DRIFT_DETECTED
   ▲                           │                               │
   │                      drift resolved                       │
   │                           │                               │
   │                           ▼                               ▼
   │                        NORMAL                    valid AprilTag found
   │                                                        │
   │                                                        ▼
   │                                                REINITIALIZING ──execute reset──> VALIDATING
   │                                                        │                              │
   │                                                        │                         validation
   │                                                        │                         success/fail
   │                                                        │                              │
   │                                                        ▼                              ▼
   └────────────────────────────────────────────────────  NORMAL                     RECOVERY
          tracking regained                                                              │
                                                                                         │
                                                                                  tracking regained
```

## Implementation Details

### QuestNavReinitializer Class

Located at: `java-robot/src/main/java/frc/robot/subsystems/questnav/QuestNavReinitializer.java`

#### Key Features

1. **Configurable Thresholds**
   - Position drift threshold (default: 0.5m)
   - Rotation drift threshold (default: 10°)
   - Suspicious time threshold (default: 0.3s)
   - Tag confidence requirements
   - Distance and ambiguity limits

2. **Confidence Scoring Algorithm**
   
   ```java
   confidence = (tagCountFactor × 0.4) + (ambiguityFactor × 0.4) + (distanceFactor × 0.2)
   
   where:
   - tagCountFactor = min(tagCount / 3.0, 1.0)
   - ambiguityFactor = 1.0 - min(averageAmbiguity, 1.0)
   - distanceFactor = 1.0 - min(averageDistance / maxDistance, 1.0)
   ```

3. **Callbacks for Integration**
   - `setOnReinitRequested()` - Called when pose reset should happen
   - `setOnReinitComplete()` - Called after reset executes
   - `setOnTrackingLost()` - Called when entering recovery mode
   - `setOnTrackingRegained()` - Called when normal operation resumes

4. **SmartDashboard Monitoring**
   - Real-time state display
   - Drift metrics (position & rotation)
   - Tag confidence scores
   - Re-init statistics (total, successful, failed)
   - Consecutive good/bad measurement counts

### VisionConstants Configuration

Located at: `java-robot/src/main/java/frc/robot/Constants/VisionConstants.java`

All thresholds are centralized in `VisionConstants` for easy tuning:

```java
// Drift Detection
kQuestNavPositionDriftThreshold = 0.5      // meters
kQuestNavRotationDriftThreshold = 10.0     // degrees
kQuestNavSuspiciousTimeThreshold = 0.3     // seconds

// AprilTag Validation
kMinTagConfidenceScore = 0.6               // 0.0-1.0 scale
kMinTagsForReinit = 2                      // minimum tags
kMaxTagDistanceForReinit = 4.0             // meters
kMaxAmbiguityForReinit = 0.3               // 0.0-1.0 scale

// Pose Fusion (std devs)
kQuestNavPositionStdDev = 0.1              // normal operation
kQuestNavPositionStdDevSuspicious = 0.5    // when drift suspected
kAprilTagPositionStdDevBase = 0.05         // ground truth
```

### Integration with DriveSubsystem

Example integration pattern:

```java
public class DriveSubsystem extends SubsystemBase {
    private final QuestNavSubsystem questNav;
    private final PhotonVisionSubsystem photonVision;
    private final QuestNavReinitializer reinitializer;
    private final SwerveDrivePoseEstimator poseEstimator;
    
    public DriveSubsystem() {
        // Initialize components
        questNav = new QuestNavSubsystem();
        photonVision = new PhotonVisionSubsystem();
        reinitializer = new QuestNavReinitializer();
        
        // Setup callbacks
        reinitializer.setOnReinitRequested(() -> {
            Pose2d aprilTagPose = photonVision.getLatestAprilTagPose();
            poseEstimator.resetPosition(aprilTagPose);
            reinitializer.updateLastKnownGoodPose(aprilTagPose);
            System.out.println("[DriveSubsystem] Pose RESET to AprilTag solution");
        });
        
        reinitializer.setOnTrackingLost(() -> {
            // Increase QuestNav std devs to reduce trust
            poseEstimator.setVisionMeasurementStdDevs(
                kQuestNavPositionStdDevSuspicious,
                kQuestNavRotationStdDevSuspicious
            );
        });
        
        reinitializer.setOnTrackingRegained(() -> {
            // Restore normal std devs
            poseEstimator.setVisionMeasurementStdDevs(
                kQuestNavPositionStdDev,
                kQuestNavRotationStdDev
            );
        });
    }
    
    @Override
    public void periodic() {
        // Get latest measurements
        Pose2d questNavPose = questNav.getPose();
        var aprilTagResult = photonVision.getAprilTagResult();
        
        // Update reinitializer
        reinitializer.updateQuestNavPose(questNavPose);
        
        if (aprilTagResult.hasValidMeasurement()) {
            reinitializer.processAprilTagMeasurement(
                aprilTagResult.pose,
                aprilTagResult.tagCount,
                aprilTagResult.averageAmbiguity,
                aprilTagResult.averageDistance,
                Timer.getFPGATimestamp()
            );
        }
        
        // Add measurements to pose estimator
        if (!reinitializer.isReinitializing()) {
            poseEstimator.addVisionMeasurement(
                questNavPose,
                questNav.getTimestamp(),
                getQuestNavStdDevs()
            );
            
            if (aprilTagResult.hasValidMeasurement()) {
                poseEstimator.addVisionMeasurement(
                    aprilTagResult.pose,
                    aprilTagResult.timestamp,
                    getAprilTagStdDevs(aprilTagResult.distance)
                );
            }
        }
        
        reinitializer.periodic();
    }
}
```

## Tuning Guide

### Step 1: Baseline Testing (No Re-init)

1. Disable re-initialization temporarily
2. Run robot through typical autonomous routines
3. Log QuestNav vs AprilTag drift over time
4. Measure natural drift rate (meters per second)

### Step 2: Set Drift Thresholds

Based on baseline data:
- Set `kQuestNavPositionDriftThreshold` to **2-3× your acceptable error**
  - Example: If you need <0.2m accuracy, set threshold to 0.5m
- Set `kQuestNavRotationDriftThreshold` to **10-15°** typically
- Set `kQuestNavSuspiciousTimeThreshold` to **3-5 frames** at QuestNav frequency
  - At 90Hz: 0.033-0.055s per frame → 0.1-0.3s threshold

### Step 3: Tune AprilTag Validation

Test with various tag configurations:
- Single tag close range (<1m)
- Multiple tags medium range (2-3m)
- Tags at edge of camera FOV
- Tags with partial occlusion

Adjust:
- `kMinTagsForReinit`: Start at 2, increase to 3 if false positives occur
- `kMaxTagDistanceForReinit`: Reduce if far tags cause bad re-inits
- `kMaxAmbiguityForReinit`: Lower if ambiguous tags cause issues

### Step 4: Validate Re-init Success Rate

Run repeated drift scenarios:
1. Artificially offset QuestNav pose (in simulation)
2. Drive through tag-visible areas
3. Count successful vs failed re-inits
4. Target: >90% success rate

### Step 5: Competition Stress Testing

Simulate match conditions:
- Rapid direction changes
- Driver-induced bumps/vibration
- Variable lighting
- Partial tag occlusion
- Multiple alliance color tests

## Exit Criteria (Phase 3)

Per project plan, Phase 3 is complete when:

✅ **Re-initialization recovers reasonable pose within defined budget:**
- Time budget: <2 seconds from drift detection to pose recovery
- Tag count budget: ≤3 tags required
- Accuracy: Recovered pose within 0.15m of true position

✅ **Statistics tracked and visible:**
- SmartDashboard shows reinits this match
- Success/failure rates logged
- Average drift at re-init recorded

✅ **Robust under competition conditions:**
- No false positives during normal driving
- Recovers from simulated tracking loss
- Handles alliance flipping correctly

✅ **Ready for upstream contribution:**
- Clean, documented code
- Configurable via constants
- No hard-coded field-specific values
- MIT license compatible (matching QuestNav)

## Upstream Contribution Plan

Once validated, this implementation should be offered as a PR to QuestNav:

1. **Extract Unity-side components** (if any Unity changes needed)
2. **Document Java implementation** for other FRC teams
3. **Create example integration guide**
4. **Submit PR to QuestNav/QuestNav** with:
   - QuestNavReinitializer.java
   - VisionConstants additions
   - Documentation (this file)
   - Example integration code

### Potential Upstream Impact

- Solves an explicitly acknowledged limitation in QuestNav FAQ
- Provides production-tested drift correction for FRC use case
- Demonstrates real-world fusion with AprilTag systems
- Could be adapted for other robotics applications (FIRST Tech Challenge, VEX, etc.)

## Troubleshooting

### Problem: Frequent false positive re-inits

**Solutions:**
- Increase `kQuestNavSuspiciousTimeThreshold`
- Increase `kMinTagsForReinit` to 3
- Decrease `kMaxTagDistanceForReinit`
- Check for vibration/mount looseness causing QuestNav jitter

### Problem: Re-init never triggers despite obvious drift

**Solutions:**
- Verify QuestNav and AprilTag poses are in same coordinate system
- Check alliance flipping logic
- Ensure AprilTag detections are actually occurring (log tag count)
- Reduce `kMinTagConfidenceScore` temporarily for debugging

### Problem: Re-init succeeds but pose is still wrong

**Solutions:**
- Check mount offset calibration
- Verify AprilTag field layout matches actual field
- Look for 180° ambiguity in tag detection
- Consider requiring more tags (`kMinTagsForReinit++`)

### Problem: System enters RECOVERY and won't exit

**Solutions:**
- Check if QuestNav has actually regained tracking (console logs)
- Verify AprilTag measurements are still being received
- May need manual reset via driver input
- Consider reducing `consecutiveGoodMeasurements` threshold

## Future Enhancements

Potential improvements for future seasons:

1. **Multi-session learning** - Remember drift patterns from previous matches
2. **Dynamic threshold adjustment** - Auto-tune based on match phase
3. **Gyroscope cross-validation** - Use robot IMU to detect impossible rotations
4. **Multi-camera fusion** - Combine AprilTags from multiple cameras before re-init
5. **Predictive drift compensation** - Apply correction before threshold exceeded
6. **Unity-side visualization** - Show drift status in Quest headset UI

## Related Documentation

- [QuestNav Original Repository](https://github.com/QuestNav/QuestNav)
- [WPILib Pose Estimator Documentation](https://docs.wpilib.org/en/stable/docs/software/advanced-controls/state-space/state-space-pose-estimators.html)
- [PhotonVision AprilTag Detection](https://docs.photonvision.org/en/latest/docs/apriltag-introduction/apriltag-intro.html)
- [Meta FRC Project Plan](../../README.md)
- [Rules Compliance Guide](../../docs/rules-compliance.md)

---

**Author:** FRC 11269 "Batteries Not Included"  
**Season:** 2027 Pre-season Development  
**Status:** Implementation Complete - Testing Phase  
**License:** MIT (compatible with QuestNav upstream)
