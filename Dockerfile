FROM eclipse-temurin:24-jre-ubi9-minimal
WORKDIR /home/container

ENV HTTP_PORT=8080
ENV RTMP_PORT=1935

# FFmpeg
RUN <<EOF
    set -e

    microdnf install xz -y

    mkdir /home/container/lib
    mkdir /home/container/lib/ffmpeg
    cd /home/container/lib/ffmpeg

    curl -L -o x86_64.tar.xz "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n8.0-latest-linux64-lgpl-shared-8.0.tar.xz"
    curl -L -o aarch64.tar.xz "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n8.0-latest-linuxarm64-lgpl-shared-8.0.tar.xz"
    
    # Move the FFmpeg binaries and libs to add it to the path
    echo "Using $(arch).tar.xz"
    tar --strip-components=1 -xJf $(arch).tar.xz
    mv /home/container/lib/ffmpeg/bin/* /usr/bin
    mv /home/container/lib/ffmpeg/lib/* /usr/lib64
    rm -rf /home/container/lib
    
    # We no longer need this :^)
    microdnf remove xz -y
    microdnf clean all
EOF

# Quark
COPY ./bootstrap/target/quark.jar /home/container

# Healthcheck
HEALTHCHECK --interval=5s --timeout=5s --retries=6 --start-period=5s \
    CMD curl -f "http://localhost:$HTTP_PORT/_healthcheck" || exit 1

# Entrypoint
CMD [ "java", "-jar", "quark.jar" ]
EXPOSE $HTTP_PORT/tcp
EXPOSE $RTMP_PORT/tcp