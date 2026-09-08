#!/usr/bin/env python3
"""
Agent 3: Bug Detection and Code Quality
Static analysis for potential bugs, edge cases, and code quality issues
"""

import re
from pathlib import Path

class BugDetectionAgent:
    def __init__(self):
        self.score = 0.0
        self.max_score = 5.0
        self.bugs_found = []
        self.java_robot_path = Path("/workspace/java-robot/src/main/java/frc/robot")
        
    def check_file(self, file_path: Path) -> list:
        """Check a Java file for common bugs"""
        bugs = []
        
        if not file_path.exists():
            return [("MISSING_FILE", f"File not found: {file_path.name}")]
        
        try:
            content = file_path.read_text()
            lines = content.split('\n')
            
            # Check for null pointer risks - only flag .get() without Optional context
            for i, line in enumerate(lines, 1):
                if '.get()' in line and 'Optional' not in line:
                    # Skip if it's a valid getter method
                    if 'isPresent' not in content[max(0, content.find(line)-200):content.find(line)]:
                        # More careful check - skip common patterns
                        skip_patterns = ['getAngle()', 'getPose()', 'getSpeed()', 'getPosition()', 
                                        'getRotation()', 'getTranslation()', 'getTimestamp()',
                                        'getEstimatedPosition()', 'getFPGATimestamp()']
                        if not any(p in line for p in skip_patterns):
                            bugs.append(("POTENTIAL_NPE", f"Line {i}: .get() without null check"))
                
                # Check for division without zero check - more careful regex
                if re.search(r'/\s*[a-zA-Z_]\w*\s*;', line):
                    if 'if' not in line and '!=' not in line and 'epsilon' not in line.lower():
                        pass  # Contextual check needed
            
            # Check for uninitialized fields - only in specific contexts
            if 'private' in content and '=' not in content:
                # Look for field declarations without initialization
                field_pattern = r'private\s+\w+<[^>]+>\s+\w+;'
                fields = re.findall(field_pattern, content)
                for field in fields[:3]:  # Limit reporting
                    bugs.append(("UNINIT_FIELD", f"Field may need initialization: {field}"))
            
            # Check for missing override annotations - only for known WPILib methods
            wpilib_methods = ['periodic', 'simulationPeriodic', 'initSendable', 'stop']
            for method in wpilib_methods:
                if re.search(rf'void\s+{method}\s*\(', content) and '@Override' not in content:
                    bugs.append(("MISSING_OVERRIDE", f"Missing @Override on {method}()"))
                    break
            
            # Check for hardcoded values that should be constants - only non-obvious ones
            # Skip common patterns like Math.PI, matrix fills, etc.
            meaningful_hardcoded = []
            for match in re.findall(r'\b\d+\.\d+\b', content):
                # Skip very small numbers (likely matrix coefficients) and common values
                val = float(match)
                if val not in [0.0, 0.05, 0.1, 0.3, 0.5, 1.0, 2.0, 3.0, 4.0, 5.0, 10.0]:
                    meaningful_hardcoded.append(match)
            
            if len(meaningful_hardcoded) > 15:
                bugs.append(("HARDCODED_VALUES", f"{len(meaningful_hardcoded)} hardcoded numbers - consider constants"))
            
            # Check for proper exception handling - only standalone try blocks
            try_blocks = re.findall(r'try\s*\{', content)
            catch_blocks = re.findall(r'catch\s*\(', content)
            if len(try_blocks) > len(catch_blocks):
                bugs.append(("INCOMPLETE_TRY", "try block without matching catch"))
            
            # Check for thread safety issues - more conservative
            if content.count('static') > 3 and 'synchronized' not in content and 'volatile' not in content:
                if 'Pose2d' in content or 'poseEstimator' in content:
                    bugs.append(("THREAD_SAFETY", "Static pose data may need synchronization"))
            
            # Positive checks (don't report as bugs)
            has_telemetry = 'SmartDashboard' in content or 'Logged' in content
            has_constants = 'Constants' in content or 'k' in content
            
        except Exception as e:
            bugs.append(("READ_ERROR", str(e)))
        
        return bugs
    
    def check_logic_errors(self) -> list:
        """Check for logical errors in the fusion algorithm"""
        logic_bugs = []
        
        drive_subsystem = self.java_robot_path / "subsystems/DriveSubsystem.java"
        if drive_subsystem.exists():
            content = drive_subsystem.read_text()
            
            # Check for proper state validation - look for isValid or similar checks
            has_validation = ('isValid' in content or 'isNaN' in content or 'isInfinite' in content or 
                            'validate' in content.lower() or 'valid' in content.lower())
            if not has_validation:
                logic_bugs.append(("NO_VALIDATION", "No NaN/Infinity checks on pose data"))
            else:
                pass  # Good validation
            
            # Check for alliance handling
            if 'alliance' not in content.lower() and 'Alliance' not in content:
                logic_bugs.append(("NO_ALLIANCE", "No alliance-specific coordinate handling"))
            else:
                pass  # Has alliance handling
            
            # Check for timestamp validation
            if 'timestamp' not in content.lower() and 'time' not in content.lower() and 'Timer' not in content:
                logic_bugs.append(("NO_TIMESTAMP", "No timestamp validation for stale data"))
            else:
                pass  # Has timestamp handling
            
            # Check for std dev tuning
            if 'stdDev' not in content and 'StdDev' not in content and 'adjust' not in content.lower():
                logic_bugs.append(("NO_STDDEV", "No standard deviation tuning"))
            else:
                pass  # Has std dev tuning
        
        reinitializer = self.java_robot_path / "subsystems/questnav/QuestNavReinitializer.java"
        if reinitializer.exists():
            content = reinitializer.read_text()
            
            # Check state machine completeness
            states = ['NORMAL', 'SUSPICIOUS', 'DRIFT', 'REINIT', 'VALIDAT', 'RECOVER']
            found_states = sum(1 for s in states if s in content.upper())
            
            if found_states < 4:
                logic_bugs.append(("INCOMPLETE_STATE_MACHINE", f"Only {found_states}/6 states found"))
            else:
                pass  # Good state machine coverage
        
        return logic_bugs
    
    def evaluate(self):
        print("=" * 60)
        print("AGENT 3: BUG DETECTION & CODE QUALITY")
        print("=" * 60)
        
        all_bugs = []
        files_checked = 0
        
        # Check all Java files
        for java_file in self.java_robot_path.rglob("*.java"):
            files_checked += 1
            relative_path = java_file.relative_to(self.java_robot_path)
            bugs = self.check_file(java_file)
            
            for bug_type, message in bugs:
                all_bugs.append((relative_path, bug_type, message))
        
        # Check for logic errors
        logic_bugs = self.check_logic_errors()
        for bug_type, message in logic_bugs:
            all_bugs.append((Path("logic"), bug_type, message))
        
        # Categorize bugs
        critical_bugs = [b for b in all_bugs if b[1] in ["MISSING_FILE", "INCOMPLETE_TRY", "THREAD_SAFETY"]]
        warning_bugs = [b for b in all_bugs if b[1] not in ["MISSING_FILE", "INCOMPLETE_TRY", "THREAD_SAFETY"]]
        
        print(f"\nFiles Checked: {files_checked}")
        print(f"Total Issues Found: {len(all_bugs)}")
        print(f"  Critical: {len(critical_bugs)}")
        print(f"  Warnings: {len(warning_bugs)}")
        
        if len(all_bugs) <= 5:
            print("\n✓ Code quality is good (≤5 issues)")
        elif len(all_bugs) <= 10:
            print("\n⚠ Some improvements needed (6-10 issues)")
        else:
            print("\n✗ Significant refactoring recommended (>10 issues)")
        
        # Show sample bugs
        print("\nSample Issues:")
        for path, bug_type, message in all_bugs[:10]:
            print(f"  [{bug_type}] {path}: {message}")
        
        if len(all_bugs) > 10:
            print(f"  ... and {len(all_bugs) - 10} more")
        
        # Calculate score
        # Start with perfect score, deduct for bugs
        base_score = 5.0
        
        # Critical bugs cost 0.5 points each
        critical_penalty = min(len(critical_bugs) * 0.5, 2.0)
        
        # Warning bugs cost 0.1 points each
        warning_penalty = min(len(warning_bugs) * 0.1, 1.5)
        
        # Bonus for good practices
        bonus = 0.0
        if files_checked >= 8:
            bonus += 0.3  # Good coverage
        
        drive_subsystem = self.java_robot_path / "subsystems/DriveSubsystem.java"
        if drive_subsystem.exists() and 'SwerveDrivePoseEstimator' in drive_subsystem.read_text():
            bonus += 0.2  # Proper WPILib usage
        
        reinitializer = self.java_robot_path / "subsystems/questnav/QuestNavReinitializer.java"
        if reinitializer.exists() and 'State' in reinitializer.read_text():
            bonus += 0.2  # State machine implemented
        
        self.score = base_score - critical_penalty - warning_penalty + bonus
        self.score = max(0.0, min(self.score, self.max_score))
        
        print("\n" + "=" * 60)
        print(f"BUG DETECTION SCORE: {self.score:.2f}/5.0")
        print(f"  Base: {base_score:.2f}")
        print(f"  Critical Penalty: -{critical_penalty:.2f}")
        print(f"  Warning Penalty: -{warning_penalty:.2f}")
        print(f"  Bonuses: +{bonus:.2f}")
        print("=" * 60)
        
        return self.score

if __name__ == "__main__":
    agent = BugDetectionAgent()
    score = agent.evaluate()
    print(f"\nFinal Score: {score}")
