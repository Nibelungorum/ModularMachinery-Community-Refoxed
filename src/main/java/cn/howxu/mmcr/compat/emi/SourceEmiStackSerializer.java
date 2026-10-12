package cn.howxu.mmcr.compat.emi;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;

import java.math.BigDecimal;

/**
 * Saves Source without requiring a world, renderer, or another recipe viewer.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceEmiStackSerializer implements EmiIngredientSerializer<SourceEmiStack> {
    @Override
    public String getType() {
        return "mmcr:source";
    }

    @Override
    public EmiIngredient deserialize(JsonElement json) {
        if (json == null || !json.isJsonObject()) return EmiStack.EMPTY;
        JsonObject object = json.getAsJsonObject();
        JsonElement type = object.get("type");
        if (type == null || !type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString()
                || !getType().equals(type.getAsString())) return EmiStack.EMPTY;
        try {
            long amount = 1;
            float chance = 1F;
            if (object.has("amount")) {
                JsonElement value = object.get("amount");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return EmiStack.EMPTY;
                amount = value.getAsBigDecimal().longValueExact();
            }
            if (object.has("chance")) {
                JsonElement value = object.get("chance");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return EmiStack.EMPTY;
                BigDecimal number = value.getAsBigDecimal();
                if (number.signum() < 0 || number.compareTo(BigDecimal.ONE) > 0) return EmiStack.EMPTY;
                chance = number.floatValue();
            }
            if (amount <= 0 || !Float.isFinite(chance) || chance < 0F || chance > 1F) return EmiStack.EMPTY;
            return new SourceEmiStack(amount).setChance(chance);
        } catch (NumberFormatException | ArithmeticException exception) {
            return EmiStack.EMPTY;
        }
    }

    @Override
    public JsonElement serialize(SourceEmiStack stack) {
        if (stack.isEmpty() || !Float.isFinite(stack.getChance())
                || stack.getChance() < 0F || stack.getChance() > 1F) return JsonNull.INSTANCE;
        JsonObject json = new JsonObject();
        json.addProperty("type", getType());
        json.addProperty("amount", stack.getAmount());
        json.addProperty("chance", stack.getChance());
        return json;
    }
}
