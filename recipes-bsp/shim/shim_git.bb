#
# Copyright (C) 2015-2017 Wind River Systems, Inc.
#

SUMMARY = "shim is a trivial EFI application."
DESCRIPTION = "shim is a trivial EFI application that, when run, attempts to open and \
execute another application. It will initially attempt to do this via the \
standard EFI LoadImage() and StartImage() calls. If these fail (because secure \
boot is enabled and the binary is not signed with an appropriate key, for \
instance) it will then validate the binary against a built-in certificate. If \
this succeeds and if the binary or signing key are not blacklisted then shim \
will relocate and execute the binary."
HOMEPAGE = "https://github.com/rhinstaller/shim.git"
SECTION = "bootloaders"

LICENSE = "shim"
LIC_FILES_CHKSUM = "file://COPYRIGHT;md5=b92e63892681ee4e8d27e7a7e87ef2bc"
PR = "r0"

COMPATIBLE_HOST = '(i.86|x86_64).*-linux'

inherit deploy user-key-store

SRC_URI = " \
	git://github.com/rhinstaller/shim.git \
	file://0001-shim-allow-to-verify-sha1-digest-for-Authenticode.patch \
	file://0002-Skip-the-error-message-when-creating-MokListRT-if-ve.patch \
	file://0003-Allow-to-override-the-path-to-openssl.patch \
	file://0004-Fix-for-the-cross-compilation.patch \
	file://0005-Fix-signing-failure-due-to-not-finding-certificate.patch \
	file://0006-Prevent-from-removing-intermediate-.efi.patch \
	file://0007-Use-sbsign-to-sign-MokManager-and-fallback.patch \
	file://0008-Fix-the-world-build-failure-due-to-the-missing-rule-.patch \
	file://0009-Don-t-enforce-to-use-gnu89-standard.patch \
	file://0010-Makefile-do-not-sign-the-efi-file.patch \
	file://0011-Update-verification_method-if-the-loaded-image-is-si.patch \
"
SRC_URI_append_x86-64 = " \
       ${@bb.utils.contains('DISTRO_FEATURES', 'msft', 'file://shim${EFI_ARCH}.efi.signed file://LICENSE' if uks_signing_model(d) == 'sample' else '', '', d)} \
"

SRCREV = "0fe4a80e9cb9f02ecbb1cebb73331011e3641ff4"
PV = "11+git${SRCPV}"

S = "${WORKDIR}/git"
DEPENDS += "\
    gnu-efi nss openssl util-linux-native openssl-native nss-native \
"

EFI_ARCH_x86 = "ia32"
EFI_ARCH_x86-64 = "x64"

EXTRA_OEMAKE = " \
	CROSS_COMPILE="${TARGET_PREFIX}" \
	LIB_GCC="`${CC} -print-libgcc-file-name`" \
	LIB_PATH="${STAGING_LIBDIR}" \
	EFI_PATH="${STAGING_LIBDIR}" \
	EFI_INCLUDE="${STAGING_INCDIR}/efi" \
	RELEASE="_${DISTRO}_${DISTRO_VERSION}" \
	DEFAULT_LOADER=\\\\\\SELoader${EFI_ARCH}.efi \
	OPENSSL=${STAGING_BINDIR_NATIVE}/openssl \
	HEXDUMP=${STAGING_BINDIR_NATIVE}/hexdump \
	PK12UTIL=${STAGING_BINDIR_NATIVE}/pk12util \
	CERTUTIL=${STAGING_BINDIR_NATIVE}/certutil \
	SBSIGN=${STAGING_BINDIR_NATIVE}/sbsign \
	AR=${AR} \
	${@'VENDOR_CERT_FILE=${WORKDIR}/vendor_cert.cer' if d.getVar('MOK_SB', True) == '1' else ''} \
	${@'VENDOR_DBX_FILE=${WORKDIR}/vendor_dbx.esl' if uks_signing_model(d) == 'user' else ''} \
"

PARALLEL_MAKE = ""

EFI_TARGET = "/boot/efi/EFI/BOOT"
FILES_${PN} += "${EFI_TARGET}"

MSFT = "${@bb.utils.contains('DISTRO_FEATURES', 'msft', '1', '0', d)}"

# Prepare the signing certificate and keys
python do_prepare_signing_keys() {
    # For UEFI_SB, shim is not built
    if d.getVar('MOK_SB', True) != '1':
        return

    path = create_mok_vendor_dbx(d)

    # Prepare shim_cert and vendor_cert.
    dir = mok_sb_keys_dir(d)

    import shutil

    shutil.copyfile(dir + 'shim_cert.pem', d.getVar('S', True) + '/shim.crt')
    pem2der(dir + 'vendor_cert.pem', d.getVar('WORKDIR', True) + '/vendor_cert.cer', d)

    # Replace the shim certificate with EV certificate for speeding up
    # the progress of MSFT signing.
    if "${MSFT}" == "1" and uks_signing_model(d) == "sample":
        shutil.copyfile('${EV_CERT}', '${S}/shim.crt')
}
addtask prepare_signing_keys after do_configure before do_compile

python do_sign() {
    # The pre-signed shim binary will override the one built from the
    # scratch.
    pre_signed = '${WORKDIR}/shim${EFI_ARCH}.efi.signed'
    dst = '${B}/shim${EFI_ARCH}.efi.signed'
    if "${MSFT}" == "1" and os.path.exists(pre_signed):
        import shutil
        shutil.copyfile(pre_signed, dst)
    else:
        if uks_signing_model(d) in ('sample', 'user'):
            uefi_sb_sign('${S}/shim${EFI_ARCH}.efi', dst, d)
        elif uks_signing_model(d) == 'edss':
            edss_sign_efi_image('${S}/shim${EFI_ARCH}.efi', dst, d)

    sb_sign('${S}/mm${EFI_ARCH}.efi', '${B}/mm${EFI_ARCH}.efi.signed', d)
    sb_sign('${S}/fb${EFI_ARCH}.efi', '${B}/fb${EFI_ARCH}.efi.signed', d)
}
addtask sign after do_compile before do_install

do_install() {
    install -d ${D}${EFI_TARGET}

    local shim_dst="${D}${EFI_TARGET}/boot${EFI_ARCH}.efi"
    local mm_dst="${D}${EFI_TARGET}/mm${EFI_ARCH}.efi"
    if [ x"${UEFI_SB}" = x"1" ]; then
        install -m 0600 ${B}/shim${EFI_ARCH}.efi.signed $shim_dst
        install -m 0600 ${B}/mm${EFI_ARCH}.efi.signed $mm_dst
    else
        install -m 0600 ${B}/shim${EFI_ARCH}.efi $shim_dst
        install -m 0600 ${B}/mm${EFI_ARCH}.efi $mm_dst
    fi
}

# Install the unsigned images for manual signing
do_deploy() {
    install -d ${DEPLOYDIR}/efi-unsigned

    install -m 0600 ${B}/shim${EFI_ARCH}.efi ${DEPLOYDIR}/efi-unsigned/boot${EFI_ARCH}.efi
    install -m 0600 ${B}/mm${EFI_ARCH}.efi ${DEPLOYDIR}/efi-unsigned/mm${EFI_ARCH}.efi

    install -m 0600 "${D}${EFI_TARGET}/boot${EFI_ARCH}.efi" "${DEPLOYDIR}"
    install -m 0600 "${D}${EFI_TARGET}/mm${EFI_ARCH}.efi" "${DEPLOYDIR}"
}
addtask deploy after do_install before do_build
