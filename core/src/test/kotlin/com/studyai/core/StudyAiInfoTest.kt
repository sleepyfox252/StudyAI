package com.studyai.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyAiInfoTest {

    @Test
    fun currentCarriesExpectedName() {
        assertEquals("StudyAI", StudyAiInfo.Current.name)
    }

    @Test
    fun currentCarriesNonBlankVersion() {
        assertEquals("0.1.0", StudyAiInfo.Current.coreVersion)
    }
}
