package com.yunx.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SegmentChainTest {
    private fun seg(start: Long, end: Long) = SegmentRange(start, end, File("seg_${start}_$end.part"))

    @Test
    fun skipsOverlappingSegments() {
        val chain = selectSegmentChain(listOf(seg(100, 199), seg(150, 249), seg(200, 299), seg(290, 299)), 100)
        assertEquals(listOf("seg_100_199.part", "seg_200_299.part"), chain?.map { it.name })
    }

    @Test
    fun returnsNullWhenChainHasGap() {
        assertNull(selectSegmentChain(listOf(seg(100, 199), seg(250, 299)), 100))
    }
}
