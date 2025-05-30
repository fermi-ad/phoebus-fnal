#!/bin/bash
#
# Script to launch a test version of Phoebus.   The production launcher is located at:
#     /usr/local/epics/Config/CSS/Phoebus/run-phoebus.sh
#
# Phoebus build and installation location
TOP="$PWD"

export JAVA_HOME=/usr/lib/jvm/jre-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"

echo $TOP

# figure out the path to the product jar
if [[ -z "${PHOEBUS_JAR}" ]]; then
  PHOEBUS_JAR=${TOP}/product-fnal/target/fnal-product-*.jar
fi
echo $PHOEBUS_JAR

# figure out the path to the configuration settings
if [[ -z "${PHOEBUS_CONFIG}" ]]; then
  PHOEBUS_CONFIG=${TOP}/config/settings.ini
fi

# To get one instance, use server mode
ID=$(id -u)
#OPT="-server 4$ID"
OPT=""

JDK_JAVA_OPTIONS=" -DCA_DISABLE_REPEATER=true"
JDK_JAVA_OPTIONS="$JDK_JAVA_OPTIONS -Dnashorn.args=--no-deprecation-warning -Darch=`uname -i`"
JDK_JAVA_OPTIONS="$JDK_JAVA_OPTIONS -Djdk.gtk.verbose=true -Djdk.gtk.version=2 -Dprism.forceGPU=false"
JDK_JAVA_OPTIONS="$JDK_JAVA_OPTIONS -Dlogback.configurationFile=${TOP}/config/logback.xml"
JDK_JAVA_OPTIONS="$JDK_JAVA_OPTIONS -Dorg.csstudio.javafx.rtplot.update_counter=false"
JDK_JAVA_OPTIONS="$JDK_JAVA_OPTIONS -Dfile.encoding=UTF-8"
export JDK_JAVA_OPTIONS

echo $JDK_JAVA_OPTIONS

java -jar $PHOEBUS_JAR -settings $PHOEBUS_CONFIG -logging $TOP/config/logging.properties $OPT "$@" &
