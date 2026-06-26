const admin = require("firebase-admin");

// Initialize Firebase Admin SDK
if (admin.apps.length === 0) {
  admin.initializeApp({
    projectId: "barberlink-bfb66"
  });
}

const db = admin.firestore();

const permissions = [
  {
    permission_identity: "approval_bon",
    permission_name: "Akses Halaman Gateway Bon",
    permission_notes: "Mengelola, menambahkan, dan menghapus berbagai data yang berhubungan dengan permintaan bon yang diajukan oleh para pegawai barbershop.",
    permission_role: "admin",
    uid: "sixQV5qrMaks8iPMO7T7"
  },
  {
    permission_identity: "beranda_admin",
    permission_name: "Akses Halaman Beranda Admin",
    permission_notes: "Mengeola, menambahkan, dan menghapus berbagai data terkait barbershop seperti data layanan, data paket, data pegawai, data produk, hingga data outlet barbershop.",
    permission_role: "admin"
  },
  {
    permission_identity: "dashboard_admin",
    permission_name: "Akses Halaman Dashboard Admin",
    permission_notes: "Melihat dan memantau berbagai jenis laporan keuangan barbeshop baik dalam bentuk grafik performa, statistik transaksi, ataupun ringkasan data.",
    permission_role: "admin"
  },
  {
    permission_identity: "manage_queue",
    permission_name: "Akses Halaman Antrean (Queue Control)",
    permission_notes: "Mengatur dan mengelola antrean pelanggan, mengubah status layanannya, hingga melakukan pemantauan terhadap jumlah antrean yang ada secara realtime.",
    permission_role: "employee"
  },
  {
    permission_identity: "manual_report",
    permission_name: "Akses Halaman Laporan Manual",
    permission_notes: "Mengelola dan membuat data laporan transaksi keuangan harian dan operasional barbershop secara manual.",
    permission_role: "employee"
  }
];

async function seedPermissions() {
  console.log("🚀 Starting database seeding for 'permission_apps' collection...");
  const collectionRef = db.collection("permission_apps");
  const batch = db.batch();

  for (const perm of permissions) {
    let docRef;
    let finalUid;

    if (perm.uid) {
      // Use predefined UID
      docRef = collectionRef.doc(perm.uid);
      finalUid = perm.uid;
    } else {
      // Auto-generate document reference to get ID
      docRef = collectionRef.doc();
      finalUid = docRef.id;
    }

    const docData = {
      permission_identity: perm.permission_identity,
      permission_name: perm.permission_name,
      permission_notes: perm.permission_notes,
      permission_role: perm.permission_role,
      uid: finalUid
    };

    batch.set(docRef, docData);
    console.log(`📌 Queued: ${perm.permission_identity} -> Ref ID: ${finalUid}`);
  }

  try {
    await batch.commit();
    console.log("✅ Success! All permissions written to 'permission_apps' collection.");
  } catch (error) {
    console.error("❌ Failed to commit transaction batch:", error);
    process.exit(1);
  }
}

seedPermissions().then(() => {
  console.log("👋 Done!");
  process.exit(0);
});
