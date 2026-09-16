# CODM Root Tutorial

Aplikasi eksternal root untuk melewati pemeriksaan tutorial CODM tanpa Zygisk.

## Build

1. Buat repository GitHub baru.
2. Upload seluruh isi project ini.
3. Buka tab **Actions** lalu jalankan **Build APK**.
4. Unduh artifact `CODM-Root-Tutorial`.

## Pemakaian

1. Buka aplikasi dan izinkan root.
2. Tekan **BUKA CODM + TERAPKAN**.
3. Helper menunggu `libunity.so`, memverifikasi byte asli, lalu menerapkan patch.
4. Gunakan **PULIHKAN BYTE ASLI** sebelum keluar bila ingin mengembalikan fungsi.

## Target tervalidasi

- Package: `com.garena.game.codm`
- `WillEnterFtue`: `0xAF08534` → selalu `false`
- `IsTutorialFinished(int)`: `0x9DE0A04` → selalu `true`
- `IsTutorialFinished(TutorialType)`: `0x9DE3174` → selalu `true`
- Build ID `libunity.so`: `1305bdeea30b989f`
- SHA-256 sumber: `91781232f051c712cbc7f78ebdb95f4faa739b70c9b2567c05b9fa5b8c53ed1f`

Patch dibatasi pada pemeriksaan tutorial. Tidak ada bypass anti-cheat.
