#!/usr/bin/env python3
"""
Agent 1: Functionality Audit
Evaluates completeness of implementation against project requirements
"""

import os
import re
from pathlib import Path

class FunctionalityAgent:
    def __init__(self):
        self.score = 0.0
        self.max_score = 5.0
        self.findings = []
        self.java_robot_path = Path("/workspace/java-robot/src/main/java/frc/robot")
        
    def check_file_exists(self, relative_path):
        """Check if a required file exists"""
        full_path = self.java_robot_path / relative_path
        exists = full_path.exists()
        self.findings.append(f"{'✓' if exists else '✗'} File: {relative_path}")
        return exists
    
    def check_class_has_method(self, file_path, class_name, method_name):
        """Check if a class contains a specific method"""
        try:
            content = file_path.read_text()
            # Simple regex to find method declarations
            pattern = rf'(public|private|protected).*{method_name}\s*\('
            has_method = bool(re.search(pattern, content))
            return has_method
        except Exception as e:
            return False
    
    def check_implementation_quality(self):
        """Evaluate implementation quality"""
        quality_checks = []
        
        # Check DriveSubsystem for fusion logic
        drive_subsystem = self.java_robot_path / "subsystems/DriveSubsystem.java"
        if drive_subsystem.exists():
            content = drive_subsystem.read_text()
            
            # Check for pose estimator usage
            has_pose_estimator = "SwerveDrivePoseEstimator" in content or "DifferentialDrivePoseEstimator" in content
            quality_checks.append(("Pose Estimator", has_pose_estimator))
            
            # Check for QuestNav integration - look for addQuestNavMeasurement method
            has_questnav = "addQuestNavMeasurement" in content and "QuestNavPose" in content
            quality_checks.append(("QuestNav Integration", has_questnav))
            
            # Check for PhotonVision integration - look for addPhotonVisionMeasurement method
            has_photonvision = "addPhotonVisionMeasurement" in content and "PhotonVision" in content
            quality_checks.append(("PhotonVision Integration", has_photonvision))
            
            # Check for alliance handling
            has_alliance = "alliance" in content.lower() or "Alliance" in content or "isRedAlliance" in content
            quality_checks.append(("Alliance Handling", has_alliance))
            
            # Check for standard deviation tuning
            has_std_dev = "stdDev" in content or "StdDev" in content or "adjust" in content.lower()
            quality_checks.append(("Std Dev Tuning", has_std_dev))
        
        # Check QuestNavReinitializer for state machine
        reinitializer = self.java_robot_path / "subsystems/questnav/QuestNavReinitializer.java"
        if reinitializer.exists():
            content = reinitializer.read_text()
            
            # Check for state machine
            has_state_enum = "enum State" in content or "State." in content or "ReinitState" in content
            quality_checks.append(("State Machine Enum", has_state_enum))
            
            # Check for drift detection
            has_drift_detection = "drift" in content.lower() or "Drift" in content
            quality_checks.append(("Drift Detection Logic", has_drift_detection))
            
            # Check for reinitialization
            has_reinit = "reinit" in content.lower() or "reset" in content.lower() or "Reinitialize" in content
            quality_checks.append(("Re-initialization Logic", has_reinit))
        
        # Check VisionConstants for tunable parameters
        constants = self.java_robot_path / "Constants/VisionConstants.java"
        if constants.exists():
            content = constants.read_text()
            has_thresholds = "threshold" in content.lower() or "Threshold" in content or "kMin" in content
            quality_checks.append(("Configurable Thresholds", has_thresholds))
        
        return quality_checks
    
    def evaluate(self):
        print("=" * 60)
        print("AGENT 1: FUNCTIONALITY AUDIT")
        print("=" * 60)
        
        # Required files check (2 points)
        required_files = [
            "subsystems/DriveSubsystem.java",
            "subsystems/QuestNavSubsystem.java",
            "subsystems/questnav/QuestNavReinitializer.java",
            "vision/PhotonVisionSubsystem.java",
            "Constants/VisionConstants.java",
            "Robot.java",
            "util/QuestNavPose.java"
        ]
        
        files_found = 0
        for f in required_files:
            if self.check_file_exists(f):
                files_found += 1
        
        file_score = (files_found / len(required_files)) * 2.0
        print(f"\nFile Coverage: {files_found}/{len(required_files)} ({file_score:.2f}/2.0)")
        
        # Implementation quality check (2 points)
        quality_checks = self.check_implementation_quality()
        quality_passed = sum(1 for _, passed in quality_checks if passed)
        quality_total = len(quality_checks)
        
        for name, passed in quality_checks:
            print(f"{'✓' if passed else '✗'} {name}")
        
        quality_score = (quality_passed / quality_total) * 2.0 if quality_total > 0 else 0
        print(f"\nImplementation Quality: {quality_passed}/{quality_total} ({quality_score:.2f}/2.0)")
        
        # Command structure check (1 point)
        commands_path = self.java_robot_path / "commands"
        has_commands = commands_path.exists() and len(list(commands_path.glob("*.java"))) >= 2
        print(f"\n{'✓' if has_commands else '✗'} Command Structure (≥2 commands)")
        command_score = 1.0 if has_commands else 0.0
        
        # Total score
        self.score = file_score + quality_score + command_score
        self.score = min(self.score, self.max_score)
        
        print("\n" + "=" * 60)
        print(f"FUNCTIONALITY SCORE: {self.score:.2f}/5.0")
        print("=" * 60)
        
        return self.score

if __name__ == "__main__":
    agent = FunctionalityAgent()
    score = agent.evaluate()
    print(f"\nFinal Score: {score}")
