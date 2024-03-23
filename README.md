# androidpp

[CMake](https://cmake.org/)-only Android native bootstrap project.

In almost no circumstance should you torture yourself by using this build system. 
You would certainly be happier by following the [orthodox way](https://developer.android.com/studio/projects/add-native-code), unless you, like me, are allergic to Gradle and Android Studio.
Or, you can think of this as a proof of concept, or an elaborate puzzle.

Tested on Windows, MacOS and Linux, with Android build tools 35, platform 34, NDK 26, CMake 3.29 and JDK 20.

## Installation

1. Install the latest Java Development Kit.
2. Install the latest Android SDK build tools, platform, platform tools, command line tools and NDK using [sdkmanager](https://developer.android.com/tools/sdkmanager) or the [Android Studio](https://developer.android.com/studio) built-in GUI.
3. Install the latest [CMake](https://cmake.org/).
4. Install [Ninja](https://ninja-build.org/), [Make](https://www.gnu.org/software/make/) or some other Clang-compatible build system.
5. Set the `ANDROID_SDK_DIR` and the `JDK_DIR` environment variables (or pass them directly to CMake later).
6. Clone this repository:
```shell
git clone https://github.com/francescozoccheddu/androidpp
cd androidpp
```

## Usage
I suggest using [Visual Studio Code](https://code.visualstudio.com/).

### Visual Studio Code

The repository comes with VS Code extension recomendations and preconfigured launch and debug tasks.

### Visual Studio

You can probably get the debugger working with an old NDK by injecting a ["Dynamic Shared Library (Android)"](https://devblogs.microsoft.com/cppblog/debug-jni-android-applications-using-visual-c-cross-platform-mobile/) 
into `CMakeLists.txt` with [`include_external_msproject`](https://cmake.org/cmake/help/latest/command/include_external_msproject.html),
but there is a limit on how much ugliness I can handle, so you'll have to deal with it on your own for now.

### Command line

1. Configure CMake:
```shell
mkdir build
cd build
cmake .. 
```
2. Build:
```shell
cmake --build .
```
3. Enjoy your `signed.apk` APK file. 😁


> [!NOTE]  
> You may as well want to remove the VS Code configuration logic in `CMakeLists.txt` and the `.vscode` directory.

## Targets

`CMakeLists.txt` configures the following targets:

- `java`: This target builds the Java code inside `android/java` into a JAR.
- `lib`: This target builds the C++20 code inside `src` into a shared library.
- `package`: This target packages the compiled Java and C++ code, `android/res`, `android/assets` and `android/AndroidManifest.xml` into a signed APK.