LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

phone_common_dir := ../PhoneCommon

src_dirs := src src-bind $(phone_common_dir)/src
res_dirs := res $(phone_common_dir)/res
asset_dirs := assets

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))
LOCAL_ASSET_DIR := $(addprefix $(LOCAL_PATH)/, $(asset_dirs))

LOCAL_STATIC_ANDROID_LIBRARIES := \
    com.google.android.material_material \
    androidx.transition_transition \
    androidx.legacy_legacy-support-v13 \
    androidx.appcompat_appcompat \
    androidx.cardview_cardview \
    androidx.recyclerview_recyclerview \
    androidx.palette_palette \
    androidx.legacy_legacy-support-v4

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-common \
    com.android.vcard \
    guava \
    libphonenumber

LOCAL_USE_AAPT2 := true

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.android.phone.common

LOCAL_PACKAGE_NAME := Contacts
LOCAL_CERTIFICATE := shared
LOCAL_PRODUCT_MODULE := true
LOCAL_PRIVILEGED_MODULE := true
LOCAL_REQUIRED_MODULES := privapp_whitelist_com.android.contacts

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 21

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
