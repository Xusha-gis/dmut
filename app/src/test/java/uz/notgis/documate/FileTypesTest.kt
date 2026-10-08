package uz.notgis.documate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileTypesTest {
    @Test
    fun detectsTypesByExtension() {
        assertEquals(FType.PDF, typeOf("Shartnoma.PDF"))
        assertEquals(FType.DOC, typeOf("diplom.docx"))
        assertEquals(FType.XLS, typeOf("jadval.csv"))
        assertEquals(FType.PPT, typeOf("taqdimot.pptx"))
        assertEquals(FType.TXT, typeOf("eslatma.txt"))
        assertNull(typeOf("rasm.jpg"))
        assertNull(typeOf("kengaytmasiz"))
    }

    @Test
    fun formatsSizes() {
        assertEquals("900 B", formatSize(900))
        assertEquals("12 KB", formatSize(12 * 1024L))
        assertEquals("2,4 MB", formatSize(2_516_582L))
    }
}
