FROM greatfox/oraclejdk8:latest

# Install Deps
RUN dpkg --add-architecture i386 && apt-get update && apt-get install -y --force-yes expect git wget libc6-i386 lib32stdc++6 lib32gcc1 lib32ncurses5 lib32z1 lrzsz

# Install Android SDK
RUN cd /opt && wget https://dl.google.com/android/android-sdk_r24.4.1-linux.tgz && tar -xzvf android-sdk_r24.4.1-linux.tgz && rm -f android-sdk_r24.4.1-linux.tgz && chown -R root.root android-sdk-linux

# Setup environment
ENV ANDROID_HOME /opt/android-sdk-linux
RUN mkdir -p /opt/android-sdk-linux/platform-tools
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools


# Install platform tools
RUN echo y | android update sdk --all --no-ui --filter "platform-tools,build-tools-23.0.3,build-tools-23.0.2,android-23,extra-android-support,extra-android-m2repository"

RUN which adb
RUN which android

# Cleaning
RUN apt-get clean


VOLUME ["/opt/ws"]

# GO to workspace
RUN mkdir -p /opt/ws
WORKDIR /opt/ws