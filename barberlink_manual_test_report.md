# 📋 BarberLink Manual Test Report

Dokumen ini berisi catatan pengujian manual (Manual Test Report) aplikasi BarberLink yang mencakup skenario pengujian, langkah-langkah, hasil yang diharapkan, serta status pengujian untuk berbagai fitur dan role.

---

## 1. Registrasi, Login, Logout, dan Setup Perangkat Antrean
**Tabel 4.4 Pengujian Registrasi, Login, Logout, dan Setup Perangkat Antrean**

| No | Skenario Pengujian | Langkah Pengujian | Hasil yang Diharapkan | Status |
|:--:|:--- |:--- |:--- |:--:|
| 1 | Membuat akun owner/pegawai baru dengan data yang valid | Memasukkan data yang valid | Akun berhasil dibuat | Berhasil |
| 2 | Membuat akun owner/pegawai baru dengan field data kosong | Memasukkan data yang kosong | Sistem menampilkan error | Berhasil |
| 3 | Membuat akun owner/pegawai baru dengan nomor telepon yang terlalu pendek | Memasukkan nomor telepon seperti 12345 | Sistem menampilkan error | Berhasil |
| 4 | Membuat akun owner/pegawai baru dengan nomor telepon yang tidak valid | Memasukkan nomor telepon yang seluruhnya terdiri dari angka nol (0) | Sistem menampilkan error | Berhasil |
| 5 | Membuat akun owner/pegawai baru dengan nomor yang telah terdaftar | Memasukkan nomor telepon yang sudah terdaftar | Sistem menampilkan error | Berhasil |
| 6 | Membuat akun owner/pegawai baru dengan alamat email yang tidak valid | Memasukkan alamat email tanpa menyertakan simbol @ | Sistem menampilkan error | Berhasil |
| 7 | Membuat akun owner/pegawai baru dengan alamat email yang sudah terdaftar | Memasukkan alamat email yang sudah terdaftar | Sistem menampilkan error | Berhasil |
| 8 | Membuat akun owner/pegawai baru dengan password kurang dari 8 karakter | Memasukkan password seperti 12345 | Sistem menampilkan error | Berhasil |
| 9 | Membuat akun owner/pegawai baru dengan confirm password yang tidak sesuai | Memasukkan nilai confirm password yang tidak sesuai dengan password | Sistem menampilkan error | Berhasil |
| 10 | Membuat akun owner baru dengan nama barbershop yang sudah digunakan | Memasukkan nama barbershop yang sudah digunakan oleh orang lain | Sistem menampilkan error | Berhasil |
| 11 | Membuat akun pegawai baru dengan username yang sudah digunakan | Memasukkan username yang sudah digunakan oleh orang lain | Sistem menampilkan error | Berhasil |
| 12 | Login sebagai owner/pegawai dengan menggunakan data yang valid | Memasukkan alamat email & password yang valid | Berhasil masuk ke sistem | Berhasil |
| 13 | Login sebagai owner/pegawai dengan alamat email yang tidak terdaftar | Memasukkan alamat email yang belum pernah terdaftar | Sistem menampilkan error | Berhasil |
| 14 | Login sebagai owner/pegawai dengan password yang salah | Memasukkan password yang salah | Sistem menampilkan error | Berhasil |
| 15 | Login sebagai owner/pegawai dengan field data kosong | Memasukkan data yang kosong | Sistem menampilkan error | Berhasil |
| 16 | Login sebagai owner dengan akun pegawai | Memasukkan alamat email & password akun pegawai yang valid | Sistem menampilkan error | Berhasil |
| 17 | Login sebagai pegawai dengan akun owner | Memasukkan alamat email & password akun owner yang valid | Sistem menampilkan error | Berhasil |
| 18 | Logout account owner/pegawai | Mengakses halaman setting dan menekan tombol logout | User dibawa kembali ke halaman Login Page | Berhasil |
| 18 | Menampilkan seluruh daftar outlet yang tersedia saat akan menyiapkan perangkat antrean | Mengakses menu ambil antrean di halaman Select Role | Menampilkan seluruh daftar outlet yang tersedia | Berhasil |
| 19 | Menyimpan sesi perangkat antrean berdasarkan outlet tempat ia bekerja | Memilih card item outlet tempat ia bekerja | Menampilkan formulir kode akses | Berhasil |
| 20 | Berusaha masuk dengan kode akses outlet yang tidak valid | Memasukkan kode akses outlet yang tidak valid | Sistem menampilkan error | Berhasil |
| 21 | Berusaha masuk dengan kode akses outlet yang valid | Memasukkan kode akses outlet yang valid | Menampilkan halaman Queue Tracker | Berhasil |
| 22 | Menghapus sesi perangkat antrean yang tersimpan agar bisa beralih ke outlet lain | Menekan icon exit yang terletak di pojok kanan atas dan mengkonfirmasi permintaan keluar | User dibawa kembali ke halaman Select Outlet | Berhasil |

---

## 2. Fitur Owner/Admin
**Tabel 4.5 Pengujian Fitur Owner/Admin**

| No | Skenario Pengujian | Langkah Pengujian | Hasil yang Diharapkan | Status |
|:--:|:--- |:--- |:--- |:--:|
| 1 | Menampilkan seluruh data yang dimiliki barbershop (data layanan, data paket, data pegawai, dan data produk) | Mengakses halaman Beranda Admin | Menampilkan semua data yang sesuai | Berhasil |
| 2 | Menambahkan data outlet dengan data yang valid | Mengisi semua field dengan data yang valid | Data outlet berhasil ditambahkan | Berhasil |
| 3 | Menambahkan data outlet dengan salah satu field (nama outlet, nomor telepon, tagline, alamat, titik koordinat, daftar layanan, daftar paket, daftar pegawai, daftar produk) dibiarkan tetap kosong | Membiarkan salah satu field data tetap kosong | Sistem menolak karena terdapat field data yang kosong | Berhasil |
| 4 | Menambahkan data outlet dengan nama outlet yang sudah pernah digunakan | Mengisi field nama outlet dengan nama outlet yang sudah pernah digunakan | Sistem menolak karena nama outlet sudah ada | Berhasil |
| 5 | Menambahkan data outlet dengan nomor telepon yang terlalu pendek | Memasukkan nomor telepon seperti 12345 | Sistem menolak karena nomor telepon terlalu pendek | Berhasil |
| 6 | Menampilkan seluruh data outlet yang baru saja ditambahkan | Memilih card item outlet yang baru saja ditambahkan | Sistem menampilkan detail seluruh informasi data outlet terkait | Berhasil |
| 7 | Memperbarui data outlet dengan data yang valid | Memperbarui semua field dengan data yang valid | Data outlet berhasil diperbarui | Berhasil |
| 8 | Memperbarui data outlet dengan salah satu field (nama outlet, nomor telepon, tagline, alamat, titik koordinat, daftar layanan, daftar paket, daftar pegawai, daftar produk) diisi dengan data kosong | Memperbarui salah satu field dengan data kosong | Sistem menolak karena terdapat field data yang kosong | Berhasil |
| 9 | Memperbarui data outlet dengan nama outlet yang sudah pernah digunakan | Memperbarui field nama outlet dengan nama outlet yang sudah pernah digunakan | Sistem menolak karena nama outlet sudah ada | Berhasil |
| 10 | Memperbarui data outlet dengan nomor telepon yang terlalu pendek | Memasukkan nomor telepon seperti 12345 | Sistem menolak karena nomor telepon terlalu pendek | Berhasil |
| 11 | Menghapus data outlet | Swipe item | Data outlet terkait berhasil dihapus | Berhasil |
| 12 | Menambahkan data layanan dengan data yang valid | Mengisi semua field dengan data yang valid | Data layanan berhasil ditambahkan | Berhasil |
| 13 | Menambahkan data layanan dengan salah satu field (nama layanan, kategori layanan, deskripsi layanan, harga layanan) dibiarkan tetap kosong | Membiarkan salah satu field data tetap kosong | Sistem menolak karena terdapat field data yang kosong | Berhasil |
| 14 | Menambahkan data layanan dengan nama layanan yang sudah pernah digunakan | Mengisi field nama layanan dengan nama layanan yang sudah pernah digunakan | Sistem menolak karena nama layanan sudah ada | Berhasil |
| 15 | Menambahkan data layanan dengan harga layanan yang diatur dengan nilai nol rupiah | Mengisi field harga layanan dengan nilai 0 | Sistem menolak karena harga layanan harus diisi dengan angka yang lebih besar dari nol | Berhasil |
| 16 | Menampilkan seluruh data layanan yang baru saja ditambahkan | Memilih card item layanan yang baru saja ditambahkan | Sistem menampilkan detail seluruh informasi data layanan terkait | Berhasil |
| 17 | Memperbarui data layanan dengan data yang valid | Memperbarui semua field dengan data yang valid | Data layanan berhasil diperbarui | Berhasil |
| 18 | Memperbarui data layanan dengan salah satu field (nama layanan, kategori layanan, deskripsi layanan, harga layanan) diisi dengan data kosong | Memperbarui salah satu field dengan data kosong | Sistem menolak karena terdapat field data yang kosong | Berhasil |
| 19 | Memperbarui data layanan dengan nama layanan yang sudah pernah digunakan | Memperbarui field nama layanan dengan nama layanan yang sudah pernah digunakan | Sistem menolak karena nama layanan sudah ada | Berhasil |
| 20 | Memperbarui data layanan dengan harga layanan yang diubah dengan nilai nol rupiah | Memperbarui field harga layanan dengan nilai 0 | Sistem menolak karena harga layanan harus diisi dengan angka yang lebih besar dari nol | Berhasil |
| 21 | Menghapus data layanan | Swipe item | Data layanan terkait berhasil dihapus | Berhasil |
| 22 | Menambahkan data paket dengan data yang valid | Mengisi semua field dengan data yang valid | Data paket berhasil ditambahkan | Berhasil |
| 23 | Menambahkan data paket dengan salah satu field (nama paket, deskripsi paket, detail item layanan paket, harga paket) dibiarkan tetap kosong | Membiarkan salah satu field data tetap kosong | Sistem menolak karena terdapat field data yang kosong | Berhasil |
| 24 | Menambahkan data paket dengan nama paket yang sudah pernah digunakan | Mengisi field nama paket dengan nama paket yang sudah pernah digunakan | Sistem menolak karena nama paket sudah ada | Berhasil |
| 25 | Menambahkan data paket dengan harga diskon yang diatur dengan nilai nol rupiah saat fitur diskon paket diaktifkan | Mengisi field harga diskon dengan nilai 0 | Sistem menolak karena harga diskon harus diisi dengan angka yang lebih besar dari nol saat fitur diskon paket diaktifkan | Berhasil |
| 26 | Menampilkan seluruh data paket yang baru saja ditambahkan | Memilih card item paket yang baru saja ditambahkan | Sistem menampilkan detail seluruh informasi data paket terkait | Berhasil |
| 27 | Memperbarui data paket dengan data yang valid | Memperbarui semua field dengan data yang valid | Data paket berhasil diperbarui | Berhasil |
| 28 | Memperbarui data paket dengan salah satu field (nama paket, deskripsi paket, detail item layanan paket, harga paket) diisi dengan data kosong | Memperbarui salah satu field dengan data kosong | Sistem menolak karena terdapat field data yang kosong | Berhasil |
| 29 | Memperbarui data paket dengan nama paket yang sudah pernah digunakan | Memperbarui field nama paket dengan nama paket yang sudah pernah digunakan | Sistem menolak karena nama paket sudah ada | Berhasil |
| 30 | Memperbarui data paket dengan harga diskon yang diatur dengan nilai nol rupiah saat fitur diskon paket diaktifkan | Memperbarui field harga diskon dengan nilai 0 | Sistem menolak karena harga diskon harus diisi dengan angka yang lebih besar dari nol saat fitur diskon paket diaktifkan | Berhasil |
| 31 | Menghapus data paket | Swipe item | Data paket terkait berhasil dihapus | Berhasil |
| 32 | Menambahkan data produk dengan data yang valid | Mengisi semua field dengan data yang valid | Data produk berhasil ditambahkan | Berhasil |
| 33 | Menambahkan data produk dengan salah satu field (nama produk, kategori produk, ukuran produk, SKU produk, barcode produk, harga beli produk, harga jual produk, deskripsi produk) dibiarkan tetap kosong | Membiarkan salah satu field data tetap kosong | Sistem menolak karena terdapat field data yang kosong | Berhasil |
| 34 | Menambahkan data produk dengan nama produk yang sudah pernah digunakan | Mengisi field nama produk dengan nama produk yang sudah pernah digunakan | Sistem menolak karena nama produk sudah ada | Berhasil |
| 35 | Menambahkan data produk dengan SKU produk yang sudah pernah digunakan | Mengisi field SKU produk dengan SKU produk yang sudah pernah digunakan | Sistem menolak karena SKU produk sudah ada | Berhasil |
| 36 | Menambahkan data produk dengan harga jual yang lebih rendah dari harga beli | Mengisi field harga jual produk lebih rendah dari harga belinya | Sistem menolak karena harga jual produk harus lebih tinggi dari harga belinya | Berhasil |
| 37 | Menampilkan seluruh data produk yang baru saja ditambahkan | Memilih card item produk yang baru saja ditambahkan | Sistem menampilkan detail seluruh informasi data produk terkait | Berhasil |
| 38 | Memperbarui data produk dengan data yang valid | Memperbarui semua field dengan data yang valid | Data produk berhasil diperbarui | Berhasil |
| 39 | Memperbarui data produk dengan salah satu field (nama produk, kategori produk, ukuran produk, SKU produk, barcode produk, harga beli produk, harga jual produk, deskripsi produk) diisi dengan data kosong | Memperbarui salah satu field dengan data kosong | Sistem menolak karena terdapat field data yang kosong | Berhasil |
| 40 | Memperbarui data produk dengan nama produk yang sudah pernah digunakan | Memperbarui field nama produk dengan nama produk yang sudah pernah digunakan | Sistem menolak karena nama produk sudah ada | Berhasil |
| 41 | Memperbarui data produk dengan SKU produk yang sudah pernah digunakan | Memperbarui field SKU produk dengan SKU produk yang sudah pernah digunakan | Sistem menolak karena SKU produk sudah ada | Berhasil |
| 42 | Memperbarui data produk dengan harga jual yang lebih rendah dari harga beli | Memperbarui field harga jual produk lebih rendah dari harga belinya | Sistem menolak karena harga jual produk harus lebih tinggi dari harga belinya | Berhasil |
| 43 | Menghapus data produk | Swipe item | Data produk terkait berhasil dihapus | Berhasil |
| 44 | Menambahkan data pegawai dengan username pegawai yang valid | Mengisi semua field dengan data yang valid | Data pegawai berhasil ditambahkan | Berhasil |
| 45 | Menambahkan data pegawai dengan salah satu field (gaji pegawai atau daftar penempatan pegawai) dibiarkan tetap kosong | Membiarkan salah satu field data tetap kosong | Sistem menolak karena terdapat field data yang kosong | Berhasil |
| 46 | Menambahkan data pegawai dengan nominal gaji yang diatur dengan nilai lebih dari 2 miliar rupiah | Mengisi field gaji pegawai dengan nominal gaji yang lebih besar dari 2 miliar rupiah | Sistem menolak karena nominal gaji terlalu besar | Berhasil |
| 47 | Menampilkan seluruh data pegawai yang baru saja ditambahkan | Memilih card item pegawai yang baru saja ditambahkan | Sistem menampilkan detail seluruh informasi data pegawai terkait | Berhasil |
| 48 | Memperbarui data pegawai dengan data yang valid | Memperbarui semua field dengan data yang valid | Data pegawai berhasil diperbarui | Berhasil |
| 49 | Memperbarui data pegawai dengan salah satu field (gaji pegawai atau daftar penempatan pegawai) diisi dengan data kosong | Memperbarui salah satu field dengan data kosong | Sistem menolak karena terdapat field data yang kosong | Berhasil |
| 50 | Memperbarui data pegawai dengan nominal gaji yang diatur dengan nilai lebih dari 2 miliar rupiah | Memperbarui field gaji pegawai dengan nominal gaji yang lebih besar dari 2 miliar rupiah | Sistem menolak karena nominal gaji terlalu besar | Berhasil |
| 51 | Menghapus data pegawai | Swipe item | Data pegawai terkait berhasil dihapus | Berhasil |
| 52 | Menampilkan data laporan keuangan dari barbershop | Mengakses halaman Dashboard Admin | Menampilkan semua data yang sesuai | Berhasil |
| 53 | Memfilter laporan keuangan berdasarkan outlet tertentu | Mengubah filtering data berdasarkan nama outlet tertentu | Menampilkan semua data yang sesuai | Berhasil |
| 54 | Memfilter laporan keuangan barbershop berdasarkan bulan tertentu | Mengubah filtering data berdasarkan bulan tertentu | Menampilkan semua data yang sesuai | Berhasil |
| 55 | Memfilter laporan keuangan barbershop berdasarkan tanggal tertentu | Mengaktifkan mode filtering data harian dan memilih tanggal yang diinginkan | Menampilkan semua data yang sesuai | Berhasil |

---

## 3. Fitur Pegawai
**Tabel 4.6 Pengujian Fitur Pegawai**

| No | Skenario Pengujian | Langkah Pengujian | Hasil yang Diharapkan | Status |
|:--:|:--- |:--- |:--- |:--:|
| 1 | Menampilkan seluruh data pendapatan pegawai | Mengakses halaman Homepage Pegawai | Menampilkan semua data yang sesuai | Berhasil |
| 2 | Menampilkan seluruh daftar antrean pelanggan barbershop | Mengakses halaman Queue Control | Menampilkan semua data yang sesuai | Berhasil |
| 3 | Mengubah status antrean dari *waiting* ke *process* | Menekan tombol do it/process | Status antrean berubah | Berhasil |
| 4 | Mengubah status antrean dari *process* ke *complete* | Menekan tombol complete | Status antrean berubah | Berhasil |
| 5 | Mengubah status antrean dari *process* ke *skipped* | Menekan tombol skipped | Status antrean berubah | Berhasil |
| 6 | Mengubah status antrean dari *process* ke *canceled* | Menekan tombol canceled | Status antrean berubah | Berhasil |
| 7 | Mengantrekan kembali antrean yang berstatus *canceled* | Menekan tombol requeue | Status antrean kembali bernilai *waiting* | Berhasil |
| 8 | Mengantrekan kembali antrean yang berstatus *skipped* | Menekan tombol requeue | Status antrean kembali bernilai *waiting* | Berhasil |
| 9 | Membatalkan perubahan status antrean yang baru saja dilakukan | Menekan tombol undo pada snackbar card yang muncul | Status antrean dikembalikan ke nilai sebelumnya | Berhasil |
| 10 | Menampilkan pop-up switch kapster untuk mengalihkan antrean ke kapster lain | Menekan tombol alihkan antrean yang ada di halaman Queue Control | Pop-up switch kapster berhasil ditampilkan | Berhasil |
| 11 | Memilih nama kapster yang akan dijadikan sebagai tujuan pengalihan antrean | Menekan dan memilih dropdown daftar kapster yang tersedia | Hanya menampilkan daftar kapster yang tersedia | Berhasil |
| 12 | Mengalihkan antrean ke kapster lain | Memilih kapster yang diinginkan dan menekan tombol simpan perubahan | Antrean dialihkan dan dihilangkan dari daftar antrean milik pegawai saat ini | Berhasil |
| 13 | Membatalkan aktivitas pengalihan antrean ke kapster lain yang baru saja dilakukan | Menekan tombol undo pada snackbar card yang muncul | Antrean dikembalikan dan dimasukkan kembali ke daftar antrean milik pegawai saat ini | Berhasil |
| 14 | Menampilkan bottom sheet edit pesanan untuk memperbarui pesanan pelanggan jika diperlukan | Menekan tombol edit pesanan yang ada di halaman Queue Control | Bottom sheet edit pesanan berhasil ditampilkan | Berhasil |
| 15 | Memperbarui daftar pesanan pelanggan | Menekan tombol plus or minus | Data pesanan berhasil diperbarui | Berhasil |
| 16 | Memperbarui metode pembayaran pelanggan | Mengubah jenis metode pembayaran yang digunakan oleh pelanggan | Data pesanan berhasil diperbarui | Berhasil |

---

## 4. Fitur Ambil Antrean
**Tabel 4.7 Pengujian Fitur Ambil Antrean**

| No | Skenario Pengujian | Langkah Pengujian | Hasil yang Diharapkan | Status |
|:--:|:--- |:--- |:--- |:--:|
| 1 | Menampilkan data antrean yang sesuai pada halaman Queue Tracker berdasarkan tanggal tertentu | Menekan black card button tanggal di bagian atas dan memilih tanggal tertentu yang diinginkan | Menampilkan pop up kalender dan menerapkan filtering data antrean yang sesuai setelah user menentukan tanggal yang diinginkan | Berhasil |
| 2 | Menampilkan data antrean yang sesuai pada halaman Queue Tracker berdasarkan nama kapster tertentu | Menekan dan memilih dropdown daftar kapster yang tersedia | Menerapkan filtering data antrean yang sesuai setelah user menentukan nama kapster yang diinginkan | Berhasil |
| 3 | Mengambil nomor antrean dengan spesifik kapster tertentu | Memilih card item kapster yang diinginkan pada halaman Queue Tracker dan mengisi seluruh data yang dibutuhkan untuk membuat pesanan | Nomor antrean baru berhasil dibuat disertai dengan data identitas kapster yang dipilih | Berhasil |
| 4 | Mengambil nomor antrean dengan skema random kapster | Menekan floating action button random kapster di pojok kanan bawah halaman Queue Tracker dan mengisi seluruh data yang dibutuhkan untuk membuat pesanan | Nomor antrean baru berhasil dibuat tanpa disertai dengan data identitas kapster | Berhasil |
| 5 | Mengambil nomor antrean dengan mengisi formulir pemesanan tanpa menyertakan data layanan yang ingin dipesan | Membiarkan bagian daftar layanan yang ingin dipesan tetap kosong | Sistem menolak karena data pesanan baru harus menyertakan layanan yang akan dipesan | Berhasil |
| 6 | Mengambil nomor antrean dengan mengisi formulir pemesanan tanpa menyertakan data pelanggan | Membiarkan bagian informasi data pelanggan tetap kosong | Sistem menolak karena data pesanan baru harus menyertakan informasi data pelanggan | Berhasil |
