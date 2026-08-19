package frc.robot.subsystems;

import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StructSubscriber;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.util.QuestNavPose;

/**
 * Subsystem that receives 6DOF pose data from QuestNav via NetworkTables.
 * 
 * QuestNav publishes pose at 90-120 Hz over NT4 with low latency.
 * This subsystem subscribes to the pose topic and provides the latest measurement.
 * 
 * Topic: /questnav/pose (struct Pose2d)
 */
public class QuestNavSubsystem extends SubsystemBase {
    private final NetworkTable m_table;
    private final StructSubscriber<Pose2d> m_poseSubscriber;
    
    // Latest pose from QuestNav
    private Pose2d m_latestPose = new Pose2d();
    private boolean m_hasValidPose = false;
    private double m_lastUpdateTime = 0.0;
    
    // Tuning parameters - adjust based on testing
    private Matrix<N3, N1> m_questNavStdDevs = VecBuilder.fill(0.1, 0.1, 0.05);
    
    public QuestNavSubsystem() {
        m_table = NetworkTableInstance.getDefault().getTable("questnav");
        m_poseSubscriber = m_table.getStructTopic("pose", Pose2d.struct).subscribe(new Pose2d());
        
        SmartDashboard.putData("QuestNav", this);
    }
    
    @Override
    public void periodic() {
        // Get latest pose from subscriber
        Pose2d newPose = m_poseSubscriber.get();
        
        if (newPose != null && !newPose.equals(m_latestPose)) {
            m_latestPose = newPose;
            m_hasValidPose = true;
            m_lastUpdateTime = edu.wpi.first.wpilibj.Timer.getFPGATimestamp();
            
            // Log update rate
            SmartDashboard.putNumber("QuestNav/UpdateRateHz", 
                1.0 / (edu.wpi.first.wpilibj.Timer.getFPGATimestamp() - m_lastUpdateTime + 0.001));
        }
        
        // Log pose validity
        SmartDashboard.putBoolean("QuestNav/HasValidPose", m_hasValidPose);
        SmartDashboard.putNumber("QuestNav/Timestamp", m_lastUpdateTime);
    }
    
    /**
     * Get the latest pose estimate from QuestNav.
     * @return Current pose (may be default if no data received)
     */
    public Pose2d getEstimatedPose() {
        return m_latestPose;
    }
    
    /**
     * Check if we have received valid pose data.
     * @return true if pose data has been received
     */
    public boolean hasValidPose() {
        return m_hasValidPose;
    }
    
    /**
     * Get the standard deviations for fusion with pose estimator.
     * Lower values = more trust in QuestNav measurement.
     * @return 3x1 matrix [x, y, theta] std devs
     */
    public Matrix<N3, N1> getStandardDeviations() {
        return m_questNavStdDevs;
    }
    
    /**
     * Set custom standard deviations for tuning.
     * @param x X-axis std dev (meters)
     * @param y Y-axis std dev (meters)
     * @param theta Rotation std dev (radians)
     */
    public void setStandardDeviations(double x, double y, double theta) {
        m_questNavStdDevs = VecBuilder.fill(x, y, theta);
        SmartDashboard.putNumber("QuestNav/StdDevX", x);
        SmartDashboard.putNumber("QuestNav/StdDevY", y);
        SmartDashboard.putNumber("QuestNav/StdDevTheta", theta);
    }
    
    /**
     * Reset the pose (called during initialization or re-calibration).
     * @param pose New pose to set
     */
    public void resetPose(Pose2d pose) {
        m_latestPose = pose;
        m_hasValidPose = true;
    }
    
    /**
     * Get time since last pose update.
     * @return Seconds since last update
     */
    public double getTimeSinceLastUpdate() {
        return edu.wpi.first.wpilibj.Timer.getFPGATimestamp() - m_lastUpdateTime;
    }
    
    /**
     * Check if tracking may have been lost (no update for >100ms).
     * @return true if stale data
     */
    public boolean isStale() {
        return getTimeSinceLastUpdate() > 0.1;
    }
}
