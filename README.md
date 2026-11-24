# iOSpring — Native iOS 18+ Android Home Launcher

**iOSpring** adalah aplikasi Android Native Home Launcher (Kotlin + pure Android View/Canvas) bergaya **iOS 18+** yang dirancang ultra-ringan (low RAM & hemat baterai), mulus di layar 60/90/120Hz, serta dioptimalkan khusus agar **kompatibel penuh dengan gestur navigasi bawaan OPPO Android 15 (ColorOS 15)**.

---

## Fitur Utama

### 1. Estetika & Pengalaman iOS 18+
- **Squircle Icons & Grid Presisi (4×6 / 5×6):** Ikon aplikasi dibentuk menggunakan kurva *superellipse* kontinu khas Apple dengan dukungan **iOS 18 Free Placement** (bebas menaruh ikon di sel mana pun).
- **Kustomisasi Ikon Gaya iOS 18:**
  - **Light (Default)** — squircle cerah dengan *specular inner stroke*.
  - **Dark Mode Icons** — pelat latar gelap arang (`#2E2E32` → `#171719`).
  - **Tinted / Monochrome** — pewarnaan monokrom dengan 7 preset warna iOS 18.
  - **Large Icons** — memperbesar ukuran ikon dan menyembunyikan teks label aplikasi.
- **Frosted Glass Dock:** Dock melayang maksimal 4 aplikasi dengan kaca buram (`RenderEffect` blur untuk Android 12+ dan translucency ringan).
- **Floating Search Pill & Page Dots:** Kapsul `🔍 Search` melayang di atas Dock yang berubah mulus menjadi indikator titik halaman saat digeser.
- **Jiggle Mode (Edit Home Screen):** Tekan tahan area kosong atau pilih *Edit Home Screen* untuk masuk mode goyang — mendukung drag-and-drop pindah posisi/halaman, menumpuk dua ikon untuk membuat **Folder**, menaruh aplikasi ke Dock, dan menghapus widget/ikon.
- **App Library (Halaman Paling Kanan):**
  - Pengelompokan otomatis ke kartu folder 2×2 (*Suggestions*, *Recently Added*, *Social*, *Productivity & Finance*, *Entertainment*, *Utilities*, *Creativity*, *Games*, *Shopping & Food*, *Information & Reading*, *Other*).
  - Beralih cepat ke daftar alfabetis **A–Z** dengan *fast-scroll index* di sisi kanan.
  - Folder **Hidden** di bagian bawah yang dilindungi autentikasi biometrik (`BiometricPrompt`).
- **Spotlight Search (Swipe Down):** Geser ke bawah di tengah layar untuk pencarian *real-time* (Prefix, Word-prefix, Inisial, Fuzzy, dan **T9 Numeric** mis. `942` → *WhatsApp*), **Kalkulator Instan** (`24 * 7`), **Siri Suggestions** lokal, dan tombol **Search the Web**.
- **Built-in iOS Widgets (Zero Extra Apps):**
  - **Clock (2×2):** Analog/Digital (ketuk untuk beralih mode, auto tema siang/malam).
  - **Calendar (2×2):** Hari merah khas iOS + tanggal besar bersih.
  - **Batteries (2×2):** Cincin baterai HP + perangkat Bluetooth terhubung.
  - **Device Summary (4×2):** Sapaan waktu, tanggal, baterai & RAM bebas.
  - **Quick Note (4×2):** Kartu catatan kuning bergaya Apple Notes yang bisa langsung diedit.
- **Today View (Halaman Paling Kiri):** Kumpulan widget cepat & pintasan pengaturan.
- **Hide Apps & Biometric App Lock:** Sembunyikan aplikasi atau kunci aplikasi tertentu menggunakan Sidik Jari / Face Unlock / PIN perangkat.
- **Notification Badges:** Lencana titik merah iOS berbasis `NotificationListenerService` opsional.

### 2. Kompatibilitas Gestur OPPO Android 15 (ColorOS 15)
- **Edge-to-Edge & Safe `WindowInsets`:** Menggunakan `enableEdgeToEdge()` dengan penanganan presisi `statusBars`, `navigationBars`, `systemGestures`, dan `mandatorySystemGestures` sehingga Dock selalu aman di atas garis gestur (*gesture pill*).
- **Zero Gesture Conflict:** Area tepi kiri/kanan (gestur *Back*) dan tepi paling bawah (gestur *Home* & *Recent Apps* ColorOS 15) tidak pernah dibajak oleh pager launcher.
- **Optimal Launcher Lifecycle:** `MainActivity` dikonfigurasi dengan `singleTask`, `clearTaskOnLaunch="true"`, `stateNotNeeded="true"`, `resumeWhilePausing="true"`, `enableOnBackInvokedCallback="true"`, dan `onNewIntent()` yang menutup overlay lalu kembali ke halaman utama dengan pegas (*spring*) halus.
- **Wallpaper Sistem Tajam & Parallax:** Menggunakan `windowShowWallpaper="true"` + `WallpaperManager.setWallpaperOffsets()` untuk wallpaper asli ColorOS 15 dengan efek parallax horizontal halus.

### 3. Ultra Ringan (Low RAM & Baterai)
- Tanpa bloat Jetpack Compose atau Material Components — 100% pure Kotlin + Android Views & Canvas.
- Pemuatan daftar aplikasi dan rendering squircle dijalankan asinkron pada `Dispatchers.IO` dengan `LruCache` terbatas (4–12 MB).
- `BroadcastReceiver` ringan (`PACKAGE_ADDED`, `PACKAGE_REMOVED`, `PACKAGE_CHANGED`) untuk update inkremental tanpa *full scan* berulang.
- Penyimpanan tata letak JSON atomik ringan + `SharedPreferences` tanpa database berat.

---

## Build & CI (GitHub Actions)

Proyek ini siap langsung di-compile melalui **GitHub Actions** (`.github/workflows/build-apk.yml`) maupun Android Studio:
- **Java:** 17 (Temurin)
- **Kotlin:** 2.0.20
- **AGP:** 8.6.1 / **Gradle:** 8.7 (Wrapper lengkap tersedia)
- **SDK:** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`

```bash
chmod +x gradlew
./gradlew testDebugUnitTest assembleDebug
```

APK Debug akan tersedia di `app/build/outputs/apk/debug/app-debug.apk` (atau tab **Artifacts** di GitHub Actions).
