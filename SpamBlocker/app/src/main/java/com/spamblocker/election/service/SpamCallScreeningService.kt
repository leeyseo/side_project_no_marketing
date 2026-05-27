package com.spamblocker.election.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.spamblocker.election.data.BlockEntry
import com.spamblocker.election.data.BlockKind
import com.spamblocker.election.data.BlockLog
import com.spamblocker.election.data.SettingsStore

class SpamCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val store = SettingsStore.get(applicationContext)
        if (!store.enabled) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart
        val decision = store.buildFilter().classifyCall(number)

        if (!decision.isSpam) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        Log.i(TAG, "Blocking call from $number reason=${decision.reason}")

        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSilenceCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(true)
            .build()
        respondToCall(callDetails, response)

        BlockLog.get(applicationContext).add(
            BlockEntry(
                timestamp = System.currentTimeMillis(),
                kind = BlockKind.CALL,
                sender = number.orEmpty(),
                preview = "",
                reason = decision.reason.orEmpty(),
            )
        )
        store.incrementBlockCount()
    }

    companion object {
        private const val TAG = "SpamCS"
    }
}
