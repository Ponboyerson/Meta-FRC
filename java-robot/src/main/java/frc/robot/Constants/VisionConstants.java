package frc.robot;

/**
 * Container for all vision-related constants.
 * Includes thresholds for QuestNav drift detection, AprilTag processing, and pose fusion.
 */
public class VisionConstants {
    
    // ==================== QuestNav Drift Detection Thresholds ====================
    
    /**
     * Maximum acceptable position drift (meters) before triggering re-initialization check.
     * Typical value: 0.5m - large enough to avoid false positives from normal noise,
     * small enough to catch meaningful drift before it affects autonomous routines.
     */
    public static final double kQuestNavPositionDriftThreshold = 0.5;
    
    /**
     * Maximum acceptable rotation drift (degrees) before triggering re-initialization check.
     * Typical value: 10.0 degrees - rotational drift is often more noticeable in driving.
     */
    public static final double kQuestNavRotationDriftThreshold = 10.0;
    
    /**
     * Time (seconds) that drift must persist before confirming it's real and not noise.
     * Typical value: 0.3s (about 3 frames at 90Hz) - balances responsiveness with noise rejection.
     */
    public static final double kQuestNavSuspiciousTimeThreshold = 0.3;
    
    // ==================== AprilTag Re-initialization Thresholds ====================
    
    /**
     * Minimum confidence score (0.0-1.0) required for an AprilTag measurement to be used for re-init.
     * Calculated from tag count, ambiguity, and distance.
     * Typical value: 0.6 - requires decent quality measurements.
     */
    public static final double kMinTagConfidenceScore = 0.6;
    
    /**
     * Minimum number of AprilTags that must be detected for re-initialization.
     * More tags = more robust solution.
     * Typical value: 2 - single tags can be ambiguous or misidentified.
     */
    public static final int kMinTagsForReinit = 2;
    
    /**
     * Maximum average distance (meters) to AprilTags for re-initialization.
     * Tags further away have higher reprojection error and less reliable poses.
     * Typical value: 4.0m - about 13 feet, reasonable for most field positions.
     */
    public static final double kMaxTagDistanceForReinit = 4.0;
    
    /**
     * Maximum average ambiguity score for AprilTags used in re-initialization.
     * Ambiguity ranges from 0.0 (perfect) to 1.0 (highly ambiguous).
     * Typical value: 0.3 - reject ambiguous tag detections.
     */
    public static final double kMaxAmbiguityForReinit = 0.3;
    
    // ==================== Pose Fusion Standard Deviations ====================
    
    /**
     * Standard deviation for QuestNav X/Y position measurements (meters).
     * Lower = trust more. QuestNav VIO is high-frequency but can drift.
     * Typical value: 0.1m (10cm) for normal operation.
     */
    public static final double kQuestNavPositionStdDev = 0.1;
    
    /**
     * Standard deviation for QuestNav rotation measurements (radians).
     * Typical value: 0.05 rad (~3 degrees) for normal operation.
     */
    public static final double kQuestNavRotationStdDev = 0.05;
    
    /**
     * Standard deviation for QuestNav measurements when drift is suspected.
     * Increased to reduce trust in potentially drifted measurements.
     * Typical value: 0.5m (5x normal).
     */
    public static final double kQuestNavPositionStdDevSuspicious = 0.5;
    
    /**
     * Standard deviation for QuestNav rotation when drift is suspected.
     * Typical value: 0.25 rad (~14 degrees, 5x normal).
     */
    public static final double kQuestNavRotationStdDevSuspicious = 0.25;
    
    // ==================== AprilTag Measurement Standard Deviations ====================
    
    /**
     * Base standard deviation for AprilTag X/Y position (meters).
     * AprilTags provide ground-truth corrections but at lower frequency.
     * Typical value: 0.05m (5cm) for close, well-lit tags.
     */
    public static final double kAprilTagPositionStdDevBase = 0.05;
    
    /**
     * Base standard deviation for AprilTag rotation (radians).
     * Typical value: 0.03 rad (~1.7 degrees).
     */
    public static final double kAprilTagRotationStdDevBase = 0.03;
    
    /**
     * Multiplier applied to AprilTag std devs based on distance.
     * Further tags get higher uncertainty.
     * Typical value: 0.2 per meter.
     */
    public static final double kAprilTagDistanceStdDevFactor = 0.2;
    
    // ==================== Staleness Thresholds ====================
    
    /**
     * Time (seconds) after which QuestNav measurements are considered stale.
     * QuestNav runs at 90-120Hz, so this should be short.
     * Typical value: 0.1s (100ms).
     */
    public static final double kQuestNavStalenessThreshold = 0.1;
    
    /**
     * Time (seconds) after which AprilTag measurements are considered stale.
     * PhotonVision typically runs at 30-60Hz depending on camera count.
     * Typical value: 0.5s (500ms).
     */
    public static final double kAprilTagStalenessThreshold = 0.5;
    
    // ==================== Coordinate System Constants ====================
    
    /**
     * Whether to flip QuestNav pose for red alliance.
     * QuestNav uses its own coordinate system; WPILib uses blue alliance origin.
     * Set to true if QuestNav doesn't handle alliance flipping internally.
     */
    public static final boolean kFlipPoseForRedAlliance = true;
    
    /**
     * Default height of QuestNav headset above the floor (meters).
     * Used for 3D-to-2D projection if needed.
     * Typical value: 0.5m (about 20 inches for a robot-mounted Quest).
     */
    public static final double kQuestNavHeightAboveFloor = 0.5;
}
