## 2026-05-08 - Add accessibility label to Export button
**Learning:** Found an icon-only button used for exporting wardriving data that lacked a content description in the Compose UI. It was set to null.
**Action:** Replaced `contentDescription = null` with `contentDescription = stringResource(R.string.export_wardriving)` so screen readers properly announce the button's action. This improves accessibility for users relying on TalkBack in the Jetpack Compose UI.
