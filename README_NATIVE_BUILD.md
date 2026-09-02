# Native Rime build architecture

The Android app does **not** load a separate `liblibrime.so` at runtime.

The GitHub Actions build clones the Rime Android dependency graph, disables Trime's GPL JNI target and optional Lua/Octagram/Predict plugins, builds the BSD-3-Clause `rime-static` target with Android NDK, and links it directly into Drumstick's own `libdrumstick_rime.so` JNI library.

```text
Java RimeNative
      |
      | JNI
      v
libdrumstick_rime.so
      |
      +-- Drumstick JNI bridge
      |
      +-- rime-static
          +-- LevelDB
          +-- marisa
          +-- OpenCC
          +-- yaml-cpp
          +-- glog
          +-- Boost
```

Android Gradle packages the resulting shared JNI library from `app/src/main/jniLibs/<ABI>/` into the APK. This follows Android's documented native-library packaging model. The build uses CMake/NDK and Gradle packaging rather than loading a separate Rime shared object at runtime.
