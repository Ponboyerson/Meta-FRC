# WPILib Robot Code — Pose Estimator Fusion

**Track:** B — Meta FRC Localization  
**Framework:** WPILib 2027 (Java)  
**Prerequisites:** Phase 0 complete (QuestNav streaming pose over NT4)

---

## Overview

This module implements robot code that fuses two pose sources:

1. **QuestNav Pose** (from Track B Unity app)
   - High-frequency (90-120 Hz)
   - Low-latency
   - Can drift over time (SLAM-based)

2. **PhotonVision AprilTag Pose** (from Track A vision system)
   - Lower-frequency (depends on camera FPS + tag visibility)
   - Drift-free ground truth
   - Only available when tags are visible

The fusion happens in WPILib's `SwerveDrivePoseEstimator` or `DifferentialDrivePoseEstimator`, which uses an Extended Kalman Filter (EKF) to combine these measurements with wheel odometry.

---

## Project Structure

```
java-robot/
├── src/main/java/frc/robot/
│   ├── Main.java
│   ├── Robot.java
│   ├── Constants.java
│   ├── subsystems/
│   │   ├── Drivetrain.java
│   │   └── VisionSystem.java
│   ├── util/
│   │   ├── QuestNavClient.java      # NT4 client for QuestNav pose
│   │   └── PoseFusionManager.java   # Fusion logic
│   └── commands/
├── vendordeps/
│   ├── WPILib.json
│   ├── Phoenix6.json
│   └── PhotonVision.json
├── build.gradle
├── settings.gradle
└── README.md          # This file
```

---

## Step 1: Clone and Initialize

### Option A: Use QuestNav java-robot Starter

```bash
cd /workspace
# If you haven't already, clone the QuestNav repository for reference
git clone https://github.com/QuestNav/QuestNav.git questnav-reference

# Copy the java-robot folder structure
cp -r questnav-reference/java-robot/* java-robot/
```

### Option B: Create from WPILib Template

```bash
# Use WPILib VS Code extension or command line
wpilib create --template java-robot --name MetaFRCRobot
```

Then integrate QuestNav NT4 client code from their repository.

---

## Step 2: Add Vendor Dependencies

### PhotonVision

Add to `vendordeps/PhotonVision.json`:

```json
{
    "fileName": "PhotonVision.json",
    "name": "PhotonVision",
    "version": "2027.0.0",
    "uuid": "b5e23f0a-6fd2-42d4-b304-5c2f3c040c59",
    "frcYear": "2027",
    "mavenUrls": [
        "https://maven.photonvision.org/repository/internal"
    ],
    "dependencies": [],
    "jniPlatforms": {},
    "artifacts": [
        {
            "groupId": "org.photonvision",
            "artifactId": "photonlib-java",
            "version": "2027.0.0"
        }
    ]
}
```

### NetworkTables (Built into WPILib)

No additional vendordep needed — NT4 is part of WPILib core.

---

## Step 3: Implement QuestNav NT4 Client

Create `src/main/java/frc/robot/util/QuestNavClient.java`:

```java
package frc.robot.util;

import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.networktables.DoubleArraySubscriber;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.units.measure.Time;
import edu.wpi.first.wpilibj.Timer;

/**
 * NT4 client that subscribes to QuestNav pose topic.
 * QuestNav publishes pose as a double array: [x, y, z, qx, qy, qz, qw]
 */
public class QuestNavClient {
    
    private final DoubleArraySubscriber poseSubscriber;
    private Pose3d lastPose = new Pose3d();
    private double lastTimestamp = 0.0;
    private boolean hasValidData = false;
    
    // Topic name should match what QuestNav publishes
    private static final String POSE_TOPIC = "/questnav/pose";
    
    public QuestNavClient() {
        var ntInstance = NetworkTableInstance.getDefault();
        
        // Connect to NT4 server (roboRIO handles this automatically)
        // For local testing: ntInstance.startClient4("QuestNavClient");
        // ntInstance.setServerTeam(teamNumber);
        
        poseSubscriber = ntInstance.getDoubleArrayTopic(POSE_TOPIC).subscribe(new double[7]);
    }
    
    /**
     * Update pose from NT4. Call periodically (e.g., in robotPeriodic).
     */
    public void update() {
        var readout = poseSubscriber.getAtomic();
        
        if (readout.value != null && readout.value.length == 7) {
            double[] data = readout.value;
            
            // Extract translation (x, y, z in meters)
            Translation3d translation = new Translation3d(
                data[0],  // x
                data[1],  // y
                data[2]   // z
            );
            
            // Extract rotation (quaternion)
            Rotation3d rotation = new Rotation3d(
                data[3],  // qx
                data[4],  // qy
                data[5],  // qz
                data[6]   // qw
            );
            
            lastPose = new Pose3d(translation, rotation);
            lastTimestamp = readout.timestamp / 1_000_000.0; // Convert microseconds to seconds
            hasValidData = true;
        }
    }
    
    /**
     * Get the latest pose from QuestNav.
     * @return Pose3d (may be default if no data received)
     */
    public Pose3d getPose() {
        return lastPose;
    }
    
    /**
     * Get 2D pose (for use with PoseEstimator).
     * @return Pose2d (projects to field plane)
     */
    public edu.wpi.first.math.geometry.Pose2d getPose2d() {
        return lastPose.toPose2d();
    }
    
    /**
     * Check if valid QuestNav data has been received.
     */
    public boolean hasValidData() {
        return hasValidData;
    }
    
    /**
     * Get timestamp of last pose update (seconds).
     */
    public double getLastTimestamp() {
        return lastTimestamp;
    }
    
    /**
     * Get age of last pose update (seconds).
     */
    public double getPoseAge() {
        return Timer.getFPGATimestamp() - lastTimestamp;
    }
}
```

---

## Step 4: Implement Pose Fusion Manager

Create `src/main/java/frc/robot/util/PoseFusionManager.java`:

```java
package frc.robot.util;

import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.wpilibj.Timer;

/**
 * Manages fusion of QuestNav and PhotonVision pose measurements
 * into the drivetrain pose estimator.
 */
public class PoseFusionManager {
    
    private final QuestNavClient questNavClient;
    private final PhotonVisionClient photonClient; // Your existing PhotonVision wrapper
    
    // Standard deviations for measurement trust tuning
    // Lower = trust more, Higher = trust less
    private Matrix<N3, N1> questNavStdDevs;
    private Matrix<N3, N1> aprilTagStdDevs;
    
    // Dynamic trust adjustment thresholds
    private static final double QUESTNAV_MAX_AGE = 0.5; // seconds before we distrust it
    private static final double APRILTAG_MAX_DISTANCE = 5.0; // meters
    
    public PoseFusionManager(QuestNavClient questNavClient, PhotonVisionClient photonClient) {
        this.questNavClient = questNavClient;
        this.photonClient = photonClient;
        
        // Initial standard deviations (tune these!)
        // QuestNav: relatively trusted when fresh, but can drift
        this.questNavStdDevs = VecBuilder.fill(0.1, 0.1, 0.05);
        
        // AprilTag: highly trusted when visible and close
        this.aprilTagStdDevs = VecBuilder.fill(0.05, 0.05, 0.02);
    }
    
    /**
     * Update both pose sources and return measurements ready for fusion.
     * Call this in robotPeriodic before adding measurements to estimator.
     * 
     * @return PoseMeasurements containing validated measurements
     */
    public PoseMeasurements update() {
        // Update QuestNav
        questNavClient.update();
        Pose2d questNavPose = null;
        Matrix<N3, N1> questNavCurrentStdDevs = null;
        
        if (questNavClient.hasValidData()) {
            double poseAge = questNavClient.getPoseAge();
            
            if (poseAge < QUESTNAV_MAX_AGE) {
                questNavPose = questNavClient.getPose2d();
                
                // Degrade trust as data gets older
                double ageFactor = 1.0 + (poseAge / QUESTNAV_MAX_AGE) * 2.0;
                questNavCurrentStdDevs = questNavStdDevs.times(ageFactor);
            }
        }
        
        // Update PhotonVision
        var photonResult = photonClient.getLatestResult();
        Pose2d aprilTagPose = null;
        Matrix<N3, N1> aprilTagCurrentStdDevs = null;
        
        if (photonResult.targetsPresent()) {
            aprilTagPose = photonResult.estimatedPose.toPose2d();
            
            // Adjust std devs based on tag distance and angle
            double distance = photonResult.bestTarget.getBestCameraToTarget().getTranslation().toVector2d().norm();
            double distanceFactor = 1.0 + (distance / APRILTAG_MAX_DISTANCE);
            aprilTagCurrentStdDevs = aprilTagStdDevs.times(distanceFactor);
        }
        
        return new PoseMeasurements(questNavPose, questNavCurrentStdDevs, 
                                    aprilTagPose, aprilTagCurrentStdDevs);
    }
    
    /**
     * Tune QuestNav standard deviations.
     * @param x X-axis std dev (meters)
     * @param y Y-axis std dev (meters)
     * @param theta Rotation std dev (radians)
     */
    public void setQuestNavStdDevs(double x, double y, double theta) {
        questNavStdDevs = VecBuilder.fill(x, y, theta);
    }
    
    /**
     * Tune AprilTag standard deviations.
     * @param x X-axis std dev (meters)
     * @param y Y-axis std dev (meters)
     * @param theta Rotation std dev (radians)
     */
    public void setAprilTagStdDevs(double x, double y, double theta) {
        aprilTagStdDevs = VecBuilder.fill(x, y, theta);
    }
    
    // Container class for measurements
    public static class PoseMeasurements {
        public final Pose2d questNavPose;
        public final Matrix<N3, N1> questNavStdDevs;
        public final Pose2d aprilTagPose;
        public final Matrix<N3, N1> aprilTagStdDevs;
        
        public PoseMeasurements(Pose2d questNavPose, Matrix<N3, N1> questNavStdDevs,
                               Pose2d aprilTagPose, Matrix<N3, N1> aprilTagStdDevs) {
            this.questNavPose = questNavPose;
            this.questNavStdDevs = questNavStdDevs;
            this.aprilTagPose = aprilTagPose;
            this.aprilTagStdDevs = aprilTagStdDevs;
        }
    }
}
```

---

## Step 5: Integrate with Drivetrain

Modify your `Drivetrain.java` subsystem:

```java
package frc.robot.subsystems;

import com.ctre.phoenix6.sensors.Pigeon2;
import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.wpilibj.DriverStation;
import frc.robot.util.QuestNavClient;
import frc.robot.util.PoseFusionManager;
import frc.robot.util.PhotonVisionClient;

public class Drivetrain extends SubsystemBase {
    
    // ... existing swerve module declarations ...
    private final Pigeon2 pigeon;
    private final SwerveDriveKinematics kinematics;
    
    // Pose estimator
    private final SwerveDrivePoseEstimator poseEstimator;
    
    // QuestNav integration
    private final QuestNavClient questNavClient;
    private final PhotonVisionClient photonClient;
    private final PoseFusionManager fusionManager;
    
    public Drivetrain() {
        // ... initialize swerve modules, pigeon, etc. ...
        
        // Initialize QuestNav client
        questNavClient = new QuestNavClient();
        
        // Initialize PhotonVision client (your existing implementation)
        photonClient = new PhotonVisionClient();
        
        // Initialize fusion manager
        fusionManager = new PoseFusionManager(questNavClient, photonClient);
        
        // Initialize pose estimator
        // Tuning values depend on your robot - tune these!
        poseEstimator = new SwerveDrivePoseEstimator(
            kinematics,
            pigeon.getRotation2d(),
            new SwerveModulePosition[] {
                getModulePositions()[0],
                getModulePositions()[1],
                getModulePositions()[2],
                getModulePositions()[3]
            },
            new Pose2d(), // Starting pose
            VecBuilder.fill(0.05, 0.05, 0.02), // Wheel odometry std devs
            VecBuilder.fill(0.1, 0.1, 0.05)    // Vision measurement std devs (default)
        );
    }
    
    @Override
    public void periodic() {
        // Update QuestNav and PhotonVision
        var measurements = fusionManager.update();
        
        // Get current wheel odometry
        var wheelOdom = getModulePositions();
        var gyroAngle = pigeon.getRotation2d();
        
        // Add wheel odometry to estimator (always)
        poseEstimator.update(gyroAngle, wheelOdom);
        
        // Add QuestNav measurement if available
        if (measurements.questNavPose != null && measurements.questNavStdDevs != null) {
            poseEstimator.addVisionMeasurement(
                measurements.questNavPose,
                Timer.getFPGATimestamp(),
                measurements.questNavStdDevs
            );
        }
        
        // Add AprilTag measurement if available (higher trust)
        if (measurements.aprilTagPose != null && measurements.aprilTagStdDevs != null) {
            poseEstimator.addVisionMeasurement(
                measurements.aprilTagPose,
                Timer.getFPGATimestamp(),
                measurements.aprilTagStdDevs
            );
        }
    }
    
    public Pose2d getPose() {
        return poseEstimator.getEstimatedPosition();
    }
    
    public void resetPose(Pose2d pose) {
        poseEstimator.resetPosition(pigeon.getRotation2d(), getModulePositions(), pose);
    }
    
    // ... rest of drivetrain methods ...
}
```

---

## Step 6: Tuning Guide

### Initial Values

Start with these values and tune based on testing:

```java
// QuestNav (can drift over time)
fusionManager.setQuestNavStdDevs(0.1, 0.1, 0.05);

// AprilTag (ground truth when visible)
fusionManager.setAprilTagStdDevs(0.05, 0.05, 0.02);

// Wheel odometry (very trusted short-term)
// In PoseEstimator constructor:
VecBuilder.fill(0.05, 0.05, 0.02)  // Wheel odom std devs
```

### Tuning Process

1. **Test with wheel odometry only** (disable both vision sources)
   - Drive a known path, measure drift
   
2. **Enable QuestNav only**
   - Drive same path with tag occluded
   - Observe drift rate vs wheel-only
   - Adjust QuestNav std devs until drift is acceptable
   
3. **Enable AprilTag only**
   - Drive past tags, observe correction behavior
   - If overcorrects → increase AprilTag std devs
   - If undercorrects → decrease AprilTag std devs
   
4. **Enable both**
   - Drive path with tag-visible → occluded → visible cycle
   - Should track smoothly without jumps
   - If jumps occur when tag appears → increase AprilTag std devs
   - If drifts too much when tag occluded → decrease QuestNav std devs

### Dynamic Trust Adjustment

The `PoseFusionManager` already includes dynamic trust adjustment:

- QuestNav trust degrades as data ages
- AprilTag trust degrades with distance

Adjust these thresholds in `PoseFusionManager`:

```java
private static final double QUESTNAV_MAX_AGE = 0.5; // seconds
private static final double APRILTAG_MAX_DISTANCE = 5.0; // meters
```

---

## Step 7: Testing

### Bench Test (Phase 0 Validation)

Before mounting on robot:

```bash
# 1. Start NT4 server on roboRIO or laptop
# 2. Run QuestNav app on Quest (wired via Ethernet)
# 3. Run robot code in simulation or with roboRIO connected

# In Shuffleboard or AdvantageScope:
# - Add QuestNav pose topic
# - Verify data updates at 90-120 Hz
# - Move headset, confirm pose changes correctly
```

### Integration Test (Phase 1)

```bash
# 1. Mount Quest on test chassis or robot
# 2. Enable QuestNav + wheel odometry fusion (no AprilTags yet)
# 3. Drive known path (e.g., 2m forward, 90° turn, repeat)
# 4. Compare estimated pose vs actual
# 5. Tune QuestNav std devs

# Then enable AprilTag fusion:
# 6. Place AprilTag at known location
# 7. Drive toward tag, observe correction
# 8. Tune AprilTag std devs
```

### Full System Test (Phase 5)

At scrimmage:

- Log all pose sources (wheel odom, QuestNav, AprilTag, fused)
- Compare accuracy vs Track A alone
- Document drop rates, latency, drift

---

## Troubleshooting

| Issue | Possible Cause | Solution |
|-------|---------------|----------|
| No QuestNav data | App not running / wrong topic | Verify QuestNav APK running, check topic name matches |
| Pose jumps wildly | Std devs too low | Increase measurement std devs |
| Slow to correct after tag loss | QuestNav std devs too high | Decrease QuestNav std devs |
| Overcorrects at tag appearance | AprilTag std devs too low | Increase AprilTag std devs |
| Fused pose drifts long-term | Not trusting AprilTag enough | Decrease AprilTag std devs, verify tag poses correct |
| NT4 connection fails | Network config | Verify team number, check firewall, confirm same subnet |

---

## Next Steps

After Phase 1 completion:

1. ✅ Phase 1 Complete: Pose fusion working through tag-visible → occluded → visible cycle
2. ➡️ Move to **Phase 2**: Robot mounting, cable management, durability testing
3. ➡️ Plan for **Phase 3**: AprilTag-based re-initialization in Unity app (drift correction contribution)

---

## Resources

- **WPILib Pose Estimator Docs:** https://docs.wpilib.org/en/stable/docs/software/advanced-controls/state-space/state-space-intro.html
- **QuestNav NT4 Protocol:** https://github.com/QuestNav/QuestNav
- **PhotonVision Integration:** https://docs.photonvision.org/en/latest/docs/apriltag-integration/integrating-apriltags.html
- **FRC 6328 Pose Estimator Guide:** https://github.com/Mechanical-Advantage/RobotCode2023/blob/main/src/main/java/org/laserbotics/subsystems/drive/DriveSubsystem.java

---

## Revision History

- **Revision 1** (Current): Initial implementation guide based on project plan
