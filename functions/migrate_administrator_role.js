const admin = require("firebase-admin");

if (admin.apps.length === 0) {
  admin.initializeApp({
    projectId: "barberlink-bfb66"
  });
}

const db = admin.firestore();

async function migrate() {
  console.log("🚀 Starting database migration: 'Administrator' -> 'Admin'");

  try {
    const batch = db.batch();
    let updatedCount = 0;

    // 1. Migrate roles collection
    console.log("Checking 'roles' collection...");
    const rolesSnapshot = await db.collection("roles").where("role_name", "==", "Administrator").get();
    for (const doc of rolesSnapshot.docs) {
      const data = doc.data();
      const currentJobDesc = data.job_desc || "";
      const updatedJobDesc = currentJobDesc.replace(/Administrator/g, "Admin");

      batch.update(doc.ref, {
        role_name: "Admin",
        job_desc: updatedJobDesc
      });
      console.log(`📌 Queued role update for doc: ${doc.id}`);
      updatedCount++;
    }

    // 2. Migrate employees collection
    console.log("Checking 'employees' collection...");
    const employeesSnapshot = await db.collection("employees").where("role", "==", "Administrator").get();
    for (const doc of employeesSnapshot.docs) {
      batch.update(doc.ref, {
        role: "Admin"
      });
      console.log(`📌 Queued employee role update for doc: ${doc.id}`);
      updatedCount++;
    }

    // 3. Migrate users collection
    console.log("Checking 'users' collection...");
    const usersSnapshot = await db.collection("users").where("role", "==", "Administrator").get();
    for (const doc of usersSnapshot.docs) {
      batch.update(doc.ref, {
        role: "Admin"
      });
      console.log(`📌 Queued user role update for doc: ${doc.id}`);
      updatedCount++;
    }

    if (updatedCount > 0) {
      await batch.commit();
      console.log(`✅ Success! Successfully migrated ${updatedCount} documents.`);
    } else {
      console.log("ℹ️ No documents found with role 'Administrator'. No changes applied.");
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
