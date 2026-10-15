# Add to your device makefile:  $(call inherit-product, device/ioslike/ioslike.mk)
PRODUCT_PACKAGES += IOSLikeUI IOSLikeOverlay

PRODUCT_COPY_FILES += \
    device/ioslike/default-permissions/default-permissions-org.ioslike.ui.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/default-permissions/default-permissions-org.ioslike.ui.xml \
    device/ioslike/permissions/privapp-permissions-org.ioslike.ui.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-org.ioslike.ui.xml
