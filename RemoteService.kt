package com.tools.shizuku.dashboard

import android.os.RemoteException

/** Runs only the fixed settings commands created by this app, under the user's Shizuku grant. */
class RemoteService : IRemoteService.Stub() {
    @Throws(RemoteException::class)
    override fun exec(command: String): String {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            if (code != 0) throw RemoteException(error.ifBlank { "command exit $code" })
            return output
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw RemoteException(e.message ?: "Command failed")
        }
    }
}
