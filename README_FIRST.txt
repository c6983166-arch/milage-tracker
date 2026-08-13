BAILEY ANN'S MILEAGE TRACKER v1.0 — ANDROID SOURCE PROJECT
08/12/2026

WHAT THIS BUILD INCLUDES
- Estate Miles
- Business Miles
- Manual Start / End Trip
- GPS mileage tracking while a trip is active
- Estate list sync from Bailey Ann's Business Manager
- Completed Estate trips sync back to the matching Estate file
- General Business mileage sync to the Business Manager's Business Mileage folder
- Optional Bluetooth car-connect reminder asking: Estate Miles or Business Miles
- Trip history with sync status
- Year-End Tax Report with separate Estate Miles, Business Miles, and Combined Total
- Android Print support (the Android print screen can print or Save as PDF)
- Bailey Ann's BA-1 logo and navy/gold/white branding

IMPORTANT
This project is configured to build the APK in GitHub Actions so Android Studio is not required on the Windows PC.

FIRST USE
1. Install/open the Mileage Sync Business Manager update included separately.
2. Open Bailey Ann's Business Manager on Windows.
3. In Business Manager Settings, locate Mobile Mileage Sync and note the six-digit pairing code.
4. Put the Android phone and Windows computer on the same private Wi-Fi network.
5. Open the Android app.
6. Settings / Car Connection > Find Business Manager.
7. Enter the six-digit pairing code and tap Save Code & Sync Estates.
8. Allow Location, Notifications, and Nearby Devices/Bluetooth permissions when Android asks.
9. Optional: choose your paired car and enable the car connection reminder.

TRACKING
- Estate Miles: select the estate, optionally enter a purpose, and Start Trip.
- Business Miles: enter the business purpose and Start Trip.
- Tap End Trip when done.
- Use Sync with Business Manager to send completed trips.

SYNC SECURITY
- The normal Business Manager web server remains localhost-only.
- A separate restricted mobile-sync service listens on the private network and only exposes pairing, estate-list, and mileage-sync endpoints.
- It does not expose your Business Manager web files or estate documents over the mobile-sync port.
