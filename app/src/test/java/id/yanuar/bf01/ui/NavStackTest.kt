package id.yanuar.bf01.ui

import org.junit.Assert.*
import org.junit.Test

/** Perilaku gesture kembali (swipe dari tepi) di Pocket Friend. */
class NavStackTest {
    @Test fun berandaExits() {
        val n = NavStack(Screen.Beranda)
        assertFalse(n.canGoBack); assertFalse(n.back()); assertEquals(Screen.Beranda, n.current)
    }
    @Test fun tabGoesToBeranda() {
        val n = NavStack(Screen.Beranda)
        for (t in listOf(Screen.Ngobrol, Screen.Tugas, Screen.Catatan, Screen.Pengaturan)) {
            n.tab(t); assertTrue(n.back()); assertEquals(Screen.Beranda, n.current); assertFalse(n.canGoBack)
        }
    }
    @Test fun tabSwitchDoesNotStack() {
        val n = NavStack(Screen.Beranda); n.tab(Screen.Ngobrol); n.tab(Screen.Tugas); n.tab(Screen.Catatan)
        n.back(); assertEquals(Screen.Beranda, n.current)
    }
    @Test fun subScreenReturnsToPrevious() {
        for (sub in listOf(Screen.Pairing, Screen.Kartu, Screen.Menu, Screen.File)) {
            val n = NavStack(Screen.Beranda); n.tab(Screen.Pengaturan); n.open(sub)
            assertEquals(sub, n.current)
            n.back(); assertEquals(Screen.Pengaturan, n.current)
            n.back(); assertEquals(Screen.Beranda, n.current); assertFalse(n.back())
        }
    }
    @Test fun firstRunPairingBackToBeranda() {
        val n = NavStack(Screen.Pairing); assertTrue(n.canGoBack); n.back(); assertEquals(Screen.Beranda, n.current)
    }
}
