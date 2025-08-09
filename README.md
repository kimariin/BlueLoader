# BlueLoader

This is a [BD-J] application for the PlayStation 4 which:

* Uses Gezine's [BD-JB-1250] exploit to break out of the Java sandbox
* Starts a remote console on port 9020
* Starts a remote JAR loader on port 9025

The application is accompanied by two payloads that can be uploaded to the PS4:

* fsdump: Dumps every file visible to the JVM process as a ZIP file over port 9030
* lapse: *(Work in progress)*

This is similar to [the current iteration][RemoteLoader] of Gezine's BD-JB-1250 demo, but may be
easier for developers to get started with since it's more self-contained.

To use the [release binaries]:

```sh
# Launch BlueLoader on PS4 and wait for it to show an IP address, then:
cat fsdump.jar | netcat 192.168.x.x 9025 -q0
# Retrieve the ZIP file:
netcat 192.168.x.x 9030 > ps4.zip
```

To build the ISO and payloads locally:

```sh
git clone https://github.com/kimariin/BlueLoader.git

# You'll need a Linux system, would recommend a Debian 12 VM if you don't have one
# Repo includes JVM binaries for Linux x64 and aarch64 so no need to install it
# If the bundled binaries don't work you'll need to change the Makefile
sudo apt install build-essential libbsd-dev git pkg-config curl

# Use fsdump from the release binaries and retrieve these files from the dump:
unzip -p ps4.zip app0/bdjstack/bdjstack.jar > thirdparty/topsecret/bdjstack.jar
unzip -p ps4.zip app0/bdjstack/lib/rt.jar   > thirdparty/topsecret/rt.jar

# Will generate build/blueloader.iso, build/fsdump.jar, etc.
make

# Send newly built fsdump to the console:
HOST=192.168.x.x make send-fsdump
```

[BD-J]: https://en.wikipedia.org/wiki/BD-J
[BD-JB-1250]: https://github.com/Gezine/BD-JB-1250
[RemoteLoader]: https://github.com/Gezine/BD-JB-1250/tree/c7d35559c5c4a3bc6423e51b4918827229db9b64
[release binaries]: https://github.com/kimariin/BlueLoader/releases