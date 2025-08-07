# BlueLoader

This is a [BD-J] application for the PlayStation 4 which:

* Uses Gezine's [BD-JB-1250] exploit to break out of the Java sandbox
* Starts a remote console on port 9020
* Starts a remote JAR loader on port 9025

The application is accompanied by a payload JAR that:

* Can be uploaded via netcat to the PS4
* Demonstrates usage of libkernel functions and syscalls
* Dumps the Blu-ray player's application directory as a ZIP file over port 9030

This is essentially equivalent to [the current iteration][RemoteLoader] of Gezine's BD-JB-1250 demo,
but may be slightly easier to get started with since it doesn't have external dependencies.

To use the release binaries:

```sh
# Launch BlueLoader on PS4 and wait for it to show an IP address, then:
cat payload.jar | netcat 192.168.x.x 9025 -q0
# Retrieve the ZIP file:
netcat 192.168.x.x 9030 > app0.zip
```

To build your own payload, you'll need bdjstack.jar and rt.jar from this ZIP file:

```sh
unzip app0.zip
cp app0/bdjstack/bdjstack.jar thirdparty/topsecret/
cp app0/bdjstack/lib/rt.jar   thirdparty/topsecret/
rm -r app0 app0.zip
```

Then, on a Linux system (Debian 12 is confirmed to work):

```sh
sudo apt install build-essential libbsd-dev git pkg-config curl
make
```

This should generate `build/blueloader.iso` and `build/payload.jar`.

[BD-J]: https://en.wikipedia.org/wiki/BD-J
[BD-JB-1250]: https://github.com/Gezine/BD-JB-1250
[RemoteLoader]: https://github.com/Gezine/BD-JB-1250/tree/c7d35559c5c4a3bc6423e51b4918827229db9b64