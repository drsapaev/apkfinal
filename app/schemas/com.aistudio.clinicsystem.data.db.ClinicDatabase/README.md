# Room Schema JSONs

Room with `exportSchema = true` generates schema JSON files in this directory
on every build. These files are used by `MigrationTestHelper` to write
migration tests.

## Current state

- `4.json` — committed (incomplete, missing column definitions)
- v5-v9 — **missing** (will be generated on next `./gradlew assembleDebug`)

## How to generate missing schemas

```bash
# Clean build — Room will emit schema JSON for the current DB version (v9)
./gradlew clean assembleDebug

# Commit generated schemas
git add app/schemas/
git commit -m "chore: generate Room schema JSONs for v5-v9"
```

## Why this matters

`MigrationTestHelper` requires schema JSONs for both the source and target
versions of each migration. Without v5-v9 JSONs, migration tests for
5→6, 6→7, 7→8, 8→9 cannot use `MigrationTestHelper.createFromAssets()` or
`MigrationTestHelper.runMigrationsAndValidate()`.

The tests in `MigrationTest.kt` currently test only 4→5, 5→6, 6→7 (the
v4.json is sufficient for these). Tests for 7→8 and 8→9 (added in PR #117)
use manual SQL verification instead of `MigrationTestHelper` as a workaround.
