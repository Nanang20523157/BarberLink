const admin = require("firebase-admin");

if (admin.apps.length === 0) {
  admin.initializeApp({
    projectId: "barberlink-bfb66"
  });
}

const db = admin.firestore();

async function migrate() {
  console.log("🚀 Starting database migration: adding 'hex_color' field to roles collection");

  try {
    const batch = db.batch();
    let updatedCount = 0;

    const rolesSnapshot = await db.collection("roles").get();
    for (const doc of rolesSnapshot.docs) {
      const data = doc.data();
      if (data.hex_color === undefined) {
        batch.update(doc.ref, {
          hex_color: ""
        });
        console.log(`📌 Queued role update to add hex_color for doc: ${doc.id} (${data.role_name})`);
        updatedCount++;
      }
    }

    if (updatedCount > 0) {
      await batch.commit();
      console.log(`✅ Success! Added 'hex_color' to ${updatedCount} documents.`);
    } else {
      console.log("ℹ️ All roles already have the 'hex_color' field. No changes applied.");
    }
  } catch (error) {
    console.error("❌ Failed migration:", error);
    process.exit(1);
  }
}

migrate().then(() => {
  console.log("👋 Done!");
  process.exit(0);
});
