#!/bin/bash
cd /home/kavia/workspace/code-generation/hearing-range-measurement-app-d3daf515/android_frontend
./gradlew lint
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

