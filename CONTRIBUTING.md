# Contributing to Clinic System Mobile

## Development Setup

1. Clone the repository
2. Create `local.properties` with `sdk.dir=/path/to/Android/Sdk`
3. Run `./gradlew assembleDebug` to verify the build
4. For release builds, see [README.md](README.md) → "Required GitHub Secrets"

## Code Style

- **ktlint**: enforced via `./gradlew ktlintCheck`
- **detekt**: enforced via `./gradlew detekt`
- Both must pass before merging a PR
- **No `android.util.Log.*`** — use Timber (enforced by detekt `ForbiddenMethodCall` + `ForbiddenImport`)
- **No `kotlin.io.print/println`** — use Timber (enforced by detekt `ForbiddenMethodCall`)

## Testing

- All new code must have unit tests
- Run tests: `./gradlew testDebugUnitTest`
- Use mockk for mocking, Robolectric for Android-dependent tests
- Test naming: backtick descriptive names (`` `login with blank username sets authError` ``)
- Every bugfix PR should include a regression test

### Test Structure

```
src/test/
├── data/           # Repository, DAO, API tests
├── domain/         # UseCase, model tests
├── ui/viewmodel/   # ViewModel tests
└── utils/          # Utility class tests
```

## Pull Request Process

1. Create a feature branch from `main` (e.g. `fix/short-description` or `feat/short-description`)
2. Ensure `./gradlew ktlintCheck detekt testDebugUnitTest` passes locally
3. Create a PR with a clear description of changes
4. Request review from at least one maintainer
5. Squash-merge to `main` (linear history enforced)
6. Delete the feature branch after merge

### PR Description Format

```markdown
## What changed
Brief summary of the changes.

## Why
The problem being solved or the feature being added.

## How to test
Step-by-step smoke test checklist.

## Files changed
- `file1.kt` — what changed
- `file2.kt` — what changed
```

### Branch Protection

- `main` branch: PR required + CI must pass + linear history (squash-merge)
- No force pushes, no deletions
- Required status checks: `lint`, `unit-test`, `release-smoke`

## Commit Messages

Follow conventional commits:
```
<type>(<scope>): <subject>

<body>
```

### Types
- `feat` — new feature
- `fix` — bug fix
- `refactor` — code restructuring (no behavior change)
- `test` — test additions or changes
- `build` — build system changes
- `ci` — CI configuration changes
- `docs` — documentation only
- `chore` — maintenance tasks
- `security` — security fix

### Scopes
- `auth` — authentication / 2FA / biometric
- `ws` — WebSocket / realtime
- `dto` — data transfer objects
- `api` — API service / network layer
- `db` — Room database / migrations
- `staff` — staff screen / ViewModel
- `patient` — patient screen / ViewModel
- `ci` — CI/CD pipeline
- `i18n` — localization
- `detekt` — static analysis rules
- `logging` — Timber / log management

### Examples
```
fix(auth): P0-1 use biometric cipher to decrypt refresh token
refactor(staff): High-6 decompose StaffScreen from 1557 to 732 LOC
test(db): Medium-4 add migration tests for 7→8 and 8→9
docs: Medium-7 fix factual errors in README and RELEASE_NOTES
```

## Architecture

- **data/** — Room, Retrofit, repositories, outbox, realtime
- **domain/** — UseCases, domain models, repository interfaces
- **ui/** — Compose screens, ViewModels, navigation, theme
- **utils/** — Utilities (TokenManager, SyncWorker, NotificationHelper, etc.)

All dependencies flow through Hilt DI. No `getInstance()` singletons.
ViewModel → UseCase → Repository (Clean Architecture layering).

## Release Process

1. Update `RELEASE_NOTES.md` with the new version's changes
2. Tag with `v*.*.*` (e.g. `v1.1.0`) to trigger the release workflow
3. The release workflow:
   - Runs ktlint → detekt → unit tests
   - Assembles release APK + bundles release AAB (with signing secrets)
   - Verifies BuildConfig has no placeholder URLs
   - Uploads artifacts (APK, AAB, mapping.txt — 90-day retention)
   - Creates GitHub Release with auto-generated notes

### Required GitHub Secrets

See [README.md](README.md) → "Required GitHub Secrets" for the full list.

## Audit Trail

All audit findings are tracked in `docs/AUDIT_2026-07-10.md`. When fixing an
audit finding, reference the finding ID (e.g. `P0-1`, `High-3`, `Medium-5`)
in the commit message and PR title.
