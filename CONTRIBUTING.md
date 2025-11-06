# Contributing to Keycloak MaxMind Extension

Thank you for your interest in contributing! This document provides guidelines and workflows for developing, testing, and releasing this extension.

## Table of Contents

- [Development Workflow](#development-workflow)
- [Branching Strategy](#branching-strategy)
- [Commit Message Conventions](#commit-message-conventions)
- [Building and Testing](#building-and-testing)
- [CI/CD Process](#cicd-process)
- [Release Process](#release-process)
- [Pull Request Guidelines](#pull-request-guidelines)

## Development Workflow

### Prerequisites

- **Java JDK 17+**: Required for building and running the extension
- **Maven 3.6+**: Build tool
- **Docker & Docker Compose**: For local Keycloak environment
- **Git**: Version control
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java support

### Initial Setup

1. **Fork and Clone**
   ```bash
   git clone https://github.com/YOUR-USERNAME/keycloak-maxmind.git
   cd keycloak-maxmind
   ```

2. **Add Upstream Remote**
   ```bash
   git remote add upstream https://github.com/zymlabs/keycloak-maxmind.git
   ```

3. **Build the Project**
   ```bash
   mvn clean package
   ```

4. **Start Local Environment**
   ```bash
   docker-compose up
   ```

   Access Keycloak at http://localhost:8080 (admin/admin)

## Branching Strategy

We follow **GitFlow** with semantic versioning powered by GitVersion.

### Branch Types

#### `master` - Production Branch
- Contains stable, production-ready code
- All releases are tagged from this branch
- Version tags: `1.0.0`, `1.1.0`, `2.0.0` (no `v` prefix)
- Protected branch - requires pull request approval

#### `develop` - Integration Branch
- Main development branch
- All features merge here first
- Automatically creates pre-release builds (`1.0.0-alpha.5`)
- Never commit directly - use feature branches

#### `feature/*` - Feature Branches
- Branch from: `develop`
- Merge to: `develop`
- Naming: `feature/short-description`
- Examples:
  - `feature/add-geolocation-support`
  - `feature/admin-ui-integration`
  - `feature/webhook-notifications`

#### `hotfix/*` - Hotfix Branches
- Branch from: `master`
- Merge to: `master` AND `develop`
- Naming: `hotfix/issue-description`
- For critical production fixes only
- Examples:
  - `hotfix/api-timeout-crash`
  - `hotfix/database-connection-leak`

#### `release/*` - Release Branches (Optional)
- Branch from: `develop`
- Merge to: `master` AND `develop`
- Naming: `release/1.2.0`
- For final release preparation (docs, version bumps)
- Creates beta builds (`1.2.0-beta.1`)

### Branch Workflow Examples

**Starting a New Feature**
```bash
# Update develop branch
git checkout develop
git pull upstream develop

# Create feature branch
git checkout -b feature/device-fingerprinting

# Work on your feature
# ... make changes ...

# Commit with conventional commits (see below)
git add .
git commit -m "feat: add device fingerprinting support"

# Push to your fork
git push origin feature/device-fingerprinting

# Open pull request to upstream/develop
```

**Creating a Hotfix**
```bash
# Start from master
git checkout master
git pull upstream master

# Create hotfix branch
git checkout -b hotfix/api-connection-timeout

# Fix the issue
# ... make changes ...

# Commit
git commit -m "fix: increase MaxMind API connection timeout"

# Push and create PR to master
git push origin hotfix/api-connection-timeout

# After merge to master, also merge to develop
git checkout develop
git pull upstream master
git push upstream develop
```

## Commit Message Conventions

We use **Conventional Commits** to enable automated versioning and changelog generation.

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- **feat**: New feature (bumps MINOR version: 1.0.0 → 1.1.0)
- **fix**: Bug fix (bumps PATCH version: 1.0.0 → 1.0.1)
- **docs**: Documentation changes only
- **style**: Code style changes (formatting, missing semicolons, etc.)
- **refactor**: Code refactoring (no functional changes)
- **perf**: Performance improvements
- **test**: Adding or updating tests
- **chore**: Maintenance tasks (dependencies, build config)
- **ci**: CI/CD pipeline changes

### Breaking Changes

Add `BREAKING CHANGE:` in the footer or `!` after type to bump MAJOR version:

```
feat!: redesign authenticator configuration API

BREAKING CHANGE: Configuration structure changed from flat map to nested config object.
Administrators must reconfigure the authenticator after upgrade.
```

### Examples

**Good Commits:**
```bash
feat(auth): add support for MaxMind Insights service level
fix(api): handle null response from MaxMind API gracefully
docs(readme): update installation instructions for Keycloak 26
test(service): add integration tests for timeout handling
chore(deps): upgrade MaxMind SDK to 1.17.0
```

**Bad Commits:**
```bash
updated stuff          # Too vague, no type
Fix bug               # Wrong capitalization, no description
Added new feature     # No type, not imperative mood
WIP                   # Work in progress - don't commit yet
```

### Scope Examples

Optional but recommended:
- `auth`: Authenticator logic
- `service`: MaxMind service wrapper
- `db`: Database/entity changes
- `config`: Configuration handling
- `ui`: Admin UI changes
- `events`: Event logging
- `api`: API interactions
- `docs`: Documentation

## Building and Testing

### Build Commands

```bash
# Full build with tests
mvn clean package

# Build without tests (faster)
mvn clean package -DskipTests

# Run tests only
mvn test

# Run specific test class
mvn test -Dtest=RiskEvaluationTest

# Run specific test method
mvn test -Dtest=RiskEvaluationTest#testLowRiskScore

# Verbose test output
mvn test -X

# Install to local Maven repository
mvn clean install
```

### Output

- **JAR Location**: `target/zymlabs-maxmind-provider.jar`
- **Test Reports**: `target/surefire-reports/`

### Test Coverage

We aim for high test coverage on core logic:

- **RiskEvaluationTest**: Risk scoring, threshold logic, fail modes
- **MaxMindMinFraudServiceTest**: API integration, error handling
- **ConfigurationParsingTest**: Configuration validation

### Running Integration Tests Locally

```bash
# Start local Keycloak environment
docker-compose up -d

# Build and deploy extension
mvn clean package
docker-compose restart keycloak

# Manually test authentication flows
# Access: http://localhost:8080
# Credentials: admin/admin
```

## CI/CD Process

### Automated Workflows

Our GitHub Actions workflow (`.github/workflows/ci.yml`) automatically handles building, testing, and releasing.

#### On Every Push/PR (All Branches)

**Job: `build-and-test`**
1. ✅ Checkout code with full git history
2. ✅ Install GitVersion and calculate semantic version
3. ✅ Set up Java 17 with Maven caching
4. ✅ Update `pom.xml` with GitVersion-calculated version
5. ✅ Run all unit tests: `mvn clean test`
6. ✅ Build JAR: `mvn package -DskipTests`
7. ✅ Upload artifacts (JAR + test reports)

**Result**: Ensures all code passes tests before merging

#### On Push to `develop` Branch

**Job: `develop-release`** (after `build-and-test` succeeds)
1. 📦 Calculate pre-release version (e.g., `1.2.0-alpha.7`)
2. 📦 Build JAR: `zymlabs-maxmind-provider-1.2.0-alpha.7.jar`
3. 📦 Create GitHub pre-release:
   - Tag: `1.2.0-alpha.7`
   - Title: "Development Build 1.2.0-alpha.7"
   - Marked as pre-release
   - Includes commit SHA and branch info
4. 🗑️ Delete old pre-releases (keeps last 5)

**Result**: Automatic pre-release builds for testing

#### On Version Tag Push (e.g., `1.0.0`)

**Job: `tagged-release`** (after `build-and-test` succeeds)
1. 🎉 Validate tag matches GitVersion calculation
2. 🎉 Build production JAR: `zymlabs-maxmind-provider-1.0.0.jar`
3. 🎉 Create GitHub stable release:
   - Tag: `1.0.0`
   - Title: "Release 1.0.0"
   - Marked as stable release
   - Auto-generated release notes
   - Installation instructions

**Result**: Production-ready release artifact

### GitVersion Configuration

Our `GitVersion.yml` configures semantic versioning:

| Branch Type | Version Format | Example |
|-------------|----------------|---------|
| `master` | `MAJOR.MINOR.PATCH` | `1.2.3` |
| `develop` | `MAJOR.MINOR.PATCH-alpha.N` | `1.3.0-alpha.5` |
| `feature/*` | `MAJOR.MINOR.PATCH-alpha.name.N` | `1.3.0-alpha.geolocation.2` |
| `hotfix/*` | `MAJOR.MINOR.PATCH-beta.N` | `1.2.4-beta.1` |
| `release/*` | `MAJOR.MINOR.PATCH-beta.N` | `1.3.0-beta.2` |

### Version Bumping

GitVersion automatically increments versions based on commit messages:

- **MAJOR** (breaking change): `feat!:` or `BREAKING CHANGE:` in footer
- **MINOR** (new feature): `feat:` commits
- **PATCH** (bug fix): `fix:` commits

## Release Process

### Pre-Release (Development Builds)

**Trigger**: Push to `develop` branch
**Purpose**: Testing and validation before official release

```bash
# Work is done in develop through merged feature branches
git checkout develop
git pull upstream develop

# Verify all features are merged and tested
# Push to trigger pre-release build
git push upstream develop

# CI automatically creates: 1.3.0-alpha.12 (or next number)
# Download from: https://github.com/zymlabs/keycloak-maxmind/releases
```

**Pre-release naming**: `zymlabs-maxmind-provider-1.3.0-alpha.12.jar`

### Production Release

**Trigger**: Version tag push from `master` branch
**Purpose**: Stable release for production use

#### Step 1: Merge `develop` to `master`

```bash
# Update both branches
git checkout master
git pull upstream master
git checkout develop
git pull upstream develop

# Merge develop into master (or create PR)
git checkout master
git merge develop

# Resolve any conflicts
# Run final tests
mvn clean test

# Push to upstream
git push upstream master
```

#### Step 2: Create Version Tag

```bash
# Ensure you're on master
git checkout master
git pull upstream master

# Create annotated tag (GitVersion requires annotated tags)
git tag -a 1.3.0 -m "Release version 1.3.0"

# Push tag to trigger release workflow
git push upstream 1.3.0
```

#### Step 3: Verify Release

1. Go to GitHub Actions and watch the `tagged-release` job
2. Verify build succeeds
3. Check the release page: `https://github.com/zymlabs/keycloak-maxmind/releases/tag/1.3.0`
4. Download and verify the JAR: `zymlabs-maxmind-provider-1.3.0.jar`

#### Step 4: Update Documentation

After release, update version references:

```bash
git checkout develop
git pull upstream develop

# Update docs with new version numbers
# Edit README.md, CHANGELOG.md, etc.

git add .
git commit -m "docs: update documentation for v1.3.0 release"
git push upstream develop
```

### Hotfix Release

For critical production bugs:

```bash
# 1. Create hotfix branch from master
git checkout master
git pull upstream master
git checkout -b hotfix/critical-bug-fix

# 2. Fix the bug
# ... make changes ...
git add .
git commit -m "fix: resolve critical API timeout issue"

# 3. Push and create PR to master
git push origin hotfix/critical-bug-fix
# Open PR to master, get review, merge

# 4. Tag the hotfix release
git checkout master
git pull upstream master
git tag -a 1.2.1 -m "Hotfix release 1.2.1"
git push upstream 1.2.1

# 5. Merge back to develop
git checkout develop
git pull upstream develop
git merge master
git push upstream develop
```

### Versioning Guidelines

Follow semantic versioning (SemVer):

- **MAJOR** (1.0.0 → 2.0.0): Breaking changes, incompatible API changes
  - Changed authenticator configuration structure
  - Removed deprecated features
  - Required Keycloak version upgrade

- **MINOR** (1.0.0 → 1.1.0): New features, backward compatible
  - Added new service level support
  - New configuration options
  - Enhanced fraud detection features

- **PATCH** (1.0.0 → 1.0.1): Bug fixes, backward compatible
  - Fixed API timeout handling
  - Resolved database connection leak
  - Corrected risk calculation

## Pull Request Guidelines

### Before Submitting

- ✅ Code builds successfully: `mvn clean package`
- ✅ All tests pass: `mvn test`
- ✅ Code follows existing style
- ✅ Added tests for new features
- ✅ Updated documentation if needed
- ✅ Commits follow conventional commit format
- ✅ Branch is up to date with target branch

### PR Title Format

Use conventional commit format:

```
feat: add MaxMind GeoIP database support
fix: prevent null pointer exception in risk evaluator
docs: improve installation guide for Keycloak 26
```

### PR Description Template

```markdown
## Description
Brief description of what this PR does.

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Testing
How has this been tested?
- [ ] Unit tests added/updated
- [ ] Manual testing in local environment
- [ ] Tested with Keycloak version: X.X.X

## Checklist
- [ ] My code follows the code style of this project
- [ ] I have added tests that prove my fix/feature works
- [ ] I have updated the documentation accordingly
- [ ] My commits follow the conventional commit format
- [ ] All tests pass locally

## Related Issues
Fixes #123
Related to #456
```

### Review Process

1. **Automated Checks**: CI must pass (tests, build)
2. **Code Review**: At least one maintainer approval required
3. **Testing**: Reviewer may test locally for complex changes
4. **Documentation**: Verify docs are updated if needed
5. **Merge**: Squash merge to `develop` or rebase merge to `master`

### After PR Merge

- Delete your feature branch (both local and remote)
- Pull latest changes from upstream
- If on develop, automatic pre-release will be created

## Code Style Guidelines

### Java Conventions

- **Indentation**: Tabs (as per existing codebase)
- **Line Length**: 120 characters max
- **Naming**:
  - Classes: `PascalCase`
  - Methods: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Packages: `lowercase`

### Documentation

- Add JavaDoc for public APIs
- Include inline comments for complex logic
- Update CLAUDE.md when changing architecture
- Update docs/ folder for major features

### Testing

- Unit test all business logic
- Mock external dependencies (MaxMind SDK, Keycloak APIs)
- Use descriptive test method names: `testRiskScoreCalculation_WhenAboveThreshold_ReturnsHighRisk`
- Follow AAA pattern: Arrange, Act, Assert

## Getting Help

- **Questions**: Open a GitHub Discussion
- **Bugs**: Open a GitHub Issue with reproduction steps
- **Security**: Email security@zymlabs.com (do not open public issue)
- **Feature Requests**: Open GitHub Issue with detailed description

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

---

**Thank you for contributing to the Keycloak MaxMind Extension!** 🎉
