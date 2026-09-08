package frc.robot.commands;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.DriveSubsystem;

/**
 * Command to manually reset the robot pose using a known field position.
 * Useful for:
 * - Initial setup before a match
 * - Recovery from catastrophic tracking loss
 * - Testing and calibration
 * 
 * Usage: new ResetPoseCommand(driveSubsystem, new Pose2d(1.0, 2.0, Rotation2d.kZero))
 */
public class ResetPoseCommand extends Command {
    private final DriveSubsystem m_driveSubsystem;
    private final Pose2d m_targetPose;
    
    /**
     * Creates a new ResetPoseCommand.
     * 
     * @param driveSubsystem The drive subsystem
     * @param targetPose The pose to reset to (in field coordinates)
     */
    public ResetPoseCommand(DriveSubsystem driveSubsystem, Pose2d targetPose) {
        m_driveSubsystem = driveSubsystem;
        m_targetPose = targetPose;
        
        addRequirements(driveSubsystem);
    }
    
    @Override
    public void initialize() {
        System.out.println("[ResetPoseCommand] Resetting pose to: " + m_targetPose);
        m_driveSubsystem.resetOdometry(m_targetPose);
    }
    
    @Override
    public boolean isFinished() {
        // Command completes immediately after resetting
        return true;
    }
    
    @Override
    public void end(boolean interrupted) {
        if (interrupted) {
            System.out.println("[ResetPoseCommand] Interrupted before completion");
        } else {
            System.out.println("[ResetPoseCommand] Pose reset complete");
        }
    }
}
