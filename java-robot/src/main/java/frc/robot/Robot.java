package frc.robot;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StructSubscriber;
import edu.wpi.first.units.measure.Voltage;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.subsystems.QuestNavSubsystem;
import frc.robot.subsystems.DriveSubsystem;
import frc.robot.vision.PhotonVisionSubsystem;

/**
 * Main Robot class implementing Track B localization fusion.
 * 
 * Fuses QuestNav 6DOF pose (high-frequency, low-latency) with 
 * PhotonVision AprilTag measurements (periodic, drift-free).
 */
public class Robot extends TimedRobot {
    private DriveSubsystem m_driveSubsystem;
    private QuestNavSubsystem m_questNavSubsystem;
    private PhotonVisionSubsystem m_photonVisionSubsystem;
    
    @Override
    public void robotInit() {
        // Initialize subsystems
        m_driveSubsystem = new DriveSubsystem();
        m_questNavSubsystem = new QuestNavSubsystem();
        m_photonVisionSubsystem = new PhotonVisionSubsystem();
        
        // Configure NetworkTables
        NetworkTableInstance.getDefault().startClient4("MetaFRC-Robot");
        NetworkTableInstance.getDefault().setServerTeam(11269);
    }
    
    @Override
    public void robotPeriodic() {
        // Update all subsystems
        m_questNavSubsystem.periodic();
        m_photonVisionSubsystem.periodic();
        m_driveSubsystem.periodic();
        
        // Fuse QuestNav pose measurement (high-frequency, low-latency)
        if (m_questNavSubsystem.hasValidPose() && !m_questNavSubsystem.isStale()) {
            Pose2d questNavPose = m_questNavSubsystem.getEstimatedPose();
            
            // Transform from Quest frame to robot frame using mount offset
            Transform2d mountTransform = frc.robot.util.QuestNavPose.createMountTransform(0.3, 0.0, 0.5, 0.0);
            Pose2d robotPose = frc.robot.util.QuestNavPose.applyRobotOffset(questNavPose, mountTransform);
            
            // Add to pose estimator with appropriate std devs
            m_driveSubsystem.addQuestNavMeasurement(robotPose, m_questNavSubsystem.getStandardDeviations());
        }
        
        // Fuse PhotonVision AprilTag measurement (periodic, drift-free)
        if (m_photonVisionSubsystem.hasValidPose() && !m_photonVisionSubsystem.isStale()) {
            Pose2d photonPose = m_photonVisionSubsystem.getEstimatedPose();
            m_driveSubsystem.addPhotonVisionMeasurement(photonPose, m_photonVisionSubsystem.getStandardDeviations());
        }
        
        // Log fused pose to dashboard
        SmartDashboard.putNumber("QuestNav/TranslationX", 
            m_questNavSubsystem.getEstimatedPose().getX());
        SmartDashboard.putNumber("QuestNav/TranslationY", 
            m_questNavSubsystem.getEstimatedPose().getY());
        SmartDashboard.putNumber("QuestNav/Rotation", 
            m_questNavSubsystem.getEstimatedPose().getRotation().getDegrees());
    }
    
    @Override
    public void autonomousInit() {
        // Reset pose estimator at start of autonomous
        m_driveSubsystem.resetOdometry(m_questNavSubsystem.getEstimatedPose());
    }
    
    @Override
    public void autonomousPeriodic() {
        // Autonomous logic here
    }
    
    @Override
    public void teleopInit() {
        // Ensure pose is current at teleop start
        m_driveSubsystem.resetOdometry(m_questNavSubsystem.getEstimatedPose());
    }
    
    @Override
    public void teleopPeriodic() {
        // Teleop control logic
        if (DriverStation.isTeleopEnabled()) {
            ChassisSpeeds speeds = new ChassisSpeeds(
                -DriverStation.getStickAxis(0, 1) * 3.0,  // Y-axis
                -DriverStation.getStickAxis(0, 0) * 3.0,  // X-axis
                -DriverStation.getStickAxis(0, 2) * 2.0   // Rotation
            );
            m_driveSubsystem.drive(speeds);
        }
    }
    
    @Override
    public void disabledPeriodic() {
        // Update pose even when disabled for field setup
    }
}
