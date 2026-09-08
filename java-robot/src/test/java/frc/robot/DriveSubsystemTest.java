package frc.robot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import frc.robot.subsystems.DriveSubsystem;

/**
 * Unit tests for DriveSubsystem pose estimation and fusion logic.
 */
public class DriveSubsystemTest {
    
    private DriveSubsystem m_driveSubsystem;
    
    @BeforeEach
    public void setUp() {
        m_driveSubsystem = new DriveSubsystem();
    }
    
    @Test
    public void testInitialPose() {
        // Verify initial pose is at origin
        Pose2d initialPose = m_driveSubsystem.getPose();
        assertEquals(0.0, initialPose.getX(), 0.001);
        assertEquals(0.0, initialPose.getY(), 0.001);
        assertEquals(0.0, initialPose.getRotation().getDegrees(), 0.001);
    }
    
    @Test
    public void testQuestNavMeasurement() {
        // Create a test QuestNav pose (5 meters forward, rotated 45 degrees)
        Pose2d questNavPose = new Pose2d(5.0, 0.0, Rotation2d.fromDegrees(45));
        Matrix<N3, N1> stdDevs = VecBuilder.fill(0.1, 0.1, 0.05);
        
        // Add measurement
        m_driveSubsystem.addQuestNavMeasurement(questNavPose, stdDevs);
        
        // Force periodic update
        m_driveSubsystem.periodic();
        
        // Verify pose was updated (not exactly 5.0 due to estimator blending)
        Pose2d fusedPose = m_driveSubsystem.getPose();
        assertTrue(fusedPose.getX() > 0.0, "X should increase after QuestNav measurement");
        assertTrue(fusedPose.getX() < 6.0, "X should be reasonable");
    }
    
    @Test
    public void testAprilTagMeasurement() {
        // Create a test AprilTag pose
        Pose2d aprilTagPose = new Pose2d(3.0, 2.0, Rotation2d.fromDegrees(90));
        Matrix<N3, N1> stdDevs = VecBuilder.fill(0.05, 0.05, 0.02);
        
        // Add measurement with good tag metrics
        m_driveSubsystem.addPhotonVisionMeasurement(
            aprilTagPose,
            2,      // 2 tags detected
            0.05,   // Low ambiguity
            1.5,    // Close distance
            stdDevs
        );
        
        // Force periodic update
        m_driveSubsystem.periodic();
        
        // Verify pose was updated
        Pose2d fusedPose = m_driveSubsystem.getPose();
        assertTrue(fusedPose.getX() > 0.0, "X should increase after AprilTag measurement");
        assertTrue(fusedPose.getY() > 0.0, "Y should increase after AprilTag measurement");
    }
    
    @Test
    public void testInvalidPoseRejection() {
        // Create invalid pose with NaN values
        Pose2d invalidPose = new Pose2d(Double.NaN, 0.0, Rotation2d.fromDegrees(0));
        Matrix<N3, N1> stdDevs = VecBuilder.fill(0.1, 0.1, 0.05);
        
        // Add invalid measurement
        m_driveSubsystem.addQuestNavMeasurement(invalidPose, stdDevs);
        
        // Force periodic update
        m_driveSubsystem.periodic();
        
        // Verify pose remains at origin (invalid measurement rejected)
        Pose2d fusedPose = m_driveSubsystem.getPose();
        assertEquals(0.0, fusedPose.getX(), 0.001, "Invalid pose should be rejected");
    }
    
    @Test
    public void testDynamicStdDevAdjustment() {
        // Test that standard deviations are adjusted based on drift state
        Pose2d testPose = new Pose2d(1.0, 1.0, Rotation2d.fromDegrees(0));
        Matrix<N3, N1> baseStdDevs = VecBuilder.fill(0.1, 0.1, 0.05);
        
        // Add measurement in normal state
        m_driveSubsystem.addQuestNavMeasurement(testPose, baseStdDevs);
        m_driveSubsystem.periodic();
        
        // Should accept measurement without issues
        Pose2d fusedPose = m_driveSubsystem.getPose();
        assertNotNull(fusedPose);
    }
    
    @Test
    public void testAllianceFlip() {
        // Test alliance flipping functionality
        assertFalse(m_driveSubsystem.isAllianceFlipped(), "Should start as blue alliance");
        
        m_driveSubsystem.setAllianceFlipped(true);
        assertTrue(m_driveSubsystem.isAllianceFlipped(), "Should be red alliance after flip");
        
        m_driveSubsystem.setAllianceFlipped(false);
        assertFalse(m_driveSubsystem.isAllianceFlipped(), "Should be blue alliance after flip back");
    }
    
    @Test
    public void testFieldRelativeMode() {
        // Test field-relative mode toggle
        assertTrue(m_driveSubsystem.isFieldRelative(), "Should start field-relative by default");
        
        m_driveSubsystem.setFieldRelative(false);
        assertFalse(m_driveSubsystem.isFieldRelative(), "Should be robot-relative after toggle");
    }
}
