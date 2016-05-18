FROM ubuntu:14.04

# Install Deps
RUN dpkg --add-architecture i386 && apt-get update && apt-get install -y --force-yes expect git wget libc6-i386 lib32stdc++6 lib32gcc1 lib32ncurses5 lib32z1 lrzsz

RUN apt-get install -y --force-yes python-software-properties software-properties-common

# Install Android SDK
RUN cd /opt && wget https://dl.google.com/android/android-sdk_r24.4.1-linux.tgz && tar -xzvf android-sdk_r24.4.1-linux.tgz && rm -f android-sdk_r24.4.1-linux.tgz && chown -R root.root android-sdk-linux

# Setup environment
ENV ANDROID_HOME /opt/android-sdk-linux
RUN mkdir -p /opt/android-sdk-linux/platform-tools
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools

# install maven gradle
RUN add-apt-repository ppa:cwchien/gradle
RUN add-apt-repository ppa:andrei-pozolotin/maven3
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y  gradle maven3 maven maven2  oracle-java8-installer

RUN gradle -v
RUN mvn --version

# Install platform tools
RUN echo y | android update sdk --all --no-ui --filter "platform-tools,build-tools-23.0.3,build-tools-23.0.2,android-23,extra-android-support,extra-android-m2repository"

RUN which adb
RUN which android


# Cleaning
RUN rm -rf /var/lib/apt/lists/* && rm -rf /var/cache/oracle-jdk8-installer


VOLUME ["/opt/ws"]

# GO to workspace
RUN mkdir -p /opt/ws
WORKDIR /opt/ws