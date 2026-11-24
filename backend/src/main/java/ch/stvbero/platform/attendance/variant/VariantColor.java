package ch.stvbero.platform.attendance.variant;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Colours a variant can have (VAR-04): a fixed palette. Each value is a pair of design tokens per theme in the
 * frontend ({@code --tag-<name>} and {@code --tag-<name>-soft}), so that every choice has enough contrast.
 */
@Schema(name = "VariantColor")
public enum VariantColor {
    GREEN, TEAL, BLUE, VIOLET, PINK, RED, ORANGE, YELLOW, GREY
}
