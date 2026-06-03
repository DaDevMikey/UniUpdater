# UniUpdater v1.0.1 Update Notes

## Key Upgrades

### 1. In-App Client Updates
- **Direct Asset Extraction**: The client now extracts direct `.apk` assets dynamically from latest GitHub Release targets.
- **In-App Download Loop**: Features a live downloading progress bar directly inside the `AppUpdateCard` component on the dashboard.
- **FileProvider Integration**: Leverages secure FileProviders to package-share and automatically trigger the Android Package Installer (`ACTION_VIEW`) on completion.
- **Installation Permissions**: Configured `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` for Android 8.0+.

### 2. Manual Recheck Button
- **Up-to-Date Manual Checks**: Added a visual "Check for updates" button inside the "System is up to date" layout card so users can force rechecks of ROM updates at any time.

### 3. Simulation Visibility Filter
- **Conditional Visibility**: The "Simulation Mode Active" status row in the System Information specifications card is now hidden if simulation is inactive. It only displays when Simulation Mode is explicitly enabled (`isSimulationEnabled == true`), keeping diagnostics clean for standard users.

### 4. Package Refactor & Version Bump
- **New Package Name**: Namespace and application ID changed to `com.universal.updater` for system permissions and store distribution.
- **Release Version**: Bumped to version `1.0.1` (`versionCode = 2`).
