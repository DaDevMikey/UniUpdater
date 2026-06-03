# UniUpdater v1.0.1 Update Notes

## Key Upgrades

### 1. In-App Client Updates
- **Direct Asset Extraction**: The client now extracts direct `.apk` assets dynamically from latest GitHub Release targets.
- **In-App Download Loop**: Features a live downloading progress bar directly inside the `AppUpdateCard` component on the dashboard.
- **FileProvider Integration**: Leverages secure FileProviders to package-share and automatically trigger the Android Package Installer (`ACTION_VIEW`) on completion.
- **Installation Permissions**: Configured `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` for Android 8.0+.

### 2. Package Refactor
- **New Package Name**: Namespace and application ID changed to `com.universal.updater` for system permissions and store distribution.
- **Source Paths Refactored**: Kotlin package hierarchy restructured and aligned to `com.universal.updater`.

### 3. Version Bump
- **Release Version**: Bumped to version `1.0.1` (`versionCode = 2`).
