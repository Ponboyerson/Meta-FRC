package frc.robot;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.networktables.NetworkTableInstance;
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
 * 
 * Features:
 * - Automatic SLAM drift detection and correction
 * - Dynamic sensor weighting based on quality metrics
 * - Alliance-aware coordinate system handling
 * - Comprehensive telemetry for debugging
 */
public class Robot extends TimedRobot {
    private DriveSubsystem m_driveSubsystem;
    private QuestNavSubsystem m_questNavSubsystem;
    private PhotonVisionSubsystem m_photonVisionSubsystem;
    
    // Track previous tag count for change detection
    private int m_prevTagCount = 0;
    
    @Override
    public void robotInit() {
        System.out.println("[Robot] Initializing Meta FRC Localization System");
        
        // Initialize subsystems
        m_driveSubsystem = new DriveSubsystem();
        m_questNavSubsystem = new QuestNavSubsystem();
        m_photonVisionSubsystem = new PhotonVisionSubsystem();
        
        // Configure NetworkTables for QuestNav communication
        NetworkTableInstance.getDefault().startClient4("MetaFRC-Robot");
        NetworkTableInstance.getDefault().setServerTeam(11269);
        
        System.out.println("[Robot] Initialization complete");
    }
    
    @Override
    public void robotPeriodic() {
        // Update all subsystems first
        m_questNavSubsystem.periodic();
        m_photonVisionSubsystem.periodic();
        m_driveSubsystem.periodic();
        
        // ===== QuestNav Fusion (High-Frequency, Low-Latency) =====
        if (m_questNavSubsystem.hasValidPose() && !m_questNavSubsystem.isStale()) {
            Pose2d questNavPose = m_questNavSubsystem.getEstimatedPose();
            
            // Validate pose before using
            if (frc.robot.util.QuestNavPose.isValid(questNavPose)) {
                // DriveSubsystem handles coordinate transformation internally
                m_driveSubsystem.addQuestNavMeasurement(
                    questNavPose, 
                    m_questNavSubsystem.getStandardDeviations()
                );
                
                SmartDashboard.putBoolean("Vision/QuestNavActive", true);
            } else {
                SmartDashboard.putBoolean("Vision/QuestNavActive", false);
            }
        } else {
            SmartDashboard.putBoolean("Vision/QuestNavActive", false);
            if (!m_questNavSubsystem.hasValidPose()) {
                SmartDashboard.putString("Vision/Status", "No QuestNav pose received");
            } else if (m_questNavSubsystem.isStale()) {
                SmartDashboard.putString("Vision/Status", "QuestNav data stale");
            }
        }
        
        // ===== PhotonVision Fusion (Periodic, Drift-Free Ground Truth) =====
        if (m_photonVisionSubsystem.hasValidPose() && !m_photonVisionSubsystem.isStale()) {
            Pose2d photonPose = m_photonVisionSubsystem.getEstimatedPose();
            int tagCount = m_photonVisionSubsystem.getTagCount();
            
            // Get detailed tag metrics for better fusion
            double avgAmbiguity = getAverageAmbiguity();
            double avgDistance = getAverageTagDistance();
            
            if (frc.robot.util.QuestNavPose.isValid(photonPose) && tagCount > 0) {
                m_driveSubsystem.addPhotonVisionMeasurement(
                    photonPose,
                    tagCount,
                    avgAmbiguity,
                    avgDistance,
                    m_photonVisionSubsystem.getStandardDeviations()
                );
                
                // Detect tag count changes for debugging
                if (tagCount != m_prevTagCount) {
                    System.out.println("[Robot] AprilTag count changed: " + m_prevTagCount + " -> " + tagCount);
                    m_prevTagCount = tagCount;
                }
                
                SmartDashboard.putBoolean("Vision/AprilTagActive", true);
                SmartDashboard.putNumber("Vision/TagCount", tagCount);
            } else {
                SmartDashboard.putBoolean("Vision/AprilTagActive", false);
            }
        } else {
            SmartDashboard.putBoolean("Vision/AprilTagActive", false);
        }
        
        // ===== Log Fused Pose and Diagnostics =====
        logDiagnostics();
    }
    
    /**
     * Get average ambiguity from PhotonVision cameras.
     * TODO: Implement based on your PhotonVision setup.
     */
    private double getAverageAmbiguity() {
        // Placeholder - replace with actual PhotonVision ambiguity reading
        return 0.1;
    }
    
    /**
     * Get average distance to detected AprilTags.
     * TODO: Implement based on your PhotonVision setup.
     */
    private double getAverageTagDistance() {
        // Placeholder - replace with actual distance calculation
        return 2.0;
    }
    
    /**
     * Log comprehensive diagnostics to SmartDashboard.
     */
    private void logDiagnostics() {
        SmartDashboard.putNumber("QuestNav/TranslationX", 
            m_questNavSubsystem.getEstimatedPose().getX());
        SmartDashboard.putNumber("QuestNav/TranslationY", 
            m_questNavSubsystem.getEstimatedPose().getY());
        SmartDashboard.putNumber("QuestNav/Rotation", 
            m_questNavSubsystem.getEstimatedPose().getRotation().getDegrees());
        
        SmartDashboard.putNumber("Fused/X", m_driveSubsystem.getPose().getX());
        SmartDashboard.putNumber("Fused/Y", m_driveSubsystem.getPose().getY());
        SmartDashboard.putNumber("Fused/Rotation", m_driveSubsystem.getPose().getRotation().getDegrees());
        
        SmartDashboard.putBoolean("System/AllSensorsHealthy", 
            m_questNavSubsystem.hasValidPose() && m_photonVisionSubsystem.hasValidPose());
    }
    
    @Override
    public void autonomousInit() {
        System.out.println("[Robot] Autonomous initialized");
        
        // Reset match statistics for new match
        m_driveSubsystem.resetMatchStats();
        
        // Reset pose estimator at start of autonomous
        // Use fused pose estimate which incorporates all sensor data
        m_driveSubsystem.resetOdometry(m_driveSubsystem.getPose());
    }
    
    @Override
    public void autonomousPeriodic() {
        // Autonomous logic here
        // Pose estimation continues in robotPeriodic()
        
        // Example: Check if tracking is lost before executing autonomous routine
        if (m_driveSubsystem.isTrackingLost()) {
            System.out.println("[Robot] WARNING: Tracking lost during autonomous!");
            // Could abort autonomous routine or switch to backup strategy
        }
    }
    
    @Override
    public void teleopInit() {
        System.out.println("[Robot] Teleop initialized");
        
        // Ensure pose is current at teleop start
        // Don't reset - just continue with current estimate
        SmartDashboard.putString("System/Mode", "Teleop");
    }
    
    @Override
    public void teleopPeriodic() {
        // Teleop control logic
        if (DriverStation.isTeleopEnabled()) {
            ChassisSpeeds speeds = new ChassisSpeeds(
                -DriverStation.getStickAxis(0, 1) * 3.0,  // Y-axis (forward/back)
                -DriverStation.getStickAxis(0, 0) * 3.0,  // X-axis (left/right)
                -DriverStation.getStickAxis(0, 2) * 2.0   // Rotation (twist)
            );
            m_driveSubsystem.drive(speeds);
        }
        
        SmartDashboard.putString("System/Mode", "Teleop");
    }
    
    @Override
    public void disabledInit() {
        System.out.println("[Robot] Disabled");
        SmartDashboard.putString("System/Mode", "Disabled");
    }
    
    @Override
    public void disabledPeriodic() {
        // Pose estimation continues even when disabled
        // This allows field setup and manual positioning
        SmartDashboard.putString("System/Mode", "Disabled");
    }
    
    @Override
    public void testInit() {
        System.out.println("[Robot] Test mode initialized");
        SmartDashboard.putString("System/Mode", "Test");
    }
    
    @Override
    public void testPeriodic() {
        // Test mode - run diagnostics
        SmartDashboard.putString("System/Mode", "Test");
    }
}
