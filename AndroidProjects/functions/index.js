const {onRequest} = require("firebase-functions/v2/https");
const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore} = require("firebase-admin/firestore");
const {getStorage} = require("firebase-admin/storage");
const {pipeline} = require("node:stream/promises");

initializeApp();

// Check both block records on each download. Keep private media off public token URLs.
exports.readObservationPhoto = onRequest({region: "us-central1", invoker: "public"}, async (req, res) => {
  res.set("Cache-Control", "private, no-store");
  if (req.method !== "GET") return res.sendStatus(405);
  const token = /^Bearer (\S+)$/.exec(req.get("Authorization") || "");
  if (!token) return res.sendStatus(401);
  let uid;
  try {
    uid = (await getAuth().verifyIdToken(token[1])).uid;
  } catch {
    return res.sendStatus(401);
  }
  const path = req.query.path;
  const match = typeof path === "string" && /^observation_photos\/([^/]+)\/([\w.-]+)$/.exec(path);
  if (!match || match[1] === "." || match[1] === "..") return res.sendStatus(400);
  const owner = match[1];
  try {
    if (owner !== uid) {
      const db = getFirestore();
      const refs = [[owner, uid], [uid, owner]].map(([a, b]) =>
        db.collection("social").doc(a).collection("blocks").doc(b));
      const blocks = await db.getAll(...refs);
      if (blocks.some(block => block.exists)) return res.sendStatus(403);
    }
    const file = getStorage().bucket().file(path);
    const [metadata] = await file.getMetadata();
    res.type(metadata.contentType || "application/octet-stream");
    await pipeline(file.createReadStream(), res);
  } catch (error) {
    if (!res.headersSent) return res.sendStatus(Number(error.code) === 404 ? 404 : 503);
    res.destroy();
  }
});