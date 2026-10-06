# Photo upload user story

As a user, I want to upload a photo so that I can include a picture with my nature observation.

## Acceptance criteria

- A user can select an image with the Android photo picker and see a preview.
- Upload Photo sends the image to Firebase Storage under the signed-in user's UID.
- Progress is visible and a success message appears only after Firebase confirms upload.
- The last uploaded object path is available for integration with an observation record.
- Failures show an error and the selected image remains available for retry.
- Images over 10 MiB are rejected, and storage rules protect each user's folder.

## Manual verification after Firebase setup

1. Choose a photo, upload it, and confirm the object exists in the Firebase console.
2. Close and reopen the app; confirm the last preview and Firebase path are displayed.
3. Upload a second photo; confirm its unique path and successful transfer.
4. Select a photo over 10 MiB; confirm rejection and no new object in Storage.
5. Disconnect internet and try uploading; confirm a failure after timeout, reconnect,
   and retry successfully. Buttons should stay disabled during an active upload.
6. In the Storage rules simulator, confirm an unauthenticated request and a request
   for another UID's photo are denied; confirm an owner's valid image write succeeds.
7. Cancel the photo picker; confirm the current selection stays unchanged.

These checks must be performed against your Firebase project. They have not been
marked complete automatically.
