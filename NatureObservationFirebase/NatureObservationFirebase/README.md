# Nature Observation — Firebase Photo Upload

As a user, I want to upload a photo so that I can include a picture with my nature observation.

Choose a photo with Android's photo picker, preview it, then tap **Upload Photo**.
The app signs in anonymously and uploads the actual image to Firebase Storage.
A progress bar displays transferred bytes. After success, the app remembers the
Firebase object path and keeps a local preview for the next launch. Images must
be no larger than 10 MiB. No broad photo-library permission is needed.