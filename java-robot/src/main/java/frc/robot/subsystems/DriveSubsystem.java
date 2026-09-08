package frc.robot.subsystems;

import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.Nat;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.wpilibj.ADIS16470_IMU;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.VisionConstants;
import frc.robot.subsystems.questnav.QuestNavReinitializer;

/**
 * Swerve drive subsystem with integrated pose estimation.
 * 
 * Fuses three sources:
 * 1. Wheel odometry (high frequency, drifts over time)
 * 2. QuestNav 6DOF pose (90-120 Hz, low latency, can drift)
 * 3. PhotonVision AprilTag poses (periodic, drift-free ground truth)
 * 
 * Features:
 * - Automatic drift detection and correction via QuestNavReinitializer
 * - Dynamic standard deviation adjustment based on sensor quality
 * - Alliance-aware coordinate system handling
 * - Comprehensive SmartDashboard telemetry
 */
public class DriveSubsystem extends SubsystemBase {
    private final SwerveDrivePoseEstimator m_poseEstimator;
    private final Field2d m_field = new Field2d();
    
    // IMU for rotation data
    private final ADIS16470_IMU m_gyro = new ADIS16470_IMU();
    
    // Kinematics (tune for your robot)
    private final SwerveDriveKinematics m_kinematics = new SwerveDriveKinematics(
        new Translation2d(0.3, 0.3),   // Front left
        new Translation2d(0.3, -0.3),  // Front right
        new Translation2d(-0.3, 0.3),  // Rear left
        new Translation2d(-0.3, -0.3)  // Rear right
    );
    
    // Mock module positions (replace with actual encoder readings)
    private SwerveModulePosition[] m_modulePositions = new SwerveModulePosition[] {
        new SwerveModulePosition(),
        new SwerveModulePosition(),
        new SwerveModulePosition(),
        new SwerveModulePosition()
    };
    
    // QuestNav re-initialization handler
    private final QuestNavReinitializer m_reinitializer = new QuestNavReinitializer();
    
    // Alliance color
    private boolean m_isRedAlliance = false;
    
    // Maximum speed for desaturation
    private static final double kMaxSpeedMetersPerSecond = 4.0;
    
    public DriveSubsystem() {
        // Initialize pose estimator with initial pose at origin
        m_poseEstimator = new SwerveDrivePoseEstimator(
            m_kinematics,
            Rotation2d.fromDegrees(0),  // Initial gyro angle
            m_modulePositions,          // Initial wheel positions
            new Pose2d(0, 0, Rotation2d.fromDegrees(0)),  // Initial pose
            VecBuilder.fill(0.05, 0.05, 0.05),  // Odometry std devs
            VecBuilder.fill(0.1, 0.1, 0.1)      // Vision measurement std devs
        );
        
        // Configure reinitializer callbacks
        m_reinitializer.setOnReinitRequested(this::executeReinitialization);
        m_reinitializer.setOnReinitComplete(() -> {
            System.out.println("[DriveSubsystem] Re-initialization complete");
        });
        m_reinitializer.setOnTrackingLost(() -> {
            System.out.println("[DriveSubsystem] WARNING: QuestNav tracking LOST - relying on other sensors");
        });
        m_reinitializer.setOnTrackingRegained(() -> {
            System.out.println("[DriveSubsystem] QuestNav tracking REGAINED");
        });
        
        SmartDashboard.putData("Field", m_field);
        setupSmartDashboard();
    }
    
    /**
     * Set up SmartDashboard entries for monitoring.
     */
    private void setupSmartDashboard() {
        SmartDashboard.putNumber("Drive/MaxSpeed", kMaxSpeedMetersPerSecond);
        SmartDashboard.putBoolean("Drive/IsRedAlliance", m_isRedAlliance);
    }
    
    @Override
    public void periodic() {
        // Update alliance color from DriverStation
        DriverStation.getAlliance().ifPresent(alliance -> {
            m_isRedAlliance = alliance == DriverStation.Alliance.Red;
        });
        
        // Update gyro angle (replace with actual gyro reading)
        Rotation2d gyroAngle = Rotation2d.fromDegrees(-m_gyro.getAngle());
        
        // Update module positions (replace with actual encoder readings)
        updateModulePositions();
        
        // Update pose estimator with odometry
        m_poseEstimator.update(gyroAngle, m_modulePositions);
        
        // Update reinitializer with current fused pose
        m_reinitializer.updateLastKnownGoodPose(getPose());
        
        // Log current pose and diagnostics
        SmartDashboard.putNumber("Robot/X", getPose().getX());
        SmartDashboard.putNumber("Robot/Y", getPose().getY());
        SmartDashboard.putNumber("Robot/Rotation", getPose().getRotation().getDegrees());
        SmartDashboard.putString("Robot/State", m_reinitializer.getState().toString());
        m_field.setRobotPose(getPose());
        
        // Update reinitializer stats
        m_reinitializer.periodic();
    }
    
    /**
     * Add QuestNav vision measurement to pose estimator.
     * Called from Robot.periodic() when new QuestNav data arrives.
     * 
     * This method now includes:
     * - Dynamic standard deviation adjustment based on drift state
     * - Automatic drift detection via QuestNavReinitializer
     * - Alliance-aware coordinate transformation
     * 
     * @param questNavPose Raw pose from QuestNav (in Quest SLAM frame)
     * @param baseStdDevs Base standard deviations [x, y, theta]
     */
    public void addQuestNavMeasurement(Pose2d questNavPose, Matrix<N3, N1> baseStdDevs) {
        // Transform QuestNav pose to FRC field coordinates
        Pose2d fieldPose = frc.robot.util.QuestNavPose.transformToFRCField(questNavPose, m_isRedAlliance);
        
        // Apply robot offset transform (Quest is mounted at a specific position on robot)
        Transform2d mountTransform = frc.robot.util.QuestNavPose.createMountTransform(0.3, 0.0, 0.5, 0.0);
        Pose2d robotPose = frc.robot.util.QuestNavPose.applyRobotOffset(fieldPose, mountTransform);
        
        // Validate pose before using
        if (!frc.robot.util.QuestNavPose.isValid(robotPose)) {
            System.err.println("[DriveSubsystem] Invalid QuestNav pose detected - skipping measurement");
            return;
        }
        
        // Update reinitializer for drift detection
        m_reinitializer.updateQuestNavPose(robotPose);
        
        // Adjust standard deviations based on drift state
        Matrix<N3, N1> adjustedStdDevs = adjustQuestNavStdDevs(baseStdDevs);
        
        // Add measurement to pose estimator
        m_poseEstimator.addVisionMeasurement(
            robotPose,
            edu.wpi.first.wpilibj.Timer.getFPGATimestamp(),
            adjustedStdDevs
        );
        
        // Log diagnostics
        SmartDashboard.putString("Vision/QuestNavPose", robotPose.toString());
        SmartDashboard.putNumber("Vision/QuestNavStdDevX", adjustedStdDevs.get(0, 0));
        SmartDashboard.putNumber("Vision/QuestNavStdDevY", adjustedStdDevs.get(1, 0));
        SmartDashboard.putNumber("Vision/QuestNavStdDevTheta", adjustedStdDevs.get(2, 0));
    }
    
    /**
     * Adjust QuestNav standard deviations based on current drift state.
     * Increases uncertainty when drift is suspected to reduce trust in measurements.
     * 
     * @param baseStdDevs Base standard deviations
     * @return Adjusted standard deviations
     */
    private Matrix<N3, N1> adjustQuestNavStdDevs(Matrix<N3, N1> baseStdDevs) {
        QuestNavReinitializer.ReinitState state = m_reinitializer.getState();
        
        switch (state) {
            case SUSPICIOUS:
                // Increase uncertainty while monitoring for confirmed drift
                return VecBuilder.fill(
                    baseStdDevs.get(0, 0) * 2.0,
                    baseStdDevs.get(1, 0) * 2.0,
                    baseStdDevs.get(2, 0) * 2.0
                );
            case DRIFT_DETECTED:
            case REINITIALIZING:
                // Very high uncertainty during drift confirmation/re-init
                return VecBuilder.fill(
                    VisionConstants.kQuestNavPositionStdDevSuspicious,
                    VisionConstants.kQuestNavPositionStdDevSuspicious,
                    VisionConstants.kQuestNavRotationStdDevSuspicious
                );
            case RECOVERY:
                // Maximum uncertainty - rely primarily on other sensors
                return VecBuilder.fill(1.0, 1.0, 0.5);
            default:
                // Normal operation - use provided std devs
                return baseStdDevs;
        }
    }
    
    /**
     * Add PhotonVision AprilTag measurement to pose estimator.
     * Called from Robot.periodic() when new AprilTag data arrives.
     * 
     * AprilTag measurements serve as ground-truth corrections and are also used
     * by QuestNavReinitializer for drift detection and automatic re-initialization.
     * 
     * @param aprilTagPose Pose from PhotonVision (already in robot frame)
     * @param tagCount Number of AprilTags detected
     * @param averageAmbiguity Average ambiguity score of detected tags
     * @param averageDistance Average distance to detected tags (meters)
     * @param baseStdDevs Base standard deviations [x, y, theta]
     */
    public void addPhotonVisionMeasurement(
        Pose2d aprilTagPose,
        int tagCount,
        double averageAmbiguity,
        double averageDistance,
        Matrix<N3, N1> baseStdDevs
    ) {
        // Validate pose
        if (!frc.robot.util.QuestNavPose.isValid(aprilTagPose)) {
            System.err.println("[DriveSubsystem] Invalid AprilTag pose detected - skipping measurement");
            return;
        }
        
        // Process through reinitializer for drift detection and potential re-init
        double timestamp = edu.wpi.first.wpilibj.Timer.getFPGATimestamp();
        m_reinitializer.processAprilTagMeasurement(
            aprilTagPose,
            tagCount,
            averageAmbiguity,
            averageDistance,
            timestamp
        );
        
        // Adjust std devs based on tag quality
        Matrix<N3, N1> adjustedStdDevs = adjustAprilTagStdDevs(baseStdDevs, tagCount, averageDistance);
        
        // Only add measurement if we have valid tags
        if (tagCount > 0) {
            m_poseEstimator.addVisionMeasurement(
                aprilTagPose,
                timestamp,
                adjustedStdDevs
            );
            
            SmartDashboard.putString("Vision/PhotonPose", aprilTagPose.toString());
            SmartDashboard.putNumber("Vision/AprilTagStdDevX", adjustedStdDevs.get(0, 0));
            SmartDashboard.putNumber("Vision/AprilTagStdDevY", adjustedStdDevs.get(1, 0));
            SmartDashboard.putNumber("Vision/AprilTagStdDevTheta", adjustedStdDevs.get(2, 0));
        }
    }
    
    /**
     * Overload for backward compatibility - uses default values for tag metrics.
     */
    public void addPhotonVisionMeasurement(Pose2d visionRobotPoseMeters, Matrix<N3, N1> stdDevs) {
        addPhotonVisionMeasurement(visionRobotPoseMeters, 1, 0.1, 2.0, stdDevs);
    }
    
    /**
     * Adjust AprilTag standard deviations based on tag count and distance.
     * More tags and closer distance = higher confidence = lower std devs.
     * 
     * @param baseStdDevs Base standard deviations
     * @param tagCount Number of tags detected
     * @param averageDistance Average distance to tags (meters)
     * @return Adjusted standard deviations
     */
    private Matrix<N3, N1> adjustAprilTagStdDevs(
        Matrix<N3, N1> baseStdDevs,
        int tagCount,
        double averageDistance
    ) {
        // Base adjustment for tag count
        double positionMultiplier = 1.0;
        double rotationMultiplier = 1.0;
        
        if (tagCount >= 2) {
            // Multiple tags - high confidence
            positionMultiplier = 0.5;
            rotationMultiplier = 0.5;
        } else if (tagCount == 1) {
            // Single tag - moderate confidence
            positionMultiplier = 0.8;
            rotationMultiplier = 0.8;
        } else {
            // No tags - very low confidence (shouldn't happen if called properly)
            return VecBuilder.fill(10.0, 10.0, 5.0);
        }
        
        // Distance factor - further tags are less reliable
        double distanceFactor = 1.0 + (averageDistance * VisionConstants.kAprilTagDistanceStdDevFactor);
        
        return VecBuilder.fill(
            baseStdDevs.get(0, 0) * positionMultiplier * distanceFactor,
            baseStdDevs.get(1, 0) * positionMultiplier * distanceFactor,
            baseStdDevs.get(2, 0) * rotationMultiplier * distanceFactor
        );
    }
    
    /**
     * Execute the actual re-initialization of the pose estimator.
     * This is called by QuestNavReinitializer when drift correction is needed.
     */
    private void executeReinitialization() {
        System.out.println("[DriveSubsystem] EXECUTING pose re-initialization");
        
        // The actual pose reset happens in the reinitializer's callback
        // We just need to acknowledge and log it here
        
        SmartDashboard.putNumber("Drive/LastReinitTime", edu.wpi.first.wpilibj.Timer.getFPGATimestamp());
        SmartDashboard.putNumber("Drive/TotalReinits", m_reinitializer.getTotalReinits());
    }
    
    /**
     * Manually trigger re-initialization (e.g., from driver input).
     * Can be called from a command bound to a controller button.
     * 
     * @param newPose The pose to reset to (typically from AprilTags)
     * @return true if re-init was triggered, false if blocked
     */
    public boolean manualReinitialize(Pose2d newPose) {
        System.out.println("[DriveSubsystem] MANUAL re-initialization requested");
        
        if (!frc.robot.util.QuestNavPose.isValid(newPose)) {
            System.err.println("[DriveSubsystem] Invalid pose for manual re-init - rejected");
            return false;
        }
        
        // Trigger re-init through the reinitializer
        boolean success = m_reinitializer.manualReinitialize(newPose);
        
        if (success) {
            // Reset odometry to the new pose
            resetOdometry(newPose);
            System.out.println("[DriveSubsystem] Manual re-init executed: " + newPose);
            SmartDashboard.putNumber("Drive/LastReinitTime", edu.wpi.first.wpilibj.Timer.getFPGATimestamp());
        } else {
            System.out.println("[DriveSubsystem] Manual re-init blocked by state machine");
        }
        
        return success;
    }
    
    /**
     * Get the QuestNavReinitializer for external access.
     * Used by commands that need to interact with the drift detection system.
     * 
     * @return The reinitializer instance
     */
    public QuestNavReinitializer getReinitializer() {
        return m_reinitializer;
    }
    
    /**
     * Check if the system is currently tracking lost.
     * Useful for autonomous routines to decide whether to proceed.
     * 
     * @return true if in RECOVERY state
     */
    public boolean isTrackingLost() {
        return m_reinitializer.isTrackingLost();
    }
    
    /**
     * Get the current re-initialization state.
     * Useful for diagnostics and autonomous decision-making.
     * 
     * @return Current ReinitState
     */
    public QuestNavReinitializer.ReinitState getReinitState() {
        return m_reinitializer.getState();
    }
    
    /**
     * Reset match statistics for the reinitializer.
     * Call at the start of each match.
     */
    public void resetMatchStats() {
        m_reinitializer.resetMatchStats();
        System.out.println("[DriveSubsystem] Match stats RESET");
    }
    
    /**
     * Drive the robot with chassis speeds.
     * 
     * @param speeds ChassisSpeeds to execute
     */
    public void drive(ChassisSpeeds speeds) {
        // Convert to module states
        SwerveModuleState[] states = m_kinematics.toSwerveModuleStates(speeds);
        
        // Normalize speeds
        SwerveDriveKinematics.desaturateWheelSpeeds(states, 4.0);  // Max speed m/s
        
        // Set module states (replace with actual motor commands)
        setModuleStates(states);
    }
    
    /**
     * Get current estimated robot pose.
     * @return Pose2d in field coordinates
     */
    public Pose2d getPose() {
        return m_poseEstimator.getEstimatedPosition();
    }
    
    /**
     * Reset odometry to a known pose.
     * 
     * @param pose New robot pose
     */
    public void resetOdometry(Pose2d pose) {
        m_poseEstimator.resetPosition(
            Rotation2d.fromDegrees(-m_gyro.getAngle()),
            m_modulePositions,
            pose
        );
    }
    
    /**
     * Update module positions from encoders.
     * TODO: Replace with actual encoder readings.
     */
    private void updateModulePositions() {
        // Placeholder - replace with actual encoder readings
        // Example:
        // m_modulePositions[0] = new SwerveModulePosition(
        //     m_frontLeftDriveEncoder.getPosition(),
        //     m_frontLeftTurnEncoder.getPosition()
        // );
    }
    
    /**
     * Set module states (motor control).
     * TODO: Replace with actual motor commands.
     * 
     * @param states Target module states
     */
    private void setModuleStates(SwerveModuleState[] states) {
        // Placeholder - replace with actual motor control
        // Example:
        // m_frontLeftDriveMotor.set(states[0].speedMetersPerSecond);
        // m_frontLeftTurnMotor.setPosition(states[0].angle.getRadians());
    }
}
