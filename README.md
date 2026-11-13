# Rakshak – Android Child Safety Starter

Rakshak is an Android-only starter project with two separate app variants:

- **Parent app** – starter dashboard.
- **Child app** – on-device app-usage summary and a child-initiated safety evidence picker.

## Build

GitHub Actions builds both debug APK variants when code is pushed to `main` or when the workflow is manually run.

Workflow: `.github/workflows/build-apk.yml`

Expected outputs:
- `app/build/outputs/apk/parent/debug/app-parent-debug.apk`
- `app/build/outputs/apk/child/debug/app-child-debug.apk`

Download the generated `rakshak-debug-apks` artifact from the completed Actions run.

## Current limitations

This is an early starter build, not a finished parental-control product. Usage summaries are read locally through Android Usage Access. A selected image is not uploaded or shared yet. Firebase, secure QR pairing, parent-child linking, cloud evidence storage and notifications still need to be implemented and tested.

Rakshak does not secretly read private messages, recover deleted chats, intercept social-media communications or record calls. Any future safety reporting must remain transparent and consent-based.
