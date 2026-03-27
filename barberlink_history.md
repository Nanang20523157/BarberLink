# 📜 BarberLink Project History

Log ini mencatat seluruh aktivitas pengembangan dan keputusan teknis penting.

## [2026-05-21] - Penambahan Data Customer & Pembuatan Script Utilitas
### 🛠️ Aktivitas
- Menambahkan 2 data customer baru (Ferry Irwandi & Fajar Ismail) secara langsung ke koleksi Firestore `users` dan `customers`, serta menambahkan username mereka (`Ferry33` dan `GojerHolic`) ke dalam field `username_list` di dokumen `/official/barberlink2024`.
- Membuat dan memperbarui script utilitas CLI Node.js `functions/add_new_customer.js` agar secara otomatis menyinkronkan penambahan username ke `/official/barberlink2024` secara atomik di dalam batch write yang sama ketika menambahkan data baru di masa depan.
- Memverifikasi keberhasilan integrasi dan penyimpanan dokumen Firestore.

### 📝 Keputusan Teknis
- Penyimpanan dokumen `customers` menggunakan ID berbasis `uid` Google Auth (misal: `VAbbjuJociY63YC0ufqNH4hRKSs1`), sedangkan `users` menggunakan ID berbasis `phone` (nomor handphone) untuk menyelaraskan arsitektur sinkronisasi multi-role BarberLink.
- Integrasi `username_list` di `/official/barberlink2024` menggunakan operasi atomik `admin.firestore.FieldValue.arrayUnion` agar tidak terjadi bentrokan race condition jika script dijalankan secara bersamaan.

## [2026-04-14] - Inisialisasi Dokumentasi Agen
### 🛠️ Aktivitas
- Pembuatan sistem dokumentasi 3-file: `skills`, `history`, `instructions`.
- Konfirmasi pembatalan sementara migrasi KMP. Fokus tetap pada **Android Native (Kotlin/XML)**.
- **Update Skills**: Menambahkan "Universal Starter Skills", "Essentials & Core Packs", dan "Android Developer Pack" ke `barberlink_skills.md`.
- **Align Shortcuts**: Menyelaraskan BarberLink Specific Shortcuts agar terintegrasi dengan paket skill universal dan Android.
- Analisis struktur `strings.xml` untuk pemetaan fitur POS, Kasbon, dan Manajemen Outlet.
- **Update Aturan**: Menambahkan kewajiban pembaruan `barberlink_history.md` pada file `barberlink_instructions.md`.

### 📝 Keputusan Teknis
- Mempertahankan arsitektur XML untuk UI agar kompatibel dengan aset yang sudah ada.
- Menitikberatkan pada integrasi Firestore untuk sinkronisasi data real-time antar role (Owner, Kasir, Capster).

### 🚀 Status Saat Ini
- Struktur proyek sudah memiliki fitur manajemen outlet dan antrian.
- Sedang dalam tahap perapihan dokumentasi agar koordinasi agen lebih presisi.
