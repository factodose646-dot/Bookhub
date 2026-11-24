const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_001";
const CLAIM_ADMIN_UID = "claim_admin_002";
const USER_UID = "reader_uid_003";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedAdminAndCatalog({ includeDraft = true } = {}) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const past = new Date(Date.now() - 10000);

    await db.collection("admins").doc(ADMIN_UID).set({
      email: "admin@bookhub.example",
      role: "admin",
      active: true,
      createdAt: past,
    });

    await db.collection("categories").doc("science_knowledge").set({
      name: "Science & Knowledge",
      shortName: "Science &\nKnowledge",
      description: "Physics and discoveries",
      icon: "science",
      color: 0xff1b76a8,
      secondaryColor: 0xff0a324d,
      sortOrder: 1,
      createdAt: past,
      updatedAt: past,
    });

    await db.collection("books").doc("book_pub_1").set({
      title: "Quantum Light",
      author: "Dr. Vance",
      description: "Explores photonics.",
      categoryId: "science_knowledge",
      coverUrl: "https://drive.usercontent.google.com/download?id=cov1&export=view",
      driveFileId: "drv_pdf_1",
      pdfUrl: "https://drive.usercontent.google.com/download?id=drv_pdf_1&export=download",
      fileSize: "2.4 MB",
      fileSizeBytes: 2516582,
      pageCount: 180,
      language: "English",
      publishedYear: 2026,
      addedOrder: 1,
      dateAdded: "Nov 24, 2026",
      featured: true,
      popular: true,
      recent: true,
      published: true,
      createdByUid: ADMIN_UID,
      updatedByUid: ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    });

    if (includeDraft) {
      await db.collection("books").doc("book_draft_2").set({
        title: "Hidden Draft",
        author: "Editor",
        categoryId: "science_knowledge",
        fileSize: "1.1 MB",
        fileSizeBytes: 1153433,
        language: "English",
        featured: false,
        popular: false,
        recent: true,
        published: false,
        createdByUid: ADMIN_UID,
        updatedByUid: ADMIN_UID,
        createdAt: past,
        updatedAt: past,
      });
    }
  });
}

test("Readers & guests: can read categories and published books, blocked from unpublished books and unfiltered queries", async () => {
  await seedAdminAndCatalog({ includeDraft: true });

  const guestDb = testEnv.unauthenticatedContext().firestore();
  const userDb = testEnv.authenticatedContext(USER_UID).firestore();

  // Categories readable
  await assertSucceeds(guestDb.collection("categories").get());
  await assertSucceeds(userDb.collection("categories").doc("science_knowledge").get());

  // Published book readable via get & filtered list query
  await assertSucceeds(guestDb.collection("books").doc("book_pub_1").get());
  await assertSucceeds(
    guestDb.collection("books").where("published", "==", true).get()
  );
  await assertSucceeds(
    userDb.collection("books").where("published", "==", true).get()
  );

  // Unpublished book rejected for guests & normal signed-in users
  await assertFails(guestDb.collection("books").doc("book_draft_2").get());
  await assertFails(userDb.collection("books").doc("book_draft_2").get());

  // Unfiltered list query (without published == true) rejected for non-admins
  await assertFails(guestDb.collection("books").get());
  await assertFails(userDb.collection("books").get());
});

test("Normal users & guests: blocked from mutating categories, books, and self-escalating in /admins", async () => {
  await seedAdminAndCatalog();
  const past = new Date(Date.now() - 2000);
  const userDb = testEnv.authenticatedContext(USER_UID).firestore();

  // Cannot write categories
  await assertFails(
    userDb.collection("categories").doc("hacked_cat").set({
      name: "Hacked",
      icon: "other",
      color: 0xff000000,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Cannot create or update books
  await assertFails(
    userDb.collection("books").doc("book_user_1").set({
      title: "Fake Book",
      author: "Mallory",
      categoryId: "science_knowledge",
      fileSize: "1.0 MB",
      fileSizeBytes: 1000,
      language: "English",
      featured: false,
      popular: false,
      recent: true,
      published: true,
      createdByUid: USER_UID,
      updatedByUid: USER_UID,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    userDb.collection("books").doc("book_pub_1").update({ title: "Hijacked" })
  );
  await assertFails(
    userDb.collection("books").doc("book_pub_1").delete()
  );

  // Cannot self-assign admin role or list admins
  await assertFails(
    userDb.collection("admins").doc(USER_UID).set({ role: "admin", active: true })
  );
  await assertFails(userDb.collection("admins").get());
  await assertFails(userDb.collection("admins").doc(ADMIN_UID).get());
  // Own non-existent admin doc check is allowed for get (returns doesNotExist)
  await assertSucceeds(userDb.collection("admins").doc(USER_UID).get());
});

test("Verified admin (/admins doc & custom claim): can read drafts, create/update/toggle-publish/delete books & categories", async () => {
  await seedAdminAndCatalog({ includeDraft: true });
  const past = new Date(Date.now() - 2000);

  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const claimAdminDb = testEnv
    .authenticatedContext(CLAIM_ADMIN_UID, { admin: true })
    .firestore();

  // Admin can read unpublished draft & unfiltered books list
  await assertSucceeds(adminDb.collection("books").doc("book_draft_2").get());
  await assertSucceeds(adminDb.collection("books").get());
  await assertSucceeds(claimAdminDb.collection("books").get());

  // Admin can create a category
  await assertSucceeds(
    adminDb.collection("categories").doc("cat_design").set({
      name: "Design Systems",
      shortName: "Design\nSystems",
      description: "Visual grids",
      icon: "tech",
      color: 0xff4b56b2,
      secondaryColor: 0xff1c2252,
      sortOrder: 8,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Admin can create a book in that category (no @DocumentId field in map)
  await assertSucceeds(
    adminDb.collection("books").doc("book_admin_01").set({
      title: "Grid Blueprint",
      author: "K. Muller",
      description: "PDF manual",
      categoryId: "cat_design",
      driveFileId: "drv_999",
      pdfUrl: "https://drive.usercontent.google.com/download?id=drv_999&export=download",
      fileSize: "3.2 MB",
      fileSizeBytes: 3355443,
      pageCount: 64,
      language: "English",
      featured: false,
      popular: false,
      recent: true,
      published: true,
      createdByUid: ADMIN_UID,
      updatedByUid: ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Partial update: toggle published -> false while preserving createdAt/createdByUid
  const later = new Date(Date.now() - 500);
  await assertSucceeds(
    adminDb.collection("books").doc("book_admin_01").update({
      published: false,
      updatedByUid: ADMIN_UID,
      updatedAt: later,
    })
  );

  // Claim admin can update and delete
  await assertSucceeds(
    claimAdminDb.collection("books").doc("book_admin_01").update({
      title: "Grid Blueprint (2nd Ed)",
      updatedByUid: CLAIM_ADMIN_UID,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertSucceeds(adminDb.collection("books").doc("book_admin_01").delete());
  await assertSucceeds(adminDb.collection("categories").doc("cat_design").delete());
});

test("Validation guards: reject ghost fields, @DocumentId id key, future timestamps, orphan categoryId, and immutable mutation", async () => {
  await seedAdminAndCatalog({ includeDraft: false });
  const past = new Date(Date.now() - 5000);
  const future = new Date(Date.now() + 60000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  // Ghost field rejected
  await assertFails(
    adminDb.collection("books").doc("book_ghost").set({
      title: "Ghost Book",
      author: "Author",
      categoryId: "science_knowledge",
      fileSize: "1 MB",
      fileSizeBytes: 1024,
      language: "English",
      featured: false,
      popular: false,
      recent: true,
      published: true,
      createdByUid: ADMIN_UID,
      updatedByUid: ADMIN_UID,
      createdAt: past,
      updatedAt: past,
      secretAdminFlag: true,
    })
  );

  // @DocumentId property key ("bookId" or "id") inside document body rejected
  await assertFails(
    adminDb.collection("books").doc("book_id_trap").set({
      id: "book_id_trap",
      title: "ID Trap",
      author: "Author",
      categoryId: "science_knowledge",
      fileSize: "1 MB",
      fileSizeBytes: 1024,
      language: "English",
      featured: false,
      popular: false,
      recent: true,
      published: true,
      createdByUid: ADMIN_UID,
      updatedByUid: ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Future timestamp rejected
  await assertFails(
    adminDb.collection("books").doc("book_future").set({
      title: "Future Book",
      author: "Author",
      categoryId: "science_knowledge",
      fileSize: "1 MB",
      fileSizeBytes: 1024,
      language: "English",
      featured: false,
      popular: false,
      recent: true,
      published: true,
      createdByUid: ADMIN_UID,
      updatedByUid: ADMIN_UID,
      createdAt: future,
      updatedAt: future,
    })
  );

  // Non-existent categoryId rejected (orphan guard)
  await assertFails(
    adminDb.collection("books").doc("book_orphan").set({
      title: "Orphan Book",
      author: "Author",
      categoryId: "missing_cat",
      fileSize: "1 MB",
      fileSizeBytes: 1024,
      language: "English",
      featured: false,
      popular: false,
      recent: true,
      published: true,
      createdByUid: ADMIN_UID,
      updatedByUid: ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Immutable createdAt mutation rejected on update
  await assertFails(
    adminDb.collection("books").doc("book_pub_1").update({
      createdAt: new Date(Date.now() - 100),
      updatedByUid: ADMIN_UID,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Deactivated admin doc loses write privileges immediately
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("admins").doc(ADMIN_UID).update({
      active: false,
    });
  });
  await assertFails(
    adminDb.collection("books").doc("book_pub_1").update({
      title: "Should Fail",
      updatedByUid: ADMIN_UID,
      updatedAt: new Date(Date.now() - 50),
    })
  );
});
