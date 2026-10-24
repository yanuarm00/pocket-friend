package id.yanuar.bf01.svc

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi

/** API 31+: sistem membangunkan app saat BF yang terasosiasi muncul (BF bangun & advertise). */
@RequiresApi(Build.VERSION_CODES.S)
class PresenceService : CompanionDeviceService() {
    @Deprecated("API 31-32") override fun onDeviceAppeared(address: String) { if (id.yanuar.bf01.data.Prefs(this).linkEnabled) BfService.start(this) }
    @Deprecated("API 31-32") override fun onDeviceDisappeared(address: String) {}
    override fun onDeviceAppeared(info: AssociationInfo) { if (id.yanuar.bf01.data.Prefs(this).linkEnabled) BfService.start(this) }
}
