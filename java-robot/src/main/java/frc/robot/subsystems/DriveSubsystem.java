package frc.robot.subsystems;

import edu.wpi.first.math.Matrix;
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
import edu.wpi.first.wpilibj.ADIS16470_IMU;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

/**
 * Swerve drive subsystem with integrated pose estimation.
 * 
 * Fuses three sources:
 * 1. Wheel odometry (high frequency, drifts over time)
 * 2. QuestNav 6DOF pose (90-120 Hz, low latency, can drift)
 * 3. PhotonVision AprilTag poses (periodic, drift-free ground truth)
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
        
        SmartDashboard.putData("Field", m_field);
    }
    
    @Override
    public void periodic() {
        // Update gyro angle (replace with actual gyro reading)
        Rotation2d gyroAngle = Rotation2d.fromDegrees(-m_gyro.getAngle());
        
        // Update module positions (replace with actual encoder readings)
        updateModulePositions();
        
        // Update pose estimator with odometry
        m_poseEstimator.update(gyroAngle, m_modulePositions);
        
        // Log current pose
        SmartDashboard.putNumber("Robot/X", getPose().getX());
        SmartDashboard.putNumber("Robot/Y", getPose().getY());
        SmartDashboard.putNumber("Robot/Rotation", getPose().getRotation().getDegrees());
        m_field.setRobotPose(getPose());
    }
    
    /**
     * Add QuestNav vision measurement to pose estimator.
     * Called from Robot.periodic() when new QuestNav data arrives.
     * 
     * @param visionRobotPoseMeters Pose from QuestNav (already transformed to robot frame)
     * @param stdDevs Standard deviations [x, y, theta]
     */
    public void addQuestNavMeasurement(Pose2d visionRobotPoseMeters, Matrix<N3, N1> stdDevs) {
        m_poseEstimator.addVisionMeasurement(
            visionRobotPoseMeters,
            edu.wpi.first.wpilibj.Timer.getFPGATimestamp(),
            stdDevs
        );
        
        SmartDashboard.putString("Vision/QuestNavPose", visionRobotPoseMeters.toString());
    }
    
    /**
     * Add PhotonVision AprilTag measurement to pose estimator.
     * Called from Robot.periodic() when new AprilTag data arrives.
     * 
     * @param visionRobotPoseMeters Pose from PhotonVision
     * @param stdDevs Standard deviations [x, y, theta]
     */
    public void addPhotonVisionMeasurement(Pose2d visionRobotPoseMeters, Matrix<N3, N1> stdDevs) {
        m_poseEstimator.addVisionMeasurement(
            visionRobotPoseMeters,
            edu.wpi.first.wpilibj.Timer.getFPGATimestamp(),
            stdDevs
        );
        
        SmartDashboard.putString("Vision/PhotonPose", visionRobotPoseMeters.toString());
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
