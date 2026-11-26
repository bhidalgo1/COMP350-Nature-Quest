// One-time migration for token links issued before protected photo downloads.
const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getStorage } = require('firebase-admin/storage');
async function main() {
  const [projectId, storageBucket] = process.argv.slice(2);
  if (!projectId || !storageBucket) throw new Error('Pass the project ID and bucket name.');
  initializeApp({ projectId, storageBucket, credential: applicationDefault() });
  const bucket = getStorage().bucket();
  let count = 0;
  for await (const file of bucket.getFilesStream({ prefix: 'observation_photos/' })) {
    const [metadata] = await file.getMetadata();
    if (!metadata.metadata?.firebaseStorageDownloadTokens) continue;
    await file.setMetadata({ metadata: { firebaseStorageDownloadTokens: null } },
      { ifMetagenerationMatch: metadata.metageneration });
    count++;
  }
  console.log('Revoked previous download links for', count, 'observation photos.');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });