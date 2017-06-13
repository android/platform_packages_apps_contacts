LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests
LOCAL_CERTIFICATE := shared

LOCAL_STATIC_JAVA_LIBRARIES := android-support-test

LOCAL_JAVA_LIBRARIES := android.test.mock.sdk legacy-android-test

src_dirs := src \
    ../../ContactsCommon/TestCommon/src

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))

LOCAL_PACKAGE_NAME := ContactsTests

LOCAL_INSTRUMENTATION_FOR := Contacts

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 21

include $(BUILD_PACKAGE)
