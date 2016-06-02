# Android-BLE-Terminal
Android Bluetooth Low Energy Terminal build with Android studio

[![Android-BLE-Terminal](https://img.shields.io/badge/build-passing-blue.svg)]()
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://raw.githubusercontent.com/Lembed/Android-BLE-Terminal/master/LICENSE)
[![Android-BLE-Terminal](https://img.shields.io/badge/version-1.0-yellow.svg)]()


Android 4.3 introduces built-in platform support for Bluetooth Low Energy ,
allows Android apps to communicate with BLE devices that have low power requirements, 
such as proximity sensors, heart rate monitors, fitness devices, and so on.

## Require
Android (API Level 18 or above) & Android studio

## Feature

1. async connect and data operate.
2. data encode/decode with utf-8 format.
3. background server with low payload to your phone.
4. easy extension display text widget.
5. easy wrapped communication interface.

## Build in docker(dir docker)
a docker image used to build the project
to build the docker image

```bash
docker build -t="android-gradle" .
```

enter the docker and build the project

```bash
docker run -v $(pwd):/opt/ws -it android-gradle bash
git clone https://github.com/Lembed/Android-BLE-Terminal.git
cd Android-BLE-Terminal
./gradlew build

```


## License
[MIT](https://github.com/Lembed/Android-BLE-Terminal/blob/master/LICENSE)
[![Analytics](https://ga-beacon.appspot.com/UA-67438080-1/Android-BLE-Terminal/readme?pixel)](https://github.com/Lembed/Android-BLE-Terminal)
