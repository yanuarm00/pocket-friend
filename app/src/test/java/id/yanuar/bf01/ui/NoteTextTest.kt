package id.yanuar.bf01.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTextTest {
    @Test fun clock() { assertEquals("0:42", NoteText.clock(42_000)); assertEquals("2:14", NoteText.clock(134_000)) }
    @Test fun linesAreTimedInOrder() {
        val l = NoteText.lines("One two. Three four five! Six?", 10_000)
        assertEquals(3, l.size); assertEquals(0L, l[0].atMs); assertTrue(l[1].atMs in 1..l[2].atMs)
        assertEquals(1, NoteText.currentLine(l, l[1].atMs + 1))
    }
    @Test fun todosFromCues() {
        val t = NoteText.todos("I need to buy flowers. The weather is nice. Remember to call the dentist about Tuesday. Book a table for four.")
        assertEquals(listOf("Buy flowers", "Call the dentist about Tuesday", "Book a table for four"), t)
    }
    @Test fun noTodosInPlainText() = assertTrue(NoteText.todos("The weather was nice today.").isEmpty())
}
