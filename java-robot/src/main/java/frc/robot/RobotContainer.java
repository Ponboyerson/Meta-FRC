package frc.robot;

import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.commands.ManualReinitCommand;
import frc.robot.commands.ResetPoseCommand;
import frc.robot.subsystems.DriveSubsystem;
import frc.robot.subsystems.QuestNavSubsystem;
import frc.robot.vision.PhotonVisionSubsystem;

/**
 * RobotContainer - Central hub for subsystem instantiation and command binding.
 * 
 * Implements FRC best practices:
 * - Subsystem encapsulation (hardware accessed only through subsystems)
 * - Command-based logic (complex actions as reusable commands)
 * - Modular autonomous routines (easily chainable with SequentialCommandGroup)
 * - Driver-friendly controls with clear feedback
 */
public class RobotContainer {
    // ===== Subsystems (Hardware Encapsulation) =====
    private final DriveSubsystem m_driveSubsystem;
    private final QuestNavSubsystem m_questNavSubsystem;
    private final PhotonVisionSubsystem m_photonVisionSubsystem;
    
    // ===== Driver Controller (Xbox Controller Port 0) =====
    private final XboxController m_driverController = new XboxController(0);
    
    // ===== Autonomous Selector =====
    private final SendableChooser<Command> m_autoChooser;
    
    /**
     * Initialize all subsystems and configure command bindings.
     */
    public RobotContainer() {
        System.out.println("[RobotContainer] Initializing Meta FRC Robot");
        
        // Initialize subsystems
        m_questNavSubsystem = new QuestNavSubsystem();
        m_photonVisionSubsystem = new PhotonVisionSubsystem();
        m_driveSubsystem = new DriveSubsystem();
        
        // Configure default command for drive subsystem
        configureDefaultCommands();
        
        // Configure driver controls
        configureDriverControls();
        
        // Configure autonomous routines
        m_autoChooser = configureAutonomous();
        
        // Put autonomous chooser on dashboard
        SmartDashboard.putData("Autonomous Mode", m_autoChooser);
        
        System.out.println("[RobotContainer] Initialization complete");
    }
    
    /**
     * Set default commands for all subsystems.
     * Default commands run continuously when no other command is scheduled.
     */
    private void configureDefaultCommands() {
        // Drive subsystem default: field-relative drive with controller
        m_driveSubsystem.setDefaultCommand(
            new RunCommand(() -> {
                double ySpeed = -m_driverController.getLeftY();
                double xSpeed = -m_driverController.getLeftX();
                double rot = -m_driverController.getRightX();
                
                // Apply deadband to prevent drift
                if (Math.abs(ySpeed) < 0.1) ySpeed = 0;
                if (Math.abs(xSpeed) < 0.1) xSpeed = 0;
                if (Math.abs(rot) < 0.1) rot = 0;
                
                m_driveSubsystem.driveFieldRelative(ySpeed, xSpeed, rot);
            }, m_driveSubsystem)
        );
    }
    
    /**
     * Bind commands to controller buttons.
     * Uses Trigger API for clean, readable bindings.
     */
    private void configureDriverControls() {
        // ===== A Button: Manual SLAM Re-initialization =====
        // Hold to trigger AprilTag-based drift correction
        new JoystickButton(m_driverController, XboxController.Button.kA.value)
            .whileTrue(new ManualReinitCommand(m_driveSubsystem));
        
        // ===== B Button: Reset Pose to Zero =====
        // For field setup or recovery from catastrophic tracking loss
        new JoystickButton(m_driverController, XboxController.Button.kB.value)
            .onTrue(new ResetPoseCommand(m_driveSubsystem));
        
        // ===== X Button: Toggle Field-Oriented / Robot-Oriented Drive =====
        new JoystickButton(m_driverController, XboxController.Button.kX.value)
            .onTrue(new RunCommand(() -> {
                boolean current = m_driveSubsystem.isFieldRelative();
                m_driveSubsystem.setFieldRelative(!current);
                System.out.println("[Drive] Field-relative: " + !current);
            }).ignoringDisable(true));
        
        // ===== Start Button: Zero Gyro / Reset Heading =====
        new JoystickButton(m_driverController, XboxController.Button.kStart.value)
            .onTrue(new RunCommand(() -> {
                m_driveSubsystem.zeroGyro();
                System.out.println("[Drive] Gyro zeroed");
            }).ignoringDisable(true));
        
        // ===== D-Pad Up: Alliance Flip Toggle =====
        new JoystickButton(m_driverController, XboxController.Button.kUp.value)
            .onTrue(new RunCommand(() -> {
                boolean current = m_driveSubsystem.isAllianceFlipped();
                m_driveSubsystem.setAllianceFlipped(!current);
                System.out.println("[Drive] Alliance flipped: " + !current);
            }).ignoringDisable(true));
    }
    
    /**
     * Build autonomous command groups using SequentialCommandGroup.
     * Modular design allows easy recombination of actions.
     * 
     * @return SendableChooser with autonomous options
     */
    private SendableChooser<Command> configureAutonomous() {
        SendableChooser<Command> chooser = new SendableChooser<>();
        
        // Option 1: Do Nothing (Safe default)
        chooser.addOption("Do Nothing", new RunCommand(() -> {}).withTimeout(0));
        
        // Option 2: Drive Forward 3 meters (Simple test)
        // TODO: Replace with PathPlanner/Choreo trajectories when available
        Command driveForward = new RunCommand(() -> {
            m_driveSubsystem.driveFieldRelative(0.5, 0, 0);
        }, m_driveSubsystem).withTimeout(2.0)
        .andThen(() -> m_driveSubsystem.driveFieldRelative(0, 0, 0));
        chooser.addOption("Drive Forward 3m", driveForward);
        
        // Option 3: Wait for AprilTags, then auto-calibrate
        // Demonstrates integration with vision system
        Command autoCalibrate = new RunCommand(() -> {
            System.out.println("[Auto] Waiting for AprilTags...");
        }).withTimeout(1.0)
        .andThen(new ManualReinitCommand(m_driveSubsystem).withTimeout(3.0))
        .andThen(() -> System.out.println("[Auto] Calibration complete"));
        chooser.addOption("Auto Calibrate with AprilTags", autoCalibrate);
        
        // Option 4: Complex routine (placeholder for PathPlanner)
        // TODO: Implement full autonomous with PathPlanner or Choreo
        Command complexAuto = new RunCommand(() -> {
            System.out.println("[Auto] Running complex autonomous routine");
            // Step 1: Drive to first position
            // Step 2: Run intake while moving
            // Step 3: Navigate to second position
            // Step 4: Score or place game piece
        }).withTimeout(15.0);
        chooser.addOption("Complex Routine (TBD)", complexAuto);
        
        return chooser;
    }
    
    /**
     * Get the autonomous command selected on Shuffleboard.
     * Called by Robot.java during autonomousInit().
     * 
     * @return Selected autonomous command
     */
    public Command getAutonomousCommand() {
        return m_autoChooser.getSelected();
    }
    
    /**
     * Get drive subsystem for testing and simulation.
     * 
     * @return DriveSubsystem instance
     */
    public DriveSubsystem getDriveSubsystem() {
        return m_driveSubsystem;
    }
    
    /**
     * Get QuestNav subsystem for testing.
     * 
     * @return QuestNavSubsystem instance
     */
    public QuestNavSubsystem getQuestNavSubsystem() {
        return m_questNavSubsystem;
    }
    
    /**
     * Get PhotonVision subsystem for testing.
     * 
     * @return PhotonVisionSubsystem instance
     */
    public PhotonVisionSubsystem getPhotonVisionSubsystem() {
        return m_photonVisionSubsystem;
    }
}
