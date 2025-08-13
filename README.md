## This project is obsolete, use [Gezine/BD-JB-1250](https://github.com/Gezine/BD-JB-1250)

----

This is a [BD-J] application for the PlayStation 4 which:

* Uses Gezine's [BD-JB-1250] exploit to break out of the Java sandbox
* Starts a remote console on port 9020
* Starts a remote JAR loader on port 9025

The application is accompanied by two payloads that can be uploaded to the PS4:

* fsdump: Dumps every file visible to the JVM process as a ZIP file over port 9030
* lapse: Java equivalent of abc's [Lapse proof-of-concept][PoC] (**not a jailbreak yet!**)

This codebase is primarily intended for developers at the moment. It's a mostly self-contained BD-J
exmaple that should be easy to build on.

For testing, you can download the ISO and payloads from the [releases page][rel]. The ISO must be
burned to a Blu-ray disc (not a DVD) that the PS4 can read (not BDXL). To send the payloads, you'll
need some software that can send/receive data over raw TCP sockets, like [netcat] or [ncat].

To use the fsdump payload, after starting BlueLoader on the PS4:

```sh
# Launch BlueLoader on PS4 and wait for it to show an IP address, then:
cat fsdump.jar | netcat 192.168.x.x 9025 -q0
# Retrieve the ZIP file:
netcat 192.168.x.x 9030 > ps4.zip
```

To use the lapse payload (**which _will_ crash your PS4** in its current state!)

```sh
# Make sure you're connected to the console to see full logs:
netcat 192.168.x.x 9020
# Cross your fingers and hope it doesn't instantly crash or frezeze:
cat lapse.jar | netcat 192.168.x.x 9025 -q0
# The PS4 OS will likely crash when you do anything else, if it hasn't already.
# Recommendation: open quick menu (long-press PS) and reboot ASAP.
```

Last few log lines should look like this, if it's working correctly:

```
-> 0x881c70d80: 00 00 00 00 00 00 00 00 00 00 00 00 00 ...
-> 0x881c70da0: 08 0d 00 06 04 01 0f 0f 0f 03 00 00 00 ...
BreakASLR: kernel image address: 0xffffffff9cef422f
BreakASLR: kernel heap  address: 0xffffc14406d06600
DoubleFree: deleting 2 leftover requests
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

# Choose a newly built payload to send to the console:
HOST=192.168.x.x make send-fsdump
HOST=192.168.x.x make send-lapse
```

[rel]: https://github.com/kimariin/BlueLoader/releases
[BD-J]: https://en.wikipedia.org/wiki/BD-J
[BD-JB-1250]: https://github.com/Gezine/BD-JB-1250
[PoC]: https://www.psdevwiki.com/ps4/Vulnerabilities#FW_5.00-12.02_-_Double_free_due_to_aio_multi_delete()_improper_locking
[RemoteLoader]: https://github.com/Gezine/BD-JB-1250/tree/c7d35559c5c4a3bc6423e51b4918827229db9b64
[netcat]: https://en.wikipedia.org/wiki/Netcat
[ncat]: https://nmap.org/ncat/