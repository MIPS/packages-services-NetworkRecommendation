#############################################
# Add app-specific Robolectric test target. #
#############################################
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

# Include the testing libraries (JUnit4 + Robolectric libs).
LOCAL_STATIC_JAVA_LIBRARIES := \
    platform-system-robolectric \
    truth-prebuilt

LOCAL_JAVA_LIBRARIES := \
    junit \
    platform-robolectric-prebuilt \
    sdk_vcurrent

# TODO: Remove the use of LOCAL_INSTRUMENTATION_FOR and use a different build flag.
LOCAL_INSTRUMENTATION_FOR := NetworkRecommendation
LOCAL_MODULE := NetworkRecommendationRoboTests

LOCAL_MODULE_TAGS := optional

include $(BUILD_STATIC_JAVA_LIBRARY)

#############################################################
# Add Robolectric runner target to run the previous target. #
#############################################################
include $(CLEAR_VARS)

LOCAL_MODULE := RunNetworkRecommendationRoboTests

LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES := \
    NetworkRecommendationRoboTests

LOCAL_TEST_PACKAGE := NetworkRecommendation

LOCAL_ROBOTEST_FAILURE_FATAL := true

include prebuilts/misc/common/robolectric/run_robotests.mk
