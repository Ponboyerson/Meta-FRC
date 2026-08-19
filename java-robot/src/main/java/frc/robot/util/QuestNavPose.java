package frc.robot.util;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;

/**
 * Utility class for QuestNav pose transformations.
 * 
 * Handles coordinate system conversions between:
 * - QuestNav SLAM frame (Unity coordinates)
 * - FRC field coordinate system (alliance-dependent)
 * - Robot-relative coordinates
 */
public class QuestNavPose {
    
    /**
     * Transform QuestNav pose to FRC field coordinates.
     * 
     * QuestNav uses Unity coordinate system:
     * - X: forward (right-handed, Y-up)
     * - Y: right
     * - Z: up
     * 
     * FRC uses:
     * - X: forward from blue alliance wall
     * - Y: left from blue alliance wall
     * - Rotation: counter-clockwise from blue alliance perspective
     * 
     * @param questNavPose Raw pose from QuestNav
     * @param isRedAlliance True if red alliance, false for blue
     * @return Transformed pose in FRC field coordinates
     */
    public static Pose2d transformToFRCField(Pose2d questNavPose, boolean isRedAlliance) {
        // QuestNav publishes in its own SLAM frame
        // We need to transform to FRC field coordinates
        
        Pose2d frcPose = questNavPose;
        
        // If red alliance, flip the pose
        if (isRedAlliance) {
            frcPose = flipForRedAlliance(frcPose);
        }
        
        return frcPose;
    }
    
    /**
     * Flip pose for red alliance.
     * Field is symmetric, so we rotate 180 degrees around field center.
     * 
     * @param pose Blue alliance pose
     * @return Red alliance pose
     */
    public static Pose2d flipForRedAlliance(Pose2d pose) {
        // Field dimensions (meters)
        final double fieldLength = 16.54;  // ~54.3 feet
        final double fieldWidth = 8.21;    // ~27 feet
        
        Translation2d flippedTranslation = new Translation2d(
            fieldLength - pose.getX(),
            fieldWidth - pose.getY()
        );
        
        Rotation2d flippedRotation = pose.getRotation().plus(Rotation2d.k180deg);
        
        return new Pose2d(flippedTranslation, flippedRotation);
    }
    
    /**
     * Apply robot-to-QuestNav transform.
     * 
     * The Quest headset is mounted at a fixed position on the robot.
     * This method transforms from Quest coordinates to robot center coordinates.
     * 
     * @param questPose Pose from QuestNav (in Quest coordinate frame)
     * @param robotToQuest Transform from robot center to Quest headset
     * @return Pose at robot center
     */
    public static Pose2d applyRobotOffset(Pose2d questPose, Transform2d robotToQuest) {
        // Inverse the transform to go from Quest -> Robot
        Transform2d questToRobot = new Transform2d(
            robotToQuest.getX(),
            robotToQuest.getY(),
            robotToQuest.getRotation().unaryMinus()
        );
        
        return questPose.transformBy(questToRobot);
    }
    
    /**
     * Create default robot-to-Quest transform for common mounting positions.
     * 
     * @param xOffset Meters forward from robot center (+X = forward)
     * @param yOffset Meters left from robot center (+Y = left)
     * @param zOffset Meters up from robot center (+Z = up)
     * @param yawDegrees Rotation offset in degrees
     * @return Transform2d for use with applyRobotOffset
     */
    public static Transform2d createMountTransform(
            double xOffset, double yOffset, double zOffset, double yawDegrees) {
        // Note: For 2D pose estimation, we only use X, Y, and yaw
        return new Transform2d(
            xOffset,
            yOffset,
            Rotation2d.fromDegrees(yawDegrees)
        );
    }
    
    /**
     * Validate that a pose is reasonable (not NaN or infinite).
     * 
     * @param pose Pose to validate
     * @return true if pose values are valid
     */
    public static boolean isValid(Pose2d pose) {
        if (pose == null) return false;
        
        return Double.isFinite(pose.getX()) &&
               Double.isFinite(pose.getY()) &&
               Double.isFinite(pose.getRotation().getRadians());
    }
    
    /**
     * Calculate distance between two poses.
     * 
     * @param pose1 First pose
     * @param pose2 Second pose
     * @return Euclidean distance in meters
     */
    public static double distance(Pose2d pose1, Pose2d pose2) {
        return pose1.getTranslation().getDistance(pose2.getTranslation());
    }
    
    /**
     * Calculate angular difference between two poses.
     * 
     * @param pose1 First pose
     * @param pose2 Second pose
     * @return Angular difference in radians (0 to π)
     */
    public static double angleDifference(Pose2d pose1, Pose2d pose2) {
        return Math.abs(pose1.getRotation().minus(pose2.getRotation()).getRadians());
    }
}
