#
# Copyright (C) 2016 Wind River Systems, Inc.
#

SUMMARY = "Tools to support reading and manipulating the UEFI signature database"
DESCRIPTION = "\
From the EFI Tools package in the Linux user-space, it's now possible \
to read and manipulate the UEFI signatures database via the new \
efi-readvar and efi-updatevar commands. Aside from needing efitools \
1.4, the EFIVARFS file-system is also needed, which was only introduced \
in the Linux 3.8 kernel. \
"

LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=e28f66b16cb46be47b20a4cdfe6e99a1"

SRC_URI = " \
    git://git.kernel.org/pub/scm/linux/kernel/git/jejb/efitools.git \
    file://Fix-for-the-cross-compilation.patch \
    file://Kill-all-the-build-warning-caused-by-implicit-declar.patch \
    file://Fix-the-wrong-dependency-for-blacklist.esl.patch \
    file://LockDown-run-system-warm-reset-after-the-key-provisi.patch \
    file://Allow-to-override-tools-for-target-build.patch \
    file://Fix-help2man-failure.patch \
    file://Don-t-build-PreLoader.efi.patch \
    file://Reuse-xxdi.pl.patch \
    file://Add-static-keyword-for-IsValidVariableHeader.patch \
"

SRCREV = "791e4639ee837f70c8d5e1190e01dc3441d4a401"
PV = "1.7.0+git${SRCPV}"

PARALLEL_MAKE = ""

inherit perlnative

DEPENDS_append := "\
    help2man-native openssl-native sbsigntool-native \
    libfile-slurp-perl-native \
"

S = "${WORKDIR}/git"

EXTRA_OEMAKE = " \
    HELP2MAN='${STAGING_BINDIR_NATIVE}/help2man' \
    OPENSSL='${STAGING_BINDIR_NATIVE}/openssl' \
    SBSIGN='${STAGING_BINDIR_NATIVE}/sbsign' \
    OPENSSL_LIB='${STAGING_LIBDIR}' \
    NM='${NM}' AR='${AR}' \
"
EXTRA_OEMAKE_append_x86 = " ARCH=ia32"
EXTRA_OEMAKE_append_x86-64 = " ARCH=x86_64"

# LDFLAGS is used by LD not CC, so remove '-Wl,'
LDFLAGS := "${@oe_filter_out('-Wl,', '${LDFLAGS}', d)}"
BUILD_LDFLAGS := "${@oe_filter_out('-Wl,', '${BUILD_LDFLAGS}', d)}"

do_compile_prepend() {
    sed -i -e "1s:#!.*:#!/usr/bin/env nativeperl:" xxdi.pl 
}

EFI_BOOT_PATH = "/boot/efi/EFI/BOOT"
FILES_${PN} += "${EFI_BOOT_PATH}"

do_install() {
    oe_runmake install DESTDIR='${D}${base_prefix}'
}
