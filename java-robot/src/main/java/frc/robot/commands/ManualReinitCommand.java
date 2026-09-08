package frc.robot.commands;

import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.DriveSubsystem;

/**
 * Command to trigger manual re-initialization of QuestNav SLAM.
 * Should be bound to a controller button for driver use.
 * 
 * Use case: Driver notices drift during a match and wants to force
 * an immediate re-init using current AprilTag measurements.
 * 
 * Note: Requires access to QuestNavReinitializer - may need to expose
 * via DriveSubsystem getter or pass directly in constructor.
 */
public class ManualReinitCommand extends Command {
    private final DriveSubsystem m_driveSubsystem;
    
    /**
     * Creates a new ManualReinitCommand.
     * 
     * @param driveSubsystem The drive subsystem (should have access to reinitializer)
     */
    public ManualReinitCommand(DriveSubsystem driveSubsystem) {
        m_driveSubsystem = driveSubsystem;
        
        // Don't require the subsystem - we just want to trigger an action
        // addRequirements(driveSubsystem);
    }
    
    @Override
    public void initialize() {
        System.out.println("[ManualReinitCommand] Manual re-init requested by driver");
        
        // TODO: Get reinitializer from DriveSubsystem and call manualReinitialize()
        // This will need to be implemented once the interface is finalized
        
        System.out.println("[ManualReinitCommand] Re-init triggered - waiting for valid AprilTag solution");
    }
    
    @Override
    public boolean isFinished() {
        // Complete immediately after triggering
        return true;
    }
    
    @Override
    public void end(boolean interrupted) {
        if (!interrupted) {
            System.out.println("[ManualReinitCommand] Command completed");
        }
    }
}
