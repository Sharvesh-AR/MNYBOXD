package io.github.sharveshar.spendly.shortcut

import android.app.Activity
import android.os.Bundle
import io.github.sharveshar.spendly.MainActivity

/**
 * Optional second app-list entry, "Add expense", for phone gestures that can only open an app
 * (back tap, double-pressing the side key, OEM quick-launch). Disabled until the user turns it on
 * in Settings. It has no UI and ignores its intent: it opens Quick Add and finishes.
 */
class QuickAddLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(MainActivity.launchIntent(this, MainActivity.ACTION_QUICK_ADD))
        finish() // Theme.NoDisplay requires finishing before onResume.
    }
}
