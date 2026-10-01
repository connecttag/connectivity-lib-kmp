# ConnectTag Connectivity Library (connectivity-lib-kmp)

[![JitPack](https://jitpack.io/v/connecttag/connectivity-lib-kmp.svg)](https://jitpack.io/#connecttag/connectivity-lib-kmp)

مكتبة مراقبة الاتصال وحالة الشبكة وإدارتها بشكل تفاعلي مع واجهات Compose Multiplatform:
- **Android** (API 24+)
- **Desktop JVM** (Windows, macOS, Linux)
- **iOS** (Arm64, SimulatorArm64)
- **Web / WasmJs**

---

## 📦 التثبيت والإضافة للمشروع (Installation via JitPack)

### 1. إضافة مستودع JitPack في `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. إضافة التبعية في `build.gradle.kts`:
```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            // For Kotlin Multiplatform:
            implementation("com.github.connecttag.connectivity-lib-kmp:connectivity-lib-kmp:1.0.0")
        }
    }
}
```

الحزمة الأساسية للمكتبة: `org.connecttag.lib.connectivity.*`

---

## 🌟 الميزات الأساسية

1. **`PlatformConnectivityService` & `ConnectivitySnapshot`:**
   - رصد ذري (Atomic snapshot) لحالة الاتصال الحالية ونوع الشبكة (Wifi, Cellular, Ethernet, VPN, Other).
   - توفير تدفق حالة حي عبر `StateFlow<ConnectivitySnapshot>`.
2. **`ConnectivityStateMachine`:**
   - إدارة انتقالات الاتصال بذكاء وتجنب التذبذب (Flapping prevention & Debounce).
   - التعامل المرن مع سيناريوهات التبديل بين الشبكات (Handover policy) وانقطاع الاتصال المؤقت.
3. **`ConnectivityCompose`:**
   - دوال Compose مدمجة: `rememberConnectivitySnapshot()`.
   - شريط ولواقط عرض حالة الاتصال في واجهات المستخدم عبر مكونات Compose الجاهزة ومترجمة للعربية والإنجليزية.
