# Makefile cheat sheet:
# https://www.gnu.org/software/make/manual/html_node/Automatic-Variables.html

all: build/blueloader.iso build/fsdump.jar build/lapse.jar

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
LOADER_SOURCES += org/bdj/RemoteConsole.java

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

# Payload to dump out any accessible parts of the PS4 filesystem to the network

FSDUMP_DSTDIR   := build/fsdump
FSDUMP_CPATH    := $(CPATH):build/blueloader.jar
FSDUMP_MANIFEST := org/bdj/fsdump/manifest.txt
FSDUMP_SOURCES  += org/bdj/fsdump/FilesystemDump.java

build/fsdump.jar: $(JAVA8) build/blueloader.jar $(addprefix src/,$(FSDUMP_SOURCES)) $(addprefix src/,$(FSDUMP_MANIFEST))
	mkdir -p $(FSDUMP_DSTDIR)
	$(JDK8)/bin/javac -d $(FSDUMP_DSTDIR) -sourcepath src $(JFLAGS) -cp $(FSDUMP_CPATH) $(addprefix src/,$(FSDUMP_SOURCES))
	$(JDK8)/bin/jar cmvf $(addprefix src/,$(FSDUMP_MANIFEST)) $@ -C $(FSDUMP_DSTDIR) .

.PHONY: send-fsdump
send-fsdump: build/fsdump.jar # Usage: HOST=192.168.x.x make send-fsdump
	cat build/fsdump.jar | netcat $(HOST) 9025 -q0

# Experimental payload for Lapse

LAPSE_DSTDIR   := build/lapse
LAPSE_CPATH    := $(CPATH):build/blueloader.jar
LAPSE_MANIFEST := org/bdj/lapse/manifest.txt
LAPSE_SOURCES  += org/bdj/lapse/Payload.java
LAPSE_SOURCES  += org/bdj/lapse/Library.java
LAPSE_SOURCES  += org/bdj/lapse/LibC.java
LAPSE_SOURCES  += org/bdj/lapse/LibKernel.java
LAPSE_SOURCES  += org/bdj/lapse/LapseMainThread.java
LAPSE_SOURCES  += org/bdj/lapse/LapseRaceThread.java

build/lapse.jar: $(JAVA8) build/blueloader.jar $(addprefix src/,$(LAPSE_SOURCES)) $(addprefix src/,$(LAPSE_MANIFEST))
	mkdir -p $(LAPSE_DSTDIR)
	$(JDK8)/bin/javac -d $(LAPSE_DSTDIR) -sourcepath src $(JFLAGS) -cp $(LAPSE_CPATH) $(addprefix src/,$(LAPSE_SOURCES))
	$(JDK8)/bin/jar cmvf $(addprefix src/,$(LAPSE_MANIFEST)) $@ -C $(LAPSE_DSTDIR) .

.PHONY: send-lapse
send-lapse: build/lapse.jar # Usage: HOST=192.168.x.x make send-lapse
	cat build/lapse.jar | netcat $(HOST) 9025 -q0

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

# Command that listens to the remote console
# Usage: HOST=192.168.x.x make console

.PHONY: console
console:
	netcat $(HOST) 9020
