#!/bin/bash

# Time Machine Local Snapshot Cleanup Script
# Automatically deletes oldest local snapshots when disk space is low

# Get free space percentage (Capacity shows % used)
USED_PERCENT=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')
FREE_PERCENT=$((100 - USED_PERCENT))
FREE_GB=$(df / | awk 'NR==2 {printf "%.1f", $4/1024/1024/1024}')

echo "Current disk usage: ${USED_PERCENT}% used (${FREE_PERCENT}% free)"
echo "Free space: ${FREE_GB}GB"

# Check if we're above 20% free threshold (no cleanup needed)
if [ "$FREE_PERCENT" -gt 20 ]; then
    echo "Disk space is above 20% - no cleanup needed."
    exit 0
fi

echo "Disk space below 20% threshold - checking for local snapshots..."

# List local snapshots
SNAPSHOTS=$(tmutil listlocalsnapshots / 2>/dev/null | grep -E "com\.apple\.TimeMachine\." | awk '{print $NF}')

if [ -z "$SNAPSHOTS" ]; then
    echo "No local snapshots found."
    exit 0
fi

# Convert to array and count
SNAPSHOT_COUNT=$(echo "$SNAPSHOTS" | wc -l | tr -d ' ')
echo "Found $SNAPSHOT_COUNT local snapshot(s)"

# If disk is below 10% or 5GB free, delete all but most recent
if [ "$FREE_PERCENT" -le 10 ] || [ "$(echo "$FREE_GB < 5" | bc 2>/dev/null)" = "1" ]; then
    echo "Disk space critical (below 10% or less than 5GB free)"
    echo "Keeping only most recent snapshot, deleting others..."
    
    # Sort snapshots (newest first) and skip the first one
    SNAPSHOTS_TO_DELETE=$(echo "$SNAPSHOTS" | sort -r | tail -n +2)
else
    echo "Disk space low (below 20%) - deleting oldest snapshot(s)..."
    # Delete oldest snapshot first
    SNAPSHOTS_TO_DELETE=$(echo "$SNAPSHOTS" | sort | head -1)
fi

# Delete snapshots
DELETED=0
for snapshot in $SNAPSHOTS_TO_DELETE; do
    echo "Deleting snapshot: $snapshot"
    if tmutil deletelocalsnapshots "$snapshot" 2>/dev/null; then
        ((DELETED++))
        echo "✓ Deleted $snapshot"
    else
        echo "✗ Failed to delete $snapshot"
    fi
done

echo ""
echo "Cleanup complete: $DELETED snapshot(s) deleted"
df -h / | awk 'NR==2 {print "New free space: " $4 " (" $5 " used)"}'

