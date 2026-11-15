package com.studyai.core.visualization.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A color encoded as a hexadecimal string.
 *
 * Supported formats (validated semantically, not at decode time):
 *  - `#RRGGBB`
 *  - `#AARRGGBB`
 *
 * The type is deliberately independent of any platform color class and is decoded
 * permissively so that malformed colors surface as structured validation errors rather
 * than as decoding exceptions.
 */
@Serializable(with = ColorValueSerializer::class)
data class ColorValue(val hex: String) {
    val isValid: Boolean get() = isValidHex(hex)

    companion object {
        private val HEX_PATTERN = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

        fun isValidHex(hex: String): Boolean = HEX_PATTERN.matches(hex)
    }
}

object ColorValueSerializer : KSerializer<ColorValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "com.studyai.core.visualization.model.ColorValue",
            PrimitiveKind.STRING,
        )

    override fun serialize(encoder: Encoder, value: ColorValue) {
        encoder.encodeString(value.hex)
    }

    override fun deserialize(decoder: Decoder): ColorValue = ColorValue(decoder.decodeString())
}
