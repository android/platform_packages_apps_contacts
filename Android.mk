LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := user

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := googlelogin-client

LOCAL_PACKAGE_NAME := Contacts
LOCAL_CERTIFICATE := shared

LOCAL_STATIC_JAVA_LIBRARIES := googlelogin-client

include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
