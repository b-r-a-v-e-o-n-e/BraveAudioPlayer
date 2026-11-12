package org.widelogic.braveaudio

import android.app.Application
import org.widelogic.braveaudio.data.AppDatabase

class BraveAudioApp : Application() {
    val database by lazy { AppDatabase.get(this) }
}
