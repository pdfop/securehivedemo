ALPINE_MAJOR=3.9
ALPINE_VERSION=3.9.0
ALPINE_ARCH=x86_64

ALPINE_TAR=alpine-minirootfs.tar.gz

ROOT_FS=client.img
MOUNTPOINT=/media/ext4disk
IMAGE_SIZE_MB=400
DATE=`date +"%y%m%d %T"`
ESCALATE_CMD=sudo

.DELETE_ON_ERROR:
.PHONY: all clean

all: $(ROOT_FS)

clean:
	
	test -f $(ROOT_FS) && rm $(ROOT_FS) || true

$(ALPINE_TAR):
	curl -L -o "$@" "https://nl.alpinelinux.org/alpine/v$(ALPINE_MAJOR)/releases/$(ALPINE_ARCH)/alpine-minirootfs-$(ALPINE_VERSION)-$(ALPINE_ARCH).tar.gz"

processor.jar : processor.jar

$(ROOT_FS): $(ALPINE_TAR) buildenv.sh build/libs/processor.jar
	dd if=/dev/zero of="$@" count=$(IMAGE_SIZE_MB) bs=1M
	mkfs.ext4 "$@"
	$(ESCALATE_CMD) /bin/bash -euxo pipefail -c '\
		mkdir -p $(MOUNTPOINT); \
		mount -t ext4 -o loop "$@" $(MOUNTPOINT); \
		tar -C $(MOUNTPOINT) -xvf $(ALPINE_TAR); \
		cp /etc/resolv.conf $(MOUNTPOINT)/etc/resolv.conf; \
		install buildenv.sh $(MOUNTPOINT)/usr/sbin; \
		mkdir -p $(MOUNTPOINT)/app; \
		mkdir -p $(MOUNTPOINT)/resources; \
		cp build/libs/processor.jar $(MOUNTPOINT)/app; \
		cp resources/SecureHiveClient.pkcs12 $(MOUNTPOINT)/resources; \
		date +"%y%m%d %T" -s "$(DATE)"; \
		mkdir -p $(MOUNTPOINT)/opt; \
		chroot $(MOUNTPOINT) /bin/sh /usr/sbin/buildenv.sh; \
		umount $(MOUNTPOINT); \
		chown $(USER) "$@"; \
	'


