package frc.robot;

import edu.wpi.first.wpilibj.RobotBase;

/**
 * Main entry point for the Meta FRC Robot project.
 * 
 * This robot code implements Track B localization using QuestNav 6DOF pose data
 * fused with Track A PhotonVision AprilTag measurements.
 */
public final class Main {
    private Main() {}

    public static void main(String... args) {
        RobotBase.startRobot(Robot::new);
    }
}
