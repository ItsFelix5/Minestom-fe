package net.minestom.server.entity.attribute;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Represents an instance of an attribute and its modifiers.
 */
public final class AttributeInstance {
    private final Attribute attribute;
    private final Map<UUID, AttributeModifier> modifiers = new HashMap<>();
    private final Consumer<AttributeInstance> propertyChangeListener;
    private double baseValue;
    private double cachedValue = 0.0f;

    public AttributeInstance(@NotNull Attribute attribute, @Nullable Consumer<AttributeInstance> listener) {
        this.attribute = attribute;
        this.propertyChangeListener = listener;
        this.baseValue = attribute.defaultValue();
        refreshCachedValue();
    }

    /**
     * Gets the attribute associated to this instance.
     *
     * @return the associated attribute
     */
    public @NotNull Attribute getAttribute() {
        return attribute;
    }

    /**
     * The base value of this instance without modifiers
     *
     * @return the instance base value
     * @see #setBaseValue(double)
     */
    public double getBaseValue() {
        return baseValue;
    }

    /**
     * Sets the base value of this instance.
     *
     * @param baseValue the new base value
     * @see #getBaseValue()
     */
    public void setBaseValue(double baseValue) {
        if (this.baseValue != baseValue) {
            this.baseValue = baseValue;
            refreshCachedValue();
        }
    }

    /**
     * Add a modifier to this instance.
     *
     * @param modifier the modifier to add
     */
    public void addModifier(@NotNull AttributeModifier modifier) {
        if (modifiers.putIfAbsent(modifier.id(), modifier) == null) refreshCachedValue();
    }

    /**
     * Remove a modifier from this instance.
     *
     * @param modifier the modifier to remove
     */
    public void removeModifier(@NotNull AttributeModifier modifier) {
        removeModifier(modifier.id());
    }

    /**
     * Remove a modifier from this instance.
     *
     * @param uuid The UUID of the modifier to remove
     */
    public void removeModifier(@NotNull UUID uuid) {
        if (modifiers.remove(uuid) != null) {
            refreshCachedValue();
        }
    }

    /**
     * Get the modifiers applied to this instance.
     *
     * @return the modifiers.
     */
    @NotNull
    public Collection<AttributeModifier> getModifiers() {
        return modifiers.values();
    }

    /**
     * Gets the value of this instance calculated with modifiers applied.
     *
     * @return the attribute value
     */
    public double getValue() {
        return cachedValue;
    }

    /**
     * Recalculate the value of this attribute instance using the modifiers.
     */
    private void refreshCachedValue() {
        final Collection<AttributeModifier> modifiers = getModifiers();
        double base = getBaseValue();

        for (var modifier : modifiers.stream().filter(mod -> mod.operation() == AttributeOperation.ADD_VALUE).toArray(AttributeModifier[]::new))
            base += modifier.amount();

        double result = base;

        for (var modifier : modifiers.stream().filter(mod -> mod.operation() == AttributeOperation.MULTIPLY_BASE).toArray(AttributeModifier[]::new))
            result += (base * modifier.amount());
        for (var modifier : modifiers.stream().filter(mod -> mod.operation() == AttributeOperation.MULTIPLY_TOTAL).toArray(AttributeModifier[]::new))
            result *= (1.0f + modifier.amount());

        this.cachedValue = Math.clamp(result, getAttribute().minValue(), getAttribute().maxValue());

        // Signal entity
        if (propertyChangeListener != null) propertyChangeListener.accept(this);
    }
}
