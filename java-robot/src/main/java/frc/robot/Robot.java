package frc.robot;

import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;

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
    private final RobotContainer m_robotContainer;
    
    @Override
    public void robotInit() {
        System.out.println("[Robot] Initializing Meta FRC Localization System");
        
        // Instantiate RobotContainer (initializes all subsystems and commands)
        m_robotContainer = new RobotContainer();
        
        // Configure NetworkTables for QuestNav communication
        NetworkTableInstance.getDefault().startClient4("MetaFRC-Robot");
        NetworkTableInstance.getDefault().setServerTeam(11269);
        
        System.out.println("[Robot] Initialization complete");
    }
    
    @Override
    public void robotPeriodic() {
        // Runs the Scheduler. This is responsible for polling buttons, adding newly-scheduled
        // commands, running already-scheduled commands, removing finished or interrupted commands,
        // and running subsystem periodic() methods. This must be called from the robot's periodic
        // block in order for anything in the Command-based framework to work.
        CommandScheduler.getInstance().run();
    }
    
    @Override
    public void autonomousInit() {
        System.out.println("[Robot] Autonomous initialized");
        
        // Reset match statistics for new match
        m_robotContainer.getDriveSubsystem().resetMatchStats();
        
        // Get selected autonomous command and schedule it
        Command autonomousCommand = m_robotContainer.getAutonomousCommand();
        if (autonomousCommand != null) {
            autonomousCommand.schedule();
        }
    }
    
    @Override
    public void autonomousPeriodic() {
        // Autonomous logic continues in scheduled commands
        // Check if tracking is lost during autonomous
        if (m_robotContainer.getDriveSubsystem().isTrackingLost()) {
            System.out.println("[Robot] WARNING: Tracking lost during autonomous!");
            // Could abort autonomous routine or switch to backup strategy
        }
    }
    
    @Override
    public void teleopInit() {
        System.out.println("[Robot] Teleop initialized");
        SmartDashboard.putString("System/Mode", "Teleop");
    }
    
    @Override
    public void teleopPeriodic() {
        // Teleop control handled by default commands in RobotContainer
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
