#!/usr/bin/env python3
"""
Master script to run all three evaluation agents and compute final score.
Continues running until all agents score >= 4.75 or max iterations reached.
"""

import subprocess
import re

def run_agent(agent_num, agent_file):
    """Run an agent and extract its final score"""
    result = subprocess.run(
        ['python3', agent_file],
        capture_output=True,
        text=True,
        cwd='/workspace'
    )
    
    # Extract score from output
    match = re.search(r'Final Score:\s*([\d.]+)', result.stdout)
    if match:
        return float(match.group(1))
    return 0.0

def main():
    agents = [
        (1, '/workspace/evaluation/agent1_functionality.py'),
        (2, '/workspace/evaluation/agent2_accuracy.py'),
        (3, '/workspace/evaluation/agent3_bugs.py'),
    ]
    
    target_score = 4.75
    max_iterations = 10
    
    print("=" * 70)
    print("META FRC CODE EVALUATION - THREE AGENT AUDIT")
    print("=" * 70)
    print(f"Target Score: {target_score}")
    print(f"Max Iterations: {max_iterations}")
    print("=" * 70)
    
    for iteration in range(1, max_iterations + 1):
        print(f"\n{'='*70}")
        print(f"Iteration {iteration}/{max_iterations}")
        print(f"{'='*70}\n")
        
        scores = []
        for agent_num, agent_file in agents:
            print(f"Running Agent {agent_num}...")
            score = run_agent(agent_num, agent_file)
            scores.append(score)
            status = "✓ PASS" if score >= target_score else "✗ FAIL"
            print(f"  Agent {agent_num} Score: {score:.2f}/5.0 [{status}]")
        
        avg_score = sum(scores) / len(scores)
        min_score = min(scores)
        
        print(f"\n{'-'*70}")
        print(f"Iteration Results:")
        print(f"  Agent Scores: {[f'{s:.2f}' for s in scores]}")
        print(f"  Average Score: {avg_score:.2f}")
        print(f"  Minimum Score: {min_score:.2f}")
        print(f"{'-'*70}")
        
        # Check if all agents passed
        if min_score >= target_score:
            print(f"\n{'='*70}")
            print("SUCCESS! All agents scored >= 4.75")
            print(f"Final Average Score: {avg_score:.2f}/5.0")
            print(f"{'='*70}")
            return True
        
        print(f"\nMinimum score ({min_score:.2f}) < {target_score}. Continuing...\n")
    
    print(f"\n{'='*70}")
    print(f"Reached maximum iterations ({max_iterations})")
    print(f"Best Average Score: {avg_score:.2f}/5.0")
    print(f"{'='*70}")
    return False

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)
