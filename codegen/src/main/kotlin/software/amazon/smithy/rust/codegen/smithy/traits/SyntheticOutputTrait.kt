/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.traits

import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AnnotationTrait

/**
 * Indicates that a shape is a synthetic input (see `OperationNormalizer.kt`)
 */
class SyntheticOutputTrait constructor(val operation: ShapeId, val originalId: ShapeId?) :
    AnnotationTrait(ID, Node.objectNode()) {
    companion object {
        val ID = ShapeId.from("smithy.api.internal#syntheticOutput")
    }
}
