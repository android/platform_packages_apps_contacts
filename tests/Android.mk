LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests
LOCAL_CERTIFICATE := shared

LOCAL_SRC_FILES := $(call all-java-files-under, src)

res_dirs := res
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_PACKAGE_NAME := ContactsTests

LOCAL_INSTRUMENTATION_FOR := Contacts

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 21

LOCAL_STATIC_JAVA_LIBRARIES += \
    androidx.test.rules \
    hamcrest-library \
    mockito-target-minus-junit4 \
    ub-uiautomator

LOCAL_JAVA_LIBRARIES := \
    android.test.runner.stubs \
    android.test.base.stubs \
    android.test.mock.stubs \


LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.android.contacts.common.tests

include $(BUILD_PACKAGE)
