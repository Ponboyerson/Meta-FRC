package frc.robot.subsystems.questnav;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.Constants.VisionConstants;

/**
 * Handles AprilTag-based re-initialization for QuestNav SLAM drift correction.
 * 
 * This class implements logic to detect tracking loss, validate AprilTag measurements,
 * and safely re-initialize the QuestNav pose estimator when drift exceeds thresholds.
 * 
 * Key features:
 * - Multi-tag validation for robust re-initialization
 * - Hysteresis to prevent oscillation between states
 * - Confidence scoring based on tag count, distance, and ambiguity
 * - Automatic recovery when tracking is regained
 */
public class QuestNavReinitializer {
    
    public enum ReinitState {
        /** Normal operation - QuestNav tracking is reliable */
        NORMAL,
        /** Suspicious - monitoring for drift confirmation */
        SUSPICIOUS,
        /** Drift detected - waiting for valid AprilTag solution */
        DRIFT_DETECTED,
        /** Executing re-initialization */
        REINITIALIZING,
        /** Re-init complete - validating new pose */
        VALIDATING,
        /** Recovery mode - QuestNav tracking lost, relying on other sensors */
        RECOVERY
    }

    private ReinitState currentState = ReinitState.NORMAL;
    
    // Configuration thresholds
    private final double positionDriftThresholdMeters;
    private final double rotationDriftThresholdDegrees;
    private final double suspiciousTimeThresholdSeconds;
    private final double minTagConfidenceScore;
    private final int minTagsForReinit;
    private final double maxTagDistanceMeters;
    private final double maxAmbiguityForReinit;
    
    // State tracking
    private double suspiciousStartTime = 0.0;
    private Pose2d lastKnownGoodPose = new Pose2d();
    private Pose2d questNavPose = new Pose2d();
    private Pose2d aprilTagPose = new Pose2d();
    private boolean hasValidAprilTagSolution = false;
    private double lastReinitTime = 0.0;
    private int consecutiveBadMeasurements = 0;
    private int consecutiveGoodMeasurements = 0;
    
    // Statistics
    private int totalReinits = 0;
    private int successfulReinits = 0;
    private int failedReinits = 0;
    private double averageDriftAtReinit = 0.0;
    private int reinitCountThisMatch = 0;
    
    // Callbacks
    private Runnable onReinitRequested;
    private Runnable onReinitComplete;
    private Runnable onTrackingLost;
    private Runnable onTrackingRegained;

    /**
     * Creates a new QuestNavReinitializer with default thresholds.
     */
    public QuestNavReinitializer() {
        this(
            VisionConstants.kQuestNavPositionDriftThreshold,
            VisionConstants.kQuestNavRotationDriftThreshold,
            VisionConstants.kQuestNavSuspiciousTimeThreshold,
            VisionConstants.kMinTagConfidenceScore,
            VisionConstants.kMinTagsForReinit,
            VisionConstants.kMaxTagDistanceForReinit,
            VisionConstants.kMaxAmbiguityForReinit
        );
    }

    /**
     * Creates a new QuestNavReinitializer with custom thresholds.
     * 
     * @param positionDriftThresholdMeters Maximum acceptable position drift before re-init
     * @param rotationDriftThresholdDegrees Maximum acceptable rotation drift before re-init
     * @param suspiciousTimeThresholdSeconds Time in suspicious state before confirming drift
     * @param minTagConfidenceScore Minimum confidence score for AprilTag solution
     * @param minTagsForReinit Minimum number of tags required for re-init
     * @param maxTagDistanceMeters Maximum distance to tags for re-init
     * @param maxAmbiguityForReinit Maximum ambiguity score for tags used in re-init
     */
    public QuestNavReinitializer(
        double positionDriftThresholdMeters,
        double rotationDriftThresholdDegrees,
        double suspiciousTimeThresholdSeconds,
        double minTagConfidenceScore,
        int minTagsForReinit,
        double maxTagDistanceMeters,
        double maxAmbiguityForReinit
    ) {
        this.positionDriftThresholdMeters = positionDriftThresholdMeters;
        this.rotationDriftThresholdDegrees = rotationDriftThresholdDegrees;
        this.suspiciousTimeThresholdSeconds = suspiciousTimeThresholdSeconds;
        this.minTagConfidenceScore = minTagConfidenceScore;
        this.minTagsForReinit = minTagsForReinit;
        this.maxTagDistanceMeters = maxTagDistanceMeters;
        this.maxAmbiguityForReinit = maxAmbiguityForReinit;
        
        setupSmartDashboard();
    }

    /**
     * Sets callback for when re-initialization is requested.
     */
    public void setOnReinitRequested(Runnable callback) {
        this.onReinitRequested = callback;
    }

    /**
     * Sets callback for when re-initialization completes.
     */
    public void setOnReinitComplete(Runnable callback) {
        this.onReinitComplete = callback;
    }

    /**
     * Sets callback for when tracking is lost.
     */
    public void setOnTrackingLost(Runnable callback) {
        this.onTrackingLost = callback;
    }

    /**
     * Sets callback for when tracking is regained.
     */
    public void setOnTrackingRegained(Runnable callback) {
        this.onTrackingRegained = callback;
    }

    /**
     * Updates the current QuestNav pose estimate.
     */
    public void updateQuestNavPose(Pose2d pose) {
        this.questNavPose = pose;
    }

    /**
     * Updates the last known good pose (from fused estimator or manual reset).
     */
    public void updateLastKnownGoodPose(Pose2d pose) {
        this.lastKnownGoodPose = pose;
    }

    /**
     * Processes AprilTag measurements and determines if re-initialization is needed.
     * 
     * @param aprilTagPose The pose estimate from AprilTag detection
     * @param tagCount Number of tags detected
     * @param averageAmbiguity Average ambiguity score of detected tags
     * @param averageDistance Average distance to detected tags
     * @param timestamp Current timestamp in seconds
     * @return true if re-initialization was performed
     */
    public boolean processAprilTagMeasurement(
        Pose2d aprilTagPose,
        int tagCount,
        double averageAmbiguity,
        double averageDistance,
        double timestamp
    ) {
        this.aprilTagPose = aprilTagPose;
        
        // Calculate confidence score for this measurement
        double confidenceScore = calculateTagConfidence(tagCount, averageAmbiguity, averageDistance);
        hasValidAprilTagSolution = confidenceScore >= minTagConfidenceScore && 
                                   tagCount >= minTagsForReinit;
        
        // Calculate drift between QuestNav and AprilTag poses
        double positionDrift = questNavPose.getTranslation().getDistance(aprilTagPose.getTranslation());
        double rotationDrift = Math.abs(questNavPose.getRotation().minus(aprilTagPose.getRotation()).getDegrees());
        
        // Update SmartDashboard
        SmartDashboard.putNumber("QuestNav/PositionDrift", positionDrift);
        SmartDashboard.putNumber("QuestNav/RotationDrift", rotationDrift);
        SmartDashboard.putNumber("QuestNav/TagConfidence", confidenceScore);
        SmartDashboard.putNumber("QuestNav/TagCount", tagCount);
        SmartDashboard.putString("QuestNav/ReinitState", currentState.toString());
        
        // State machine logic
        switch (currentState) {
            case NORMAL:
                handleNormalState(positionDrift, rotationDrift, timestamp);
                break;
            case SUSPICIOUS:
                handleSuspiciousState(positionDrift, rotationDrift, timestamp);
                break;
            case DRIFT_DETECTED:
                handleDriftDetectedState(hasValidAprilTagSolution, timestamp);
                break;
            case REINITIALIZING:
                handleReinitializingState(timestamp);
                break;
            case VALIDATING:
                handleValidatingState(positionDrift, rotationDrift, timestamp);
                break;
            case RECOVERY:
                handleRecoveryState(hasValidAprilTagSolution, timestamp);
                break;
        }
        
        // Update consecutive measurement counters
        if (hasValidAprilTagSolution && positionDrift < positionDriftThresholdMeters) {
            consecutiveGoodMeasurements++;
            consecutiveBadMeasurements = 0;
        } else {
            consecutiveBadMeasurements++;
            consecutiveGoodMeasurements = 0;
        }
        
        return false; // Will return true if reinit actually happens
    }

    /**
     * Handles normal operation state.
     */
    private void handleNormalState(double positionDrift, double rotationDrift, double timestamp) {
        if (positionDrift > positionDriftThresholdMeters || 
            rotationDrift > rotationDriftThresholdDegrees) {
            currentState = ReinitState.SUSPICIOUS;
            suspiciousStartTime = timestamp;
            System.out.println("[QuestNav] Entering SUSPICIOUS state - drift detected");
        }
    }

    /**
     * Handles suspicious state - monitoring for confirmed drift.
     */
    private void handleSuspiciousState(double positionDrift, double rotationDrift, double timestamp) {
        double timeInSuspicious = timestamp - suspiciousStartTime;
        
        // Check if drift persists long enough to confirm
        if (timeInSuspicious > suspiciousTimeThresholdSeconds &&
            positionDrift > positionDriftThresholdMeters) {
            currentState = ReinitState.DRIFT_DETECTED;
            System.out.println("[QuestNav] Drift CONFIRMED after " + timeInSuspicious + "s");
        } else if (positionDrift < positionDriftThresholdMeters * 0.5) {
            // Drift resolved naturally
            currentState = ReinitState.NORMAL;
            System.out.println("[QuestNav] Drift resolved naturally");
        }
    }

    /**
     * Handles drift detected state - waiting for valid AprilTag solution.
     */
    private void handleDriftDetectedState(boolean hasValidSolution, double timestamp) {
        if (hasValidSolution) {
            currentState = ReinitState.REINITIALIZING;
            reinitCountThisMatch++;
            System.out.println("[QuestNav] Starting re-initialization ( #" + reinitCountThisMatch + ")");
            
            if (onReinitRequested != null) {
                onReinitRequested.run();
            }
        }
    }

    /**
     * Handles re-initializing state - executes the pose reset.
     */
    private void handleReinitializingState(double timestamp) {
        // Execute re-initialization immediately
        performReinitialization();
        currentState = ReinitState.VALIDATING;
        lastReinitTime = timestamp;
        
        if (onReinitComplete != null) {
            onReinitComplete.run();
        }
    }

    /**
     * Handles validating state - confirms re-init was successful.
     */
    private void handleValidatingState(double positionDrift, double rotationDrift, double timestamp) {
        // Give QuestNav a moment to stabilize after re-init
        if (timestamp - lastReinitTime > 0.5) {
            if (positionDrift < positionDriftThresholdMeters * 0.3) {
                // Re-init successful
                currentState = ReinitState.NORMAL;
                successfulReinits++;
                totalReinits++;
                averageDriftAtReinit = (averageDriftAtReinit * (totalReinits - 1) + positionDrift) / totalReinits;
                System.out.println("[QuestNav] Re-initialization VALIDATED");
            } else {
                // Re-init failed, try again or go to recovery
                if (reinitCountThisMatch < 3) {
                    currentState = ReinitState.DRIFT_DETECTED;
                    System.out.println("[QuestNav] Re-init validation FAILED, retrying");
                } else {
                    currentState = ReinitState.RECOVERY;
                    failedReinits++;
                    totalReinits++;
                    System.out.println("[QuestNav] Too many failed re-inits, entering RECOVERY");
                    
                    if (onTrackingLost != null) {
                        onTrackingLost.run();
                    }
                }
            }
        }
    }

    /**
     * Handles recovery state - QuestNav tracking lost, relying on other sensors.
     */
    private void handleRecoveryState(boolean hasValidSolution, double timestamp) {
        // Wait for QuestNav to regain tracking naturally
        if (hasValidSolution && consecutiveGoodMeasurements > 10) {
            currentState = ReinitState.NORMAL;
            reinitCountThisMatch = 0; // Reset counter
            System.out.println("[QuestNav] Tracking REGAINED, returning to NORMAL");
            
            if (onTrackingRegained != null) {
                onTrackingRegained.run();
            }
        }
    }

    /**
     * Performs the actual re-initialization of the QuestNav pose.
     * This is called by the callback set via setOnReinitRequested().
     */
    private void performReinitialization() {
        System.out.println("[QuestNav] EXECUTING re-initialization");
        System.out.println("  Old QuestNav pose: " + questNavPose);
        System.out.println("  New pose from AprilTags: " + aprilTagPose);
        
        // The actual pose reset is done by the callback in DriveSubsystem or RobotContainer
        // Example callback implementation:
        // driveSubsystem.resetPose(aprilTagPose);
        // questNavReinitializer.updateLastKnownGoodPose(aprilTagPose);
        
        // Statistics tracking
        double drift = questNavPose.getTranslation().getDistance(aprilTagPose.getTranslation());
        averageDriftAtReinit = (averageDriftAtReinit * (totalReinits) + drift) / (totalReinits + 1);
    }

    /**
     * Calculates a confidence score for an AprilTag measurement.
     * 
     * @param tagCount Number of tags detected
     * @param averageAmbiguity Average ambiguity score (0.0 = perfect, 1.0 = ambiguous)
     * @param averageDistance Average distance to tags in meters
     * @return Confidence score from 0.0 to 1.0
     */
    private double calculateTagConfidence(int tagCount, double averageAmbiguity, double averageDistance) {
        if (tagCount == 0) return 0.0;
        
        // Tag count factor (more tags = higher confidence)
        double tagCountFactor = Math.min(tagCount / 3.0, 1.0);
        
        // Ambiguity factor (lower ambiguity = higher confidence)
        double ambiguityFactor = 1.0 - Math.min(averageAmbiguity, 1.0);
        
        // Distance factor (closer tags = higher confidence)
        double distanceFactor = 1.0 - Math.min(averageDistance / maxTagDistanceMeters, 1.0);
        distanceFactor = Math.max(distanceFactor, 0.0);
        
        // Weighted combination
        double confidence = (tagCountFactor * 0.4) + (ambiguityFactor * 0.4) + (distanceFactor * 0.2);
        
        return confidence;
    }

    /**
     * Manually triggers re-initialization (e.g., from driver input).
     * 
     * @param newPose The pose to reset to
     * @return true if re-init was triggered
     */
    public boolean manualReinitialize(Pose2d newPose) {
        if (currentState != ReinitState.NORMAL) {
            System.out.println("[QuestNav] Manual re-init blocked - already in state: " + currentState);
            return false;
        }
        
        System.out.println("[QuestNav] MANUAL re-initialization requested");
        aprilTagPose = newPose;
        hasValidAprilTagSolution = true;
        currentState = ReinitState.REINITIALIZING;
        reinitCountThisMatch++;
        
        if (onReinitRequested != null) {
            onReinitRequested.run();
        }
        
        return true;
    }

    /**
     * Resets match-specific statistics.
     */
    public void resetMatchStats() {
        reinitCountThisMatch = 0;
        currentState = ReinitState.NORMAL;
        consecutiveBadMeasurements = 0;
        consecutiveGoodMeasurements = 0;
        hasValidAprilTagSolution = false;
        System.out.println("[QuestNav] Match stats RESET");
    }

    /**
     * Gets the current re-initialization state.
     */
    public ReinitState getState() {
        return currentState;
    }

    /**
     * Checks if re-initialization is currently in progress.
     */
    public boolean isReinitializing() {
        return currentState == ReinitState.REINITIALIZING || 
               currentState == ReinitState.VALIDATING;
    }

    /**
     * Checks if the system is in recovery mode (QuestNav tracking lost).
     */
    public boolean isTrackingLost() {
        return currentState == ReinitState.RECOVERY;
    }

    /**
     * Gets statistics about re-initializations.
     */
    public String getStats() {
        return String.format(
            "Reinits: %d total, %d successful, %d failed | Avg drift: %.3fm | This match: %d",
            totalReinits, successfulReinits, failedReinits, 
            averageDriftAtReinit, reinitCountThisMatch
        );
    }

    /**
     * Sets up SmartDashboard entries for monitoring.
     */
    private void setupSmartDashboard() {
        SmartDashboard.putString("QuestNav/ReinitState", currentState.toString());
        SmartDashboard.putNumber("QuestNav/PositionDrift", 0.0);
        SmartDashboard.putNumber("QuestNav/RotationDrift", 0.0);
        SmartDashboard.putNumber("QuestNav/TagConfidence", 0.0);
        SmartDashboard.putNumber("QuestNav/TagCount", 0);
        SmartDashboard.putNumber("QuestNav/TotalReinits", 0);
        SmartDashboard.putNumber("QuestNav/SuccessfulReinits", 0);
        SmartDashboard.putNumber("QuestNav/FailedReinits", 0);
    }

    /**
     * Periodic method to update SmartDashboard statistics.
     */
    public void periodic() {
        SmartDashboard.putString("QuestNav/ReinitState", currentState.toString());
        SmartDashboard.putNumber("QuestNav/TotalReinits", totalReinits);
        SmartDashboard.putNumber("QuestNav/SuccessfulReinits", successfulReinits);
        SmartDashboard.putNumber("QuestNav/FailedReinits", failedReinits);
        SmartDashboard.putNumber("QuestNav/AvgDriftAtReinit", averageDriftAtReinit);
        SmartDashboard.putNumber("QuestNav/ReinitsThisMatch", reinitCountThisMatch);
        SmartDashboard.putBoolean("QuestNav/HasValidAprilTag", hasValidAprilTagSolution);
        SmartDashboard.putNumber("QuestNav/ConsecutiveGoodMeasurements", consecutiveGoodMeasurements);
        SmartDashboard.putNumber("QuestNav/ConsecutiveBadMeasurements", consecutiveBadMeasurements);
    }
}
