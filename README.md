# РБМК-1000 — Nükleer Reaktör Simülatörü

**Paket:** `com.rbmk.alexandr`
**Karakter:** Aleksandr Bryuhanov — Smolensk AES, 2026
**Platform:** Android (yatay mod zorunlu, API 26+)

---

## Proje Yapısı

```
rbmk_alexandr/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   ├── reactor_engine.h          ← Fizik sabitleri, struct'lar, sınıf tanımı
│       │   ├── reactor_engine.cpp        ← Reaktör fizik motoru (C++17)
│       │   └── reactor_jni.cpp           ← JNI köprüsü (Kotlin ↔ C++)
│       ├── java/com/rbmk/alexandr/
│       │   ├── MainActivity.kt           ← Giriş noktası + navigasyon
│       │   ├── model/
│       │   │   └── LevelModels.kt        ← Veri modelleri + 16 level tanımı
│       │   ├── viewmodel/
│       │   │   └── ReactorViewModel.kt   ← JNI çağrıları + oyun mantığı
│       │   ├── sound/
│       │   │   └── SoundManager.kt       ← Alarm ve acil durum sesleri
│       │   └── ui/
│       │       ├── theme/
│       │       │   └── Colors.kt         ← Renk sistemi
│       │       └── screens/
│       │           ├── GameScreen.kt     ← Ana oyun ekranı (tüm UI)
│       │           └── MainMenuScreen.kt ← Menü + level seçimi + hikaye
│       └── res/values/strings.xml
├── build.gradle
├── settings.gradle
└── gradle/wrapper/gradle-wrapper.properties
```

---

## Gereksinimler

| Araç | Gerekli Sürüm |
|------|--------------|
| Android Studio | Ladybug (2024.2.x) veya üzeri |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Gradle | 8.9 |
| NDK | r25c veya r26d |
| CMake | 3.22.1+ |
| compileSdk | 35 |
| minSdk | 26 (Android 8.0) |

---

## Derleme Adımları

### 1. Android Studio'ya Aktarma

```bash
# Projeyi indirdikten sonra:
cd rbmk_alexandr
```

Android Studio'yu açın → **File → Open** → `rbmk_alexandr` klasörünü seçin.

---

### 2. NDK ve CMake Kurulumu

Android Studio içinde:

```
File → Settings → Appearance & Behavior → System Settings → Android SDK
  → SDK Tools sekmesi
    ✓ NDK (Side by side)  → 25.2.9519653 (r25c) veya 26.3.11579264 (r26d)
    ✓ CMake               → 3.22.1
```

Komut satırından kurulum:
```bash
# SDK Manager ile (sdkmanager yolunuza göre düzenleyin)
sdkmanager "ndk;25.2.9519653" "cmake;3.22.1"
```

---

### 3. local.properties Oluşturma

Proje kökünde `local.properties` dosyasını oluşturun (Android Studio genellikle otomatik oluşturur):

```properties
sdk.dir=/Users/KULLANICI_ADI/Library/Android/sdk       # macOS
# sdk.dir=C\:\\Users\\KULLANICI_ADI\\AppData\\Local\\Android\\Sdk  # Windows
# sdk.dir=/home/KULLANICI_ADI/Android/Sdk                           # Linux
```

---

### 4. Gradle Senkronizasyonu

Android Studio'da:
```
File → Sync Project with Gradle Files
```

veya terminal:
```bash
./gradlew dependencies
```

Hata alırsanız:
```bash
./gradlew --refresh-dependencies
```

---

### 5. Debug APK Derleme

**Android Studio üzerinden:**
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

**Komut satırından:**
```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

Çıktı konumu:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

### 6. Release APK Derleme

```bash
./gradlew assembleRelease
```

> **Not:** Release build için `app/build.gradle` içine keystore bilgilerini eklemeniz gerekir.
> Debug build cihaza kurulum için yeterlidir.

---

### 7. Cihaza Yükleme

```bash
# USB ile bağlı cihaza doğrudan yükle (USB hata ayıklama açık olmalı)
adb install app/build/outputs/apk/debug/app-debug.apk

# Veya Android Studio'dan: Run → Run 'app' (Shift+F10)
```

---

### 8. Emülatör ile Test

Emülatörde çalıştırmak için:

1. AVD Manager → **Create Virtual Device**
2. **Tablet** kategorisinden Nexus 10 veya Pixel Tablet seçin (yatay mod için geniş ekran)
3. API Level: 26 veya üzeri
4. RAM: en az 2 GB
5. Emülatörü başlatın → `./gradlew installDebug`

> **Önemli:** Oyun **yatay mod zorunlu** olarak tasarlanmıştır.
> Emülatörü yatay modda başlatın veya döndürme kilidini kaldırın.

---

## Olası Derleme Hataları ve Çözümleri

### `NDK not configured`
```
Çözüm: local.properties dosyasına ndk.dir ekleyin:
ndk.dir=/Users/KULLANICI/Library/Android/sdk/ndk/25.2.9519653
```

### `CMake not found`
```
Çözüm: SDK Tools'tan CMake 3.22.1'i yükleyin.
Ardından: File → Invalidate Caches / Restart
```

### `Kotlin compiler version mismatch`
```
Çözüm: app/build.gradle'daki kotlinCompilerExtensionVersion
Compose BOM 2024.12.01 ile uyumlu olmalı: '1.5.14'
```

### `Unsupported class file major version`
```
Çözüm: File → Project Structure → SDK Location
JDK sürümünün 17 olduğundan emin olun.
```

### `ABI mismatch` (x86 emülatör)
```
Çözüm: app/build.gradle → ndk → abiFilters'a "x86" ekleyin:
abiFilters "arm64-v8a", "x86_64", "x86"
```

### `Native method not found` (JNI)
```
Bu hata JNI fonksiyon adı uyuşmazlığında olur.
reactor_jni.cpp'deki fonksiyon adları şu formatta olmalı:
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeXxx
Paket adı com.rbmk.alexandr ise bu zaten doğru.
```

---

## Fizik Motoru Hakkında

C++17 ile yazılmış reaktör motoru şu modelleri içerir:

| Model | Açıklama |
|-------|----------|
| Point Kinetics | Gecikmeli nötron dinamikleri (β = 0.0065) |
| Xenon-135 | İyot zinciri, 9.2 saatlik yarılanma ömrü |
| Void katsayısı | RBMK pozitif boşluk katsayısı (+0.50β - +1.20β) |
| Doppler | Yakıt sıcaklığı geri beslemesi (negatif) |
| Grafit uç etkisi | AZ-5 ilk anında +0.10β/çubuk |
| Termal hidrolik | Soğutucu sıcaklık, void, basınç hesabı |
| Xenon tuzağı | Güç kapatması sonrası xenon zirvesi |

**Simülasyon adım boyutu:** 100ms (0.1s)
**Simülasyon hızı:** Gerçek zamana yakın (oyun içi hız çarpanı yok)

---

## Oyun İçeriği

- **16 level** — oryantasyondan scripted Çernobil senaryosuna
- **Zorg seviyeleri** (Level 5, 10, 15): Öngörülemeyen kritik arızalar
- **Level 16**: Grafit uç etkisi ile scripted patlama (kaçınılmaz)
- **Tüm kontroller**: 211 çubuk, 8 pompa, 2 türbin, SAOR, AR sistemi, dizel jeneratör
- **Ses sistemi**: Alarm, SCRAM, patlama sesleri
- **Gerçek zamanlı log**: Tüm olaylar Rusça kod + Türkçe açıklama ile

---

## Geliştirici Notu

Bu proje **eğitim amaçlıdır** ve RBMK reaktörünün tarihsel önemini,
tasarım sorunlarını ve operasyonel karmaşıklığını simüle eder.
Gerçek nükleer tesis işletimini temsil etmez.

Level 16'daki senaryo, 26 Nisan 1986 Çernobil olayının
2026 yılına uyarlanmış dramatize versiyonudur.
