// Run once with Application Default Credentials for the selected project.
const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

async function main() {
  const projectId = process.argv[2];
  if (!projectId) throw new Error('Pass the Firebase project ID.');
  initializeApp({ projectId, credential: applicationDefault() });
  const db = getFirestore();
  const seen = new Set();
  let created = 0;
  for await (const post of db.collection('observations').select('authorId').stream()) {
    const uid = post.get('authorId');
    if (typeof uid !== 'string' || !uid || uid.includes('/') || seen.has(uid)) continue;
    seen.add(uid);
    const ref = db.collection('publicProfiles').doc(uid);
    if ((await ref.get()).exists) continue;
    const user = await db.collection('users').doc(uid).get();
    try {
      await ref.create({ username: user.get('username') || 'Nature explorer' });
      created++;
    } catch (error) {
      if (error.code !== 6) throw error; // Another writer created this profile first.
    }
  }
  console.log('Created', created, 'missing author profiles in', projectId);
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });