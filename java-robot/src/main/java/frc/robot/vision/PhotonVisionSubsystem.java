package frc.robot.vision;

import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.apriltag.AprilTagFields;
import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.photonvision.EstimatedRobotPose;
import edu.wpi.first.photonvision.PhotonCamera;
import edu.wpi.first.photonvision.PhotonPoseEstimator;
import edu.wpi.first.photonvision.PhotonPoseEstimator.PoseStrategy;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Subsystem for PhotonVision AprilTag pose estimation (Track A).
 * 
 * Provides periodic, drift-free ground-truth corrections to fuse with
 * the high-frequency QuestNav pose data.
 * 
 * Supports multiple cameras for improved coverage and accuracy.
 */
public class PhotonVisionSubsystem extends SubsystemBase {
    private final List<PhotonCamera> m_cameras = new ArrayList<>();
    private final List<PhotonPoseEstimator> m_poseEstimators = new ArrayList<>();
    
    private AprilTagFieldLayout m_fieldLayout;
    private Pose2d m_latestPose = new Pose2d();
    private boolean m_hasValidPose = false;
    private double m_lastUpdateTime = 0.0;
    private int m_tagsDetected = 0;
    
    // Tuning parameters - adjust based on testing
    private Matrix<N3, N1> m_aprilTagStdDevs = VecBuilder.fill(0.5, 0.5, 0.5);
    private double m_maxDistanceMeters = 5.0;  // Max reliable detection distance
    
    public PhotonVisionSubsystem() {
        // Load field layout
        try {
            m_fieldLayout = AprilTagFieldLayout.loadField(AprilTagFields.kDefaultField);
        } catch (Exception e) {
            System.err.println("Failed to load AprilTag field layout: " + e.getMessage());
        }
        
        // Initialize cameras (add your camera names here)
        addCamera("FrontLeftCamera", 0.5, 0.3, 0.5, 0.0);  // x, y, z, yaw
        addCamera("FrontRightCamera", 0.5, -0.3, 0.5, 0.0);
        
        SmartDashboard.putData("PhotonVision", this);
    }
    
    /**
     * Add a camera to the pose estimation system.
     * 
     * @param cameraName Name of the camera as configured in PhotonVision UI
     * @param xOffset Meters forward from robot center
     * @param yOffset Meters left from robot center
     * @param zOffset Meters up from robot center
     * @param yawDegrees Rotation around Z axis (degrees)
     */
    private void addCamera(String cameraName, double xOffset, double yOffset, 
                          double zOffset, double yawDegrees) {
        try {
            PhotonCamera camera = new PhotonCamera(cameraName);
            m_cameras.add(camera);
            
            // Create pose estimator for this camera
            PhotonPoseEstimator estimator = new PhotonPoseEstimator(
                m_fieldLayout,
                PoseStrategy.MULTI_TAG_PNP_ON_COPROCESSOR,
                camera,
                new edu.wpi.first.math.geometry.Transform3d(
                    xOffset, yOffset, zOffset,
                    new Rotation2d(yawDegrees * Math.PI / 180.0).getRotation3d()
                )
            );
            
            estimator.setMultiTagFallbackStrategy(PoseStrategy.LOWEST_AMBIGUITY);
            m_poseEstimators.add(estimator);
            
            System.out.println("Added PhotonVision camera: " + cameraName);
        } catch (Exception e) {
            System.err.println("Failed to initialize camera " + cameraName + ": " + e.getMessage());
        }
    }
    
    @Override
    public void periodic() {
        m_tagsDetected = 0;
        Optional<Pose2d> bestPose = Optional.empty();
        double lowestAmbiguity = Double.MAX_VALUE;
        
        // Check all cameras for AprilTag detections
        for (int i = 0; i < m_poseEstimators.size(); i++) {
            PhotonPoseEstimator estimator = m_poseEstimators.get(i);
            Optional<EstimatedRobotPose> result = estimator.update();
            
            if (result.isPresent()) {
                EstimatedRobotPose estPose = result.get();
                m_tagsDetected += estPose.targetsUsed.size();
                
                // Use pose with lowest ambiguity
                if (estPose.ambiguity < lowestAmbiguity && 
                    estPose.ambiguity < 0.3) {  // Reject ambiguous tags
                    bestPose = Optional.of(estPose.estimatedPose.toPose2d());
                    lowestAmbiguity = estPose.ambiguity;
                    m_lastUpdateTime = estPose.timestampSeconds;
                }
            }
        }
        
        // Update latest pose if we have a valid measurement
        if (bestPose.isPresent()) {
            m_latestPose = bestPose.get();
            m_hasValidPose = true;
        }
        
        // Log diagnostics
        SmartDashboard.putNumber("PhotonVision/TagsDetected", m_tagsDetected);
        SmartDashboard.putBoolean("PhotonVision/HasValidPose", m_hasValidPose);
        SmartDashboard.putNumber("PhotonVision/Ambiguity", lowestAmbiguity);
    }
    
    /**
     * Get the latest AprilTag-based pose estimate.
     * @return Current pose (may be default if no tags visible)
     */
    public Pose2d getEstimatedPose() {
        return m_latestPose;
    }
    
    /**
     * Check if we have a valid AprilTag measurement.
     * @return true if at least one tag is being tracked
     */
    public boolean hasValidPose() {
        return m_hasValidPose;
    }
    
    /**
     * Get standard deviations for fusion with pose estimator.
     * Adjusts based on number of tags detected and distance.
     * 
     * @return 3x1 matrix [x, y, theta] std devs
     */
    public Matrix<N3, N1> getStandardDeviations() {
        // Tighten std devs with more tags
        if (m_tagsDetected >= 2) {
            return VecBuilder.fill(0.1, 0.1, 0.1);
        } else if (m_tagsDetected == 1) {
            return VecBuilder.fill(0.3, 0.3, 0.3);
        } else {
            return m_aprilTagStdDevs;  // Very loose if no tags
        }
    }
    
    /**
     * Get number of AprilTags currently detected.
     * @return Tag count across all cameras
     */
    public int getTagCount() {
        return m_tagsDetected;
    }
    
    /**
     * Set custom standard deviations for tuning.
     */
    public void setStandardDeviations(double x, double y, double theta) {
        m_aprilTagStdDevs = VecBuilder.fill(x, y, theta);
    }
    
    /**
     * Check if pose may be unreliable (no tags for >500ms).
     */
    public boolean isStale() {
        return edu.wpi.first.wpilibj.Timer.getFPGATimestamp() - m_lastUpdateTime > 0.5;
    }
}
