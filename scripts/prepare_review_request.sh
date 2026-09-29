#!/bin/bash
set -euo pipefail

BRANCH="$(git branch --show-current)"
LOCAL_HEAD="$(git rev-parse HEAD)"

# Fetch to ensure we have latest refs
git fetch origin

REMOTE_HEAD="$(git ls-remote origin "refs/heads/$BRANCH" | cut -f1)"

if [ "$LOCAL_HEAD" != "$REMOTE_HEAD" ]; then
    echo "Push is NOT synced!"
    exit 1
fi

sed -i "s/WILL_BE_REPLACED_BY_GIT_REV_PARSE/$LOCAL_HEAD/g" reviews/REVIEW_REQUEST.md
git add reviews/REVIEW_REQUEST.md
git commit -m "docs: prepare REVIEW_REQUEST for review 15"
git push origin "$BRANCH"

REVIEW_COMMIT="$(git rev-parse HEAD)"

echo "=== REVIEW HANDOFF ==="
echo "Repository: clown66644/hisho"
echo "Branch: $BRANCH"
echo "Code Head SHA: $LOCAL_HEAD"
echo "Review Request Commit: $REVIEW_COMMIT"
echo "Remote Sync: OK"
echo "Review Request: READY"