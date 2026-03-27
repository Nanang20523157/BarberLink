const admin = require("firebase-admin");

// Note: This script assumes it's being run in an environment where 
// firebase-admin can authenticate (e.g. via GOOGLE_APPLICATION_CREDENTIALS 
// or if run on a machine already logged into firebase)
// Since I'm an agent, I'll try to use the default app if possible.

if (admin.apps.length === 0) {
  admin.initializeApp();
}

const db = admin.firestore();

async function migrate() {
  const today = new Date();
  today.setUTCHours(0, 0, 0, 0);
  const todayTimestamp = admin.firestore.Timestamp.fromDate(today);

  console.log(`Starting migration for date: ${today.toISOString()}`);

  try {
    const reservationsSnapshot = await db.collectionGroup("reservations").get();
    
    if (reservationsSnapshot.empty) {
      console.log("No reservations found.");
      return;
    }

    const batchSize = 500;
    let currentBatch = db.batch();
    let count = 0;
    let totalProcessed = 0;

    for (const doc of reservationsSnapshot.docs) {
      const data = doc.data();
      let isExpired = false;

      if (data.queue_status === "waiting") {
        const timestampToBooking = data.timestamp_to_booking;
        isExpired = timestampToBooking && timestampToBooking.toMillis() < todayTimestamp.toMillis();
      } else {
        isExpired = false;
      }

      currentBatch.update(doc.ref, { is_expired_reservation: isExpired });
      count++;
      totalProcessed++;

      if (count === batchSize) {
        await currentBatch.commit();
        currentBatch = db.batch();
        count = 0;
        console.log(`Processed ${totalProcessed} documents...`);
      }
    }

    if (count > 0) {
      await currentBatch.commit();
    }

    console.log(`Successfully migrated ${totalProcessed} reservations.`);
  } catch (error) {
    console.error("Error during migration:", error);
  }
}

migrate();
