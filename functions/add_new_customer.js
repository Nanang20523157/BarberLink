const admin = require("firebase-admin");
const readline = require("readline");

// Initialize Firebase Admin SDK
// By default, it will look for GOOGLE_APPLICATION_CREDENTIALS or local firebase credentials.
if (admin.apps.length === 0) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Parses the raw text block containing "Informasi Dasar" and returns an array of records.
 * @param {string} text 
 */
function parseRawInput(text) {
  const lines = text.split(/\r?\n/);
  const records = [];
  let currentRecord = null;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    // Check if line starts with a number followed by '='
    const eqIdx = trimmed.indexOf("=");
    if (eqIdx === -1) continue;

    const key = trimmed.slice(0, eqIdx).trim();
    const val = trimmed.slice(eqIdx + 1).trim();

    // If key is '1', it marks the start of a new record block
    if (key === "1") {
      if (currentRecord && Object.keys(currentRecord).length > 0) {
        records.push(currentRecord);
      }
      currentRecord = {};
    }

    if (currentRecord) {
      currentRecord[key] = val;
    }
  }

  if (currentRecord && Object.keys(currentRecord).length > 0) {
    records.push(currentRecord);
  }

  return records;
}

/**
 * Main execution function to add the parsed customers to Firestore.
 * @param {Array<Object>} records 
 */
async function addCustomersToFirestore(records) {
  if (records.length === 0) {
    console.log("⚠️ No valid records found to add.");
    return;
  }

  console.log(`🚀 Processing ${records.length} customer records...`);

  for (let i = 0; i < records.length; i++) {
    const record = records[i];
    const phone = record["1"];
    const email = record["2"];
    const uid = record["3"];
    const fullname = record["4"];
    const gender = record["5"];
    const username = record["6"];
    const password = record["7"];
    const photo_profile = record["8"] || "";

    if (!phone || !email || !uid || !fullname || !gender || !username || !password) {
      console.warn(`⚠️ Record #${i + 1} is missing required fields. Skipping. Got:`, record);
      continue;
    }

    console.log(`\n-----------------------------------`);
    console.log(`👤 Processing Record #${i + 1}: ${fullname}`);
    console.log(`   Email: ${email}`);
    console.log(`   Phone: ${phone}`);
    console.log(`   UID:   ${uid}`);

    try {
      const batch = db.batch();

      // 1. Add to customers collection
      const customerRef = db.collection("customers").doc(uid);
      batch.set(customerRef, {
        email: email,
        fullname: fullname,
        gender: gender,
        membership: false,
        password: password,
        phone: phone,
        photo_profile: photo_profile,
        uid: uid,
        user_coins: 0,
        user_notification: null,
        user_reminder: null,
        username: username
      });

      // 2. Add to users collection
      const userRef = db.collection("users").doc(phone);
      batch.set(userRef, {
        admin_provider: "",
        admin_ref: "",
        customer_provider: "email",
        customer_ref: `customers/${uid}`,
        employee_provider: "",
        employee_ref: "",
        role: "customer",
        uid: phone
      });

      // 3. Add username to official list
      const officialRef = db.collection("official").doc("barberlink2024");
      batch.update(officialRef, {
        username_list: admin.firestore.FieldValue.arrayUnion(username)
      });

      await batch.commit();
      console.log(`   ✅ Success! Documents created and username added to official list:`);
      console.log(`      - customers/${uid}`);
      console.log(`      - users/${phone}`);
      console.log(`      - official/barberlink2024 (username_list += '${username}')`);
    } catch (err) {
      console.error(`   ❌ Failed to add record #${i + 1}:`, err.message);
    }
  }
}

// Check how the script is run
async function main() {
  const args = process.argv.slice(2);

  if (args.length > 0) {
    // If user passed arguments, treat it as direct text or a file
    const fs = require("fs");
    let inputText = "";

    if (fs.existsSync(args[0])) {
      inputText = fs.readFileSync(args[0], "utf8");
      console.log(`📖 Reading input from file: ${args[0]}`);
    } else {
      inputText = args.join(" ");
      console.log(`📖 Parsing input from command line argument...`);
    }

    const records = parseRawInput(inputText);
    await addCustomersToFirestore(records);
    process.exit(0);
  } else {
    // Read from standard input (stdin)
    console.log("📝 Paste the 'Informasi Dasar' block below.");
    console.log("   (Press Ctrl+D on Unix/Mac or Ctrl+Z on Windows then Enter when finished):\n");

    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
      terminal: false
    });

    let inputText = "";
    rl.on("line", (line) => {
      inputText += line + "\n";
    });

    rl.on("close", async () => {
      console.log("\n📥 Input received. Processing...");
      const records = parseRawInput(inputText);
      await addCustomersToFirestore(records);
      process.exit(0);
    });
  }
}

main();
