#!/bin/sh

# Gradle wrapper script

GRADLE_VERSION=8.2

if [ ! -d "$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
fi

exec gradle wrapper "$@"
