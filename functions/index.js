const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onRequest } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Returns the Firestore Timestamp for the start of "today" in the
 * Asia/Jakarta timezone (UTC+7).
 *
 * Example: if called at 2026-05-18T10:00:00Z (17:00 WIB),
 *   → Jakarta date = "2026-05-18"
 *   → Jakarta midnight = 2026-05-18T00:00:00+07:00 = 2026-05-17T17:00:00Z
 */
function getTodayMidnightJakarta() {
  const JAKARTA_OFFSET_MS = 7 * 60 * 60 * 1000; // UTC+7
  const now = new Date();

  // Shift "now" to Jakarta local time, then extract the date string "YYYY-MM-DD"
  const jakartaLocalDate = new Date(now.getTime() + JAKARTA_OFFSET_MS);
  const dateStr = jakartaLocalDate.toISOString().slice(0, 10); // e.g. "2026-05-18"
  const [year, month, day] = dateStr.split("-").map(Number);

  // Build midnight in Jakarta = that date at 00:00 WIB = subtract 7h from UTC midnight
  const midnightUtc = Date.UTC(year, month - 1, day); // 2026-05-18T00:00:00Z
  const midnightJakarta = new Date(midnightUtc - JAKARTA_OFFSET_MS); // 2026-05-17T17:00:00Z

  return {
    timestamp: admin.firestore.Timestamp.fromDate(midnightJakarta),
    jakartaDateStr: dateStr,
    utcIso: midnightJakarta.toISOString(),
  };
}

/**
 * Commits a Firestore batch and resets it.
 */
async function commitBatch(batch, db) {
  await batch.commit();
  return db.batch();
}

// ─────────────────────────────────────────────────────────────────────────────
// Scheduled: runs at 00:01 every day, Jakarta time
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Marks reservations as expired and handles stuck "process" reservations.
 *
 * RULE 1 — waiting + past date → is_expired_reservation: true
 * RULE 2 — process + past date → is_expired_reservation: true + queue_status: "waiting"
 *           (the barber session was never closed, reset it so tellers can see it)
 *
 * Uses composite index on (queue_status ASC, timestamp_to_booking ASC)
 * COLLECTION_GROUP scope for efficient querying.
 *
 * Index: projects/barberlink-bfb66/databases/(default)/collectionGroups/reservations/indexes/CICAgJjmtZUK
 */
exports.checkExpiredReservations = onSchedule(
  {
    schedule: "1 0 * * *",
    timeZone: "Asia/Jakarta",
  },
  async (event) => {
    const db = admin.firestore();
    const { timestamp: todayTimestamp, jakartaDateStr, utcIso } = getTodayMidnightJakarta();

    logger.info(
      `checkExpiredReservations started. Jakarta date: ${jakartaDateStr}, ` +
        `threshold (UTC): ${utcIso}`
    );

    try {
      const BATCH_SIZE = 500;
      let totalUpdatedWaiting = 0;
      let totalUpdatedProcess = 0;

      // ── RULE 1: "waiting" + past date → expired ─────────────────────────────
      const waitingSnapshot = await db
        .collectionGroup("reservations")
        .where("queue_status", "==", "waiting")
        .where("timestamp_to_booking", "<", todayTimestamp)
        .get();

      if (!waitingSnapshot.empty) {
        let batch = db.batch();
        let pendingWrites = 0;

        for (const doc of waitingSnapshot.docs) {
          const data = doc.data();
          if (data.is_expired_reservation === true) continue; // already correct

          batch.update(doc.ref, { is_expired_reservation: true });
          pendingWrites++;
          totalUpdatedWaiting++;

          logger.info(
            `[waiting→expired] ${doc.ref.path} ` +
              `(booking: ${data.timestamp_to_booking?.toDate().toISOString()})`
          );

          if (pendingWrites === BATCH_SIZE) {
            batch = await commitBatch(batch, db);
            pendingWrites = 0;
          }
        }
        if (pendingWrites > 0) await batch.commit();
      }

      // ── RULE 2: "process" + past date → expired + reset to "waiting" ────────
      // Reason: the barber opened the session but never closed it.
      // Reset queue_status to "waiting" so tellers/admin can see and re-handle it.
      const processSnapshot = await db
        .collectionGroup("reservations")
        .where("queue_status", "==", "process")
        .where("timestamp_to_booking", "<", todayTimestamp)
        .get();

      if (!processSnapshot.empty) {
        let batch = db.batch();
        let pendingWrites = 0;

        for (const doc of processSnapshot.docs) {
          const data = doc.data();

          // Update both fields regardless of current is_expired_reservation value
          // because queue_status also needs to be reset
          const alreadyCorrect =
            data.is_expired_reservation === true && data.queue_status === "waiting";
          if (alreadyCorrect) continue;

          batch.update(doc.ref, {
            is_expired_reservation: true,
            queue_status: "waiting",
          });
          pendingWrites++;
          totalUpdatedProcess++;

          logger.info(
            `[process→expired+waiting] ${doc.ref.path} ` +
              `(booking: ${data.timestamp_to_booking?.toDate().toISOString()})`
          );

          if (pendingWrites === BATCH_SIZE) {
            batch = await commitBatch(batch, db);
            pendingWrites = 0;
          }
        }
        if (pendingWrites > 0) await batch.commit();
      }

      logger.info(
        `checkExpiredReservations finished. ` +
          `waiting→expired: ${totalUpdatedWaiting}, ` +
          `process→expired+reset: ${totalUpdatedProcess}`
      );
    } catch (error) {
      logger.error("Error in checkExpiredReservations:", error);
      throw error; // Re-throw so Firebase marks the run as failed
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// HTTP: one-time migration / manual trigger
// ─────────────────────────────────────────────────────────────────────────────
/**
 * One-time migration function to initialize (or re-sync) the
 * is_expired_reservation field for ALL reservations.
 *
 * Call with: GET /migrateReservations
 *
 * PASS 1 — "waiting" + past date → is_expired_reservation: true
 * PASS 2 — "process" + past date → is_expired_reservation: true + queue_status: "waiting"
 * PASS 3 — Reset incorrectly-marked documents back to false:
 *           • completed, cancelled, skipped → always false (terminal states, never "expired")
 *           • waiting / process with FUTURE date → false (not yet expired)
 */
exports.migrateReservations = onRequest(async (req, res) => {
  const db = admin.firestore();
  const { timestamp: todayTimestamp, jakartaDateStr, utcIso } = getTodayMidnightJakarta();

  logger.info(
    `migrateReservations started. Jakarta date: ${jakartaDateStr}, ` +
      `threshold (UTC): ${utcIso}`
  );

  try {
    const BATCH_SIZE = 500;
    let totalExpiredWaiting = 0;
    let totalExpiredProcess = 0;
    let totalReset = 0;

    // ── PASS 1: Mark all expired "waiting" reservations ─────────────────────
    const waitingExpiredSnap = await db
      .collectionGroup("reservations")
      .where("queue_status", "==", "waiting")
      .where("timestamp_to_booking", "<", todayTimestamp)
      .get();

    if (!waitingExpiredSnap.empty) {
      let batch = db.batch();
      let pendingWrites = 0;

      for (const doc of waitingExpiredSnap.docs) {
        const data = doc.data();
        if (data.is_expired_reservation === true) continue;

        batch.update(doc.ref, { is_expired_reservation: true });
        pendingWrites++;
        totalExpiredWaiting++;

        if (pendingWrites === BATCH_SIZE) {
          batch = await commitBatch(batch, db);
          pendingWrites = 0;
        }
      }
      if (pendingWrites > 0) await batch.commit();
    }

    // ── PASS 2: Fix stuck "process" reservations with past booking date ──────
    const processExpiredSnap = await db
      .collectionGroup("reservations")
      .where("queue_status", "==", "process")
      .where("timestamp_to_booking", "<", todayTimestamp)
      .get();

    if (!processExpiredSnap.empty) {
      let batch = db.batch();
      let pendingWrites = 0;

      for (const doc of processExpiredSnap.docs) {
        const data = doc.data();
        const alreadyCorrect =
          data.is_expired_reservation === true && data.queue_status === "waiting";
        if (alreadyCorrect) continue;

        batch.update(doc.ref, {
          is_expired_reservation: true,
          queue_status: "waiting",
        });
        pendingWrites++;
        totalExpiredProcess++;

        logger.info(`[PASS 2] process→expired+waiting: ${doc.ref.path}`);

        if (pendingWrites === BATCH_SIZE) {
          batch = await commitBatch(batch, db);
          pendingWrites = 0;
        }
      }
      if (pendingWrites > 0) await batch.commit();
    }

    // ── PASS 3: Reset incorrectly-marked documents ───────────────────────────
    // Terminal statuses that should NEVER be expired:
    //   - completed : successfully served, not "abandoned"
    //   - cancelled : explicitly cancelled, not "abandoned"
    //   - skipped   : skipped by teller/admin, not "abandoned"
    // Also reset active reservations with future booking dates.
    const wronglyExpiredSnap = await db
      .collectionGroup("reservations")
      .where("is_expired_reservation", "==", true)
      .get();

    if (!wronglyExpiredSnap.empty) {
      let batch = db.batch();
      let pendingWrites = 0;

      // Terminal statuses that must never carry is_expired_reservation: true
      const TERMINAL_STATUSES = new Set(["completed", "cancelled", "skipped"]);

      for (const doc of wronglyExpiredSnap.docs) {
        const data = doc.data();
        const status = data.queue_status;

        // Case A: terminal status → always false
        const isTerminal = TERMINAL_STATUSES.has(status);

        // Case B: still "waiting" or "process" but booking date is in the future → false
        const isFutureActive =
          (status === "waiting" || status === "process") &&
          data.timestamp_to_booking != null &&
          data.timestamp_to_booking.toMillis() >= todayTimestamp.toMillis();

        if (!isTerminal && !isFutureActive) continue; // correctly marked, skip

        batch.update(doc.ref, { is_expired_reservation: false });
        pendingWrites++;
        totalReset++;

        logger.info(
          `[PASS 3] Reset to false: ${doc.ref.path} ` +
            `(status: ${status}, reason: ${isTerminal ? "terminal" : "future date"})`
        );

        if (pendingWrites === BATCH_SIZE) {
          batch = await commitBatch(batch, db);
          pendingWrites = 0;
        }
      }
      if (pendingWrites > 0) await batch.commit();
    }

    const msg =
      `Migration complete. ` +
      `waiting→expired: ${totalExpiredWaiting}, ` +
      `process→expired+reset: ${totalExpiredProcess}, ` +
      `reset to false: ${totalReset}.`;
    logger.info(msg);
    res.status(200).send(msg);
  } catch (error) {
    logger.error("Error during migration:", error);
    res.status(500).send(`Internal Server Error: ${error.message}`);
  }
});
