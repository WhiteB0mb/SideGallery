import android.media.MediaMetadataRetriever
import android.os.Build

fun test() {
    val retriever = MediaMetadataRetriever()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val frame = retriever.getFrameAtIndex(0)
    }
}
