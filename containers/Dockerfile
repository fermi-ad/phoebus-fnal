# Dockerfile for building and running Phoebus FNAL Product
# This container builds Phoebus from source and packages it for GUI use on a user's computer

# ===== Build Stage =====
FROM ubuntu:24.04 AS builder

# Avoid interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install build dependencies
RUN apt-get update && apt-get install -y \
    openjdk-21-jdk \
    maven \
    git \
    && rm -rf /var/lib/apt/lists/*

# Set Java environment - JDK 21 recommended per README
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Create build directory
WORKDIR /build

# Copy the phoebus-fnal source code
COPY . /build/

# Clone the base Phoebus repository (v5.0.0 per README)
RUN git clone -b v5.0.0 https://github.com/ControlSystemStudio/phoebus.git lib/phoebus

# Build base Phoebus (not the FNAL modules which require GitHub Packages auth)
RUN cd lib/phoebus && mvn clean install -DskipTests=true --batch-mode

# ===== Runtime Stage =====
FROM ubuntu:24.04 AS runtime

ENV DEBIAN_FRONTEND=noninteractive

# Install runtime dependencies including OpenJFX for GUI
RUN apt-get update && apt-get install -y \
    openjdk-21-jre \
    openjfx \
    libgtk-3-0 \
    libgl1 \
    libx11-6 \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    dbus-x11 \
    && rm -rf /var/lib/apt/lists/*

# Set Java environment
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Create phoebus directory
WORKDIR /opt/phoebus

# Copy the built base phoebus product from builder stage
COPY --from=builder /build/lib/phoebus/phoebus-product/target/product-*.jar /opt/phoebus/
COPY --from=builder /build/lib/phoebus/phoebus-product/target/lib/ /opt/phoebus/lib/
COPY --from=builder /build/config/ /opt/phoebus/config/

# Create a script to launch phoebus
RUN echo '#!/bin/bash\n\
java -jar /opt/phoebus/product-*.jar "$@"' > /opt/phoebus/run-phoebus.sh && \
    chmod +x /opt/phoebus/run-phoebus.sh

# Set display for X11 forwarding
ENV DISPLAY=:0

# Default entrypoint runs phoebus
ENTRYPOINT ["/opt/phoebus/run-phoebus.sh"]
CMD []
