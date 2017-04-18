#
# Copyright (C) 2016 Wind River Systems, Inc.
#

require efitools.inc

inherit native

DEPENDS_append = " gnu-efi-native"

EXTRA_OEMAKE_append = " \
    INCDIR_PREFIX='${STAGING_DIR_NATIVE}' \
    CRTPATH_PREFIX='${STAGING_DIR_NATIVE}' \
    EXTRA_LDFLAGS='-Wl,-rpath,${libdir}' \
"
