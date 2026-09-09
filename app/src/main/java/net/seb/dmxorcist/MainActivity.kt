package net.seb.dmxorcist

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import net.seb.dmxorcist.ui.TesterScreen

class MainActivity : ComponentActivity() {

    private val vm: TesterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A tester gets used with the phone sitting on a flight case: never sleep.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    TesterScreen(vm)
                }
            }
        }

        handleAttachIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAttachIntent(intent)
    }

    /** The app is launched automatically when the dongle is plugged in, so carry straight on. */
    private fun handleAttachIntent(intent: Intent?) {
        if (intent?.action == "android.hardware.usb.action.USB_DEVICE_ATTACHED") {
            vm.refresh()
            vm.connect()
        }
    }

    override fun onDestroy() {
        vm.disconnect()
        super.onDestroy()
    }
}
