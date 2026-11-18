# Post filters integration

This integrates the Post-Filters feature into the real AndroidProjects app on the
integrate-post-filters branch. It is based on the uploaded post-filters-integration.zip.

## Changes

- Home hamburger menu > Filter posts opens a category selector using the existing
  Compose navigation graph and app theme. No separate activity or module is needed.
- Categories retain the teammate's Plant, Mammal, Bird, Reptile, and Insect options.
- Upload adds Category (optional). Uncategorized is the initial value so new posts
  are not silently labelled Plant. The selected category is stored as organism.
- The model's new organism field has an empty default, so older posts remain readable.
- Filter results reuse the current PhotoFeedScreen cards: map links, photo aspect
  ratio/orientation, descriptions with Show more/less, likes, comments, and deletion.
- One live Firestore whereIn query handles all selected categories. It retrieves
  matching observations and sorts them newest first locally. It does not download
  the whole collection once per category. Live updates remove deleted posts without
  rebuilding them from stale category lists during composition.
- The normal feed keeps its existing latest-40 query. Filter results are not capped
  at those same 40 posts; they currently load all matching observations. Pagination
  should be added if the dataset grows large.
- Readable query errors remain visible instead of becoming a misleading empty result.
- The duplicated NQ Filters files are removed from THIS integration branch. The
  teammate's original Post-Filters branch and commit history are unaffected.
- No Gradle dependency, manifest activity, Firebase rule, or deployed setting changes.

## Apply

Stay on integrate-post-filters with a clean working tree. Save any independent local
edits before applying. From the repository root:

```bash
unzip -o ~/Downloads/post-filters-integrated.zip -d ~/Downloads/post-filters-integrated
git apply --check ~/Downloads/post-filters-integrated/integrate-post-filters.patch
git apply ~/Downloads/post-filters-integrated/integrate-post-filters.patch
```

Run the check separately. If it reports an error, stop and retain the exact output.
It checks the source snapshot before modifying files, including the duplicate files
that will be removed. The commands do not stage, commit, push, or change branches.

Sync Gradle in Android Studio and run the existing AndroidProjects app configuration.
Do not open NQ Filters as a separate app. No Firebase changes are needed if organism
is still ALLOWED in observations.hasOnly and OPTIONAL in observations.hasAll, as
in the recently adjusted shared rules. Keep it optional for older app versions.

## Test on the emulator

1. Home menu > Filter posts must open within the app you already run.
2. Create a Plant photo post, a Mammal photo post, and one Uncategorized post.
3. Select Plant and Apply: only Plant-tagged observations should appear.
4. Return to filters, add Mammal, and Apply: both categories should appear, with
   no duplicate posts and a combined newest-first order.
5. Back to filters should retain selections. Clear filters should uncheck all and
   disable Apply until at least one category is selected.
6. Delete your own post from the filtered results. It should stay removed when
   another like/comment updates, and after navigating away and back.
7. Check normal home feed, liked posts, map links, camera, upload rotation/zoom,
   long descriptions, and appearance settings.
8. Uncategorized/legacy posts should still appear in the regular feed; they do
   not match any of the five category filters. Old posts are not auto-classified.
9. Verify the saved Firestore organism value matches the selected category. After
   posting, the next draft should start with Uncategorized again.

The single-field organism filter normally uses Firestore's existing automatic
indexing. If an index or permission error appears, retain its exact text. No index
or rule has been deployed by this patch.

## Profile-Expansion work

Your avatar/username/profile code and unpushed commits remain on Profile-Expansion.
They were not in this uploaded branch snapshot and are not included here. Once the
filters are verified, merge the branches deliberately and resolve the overlapping
menu/feed/repository edits together. Do not overwrite either branch's files wholesale.

## Validation

Source changes and call sites were inspected. The patch was applied to a clean
copy of the uploaded snapshot, all output bytes were compared against the intended
result, and reversal was checked, including the duplicated module's binary assets.
Kotlin source delimiter/import checks were run. Android compilation and Firebase/
emulator behavior are not yet verified: this environment cannot download the
project's Gradle distribution. Build and complete the checks above before publishing.
