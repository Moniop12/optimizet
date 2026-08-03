package com.monai.optimizer.ui.neumorphic.shapes


import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.monai.optimizer.ui.neumorphic.internal.BlurMaker

/**
 * Represents neumorphic shape
 */
interface NeuShape {

    fun drawShadows(drawScope: ContentDrawScope, blurMaker: BlurMaker, shapeConfig: ShapeConfig)
}