package com.studyai.core.visualization.geometry

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** An immutable 2D point in either world or screen space. */
data class Point2D(val x: Double, val y: Double)

/**
 * A minimal 2D affine transform.
 *
 * The six coefficients map a point `(x, y)` as:
 * ```
 * x' = a*x + c*y + e
 * y' = b*x + d*y + f
 * ```
 *
 * This is the only geometry primitive the renderer and hit-tester need. It deliberately
 * supports just composition, inversion and point mapping — no general linear algebra.
 */
data class AffineTransform(
    val a: Double = 1.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 1.0,
    val e: Double = 0.0,
    val f: Double = 0.0,
) {
    fun apply(x: Double, y: Double): Point2D = Point2D(a * x + c * y + e, b * x + d * y + f)

    fun apply(point: Point2D): Point2D = apply(point.x, point.y)

    /**
     * Returns `this ∘ other`: the transform that applies [other] first and then this one.
     * Composition order matches how nested scene-graph transforms are applied.
     */
    fun compose(other: AffineTransform): AffineTransform = AffineTransform(
        a = a * other.a + c * other.b,
        b = b * other.a + d * other.b,
        c = a * other.c + c * other.d,
        d = b * other.c + d * other.d,
        e = a * other.e + c * other.f + e,
        f = b * other.e + d * other.f + f,
    )

    val determinant: Double get() = a * d - b * c

    /**
     * Geometric mean of the two axis scale factors — a stable scalar approximation of
     * "how many local units is one transformed unit". Used to scale stroke widths and font
     * sizes under non-uniform transforms.
     */
    val meanScale: Double get() = sqrt(abs(determinant))

    /** The inverse transform, or `null` when this transform is singular (determinant ≈ 0). */
    fun inverse(): AffineTransform? {
        val det = determinant
        if (abs(det) < 1e-12) return null
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return AffineTransform(
            a = ia,
            b = ib,
            c = ic,
            d = id,
            e = -(ia * e + ic * f),
            f = -(ib * e + id * f),
        )
    }

    companion object {
        val Identity: AffineTransform = AffineTransform()

        fun translation(tx: Double, ty: Double): AffineTransform = AffineTransform(e = tx, f = ty)

        fun scaling(sx: Double, sy: Double): AffineTransform = AffineTransform(a = sx, d = sy)

        fun rotationDegrees(degrees: Double): AffineTransform {
            val radians = Math.toRadians(degrees)
            val cs = cos(radians)
            val sn = sin(radians)
            return AffineTransform(a = cs, b = sn, c = -sn, d = cs)
        }
    }
}
