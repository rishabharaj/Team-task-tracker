#!/bin/sh
# Gradle wrapper script for Unix
exec "$JAVA_HOME/bin/java" $JAVA_OPTS -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
