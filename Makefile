# Makefile cheat sheet:
# https://www.gnu.org/software/make/manual/html_node/Automatic-Variables.html

all: build/bluelapse.iso build/stage2.jar

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

# Stage1 is the initial Xlet that the PS4 loads. It always lives on the Blu-ray disc. It tries to
# It use the BD-JB-1250 exploit to escape the JVM sandbox, then chainloads stage 2.

CPATH  := thirdparty/bd-stubs/interactive.zip:thirdparty/topsecret/rt.jar:thirdparty/topsecret/bdjstack.jar
JFLAGS := -Xlint:all -Xlint:-options -source 1.4 -target 1.4

S1_DSTDIR  := build/stage1
S1_BDPERM  := org/bdj/bluray.Stage1.perm
S1_SOURCES := org/bdj/Stage1.java
S1_SOURCES += org/bdj/Stage2.java
S1_SOURCES += org/bdj/DisableSecurity.java
S1_SOURCES += org/bdj/UITextBox.java

build/stage1.jar: $(JAVA8) $(addprefix src/,$(S1_SOURCES)) src/$(S1_BDPERM)
	mkdir -p $(S1_DSTDIR)
	mkdir -p $(S1_DSTDIR)/$(dir $(S1_BDPERM))
	cp src/$(S1_BDPERM) $(S1_DSTDIR)/$(S1_BDPERM)
	$(JDK8)/bin/javac -d $(S1_DSTDIR) -sourcepath src $(JFLAGS) -cp $(CPATH) $(addprefix src/,$(S1_SOURCES))
	$(JDK8)/bin/jar cf $@ -C $(S1_DSTDIR) .
	$(BDSIGNER) $@
	-rm META-INF/SIG-BD00.RSA
	-rm META-INF/SIG-BD00.SF
	-rmdir META-INF

# Stage2 will be an Xlet that can be loaded from the disc, from a USB drive or over the network.
# FIXME: Just not yet.

S2_DSTDIR  := build/stage2
S2_SOURCES := org/bdj/Stage2.java

build/stage2.jar: $(JAVA8) $(addprefix src/,$(S2_SOURCES))
	mkdir -p $(S2_DSTDIR)
	$(JDK8)/bin/javac -d $(S2_DSTDIR) -sourcepath src $(JFLAGS) -cp $(CPATH) $(addprefix src/,$(S2_SOURCES))
	$(JDK8)/bin/jar cf $@ -C $(S2_DSTDIR) .
	$(BDSIGNER) $@
	-rm META-INF/SIG-BD00.RSA
	-rm META-INF/SIG-BD00.SF
	-rmdir META-INF

# Assemble the Blu-ray disc

DISC      := build/disc
BD_JO     := $(DISC)/BDMV/BDJO/00000.bdjo
BD_STAGE1 := $(DISC)/BDMV/JAR/00000.jar
BD_STAGE2 := $(DISC)/BDMV/JAR/00001.jar
BD_FONT   := $(DISC)/BDMV/AUXDATA/00000.otf
BD_META   := $(DISC)/BDMV/META/DL/bdmt_eng.xml
BD_BANNER := $(DISC)/BDMV/META/DL/banner.jpg
BD_INDEX  := $(DISC)/BDMV/index.bdmv
BD_MVOBJ  := $(DISC)/BDMV/MovieObject.bdmv
BD_ID     := $(DISC)/CERTIFICATE/id.bdmv
BD_APPCRT := $(DISC)/CERTIFICATE/app.discroot.crt
BD_BUCRT  := $(DISC)/CERTIFICATE/bu.discroot.crt
BD_ALL    := $(BD_JO) $(BD_STAGE1) $(BD_STAGE2) $(BD_FONT) $(BD_META) $(BD_BANNER) $(BD_INDEX) \
             $(BD_MVOBJ) $(BD_ID) $(BD_APPCRT) $(BD_BUCRT)

# Create directories
$(DISC): $(sort $(dir $(BD_ALL)))
$(sort $(dir $(BD_ALL))):
	mkdir -p $(dir $(BD_ALL))

# bdjo.xml/00000.bdjo tells the Blu-ray player which Xlet subclass to load
$(BD_JO): bd-metadata/bdjo.xml $(DISC) $(JAVA8) thirdparty/bd-tools/bdjo.jar
	$(JAVA8) -jar thirdparty/bd-tools/bdjo.jar $< $@

# Signed Stage1 JAR file containing that Xlet
$(BD_STAGE1): build/stage1.jar $(DISC)
	cp $< $@

# Signed Stage2 JAR file
$(BD_STAGE2): build/stage2.jar $(DISC)
	cp $< $@

# There needs to be at least one font file on the disc, if I understand correctly
$(BD_FONT): bd-metadata/OpenSans-Regular.otf $(DISC)
	cp $< $@

# Metadata about the disc, including user-visible name and banner
$(BD_META): bd-metadata/bdmt_eng.xml $(DISC)
	cp $< $@
$(BD_BANNER): bd-metadata/banner.jpg $(DISC)
	cp $< $@

# Boilerplate, not relevant for BD-J apps
$(BD_INDEX): bd-metadata/index.xml $(DISC) $(JAVA8) thirdparty/bd-tools/index.jar
	$(JAVA8) -jar thirdparty/bd-tools/index.jar $< $@

# Boilerplate, not relevant for BD-J apps
$(BD_MVOBJ): bd-metadata/movieobject.xml $(DISC) $(JAVA8) thirdparty/bd-tools/movieobject.jar
	$(JAVA8) -jar thirdparty/bd-tools/movieobject.jar $< $@

# Crypto nonsense?
$(BD_ID): bd-metadata/id.xml $(DISC) $(JAVA8) thirdparty/bd-tools/id.jar
	$(JAVA8) -jar thirdparty/bd-tools/id.jar $< $@

# Certificates are taken from elsewhere so we can just copy them
$(BD_APPCRT): thirdparty/bd-certificates/app.discroot.crt $(DISC)
	cp $< $@
$(BD_BUCRT): thirdparty/bd-certificates/bu.discroot.crt $(DISC)
	cp $< $@

# Generate the final ISO containing stage1
# FIXME: Should also (optionally?) include stage2 when we have one

DISC_LABEL := BlueLapse

build/bluelapse.iso: $(MAKEFS) $(BD_ALL)
	$(MAKEFS) -m 16m -t udf -o T=bdre,v=2.50,L=$(DISC_LABEL) $@ $(DISC)
