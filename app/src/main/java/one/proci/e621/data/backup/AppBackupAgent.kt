package one.proci.e621.data.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import one.proci.e621.data.settings.UserPreferences

/**
 * Lets the user turn Android's Auto Backup off entirely from Settings > Backup & Restore
 * (see [one.proci.e621.data.settings.UserSettings.cloudBackupEnabled]), rather than it being an
 * all-or-nothing manifest-time decision - `android:allowBackup` alone offers no runtime toggle,
 * so this intercepts the platform's full-data backup pass to conditionally skip it.
 *
 * This app doesn't use the older key/value backup API, only Auto Backup's full-data path, so
 * [onBackup]/[onRestore] are unused no-ops required only because [BackupAgent] declares them
 * abstract.
 */
class AppBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        // Read straight from disk via a plain Context - applicationContext can be a bare
        // android.app.Application (not E621Application) in this backup-only process, since the
        // backup agent can be invoked before/without the app's own Application.onCreate() - see
        // UserPreferences.Companion.isCloudBackupEnabled's doc.
        val enabled = runBlocking { UserPreferences.isCloudBackupEnabled(applicationContext) }
        if (enabled) {
            // Defers to the platform's default handling, which honors the include/exclude rules
            // in data_extraction_rules.xml / backup_rules.xml.
            super.onFullBackup(data)
        }
        // Disabled: write nothing this pass, skipping the backup entirely.
    }

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {
        // Classic key/value backup isn't used by this app - see class doc.
    }

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {
        // Classic key/value restore isn't used by this app - Auto Backup's full-data restore
        // doesn't route through here.
    }
}
