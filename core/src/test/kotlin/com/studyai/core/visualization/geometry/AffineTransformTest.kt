package com.studyai.core.visualization.geometry

import com.studyai.core.visualization.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AffineTransformTest {

    private val delta = 1e-9

    @Test
    fun identityLeavesPointsUnchanged() {
        val p = AffineTransform.Identity.apply(3.0, -7.0)
        assertEquals(3.0, p.x, delta)
        assertEquals(-7.0, p.y, delta)
    }

    @Test
    fun translationMovesPoints() {
        val p = AffineTransform.translation(2.0, 5.0).apply(1.0, 1.0)
        assertEquals(3.0, p.x, delta)
        assertEquals(6.0, p.y, delta)
    }

    @Test
    fun scalingScalesPoints() {
        val p = AffineTransform.scaling(2.0, 3.0).apply(2.0, 2.0)
        assertEquals(4.0, p.x, delta)
        assertEquals(6.0, p.y, delta)
    }

    @Test
    fun rotationDegreesRotatesCounterClockwise() {
        val p = AffineTransform.rotationDegrees(90.0).apply(1.0, 0.0)
        assertEquals(0.0, p.x, 1e-9)
        assertEquals(1.0, p.y, 1e-9)
    }

    @Test
    fun composeAppliesOtherFirst() {
        // Scale then translate: (1,1) -> (2,2) -> (5,7).
        val m = AffineTransform.translation(3.0, 5.0).compose(AffineTransform.scaling(2.0, 2.0))
        val p = m.apply(1.0, 1.0)
        assertEquals(5.0, p.x, delta)
        assertEquals(7.0, p.y, delta)
    }

    @Test
    fun inverseUndoesTransform() {
        val m = AffineTransform.translation(4.0, -2.0)
            .compose(AffineTransform.rotationDegrees(37.0))
            .compose(AffineTransform.scaling(2.0, 3.0))
        val inv = m.inverse()!!
        val p = m.apply(1.3, -0.7)
        val back = inv.apply(p)
        assertEquals(1.3, back.x, 1e-9)
        assertEquals(-0.7, back.y, 1e-9)
    }

    @Test
    fun singularTransformHasNoInverse() {
        assertNull(AffineTransform.scaling(0.0, 1.0).inverse())
    }

    @Test
    fun meanScaleIsGeometricMeanOfAxes() {
        assertEquals(Math.sqrt(6.0), AffineTransform.scaling(2.0, 3.0).meanScale, 1e-9)
        assertEquals(5.0, AffineTransform.scaling(5.0, 5.0).meanScale, 1e-9)
    }

    @Test
    fun transformToAffineAppliesTranslateRotateScale() {
        // scale 2, rotate 90, translate (1,0): point (1,0) -> (0,2) -> (0,2) -> (1,2)
        val t = Transform(translateX = 1.0, translateY = 0.0, rotation = 90.0, scaleX = 2.0, scaleY = 2.0)
        val p = t.toAffine().apply(1.0, 0.0)
        assertEquals(1.0, p.x, 1e-9)
        assertEquals(2.0, p.y, 1e-9)
    }
}
