# 📘 BarberLink Instructions & PRD

Dokumen ini adalah "Kebenaran Tunggal" (Single Source of Truth) untuk fitur dan aturan komunikasi.

## 🇮🇩 Aturan Komunikasi (Wajib)
1. **Bahasa Indonesia**: Gunakan Bahasa Indonesia yang profesional dan mudah dimengerti untuk semua interaksi dengan USER.
2. **Komentar Kode**: Tetap gunakan Bahasa Inggris di dalam kode (nama variabel, fungsi, komentar teknis) agar standar global, namun keterangan log atau error message untuk user boleh dwibahasa.
3. **Pemeliharaan Riwayat**: Setiap kali sebuah pekerjaan berhasil diselesaikan atau dilakukan tindakan **Undo**, Anda WAJIB memperbarui file `barberlink_history.md` dengan detail aktivitas tersebut.

## 🛠️ Product Requirement Document (PRD)

### 1. Manajemen Outlet (Kelola Toko)
- Admin/Owner dapat menambah outlet baru.
- Penentuan lokasi menggunakan Maps Picker (Titik Koordinat).
- Kode Akses Kasir dibuat per outlet.

### 2. Manajemen SDM (Pegawai)
- Role: Owner/Admin, Kasir/Teller, Pegawai/Capster.
- Pengaturan gaji pokok, komisi (jasa & produk), dan rating.
- Sinkronisasi akun antar role.

### 3. Layanan & POS (Point of Sale)
- Daftar Layanan & Paket Bundling dengan harga dinamis (tergantung Capster atau General).
- Sistem Antrian: Waiting, Active, Completed, Canceled, Skipped.
- Pembayaran: Cash & QRIS.
- Fitur "Random Capster" dengan estimasi biaya otomatis.

### 4. Keuangan & Operasional
- Pencatatan Kasbon (Employee Bon) dengan sistem persetujuan (Gateway).
- Laporan pamasukan/pengeluaran manual (Manual Report).
- Slip Gaji otomatis berdasarkan akumulasi komisi dan potongan kasbon.

## 🎨 Standar Estetika
- Menggunakan **Material Design 3**.
- Palet Warna: Dark Grey (#1A1A1A) & Lime Green (#8FD14F).
- Tipografi: Inter/Outfit (Sesuai aset font yang tersedia).

---
*Dokumen ini harus diperbarui jika ada permintaan fitur baru atau perubahan kebijakan aplikasi.*
