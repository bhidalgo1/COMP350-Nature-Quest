# Nature Observation — Firebase Photo Posts

## What the app does

- **Post**: select/preview an image, add a title and optional description, publish to Firebase Storage + Cloud Firestore.
- **Feed**: automatically streams the newest 40 Firestore observation records and displays images from Firebase Storage.
- **Upload Only**: the original standalone photo-upload screen is still available.

Firebase Storage contains the actual image files; Firestore collection `observations` contains
`authorId`, `title`, `description`, `storagePath`, and `createdAt` (server time).
A record is written only after the image is uploaded. If Firestore fails, the new image is removed when possible.

## Firebase setup

1. Register Android application ID `com.example.natureobservation` in Firebase (or adjust it in app/build.gradle.kts).
2. Download the matching `google-services.json` from **Project settings > Your apps**, put it at `app/google-services.json`.
3. **Authentication > Sign-in method > Anonymous > Enable**.
4. Create a **Cloud Firestore** database; replace its rules with `firestore.rules` and publish.
5. Create a **Cloud Storage for Firebase** default bucket; replace Storage rules with `storage.rules` and publish.
   New Storage buckets may require a billing-enabled (Blaze) Firebase project.
6. In Android Studio open this project root (the directory with `settings.gradle.kts`), sync Gradle, and run.
   Internet access is required. The project includes newer SDK/plugin versions; install the requested
   Android SDK in SDK Manager if Android Studio asks.

**Security:** Anonymous users are signed in, so the supplied demo rules let any authenticated app user
view posts/images. For production, add moderation/reporting, appropriate access policy, and rate controls.
Both the Cloud Firestore and Storage rules must be published in Firebase Console;
copying `.rules` files into Android Studio by itself does not activate them.


