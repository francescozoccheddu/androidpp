cmake_minimum_required(VERSION 3.22)

# ---------- SETTINGS ----------
set(ANDROID_SDK_DIR "$ENV{ANDROID_SDK_DIR}" CACHE PATH "The Android SDK root directory")
set(JDK_DIR "$ENV{JDK_DIR}" CACHE PATH "The JDK root directory")
set(ANDROID_NDK_DIR "$ENV{ANDROID_NDK_DIR}" CACHE PATH "The Android NDK root directory")
set(ANDROID_SDK_BUILD_TOOLS_VERSION "" CACHE STRING "The Android SDK build tools version")
set(ANDROID_SDK_API_LEVEL "" CACHE STRING "The Android SDK API level")
set(ANDROID_ABI "arm64-v8a" CACHE STRING "The Android ABI")
set(KEYSTORE_FILE "" CACHE FILEPATH "The keystore file to use when signing the APK")
set(KEYSTORE_STORE_PASS "" CACHE STRING "The keystore store password to use when signing the APK")
set(KEYSTORE_KEY_PASS "" CACHE STRING "The keystore key password to use when signing the APK")
set(KEYSTORE_KEY_ALIAS "" CACHE STRING "The keystore key alias to use when signing the APK")
set(MAIN_ACTIVITY_NAME "" CACHE STRING "The full Android activity name to launch")

# --------- VALIDATION ---------
macro(LATEST_DIR RESULT DIR)
  file(GLOB CHILDREN RELATIVE "${DIR}" "${DIR}/*")
  list(SORT CHILDREN COMPARE NATURAL CASE INSENSITIVE ORDER DESCENDING)
  list(GET CHILDREN 0 "${RESULT}")
endmacro()

if(NOT ANDROID_SDK_DIR)
  message(FATAL_ERROR "ANDROID_SDK_DIR was not set")
endif()

if(NOT IS_DIRECTORY "${ANDROID_SDK_DIR}")
  message(FATAL_ERROR "ANDROID_SDK_DIR is not a directory")
endif()

if(NOT JDK_DIR)
  message(FATAL_ERROR "JDK_DIR was not set")
endif()

if(NOT IS_DIRECTORY "${JDK_DIR}")
  message(FATAL_ERROR "JDK_DIR is not a directory")
endif()

set(JAVA_HOME "${JDK_DIR}")
find_package(Java 1.7 REQUIRED COMPONENTS Development QUIET)
include(UseJava)

if(NOT Java_FOUND)
  message(FATAL_ERROR "Java was not found in JDK_DIR ")
endif()

if(NOT ANDROID_NDK_DIR)
  if(IS_DIRECTORY "${ANDROID_SDK_DIR}/ndk")
    LATEST_DIR(ANDROID_NDK_DIR "${ANDROID_SDK_DIR}/ndk")
    set(ANDROID_NDK_DIR "${ANDROID_SDK_DIR}/ndk/${ANDROID_NDK_DIR}")
  elseif(IS_DIRECTORY "${ANDROID_SDK_DIR}/ndk-bundle")
    LATEST_DIR(ANDROID_NDK_DIR "${ANDROID_SDK_DIR}/ndk-bundle")
    set(ANDROID_NDK_DIR "${ANDROID_SDK_DIR}/ndk-bundle/${ANDROID_NDK_DIR}")
  else()
    message(FATAL_ERROR "ANDROID_NDK_DIR was not set and could not be inferred")
  endif()

  message("Using ANDROID_NDK_DIR=${ANDROID_NDK_DIR}")
endif()

if(NOT IS_DIRECTORY "${ANDROID_NDK_DIR}")
  message(FATAL_ERROR "ANDROID_NDK_DIR is not a directory")
endif()

if(NOT ANDROID_SDK_BUILD_TOOLS_VERSION)
  LATEST_DIR(ANDROID_SDK_BUILD_TOOLS_VERSION "${ANDROID_SDK_DIR}/build-tools")
  message("Using ANDROID_SDK_BUILD_TOOLS_VERSION=${ANDROID_SDK_BUILD_TOOLS_VERSION}")
endif()

if(ANDROID_SDK_BUILD_TOOLS_VERSION VERSION_LESS "26.0.0")
  message(FATAL_ERROR "Build tools version 26.0.0 or later are required")
endif()

set(SDKBT "${ANDROID_SDK_DIR}/build-tools/${ANDROID_SDK_BUILD_TOOLS_VERSION}")

if(NOT IS_DIRECTORY "${SDKBT}")
  message(FATAL_ERROR "ANDROID_SDK_BUILD_TOOLS_VERSION is not a directory under ANDROID_SDK_DIR/build-tools/")
endif()

if(NOT ANDROID_SDK_API_LEVEL)
  LATEST_DIR(ANDROID_SDK_API_LEVEL "${ANDROID_SDK_DIR}/platforms")

  if(ANDROID_SDK_API_LEVEL MATCHES "android-.*")
    string(SUBSTRING "${ANDROID_SDK_API_LEVEL}" 8 -1 ANDROID_SDK_API_LEVEL)
  endif()

  message("Using ANDROID_SDK_API_LEVEL=${ANDROID_SDK_API_LEVEL}")
endif()

if(ANDROID_SDK_API_LEVEL LESS "21")
  message(FATAL_ERROR "API level 21 or greater is required")
endif()

set(SDKP "${ANDROID_SDK_DIR}/platforms/android-${ANDROID_SDK_API_LEVEL}")

if(NOT IS_DIRECTORY "${SDKP}")
  message(FATAL_ERROR "ANDROID_SDK_API_LEVEL is not a directory under ANDROID_SDK_DIR/platforms/android-")
endif()

set(SDKCP "${SDKP}/android.jar")

set(VALID_ABIS "armeabi-v7a" "arm64-v8a" "x86" "x86_64")

if(NOT ANDROID_ABI IN_LIST VALID_ABIS)
  message(FATAL_ERROR "ANDROID_ABI is not a valid ABI")
endif()

set(SDKCLT "${ANDROID_SDK_DIR}/cmdline-tools/latest")

if(NOT IS_DIRECTORY "${SDKCLT}")
  message(FATAL_ERROR "cmdline-tools/latest is not a directory under ANDROID_SDK_DIR")
endif()

set(SDKPT "${ANDROID_SDK_DIR}/platform-tools")

if(NOT IS_DIRECTORY "${SDKPT}")
  message(FATAL_ERROR "platform-tools is not a directory under ANDROID_SDK_DIR")
endif()

# ----------- PROJECT -----------
set(ANDROID_PLATFORM "${ANDROID_SDK_API_LEVEL}")
set(CMAKE_TOOLCHAIN_FILE "${ANDROID_NDK_DIR}/build/cmake/android.toolchain.cmake")

project(androidpp CXX)

set(OUT "${PROJECT_BINARY_DIR}")
set(IN "${PROJECT_SOURCE_DIR}")
set(ANDROID_DIR "${IN}/android")
set(MANIFEST_FILE "${ANDROID_DIR}/AndroidManifest.xml")
set(FINAL_APK_FILE "${OUT}/signed.apk")

if(CMAKE_HOST_WIN32)
  set(SH_EXT ".bat")
else()
  set(SH_EXT "")
endif()

file(MAKE_DIRECTORY "${ANDROID_DIR}")
file(MAKE_DIRECTORY "${ANDROID_DIR}/res")
file(MAKE_DIRECTORY "${ANDROID_DIR}/assets")
file(MAKE_DIRECTORY "${ANDROID_DIR}/java")
file(TOUCH "${MANIFEST_FILE}")

file(GLOB_RECURSE RES_FILES CONFIGURE_DEPENDS "${ANDROID_DIR}/res/*")
file(GLOB_RECURSE ASSET_FILES CONFIGURE_DEPENDS "${ANDROID_DIR}/assets/*")
file(GLOB_RECURSE JAVA_SRC_FILES CONFIGURE_DEPENDS "${ANDROID_DIR}/java/*.java")
set(JAVA_GEN_DIR "${OUT}/gen")

# find package ID
set(OUT_MANIFEST_RES_ZIP_FILE "${OUT}/manifest-res.zip")
set(OUT_MANIFEST_APK_FILE "${OUT}/manifest.apk")

execute_process(
  COMMAND "${SDKBT}/aapt2"
  "compile"
  "--dir" "${ANDROID_DIR}/res"
  "-o" "${OUT_MANIFEST_RES_ZIP_FILE}"
  COMMAND "${SDKBT}/aapt2"
  "link"
  "-I" "${SDKCP}"
  "--manifest" "${MANIFEST_FILE}"
  "-o" "${OUT_MANIFEST_APK_FILE}"
  "${OUT_MANIFEST_RES_ZIP_FILE}"
  COMMAND_ERROR_IS_FATAL ANY
)
execute_process(
  COMMAND "${SDKCLT}/bin/apkanalyzer${SH_EXT}" "manifest" "application-id" "${OUT_MANIFEST_APK_FILE}"
  OUTPUT_VARIABLE PACKAGE
  COMMAND_ERROR_IS_FATAL ANY
)
execute_process(
  COMMAND "${SDKCLT}/bin/apkanalyzer${SH_EXT}" "manifest" "min-sdk" "${OUT_MANIFEST_APK_FILE}"
  OUTPUT_VARIABLE MIN_API_LEVEL
  COMMAND_ERROR_IS_FATAL ANY
)

file(REMOVE "${OUT_MANIFEST_RES_ZIP_FILE}")
file(REMOVE "${OUT_MANIFEST_APK_FILE}")

string(STRIP "${PACKAGE}" PACKAGE)
string(STRIP "${MIN_API_LEVEL}" MIN_API_LEVEL)
string(REPLACE "." "/" PACKAGE_DIR "${PACKAGE}")

if(NOT MAIN_ACTIVITY_NAME)
  set(MAIN_ACTIVITY_NAME "${PACKAGE}.MainActivity")
endif()

# Visual Studio code configuration
set(VSCODE_DIR "${IN}/.vscode")
file(MAKE_DIRECTORY "${VSCODE_DIR}")
set(VSCODE_SETTINGS_FILE "${VSCODE_DIR}/settings.json")
set(VSCODE_TASKS_FILE "${VSCODE_DIR}/tasks.json")

if(EXISTS VSCODE_SETTINGS_FILE)
  file(READ "${VSCODE_SETTINGS_FILE}" VSCODE_SETTINGS)
else()
  set(VSCODE_SETTINGS "{}")
endif()

string(JSON VSCODE_SETTINGS SET "${VSCODE_SETTINGS}" "java.project.referencedLibraries" "[\"${SDKCP}\"]")
string(JSON VSCODE_SETTINGS SET "${VSCODE_SETTINGS}" "java.project.sourcePaths" "[\"${ANDROID_DIR}/java\",\"${JAVA_GEN_DIR}\"]")
string(JSON VSCODE_SETTINGS SET "${VSCODE_SETTINGS}" "java.jdt.ls.java.home" "\"${JDK_DIR}\"")
file(WRITE "${VSCODE_SETTINGS_FILE}" "${VSCODE_SETTINGS}")

set(VSCODE_TASK_BOILERPLATE "\
  \"type\":\"shell\",\
  \"group\":\"build\",\
  \"presentation\": {\
    \"echo\": false,\
    \"reveal\":\"never\",\
    \"focus\": false,\
    \"panel\":\"dedicated\",\
    \"showReuseMessage\": false\
  }\
")
set(VSCODE_TASKS "{\
  \"version\":\"2.0.0\",\
  \"tasks\": [\
    {\
      \"label\":\"Uninstall APK with ADB\",\
      \"command\":\"${SDKPT}/adb\",\
      \"args\": [\
        \"uninstall\",\
        \"${PACKAGE}\"\
      ],\
      ${VSCODE_TASK_BOILERPLATE}\
    },\
    {\
      \"label\":\"Install built APK with ADB\",\
      \"dependsOn\":\"Uninstall APK with ADB\",\
      \"command\":\"${SDKPT}/adb\",\
      \"args\": [\
        \"install\",\
        \"-r\",\
        \"-t\",\
        \"${FINAL_APK_FILE}\"\
      ],\
      ${VSCODE_TASK_BOILERPLATE}\
    },\
    {\
      \"label\":\"Launch APK with ADB\",\
      \"command\":\"${SDKPT}/adb\",\
      \"dependsOn\":\"Install built APK with ADB\",\
      \"args\": [\
        \"shell\",\
        \"am\",\
        \"start\",\
        \"-W\",\
        \"-S\",\
        \"${PACKAGE}/${MAIN_ACTIVITY_NAME}\"\
      ],\
      ${VSCODE_TASK_BOILERPLATE}\
    }\,
    {\
      \"label\":\"Debug APK with ADB\",\
      \"command\":\"${SDKPT}/adb\",\
      \"dependsOn\":\"Install built APK with ADB\",\
      \"args\": [\
        \"shell\",\
        \"am\",\
        \"start\",\
        \"-W\",\
        \"-D\",\
        \"-S\",\
        \"${PACKAGE}/${MAIN_ACTIVITY_NAME}\"\
      ],\
      ${VSCODE_TASK_BOILERPLATE}\
    }\
  ]\
}")
string(JSON VSCODE_TASKS REMOVE "${VSCODE_TASKS}" __NONEXISTENT_MEMBER__) # format only
file(WRITE "${VSCODE_TASKS_FILE}" "${VSCODE_TASKS}")

# validate keystore
if(NOT KEYSTORE_FILE)
  set(KEYSTORE_FILE "${OUT}/debug-keystore.jks")
  set(KEYSTORE_STORE_PASS "androidpp")
  set(KEYSTORE_KEY_PASS "androidpp")
  set(KEYSTORE_KEY_ALIAS "androidpp")

  if(NOT EXISTS "${KEYSTORE_FILE}")
    execute_process(
      COMMAND "${JDK_DIR}/bin/keytool"
      "-genkeypair"
      "-keystore" "${KEYSTORE_FILE}"
      "-alias" "${KEYSTORE_KEY_ALIAS}"
      "-dname" "CN=androidpp"
      "-validity" "10000"
      "-keyalg" "RSA"
      "-keysize" "2048"
      "-storepass" "${KEYSTORE_STORE_PASS}"
      "-keypass" "${KEYSTORE_KEY_PASS}"
    )
  endif()
endif()

if((NOT EXISTS "${KEYSTORE_FILE}") OR IS_DIRECTORY "${KEYSTORE_FILE}")
  message(FATAL_ERROR "KEYSTORE_FILE is not a file")
endif()

# compile resources
set(RES_ZIP_FILE "${OUT}/resources.zip")

add_custom_command(
  OUTPUT "${RES_ZIP_FILE}"
  DEPENDS ${RES_FILES}
  COMMAND "${SDKBT}/aapt2" ARGS
  "compile"
  "--dir" "${ANDROID_DIR}/res"
  "-o" "${RES_ZIP_FILE}"
)

set(RES_APK_FILE "${OUT}/resources.apk")
set(JAVA_R_FILE "${JAVA_GEN_DIR}/${PACKAGE_DIR}/R.java")
set(PROGUARD_FILE "${OUT}/proguard.txt")

add_custom_command(
  OUTPUT "${RES_APK_FILE}" "${PROGUARD_FILE}" "${JAVA_R_FILE}"
  DEPENDS "${RES_ZIP_FILE}" "${MANIFEST_FILE}" ${ASSET_FILES}
  COMMAND "${SDKBT}/aapt2" ARGS
  "link"
  "-I" "${SDKCP}"
  "-A" "${ANDROID_DIR}/assets"
  "--java" "${JAVA_GEN_DIR}"
  "--manifest" "${MANIFEST_FILE}"
  "--proguard" "${PROGUARD_FILE}"
  "-o" "${RES_APK_FILE}"
  $<$<CONFIG:Debug>:--debug-mode>
  "${RES_ZIP_FILE}"
)

# compile Java
set(CMAKE_JAVA_INCLUDE_PATH "${SDKCP}")
add_jar(
  java
  SOURCES ${JAVA_SRC_FILES} ${JAVA_R_FILE}
  GENERATE_NATIVE_HEADERS jni
)

get_target_property(JAVA_JAR_FILE java JAR_FILE)

# convert Java into DEX
set(DEX_JAR_FILE "${OUT}/dex.jar")
set(DEX_DIR "${OUT}/dex")
set(DEX_FILE "${DEX_DIR}/classes.dex")

add_custom_command(
  OUTPUT "${DEX_JAR_FILE}"
  DEPENDS java
  COMMAND "${SDKBT}/d8" ARGS
  "--classpath" "${SDKCP}"
  "--output" "${DEX_JAR_FILE}"
  "${JAVA_JAR_FILE}"
)

add_custom_command(
  OUTPUT "${DEX_DIR}"
  COMMAND "${CMAKE_COMMAND}" ARGS
  "-E" "make_directory"
  "${DEX_DIR}"
)

add_custom_command(
  OUTPUT "${DEX_FILE}"
  DEPENDS "${DEX_JAR_FILE}" "${DEX_DIR}"
  COMMAND "${JDK_DIR}/bin/java" ARGS
  "-cp" "${SDKBT}/lib/d8.jar"
  "com.android.tools.r8.R8"
  "--lib" "${SDKCP}"
  "--output" "${DEX_DIR}"
  "--min-api" "${MIN_API_LEVEL}"
  "--pg-conf" "${PROGUARD_FILE}"
  $<$<CONFIG:Release>:--release>
  $<$<CONFIG:Debug>:--debug>
  "${DEX_JAR_FILE}"
)

# add DEX classes to APK
set(MERGED_APK_FILE "${OUT}/merged.apk")
set(EXTR_MERGED_APK_DIR "${OUT}/merged")
set(EXTR_MERGED_APK_MANIFEST_FILE "${EXTR_MERGED_APK_DIR}/AndroidManifest.xml")
set(EXTR_MERGED_APK_DEX_FILE "${EXTR_MERGED_APK_DIR}/classes.dex")
set(EXTR_MERGED_APK_LIB_FILE "${EXTR_MERGED_APK_DIR}/lib/${ANDROID_ABI}/libmain.so")

add_custom_command(
  OUTPUT "${EXTR_MERGED_APK_DIR}"
  COMMAND "${CMAKE_COMMAND}" ARGS
  "-E" "make_directory"
  "${EXTR_MERGED_APK_DIR}"
)

add_custom_command(
  OUTPUT "${EXTR_MERGED_APK_MANIFEST_FILE}"
  DEPENDS "${EXTR_MERGED_APK_DIR}" "${RES_APK_FILE}"
  COMMAND "${CMAKE_COMMAND}" ARGS
  "-E" "tar"
  "x" "${RES_APK_FILE}"
  WORKING_DIRECTORY "${EXTR_MERGED_APK_DIR}"
)

add_custom_command(
  OUTPUT "${EXTR_MERGED_APK_LIB_FILE}"
  DEPENDS "${EXTR_MERGED_APK_DIR}" lib
  COMMAND "${CMAKE_COMMAND}" ARGS
  "-E" "copy"
  $<TARGET_FILE_NAME:lib>
  "${EXTR_MERGED_APK_LIB_FILE}"
)

add_custom_command(
  OUTPUT "${EXTR_MERGED_APK_DEX_FILE}"
  DEPENDS "${EXTR_MERGED_APK_DIR}" "${DEX_FILE}"
  COMMAND "${CMAKE_COMMAND}" ARGS
  "-E" "copy"
  "${DEX_FILE}"
  "${EXTR_MERGED_APK_DIR}"
)

add_custom_command(
  OUTPUT "${MERGED_APK_FILE}"
  DEPENDS "${EXTR_MERGED_APK_MANIFEST_FILE}" "${EXTR_MERGED_APK_DEX_FILE}" "${EXTR_MERGED_APK_LIB_FILE}"
  COMMAND "${CMAKE_COMMAND}" ARGS
  "-E" "tar"
  "c" "${MERGED_APK_FILE}"
  "--format=zip"
  "."
  WORKING_DIRECTORY "${EXTR_MERGED_APK_DIR}"
)

# optimize merged APK
set(OPT_APK_FILE "${OUT}/opt.apk")
set(ZA_APK_FILE "${OUT}/za.apk")

add_custom_command(
  OUTPUT "${OPT_APK_FILE}"
  DEPENDS "${MERGED_APK_FILE}"
  COMMAND "${SDKBT}/aapt2" ARGS
  "optimize"
  "${MERGED_APK_FILE}"
  "-o" "${OPT_APK_FILE}"
)

add_custom_command(
  OUTPUT "${ZA_APK_FILE}"
  DEPENDS "${OPT_APK_FILE}"
  COMMAND "${SDKBT}/zipalign" ARGS
  "-f"
  "-p" "4"
  "${OPT_APK_FILE}"
  "${ZA_APK_FILE}"
)

# sign
add_custom_command(
  OUTPUT "${FINAL_APK_FILE}"
  DEPENDS "${OPT_APK_FILE}"
  COMMAND "${SDKBT}/apksigner" ARGS
  "sign"
  "--ks" "${KEYSTORE_FILE}"
  "--ks-key-alias" "${KEYSTORE_KEY_ALIAS}"
  "--ks-pass" "pass:${KEYSTORE_STORE_PASS}"
  "--key-pass" "pass:${KEYSTORE_KEY_PASS}"
  "--out" "${FINAL_APK_FILE}"
  "${OPT_APK_FILE}"
)

add_custom_target(package ALL DEPENDS "${FINAL_APK_FILE}")

add_custom_command(
  TARGET package
  PRE_BUILD
  COMMAND "${CMAKE_COMMAND}" ARGS
  "-E" "rm"
  "-rf"
  "--"
  "${RES_ZIP_FILE}" "${RES_APK_FILE}" "${PROGUARD_FILE}" "${JAVA_JAR_FILE}" "${DEX_JAR_FILE}" "${DEX_DIR}" "${MERGED_APK_FILE}" "${EXTR_MERGED_APK_DIR}" "${OPT_APK_FILE}" "${ZA_APK_FILE}"
)

add_library(lib SHARED
  "src/hello_world.cpp"
  "src/jni.cpp"
)

set_target_properties(lib PROPERTIES
  CXX_STANDARD 20
  CXX_EXTENSIONS OFF
  CXX_STANDARD_REQUIRED ON
)

target_include_directories(lib
  PRIVATE "include"
)

target_link_libraries(lib PRIVATE jni)