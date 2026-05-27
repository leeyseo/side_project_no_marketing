package com.spamblocker.election.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.spamblocker.election.data.BlockEntry
import com.spamblocker.election.data.BlockKind
import com.spamblocker.election.data.BlockLog
import com.spamblocker.election.data.SettingsStore

class SpamNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val store = SettingsStore.get(applicationContext)
        if (!store.enabled) return

        if (sbn.packageName !in MESSAGING_PACKAGES) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = listOf(text, bigText).firstOrNull { it.isNotBlank() }.orEmpty()

        if (title.isBlank() && body.isBlank()) return

        val decision = store.buildFilter().classifyMessage(sender = title, body = body)
        if (!decision.isSpam) return

        Log.i(TAG, "Blocking notification from ${sbn.packageName} sender=$title reason=${decision.reason}")

        runCatching { cancelNotification(sbn.key) }
            .onFailure { Log.w(TAG, "cancelNotification failed: ${it.message}") }

        BlockLog.get(applicationContext).add(
            BlockEntry(
                timestamp = System.currentTimeMillis(),
                kind = BlockKind.SMS,
                sender = title.ifBlank { sbn.packageName },
                preview = body.take(120),
                reason = decision.reason.orEmpty(),
            )
        )
        store.incrementBlockCount()
    }

    override fun onListenerConnected() {
        Log.i(TAG, "Notification listener connected")
    }

    companion object {
        private const val TAG = "SpamNL"

        private val MESSAGING_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.kt.ollehmessage",
            "com.skt.prod.dialer",
            "com.lguplus.messenger",
        )
    }
}
