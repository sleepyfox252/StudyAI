package com.studyai.core.visualization.geometry

import com.studyai.core.visualization.model.Transform

/**
 * Converts a model [Transform] into an [AffineTransform].
 *
 * The model transform places a node's local frame inside its parent's frame; the resulting
 * matrix applies scale first, then rotation, then translation (the conventional order).
 */
fun Transform.toAffine(): AffineTransform =
    AffineTransform.translation(translateX, translateY)
        .compose(AffineTransform.rotationDegrees(rotation))
        .compose(AffineTransform.scaling(scaleX, scaleY))
