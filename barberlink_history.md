# 📜 BarberLink Project History

Log ini mencatat seluruh aktivitas pengembangan dan keputusan teknis penting.

## [2026-06-22] - Perbaikan Bug & Error Kompilasi di Halaman ManageEmployeePage
### 🛠️ Aktivitas
- Memperbaiki kesalahan kompilasi `qwerty` di dalam `ManageEmployeePage.kt` pada method `onChildDraw` dan `navigatePage`.
- Mengganti inisialisasi `ItemManageEmployeeAdapter` dari `ItemManageEmployeeAdapter(this, this)` menjadi lambda expressions agar sesuai dengan signature constructor adapter.
- Menambahkan method `setBlockStatusUI` dan `updateNetworkStatus` ke dalam `ItemManageEmployeeAdapter.kt` dan mengimplementasikan validasi `blockAllUserClickAction` sebelum aksi klik dieksekusi.
- Memperbaiki tipe data dan signature parameter pada method navigasi `navigatePage` di `ManageEmployeePage.kt` dari `BundlingPackage` ke `UserEmployeeData` dan menyinkronkan intent extras yang dikirimkan.
- Menghapus method `onNavigationRequest` dari `ManageEmployeePage.kt` karena tidak meng-override method apa pun.
- Menghapus keyword `override` dari method `displayThisToast` di `ManageEmployeePage.kt` karena tidak meng-override method apa pun.
- Memperbaiki key mismatch `"EMPLOYEE_ROLES_KEY"` di `SearchUserCapsterFragment.kt` menjadi `"EMPLOYEE_ROLES_LIST_KEY"` agar sinkron dengan extra key yang dibaca di `AddEmployeeFormActivity.kt`.
- Menambahkan import `androidx.recyclerview.widget.GridLayoutManager` yang hilang di `ManageServicePage.kt`.
- Menyesuaikan ukuran, koordinat viewport, dan path data `ic_star_half_figma.xml` agar sejajar secara pixel-perfect dan berukuran sama persis dengan `ic_star_full_figma.xml` (sisi kiri terisi penuh menggunakan koordinat absolut luar) dan `ic_star_empty_figma.xml` (sisi kanan hollow outline).
- Memperbaiki urutan pemanggilan `setupRecyclerView()` ke baris pertama `onViewCreated` di `PlacementSelectionFragment.kt` untuk mencegah `UninitializedPropertyAccessException` pada `placementAdapter`.
- Memperbaiki inisialisasi `ItemListWorkPlacementAdapter` di `AddEmployeeFormActivity.kt` pada baris 445 dan 1295 agar tidak memasukkan argumen list pada konstruktor yang tidak sesuai signature baru, melainkan disuplai secara dinamis melalui `submitList(...)` dan mengontrol shimmer via `setShimmer(false)`.
- Menambahkan layout empty state `llEmptyWorkPlacement` di `activity_add_employee_form.xml` dengan tampilan premium dashed-border yang seragam dengan empty state lainnya.
- Menyediakan string resource baru (`empty_state_work_placement_title` & `empty_state_work_placement_subtitle`) di `strings.xml`.
- Mengintegrasikan logika visibilitas empty state di dalam `updateWorkPlacementRecyclerView()` pada `AddEmployeeFormActivity.kt`.
- Memverifikasi keberhasilan kompilasi seluruh proyek menggunakan gradlew compileDebugKotlin.

### 📝 Keputusan Teknis
- Menggunakan parameter lambda pada `ItemManageEmployeeAdapter` untuk menjaga decoupling komponen UI.
- Mengintegrasikan deteksi status block UI untuk mencegah double click/tindakan ganda saat aplikasi sedang memproses request Firebase Firestore.
- Menyediakan inisialisasi awal adapter RecyclerView sebelum listener diset untuk menghindari runtime crash akibat `lateinit var` yang belum diinisialisasi.
- Mengimplementasikan empty state terpadu menggunakan dashed-border container demi konsistensi gaya visual UI/UX premium.

## [2026-06-21] - Refaktor Snapshot Listener & Integrasi Perizinan Baru di BarberLinkApp
### 🛠️ Aktivitas
- Membuat koleksi baru `permission_apps` di Firestore.
- Melakukan seeding data 5 hak akses dasar (`approval_bon`, `beranda_admin`, `dashboard_admin`, `manage_queue`, `manual_report`) lengkap dengan nama halaman, catatan detail perizinan, peran terkait, dan UID masing-masing.
- Membuat skrip utilitas `functions/seed_permission_apps.js` untuk mengotomatiskan proses penulisan batch data di masa mendatang.
- Menyesuaikan kelas data `PermissionItem` dengan menambahkan anotasi `@get:PropertyName` / `@set:PropertyName` untuk pemetaan properti Firestore, `@get:Exclude` / `@set:Exclude` untuk field status UI `isChecked`, serta menambahkan field `role` dan `uid`.
- Memindahkan pemantauan perizinan aplikasi di `BarberLinkApp.kt` dari snapshot listener dokumen `/official/barberlink2024` ke snapshot listener koleksi `permission_apps` yang baru dibuat.
- Mengubah alur penyimpanan di `BarberLinkApp.kt` agar melakukan serialisasi `PermissionItem` menjadi JSON String lalu menyimpannya dalam `Map<String, String>` ke `SessionManager.savePermissionList`.
- Menambahkan fungsi `getPermissionList(): List<PermissionItem>` pada `SessionManager.kt` untuk mengambil data `Map<String, String>` dari Shared Preferences dan mendeserialisasinya kembali menjadi daftar objek `PermissionItem`.

### 📝 Keputusan Teknis
- Dokumen `approval_bon` diatur menggunakan UID spesifik yang ditentukan (`sixQV5qrMaks8iPMO7T7`), sedangkan 4 dokumen lainnya menggunakan UID unik hasil auto-generate Firestore.
- Mengintegrasikan deskripsi hak akses dalam Bahasa Indonesia secara mendetail untuk mempermudah visualisasi pemetaan perizinan operasional barbershop.
- Mengubah properti `PermissionItem` menjadi `var` dengan nilai default agar mendukung no-argument constructor yang diwajibkan oleh Firestore SDK untuk operasi deserialisasi data (getting/setting).
- Memisahkan pemantauan perizinan ke dalam fungsi `setupPermissionAppsListener` untuk menjaga kerapian kode dan modularitas listener di level Application class.



## [2026-05-31] - Konversi dan Ekspor Aset Gambar ke Figma
### 🛠️ Aktivitas
- Berhasil mengekspor dan mengonversi aset gambar Android Vector Drawable (`R.drawable.ic_hair_cut`, `R.drawable.ic_face`, dan `R.drawable.ic_content_cut`) ke dalam format standar vector SVG (`.svg`).
- Menyimpan hasil konversi di direktori baru `c:\Users\Acer\StudioProjects\BarberLink\figma_assets\` untuk mempermudah integrasi atau drag-and-drop langsung ke Figma canvas oleh pengguna/desainer.
- Memastikan rendering warna (`#000000` untuk black dan `#00E676` untuk `green_role`) dan scaling transform dari file asli XML terjemah dengan sempurna ke SVG.

### 📝 Keputusan Teknis
- Menggunakan skrip Python kustom untuk melakukan parsing tag XML (`vector`, `group`, `path`) ke SVG standar (`svg`, `g`, `path`) untuk menjamin kepatuhan format Figma tanpa kehilangan detail koordinat atau ketebalan stroke.
- Dikarenakan integrasi Dev Mode MCP Server bersifat *read-only* dalam hal pengubahan canvas utama, penyediaan aset SVG mandiri di direktori lokal adalah pendekatan yang paling andal bagi alur kerja tim.

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
