# APSRTC Connect

Android-first APSRTC companion app built with Kotlin and Jetpack Compose.

## Features in this starter
- Search APSRTC stops and find routes that serve both stops using GTFS stop_times.
- Download and cache the APSRTC static GTFS feed.
- Live vehicle positions, feed freshness status and trip updates from Reclaim City.
- OpenStreetMap live vehicle map using Leaflet.
- English/Telugu interface toggle.
- Favourite route storage on device.
- GitHub Actions debug APK build.

## Data sources
- Static GTFS: https://maps.reclaim.city/gtfs/apsrtc.zip
- Vehicle positions: https://maps.reclaim.city/apsrtc/rt/vehicle_positions.json
- Trip updates: https://maps.reclaim.city/apsrtc/rt/trip_updates.json
- Feed status: https://maps.reclaim.city/apsrtc/rt/status.json
- API documentation: https://api.reclaim.city/docs/#buses

The feed is unofficial and should be shown with freshness information. APSRTC's source refresh is approximately every 60 seconds; this app refreshes only when requested in this starter build. Do not present stale positions as live.

## Build locally
Requires JDK 17, Android SDK 35 and Gradle 8.9.

```bash
cd APSRTC-Connect
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions
Open Actions → Build APSRTC Connect APK → Run workflow. The generated debug APK is available as the `APSRTC-Connect-debug` artifact.

## Notes
- Nearby stops need Android location permission and a location-based stop filter; location permissions are declared but the permission flow is not yet wired in this starter.
- The map uses OpenStreetMap tiles and Leaflet via the WebView.
- Arrival trip updates are parsed from the GTFS-Realtime JSON mapping. UI stop-specific arrival rendering and route-to-trip enrichment are next iteration items.
- Feed attribution: reclaim.city and OpenStreetMap contributors. Check source licences before commercial distribution.
