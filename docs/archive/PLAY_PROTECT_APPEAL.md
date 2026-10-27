# Play Protect appeal — Pocket Friend (BELUM DIKIRIM, hanya disiapkan)

Form resmi: https://support.google.com/googleplay/android-developer/contact/protectappeals
Panduan: https://developers.google.com/android/play-protect/warning-dev-guidance (bagian "App blocked to protect your device")

Catatan: keputusan banding final dan Google **tidak mengirim balasan**. Form tidak menyebut lama review.

## Isi field form

| Field | Nilai |
|---|---|
| Email address * | _(email Google Mas Yan, isi sendiri)_ |
| Developer name | Yanuar |
| Application package name * | `id.yanuarmoo.pocketfriend` |
| SHA256 Hash of the apk of the uploaded file on Virus Total * | `01400d237b5583a600d87152438c3e053fffbe810fc4fa3c5cc2154e36aac2ef` (SHA-256 **file APK** `pocket-friend-v0.2.2-release.apk`; upload dulu file ini ke https://www.virustotal.com) |
| Additional information to support your appeal * (maks 1000 karakter) | lihat teks di bawah |

Info tambahan (tidak diminta form, untuk catatan): versi 0.2.2 (versionCode 4), sertifikat penandatangan
`CN=Yanuar, O=Pocket Friend, C=ID`, SHA-256 sertifikat `a3:e0:60:8e:6a:b1:c5:49:c2:b8:5d:03:4d:da:d6:33:c7:a9:7c:fe:cb:96:b1:4b:67:4e:71:0e:ee:fe:06:dc`.

## Teks "Additional information" (English, <1000 karakter)

```
Pocket Friend is a personal companion app for my own hardware device, BF-01 (a small BLE gadget I built). Not on Play; installed on my own phone. The install is blocked because of the notification listener.
Permissions and why:
- Notification listener: only reads Google Maps navigation and media notifications, to show turn directions and the playing song on the BF-01 screen. User enables it in Settings.
- Bluetooth scan/connect, companion device (CDM) presence and connectedDevice foreground service: pair with and stay connected to BF-01.
- Calendar (read): send upcoming agenda to the device.
- Location: remember where the device was last seen (Find my BF).
- Notification policy / audio settings: "find my phone" alarm can ring through Do Not Disturb.
- Internet: voice chat via the user's own AI key; local server for the device.
No accessibility, no SMS. No ads, no analytics, no data is sold or shared. Keys stay in Android Keystore. Open source: github.com/yanuarm00/pocket-friend
```
