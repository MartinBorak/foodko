// Firestore security rules (firestore.rules) against the emulator. Spec §10: only allowlisted
// accounts get in, and each sees only its own subtree; the food database is shared.
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc } from "firebase/firestore";

const rules = readFileSync(new URL("../firestore.rules", import.meta.url), "utf8");

// FIRESTORE_EMULATOR_HOST is set by `firebase emulators:exec`; fall back to firebase.json's port.
const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080").split(":");

let env;

const alice = { uid: "alice-uid", email: "alice@example.com" };
const bob = { uid: "bob-uid", email: "bob@example.com" };
const stranger = { uid: "stranger-uid", email: "stranger@example.com" };

const asUser = (user, verified = true) =>
  env.authenticatedContext(user.uid, { email: user.email, email_verified: verified }).firestore();
const asNobody = () => env.unauthenticatedContext().firestore();

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-foodko-rules",
    firestore: { rules, host, port: Number(port) },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "allowlist", alice.email), { name: "Alice" });
    await setDoc(doc(db, "allowlist", bob.email), { name: "Bob" });
    await setDoc(doc(db, "foods", "skyr"), { label: "Skyr", labelLower: "skyr", kcalPer100g: 63 });
    await setDoc(doc(db, "users", bob.uid), { displayName: "Bob", dailyKcalTarget: 2200 });
    await setDoc(doc(db, "users", bob.uid, "meals", "m1"), { foodId: "skyr", grams: 150 });
    await setDoc(doc(db, "users", bob.uid, "weights", "w1"), { kg: 80 });
  });
});

describe("who gets in", () => {
  it("refuses anonymous reads", async () => {
    await assertFails(getDoc(doc(asNobody(), "foods", "skyr")));
  });

  it("refuses a signed-in account that is not on the allowlist", async () => {
    const db = asUser(stranger);
    await assertFails(getDoc(doc(db, "foods", "skyr")));
    await assertFails(getDocs(collection(db, "foods")));
    // The app detects refusal through its own user document: that must fail too.
    await assertFails(getDoc(doc(db, "users", stranger.uid)));
    await assertFails(setDoc(doc(db, "users", stranger.uid), { displayName: "Me" }));
  });

  it("refuses an allowlisted address whose e-mail is not verified", async () => {
    await assertFails(getDoc(doc(asUser(alice, false), "foods", "skyr")));
  });

  it("lets an allowlisted, verified account in", async () => {
    await assertSucceeds(getDoc(doc(asUser(alice), "foods", "skyr")));
  });
});

describe("the allowlist itself", () => {
  it("is invisible and read-only for everyone, including allowlisted users", async () => {
    const db = asUser(alice);
    await assertFails(getDoc(doc(db, "allowlist", alice.email)));
    await assertFails(getDocs(collection(db, "allowlist")));
    await assertFails(setDoc(doc(db, "allowlist", stranger.email), { name: "Friend" }));
    await assertFails(deleteDoc(doc(db, "allowlist", bob.email)));
  });
});

describe("shared foods", () => {
  it("can be read, created, edited and redirected by any allowlisted user", async () => {
    const db = asUser(alice);
    await assertSucceeds(getDocs(collection(db, "foods")));
    await assertSucceeds(setDoc(doc(db, "foods", "bread"), { label: "Bread", labelLower: "bread", kcalPer100g: 250, createdBy: alice.uid }));
    // Foods created by one user are editable by another (merge, corrections).
    await assertSucceeds(updateDoc(doc(asUser(bob), "foods", "bread"), { kcalPer100g: 247 }));
    await assertSucceeds(updateDoc(doc(asUser(bob), "foods", "skyr"), { mergedInto: "bread" }));
  });

  it("cannot be touched by outsiders", async () => {
    await assertFails(setDoc(doc(asUser(stranger), "foods", "x"), { label: "X" }));
    await assertFails(setDoc(doc(asNobody(), "foods", "x"), { label: "X" }));
  });
});

describe("private data", () => {
  it("lets a user read and write their own settings, meals and weights", async () => {
    const db = asUser(alice);
    await assertSucceeds(setDoc(doc(db, "users", alice.uid), { displayName: "Alice", language: "sk" }));
    await assertSucceeds(getDoc(doc(db, "users", alice.uid)));
    await assertSucceeds(setDoc(doc(db, "users", alice.uid, "meals", "m1"), { foodId: "skyr", grams: 200 }));
    await assertSucceeds(getDocs(collection(db, "users", alice.uid, "meals")));
    await assertSucceeds(setDoc(doc(db, "users", alice.uid, "weights", "w1"), { kg: 70 }));
    await assertSucceeds(deleteDoc(doc(db, "users", alice.uid, "weights", "w1")));
  });

  it("hides one user's meals, weights and settings from another allowlisted user", async () => {
    const db = asUser(alice);
    await assertFails(getDoc(doc(db, "users", bob.uid)));
    await assertFails(getDoc(doc(db, "users", bob.uid, "meals", "m1")));
    await assertFails(getDocs(collection(db, "users", bob.uid, "meals")));
    await assertFails(getDocs(collection(db, "users", bob.uid, "weights")));
    await assertFails(setDoc(doc(db, "users", bob.uid, "meals", "m2"), { foodId: "skyr", grams: 1 }));
    await assertFails(updateDoc(doc(db, "users", bob.uid), { dailyKcalTarget: 1 }));
    await assertFails(deleteDoc(doc(db, "users", bob.uid, "weights", "w1")));
  });

  it("hides everything from non-allowlisted and anonymous callers", async () => {
    await assertFails(getDocs(collection(asUser(stranger), "users", bob.uid, "meals")));
    await assertFails(getDocs(collection(asNobody(), "users", bob.uid, "meals")));
  });
});
