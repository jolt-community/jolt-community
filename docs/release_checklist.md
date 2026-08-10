# Jolt Community — Release Checklist

1. Make sure `develop` is release-ready (features/fixes merged, CI green).
2. Run the **Pre-Release Sync** workflow (manual dispatch) → merges `develop` into `main`.
3. On `main`, bump the version in the different `pom.xml` files and commit/push. This ensures the tag/release created
   next points straight at the right commit.
4. Review/edit the auto-generated draft release notes (Release Drafter, on the Releases tab).
5. Create a GitHub Release on `main`, tagged `vX.Y.Z`. The tag now points at the version-bump commit from step 3.
6. Publish the release → **Release and Publish to Maven Central** workflow runs automatically.
7. Verify: workflow succeeded, artifacts show up on Maven Central under `io.github.jolt-community`, jars attached to 
   the GitHub Release.
8. Run the **Post-Release Sync** workflow (manual dispatch) → merges `main` back into `develop`.

Source: [jolt-community discussion #23](https://github.com/orgs/jolt-community/discussions/23) (adapted: version bump
moved before tag/release creation instead of after, to avoid deleting/recreating the tag)
