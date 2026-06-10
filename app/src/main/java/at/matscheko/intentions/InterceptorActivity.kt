package at.matscheko.intentions

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import at.matscheko.intentions.core.IntentCodec
import at.matscheko.intentions.core.SnifferRepository

/**
 * Appears in "Open with" / share sheets for common schemes and SEND. When the
 * system routes an intent here, we log it to the sniffer and hand it to
 * [MainActivity] so it loads into the editor. Implements the README "intercept
 * (sniff) protocol handler" TODO (activity filters must be static — manifest).
 */
class InterceptorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val captured = intent
        if (captured != null) {
            SnifferRepository.record(this, captured)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_CAPTURED, IntentCodec.encode(captured))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            Toast.makeText(this, "Intent captured", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
