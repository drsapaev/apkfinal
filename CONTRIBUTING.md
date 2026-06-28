# Contributing to Clinic System Mobile

## Development Setup

1. Clone the repository
2. Create `local.properties` with `sdk.dir=/path/to/Android/Sdk`
3. Run `./gradlew assembleDebug` to verify the build

## Code Style

- **ktlint**: enforced via `./gradlew ktlintCheck`
- **detekt**: enforced via `./gradlew detekt`
- Both must pass before merging a PR

## Testing

- All new code must have unit tests
- Run tests: `./gradlew testDebugUnitTest`
- Use mockk for mocking, Robolectric for Android-dependent tests

## Pull Request Process

1. Create a feature branch from `main`
2. Ensure `./gradlew ktlintCheck detekt testDebugUnitTest` passes
3. Create a PR with a clear description of changes
4. Squash-merge to `main` (linear history enforced)
5. For releases, tag with `v*.*.*` to trigger the release workflow

## Commit Messages

Follow conventional commits:
```
<type>(<scope>): <subject>

<body>
```

Types: `feat`, `fix`, `refactor`, `test`, `build`, `ci`, `docs`, `chore`, `security`

## Architecture

- **data/** — Room, Retrofit, repositories
- **domain/** — UseCases, domain models, repository interfaces
- **ui/** — Compose screens, ViewModels, navigation
- **utils/** — Utilities (TokenManager, SyncWorker, etc.)

All dependencies flow through Hilt DI. No `getInstance()` singletons.
