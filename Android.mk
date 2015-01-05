LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

contacts_common_dir := ../ContactsCommon
phone_common_dir := ../PhoneCommon

src_dirs := src $(contacts_common_dir)/src $(phone_common_dir)/src
res_dirs := res $(contacts_common_dir)/res $(phone_common_dir)/res

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs)) \
    frameworks/support/v7/cardview/res

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.android.contacts.common \
    --extra-packages com.android.phone.common \
    --extra-packages android.support.v7.cardview

LOCAL_JAVA_LIBRARIES := telephony-common voip-common
LOCAL_STATIC_JAVA_LIBRARIES := \
    com.android.vcard \
    android-common \
    guava \
    android-support-v13 \
    android-support-v7-cardview \
    android-support-v7-palette \
    android-support-v4 \
    libphonenumber \
    libgeocoding

LOCAL_PACKAGE_NAME := Contacts
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))

# Only for test
