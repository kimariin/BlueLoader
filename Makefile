# Makefile cheat sheet:
# https://www.gnu.org/software/make/manual/html_node/Automatic-Variables.html

all: build/blueloader.iso build/payload.jar

# Known-good JDK packages for Linux
# Source: https://github.com/adoptium/temurin8-binaries/releases

ARCH := $(shell uname -m)
ifeq ($(ARCH),aarch64)
	JDK8_PACKAGE  := thirdparty/OpenJDK8U-jdk_aarch64_linux_hotspot_8u462b08.tar.gz
	JDK11_PACKAGE := thirdparty/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.28_6.tar.gz
else ifeq ($(ARCH),x86_64)
	JDK8_PACKAGE  := thirdparty/OpenJDK8U-jdk_x64_linux_hotspot_8u462b08.tar.gz
	JDK11_PACKAGE := thirdparty/OpenJDK11U-jdk_x64_linux_hotspot_11.0.28_6.tar.gz
else
	$(error Unknown ARCH "$(ARCH)")
endif

# JDK8 is required for almost every part of the process

JDK8  := build/jdk8
JAVA8 := $(JDK8)/bin/java
$(JAVA8):
	mkdir -p $(JDK8)
	tar -xf $(JDK8_PACKAGE) -C $(JDK8) --strip-components=1

# A Linux port of NetBSD makefs is used to create the final UDF-format ISO image

MAKEFS := build/makefs
MAKEFS_SOURCES := $(wildcard thirdparty/makefs/* thirdparty/makefs/udf/*)
$(MAKEFS): $(MAKEFS_SOURCES)
	$(MAKE) -C thirdparty/makefs
	mkdir -p $(dir $(MAKEFS))
	mv thirdparty/makefs/makefs $(MAKEFS)

# BD-J JAR files need to be signed with BDSigner

BDTOOLS  := thirdparty/bd-tools
SECCPATH := $(BDTOOLS)/security.jar:$(BDTOOLS)/bcprov-jdk15-137.jar:$(BDTOOLS)/jdktools.jar
KEYSTORE := thirdparty/bd-certificates/keystore.store
BDSIGNER := $(JAVA8) -cp $(SECCPATH) net.java.bd.tools.security.BDSigner -keystore $(KEYSTORE)

# MainXlet is the initial Xlet that the PS4 loads. It always lives on the Blu-ray disc.

CPATH  := thirdparty/bd-stubs/interactive.zip:thirdparty/topsecret/rt.jar:thirdparty/topsecret/bdjstack.jar
JFLAGS := -Xlint:all -Xlint:-options -source 1.4 -target 1.4

LOADER_DSTDIR  := build/blueloader
LOADER_BD_PERM := org/bdj/bluray.MainXlet.perm
LOADER_SOURCES += org/bdj/MainXlet.java
LOADER_SOURCES += org/bdj/DisableSecurity.java
LOADER_SOURCES += org/bdj/DisableSecurityAction.java
LOADER_SOURCES += org/bdj/DisableSecurityXlet.java
LOADER_SOURCES += org/bdj/UITextConsole.java
LOADER_SOURCES += org/bdj/RemoteLoader.java

build/blueloader.jar: $(JAVA8) $(addprefix src/,$(LOADER_SOURCES)) src/$(LOADER_BD_PERM)
	mkdir -p $(LOADER_DSTDIR)
	mkdir -p $(LOADER_DSTDIR)/$(dir $(LOADER_BD_PERM))
	cp src/$(LOADER_BD_PERM) $(LOADER_DSTDIR)/$(LOADER_BD_PERM)
	$(JDK8)/bin/javac -d $(LOADER_DSTDIR) -sourcepath src $(JFLAGS) -cp $(CPATH) $(addprefix src/,$(LOADER_SOURCES))
	$(JDK8)/bin/jar cf $@ -C $(LOADER_DSTDIR) .
	$(BDSIGNER) $@
	-rm META-INF/SIG-BD00.RSA
	-rm META-INF/SIG-BD00.SF
	-rmdir META-INF

# Payload JAR that can be uploaded via 'make upload [ip]:[port]'

PAYLOAD_DSTDIR  := build/payload
PAYLOAD_CPATH   := $(CPATH):build/blueloader.jar
PAYLOAD_SOURCES += org/bdj/payload/Payload.java
PAYLOAD_SOURCES += org/bdj/api/AbstractInt.java
PAYLOAD_SOURCES += org/bdj/api/API.java
PAYLOAD_SOURCES += org/bdj/api/Buffer.java
PAYLOAD_SOURCES += org/bdj/api/Int8.java
PAYLOAD_SOURCES += org/bdj/api/Int16.java
PAYLOAD_SOURCES += org/bdj/api/Int32.java
PAYLOAD_SOURCES += org/bdj/api/Int64.java
PAYLOAD_SOURCES += org/bdj/api/Text.java
PAYLOAD_SOURCES += org/bdj/api/UnsafeInterface.java
PAYLOAD_SOURCES += org/bdj/api/UnsafeSunImpl.java

build/payload.jar: $(JAVA8) $(addprefix src/,$(PAYLOAD_SOURCES))
	mkdir -p $(PAYLOAD_DSTDIR)
	$(JDK8)/bin/javac -d $(PAYLOAD_DSTDIR) -sourcepath src $(JFLAGS) -cp $(CPATH) $(addprefix src/,$(PAYLOAD_SOURCES))
	$(JDK8)/bin/jar cf $@ -C $(PAYLOAD_DSTDIR) .

# Assemble the Blu-ray disc

DISC      := build/disc
BD_JO     := $(DISC)/BDMV/BDJO/00000.bdjo
BD_JAR    := $(DISC)/BDMV/JAR/00000.jar
BD_FONT   := $(DISC)/BDMV/AUXDATA/00000.otf
BD_FNTIDX := $(DISC)/BDMV/AUXDATA/dvb.fontindex
BD_META   := $(DISC)/BDMV/META/DL/bdmt_eng.xml
BD_BANNER := $(DISC)/BDMV/META/DL/banner.jpg
BD_INDEX1 := $(DISC)/BDMV/index.bdmv
BD_INDEX2 := $(DISC)/BDMV/BACKUP/index.bdmv
BD_INDEX3 := $(DISC)/BDMV/BACKUP/INDEX.BDM
BD_MVOBJ1 := $(DISC)/BDMV/MovieObject.bdmv
BD_MVOBJ2 := $(DISC)/BDMV/BACKUP/MovieObject.bdmv
BD_MVOBJ3 := $(DISC)/BDMV/BACKUP/MOVIEOBJ.BDM
BD_CLPI   := $(DISC)/BDMV/CLIPINF/00000.clpi
BD_MPLS   := $(DISC)/BDMV/PLAYLIST/00000.mpls
BD_STREAM := $(DISC)/BDMV/STREAM/00000.m2ts
BD_ID1    := $(DISC)/CERTIFICATE/id.bdmv
BD_ID2    := $(DISC)/CERTIFICATE/BACKUP/id.bdmv
BD_ACRT1  := $(DISC)/CERTIFICATE/app.discroot.crt
BD_ACRT2  := $(DISC)/CERTIFICATE/BACKUP/app.discroot.crt
BD_BCRT1  := $(DISC)/CERTIFICATE/bu.discroot.crt
BD_BCRT2  := $(DISC)/CERTIFICATE/BACKUP/bu.discroot.crt
BD_ALL    := $(BD_JO) $(BD_JAR) $(BD_FONT) $(BD_FNTIDX) $(BD_META) $(BD_BANNER) \
             $(BD_INDEX1) $(BD_INDEX2) $(BD_INDEX3) $(BD_MVOBJ1) $(BD_MVOBJ2) $(BD_MVOBJ3) \
             $(BD_CLPI) $(BD_MPLS) $(BD_STREAM) \
             $(BD_ID1) $(BD_ID2) $(BD_ACRT1) $(BD_ACRT2) $(BD_BCRT1) $(BD_BCRT2)

# Create directories
$(DISC): $(sort $(dir $(BD_ALL)))
$(sort $(dir $(BD_ALL))):
	mkdir -p $(dir $(BD_ALL))

# bdjo.xml/00000.bdjo tells the Blu-ray player which Xlet subclass to load
$(BD_JO): bd-metadata/bdjo.xml $(DISC) $(JAVA8) thirdparty/bd-tools/bdjo.jar
	$(JAVA8) -jar thirdparty/bd-tools/bdjo.jar $< $@

# Signed JAR containing the BlueLoader Xlet
$(BD_JAR): build/blueloader.jar $(DISC)
	cp $< $@

# There needs to be at least one font file on the disc, if I understand correctly
$(BD_FONT): bd-metadata/DejaVuSansMono.otf $(DISC)
	cp $< $@
$(BD_FNTIDX): bd-metadata/dvb.fontindex $(DISC)
	cp $< $@

# Metadata about the disc, including user-visible name and banner
$(BD_META): bd-metadata/bdmt_eng.xml $(DISC)
	cp $< $@
$(BD_BANNER): bd-metadata/banner.jpg $(DISC)
	cp $< $@

# Boilerplate that we just have to copy from BDJ-SDK
# This is mostly bloat and ideally we'd generate a simpler set using the Disc Creation Tools, but
# figuring out which bits the PS4 actually cares about is just too painful for me at this point.
$(BD_INDEX1): thirdparty/bd-template/index.bdmv $(DISC)
	cp $< $@
$(BD_INDEX2): thirdparty/bd-template/index.bdmv $(DISC)
	cp $< $@
$(BD_INDEX3): thirdparty/bd-template/index.bdmv $(DISC)
	cp $< $@
$(BD_MVOBJ1): thirdparty/bd-template/MovieObject.bdmv $(DISC)
	cp $< $@
$(BD_MVOBJ2): thirdparty/bd-template/MovieObject.bdmv $(DISC)
	cp $< $@
$(BD_MVOBJ3): thirdparty/bd-template/MovieObject.bdmv $(DISC)
	cp $< $@
$(BD_CLPI): thirdparty/bd-template/CLIPINF/00000.clpi $(DISC)
	cp $< $@
$(BD_MPLS): thirdparty/bd-template/PLAYLIST/00000.mpls $(DISC)
	cp $< $@
$(BD_STREAM): thirdparty/bd-template/STREAM/00000.m2ts $(DISC)
	cp $< $@

# Certificates are also taken from BDJ-SDK
$(BD_ID1): thirdparty/bd-certificates/id.bdmv $(DISC)
	cp $< $@
$(BD_ID2): thirdparty/bd-certificates/id.bdmv $(DISC)
	cp $< $@
$(BD_ACRT1): thirdparty/bd-certificates/app.discroot.crt $(DISC)
	cp $< $@
$(BD_ACRT2): thirdparty/bd-certificates/app.discroot.crt $(DISC)
	cp $< $@
$(BD_BCRT1): thirdparty/bd-certificates/bu.discroot.crt $(DISC)
	cp $< $@
$(BD_BCRT2): thirdparty/bd-certificates/bu.discroot.crt $(DISC)
	cp $< $@

# Generate the final ISO containing BlueLoader

DISC_LABEL := BlueLoader

build/blueloader.iso: $(MAKEFS) $(BD_ALL)
	$(MAKEFS) -m 16m -t udf -o T=bdre,v=2.50,L=$(DISC_LABEL) $@ $(DISC)

# Command that uploads built payload to loader
# Usage: HOST=192.168.x.x PORT=9025 make upload

.PHONY: upload
upload: build/payload.jar
	cat build/payload.jar | netcat $(HOST) $(PORT) -q0