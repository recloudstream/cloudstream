package com.lagradost.cloudstream3.ui.sync

import com.journeyapps.barcodescanner.CaptureActivity

class CustomScannerActivity : CaptureActivity() {
    // This activity is intentionally empty.
    // It's used purely to provide a custom CaptureActivity for the QR scanner
    // that we can force to portrait orientation via the AndroidManifest.xml.
}
