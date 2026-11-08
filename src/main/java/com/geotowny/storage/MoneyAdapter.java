package com.geotowny.storage;

import com.geotowny.domain.money.Money;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * {@link Money} 的 Gson 适配器。
 * <p>兼容三种旧格式：数字（旧 double 金额）、字符串（如 "100.00"）、对象（含 cents）。
 * 新格式统一写为 {@code {"cents": 12345}}；读取时数字按旧 double 金额解释。
 */
public final class MoneyAdapter extends TypeAdapter<Money> {

    @Override
    public void write(JsonWriter out, Money value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("cents").value(value.cents());
        out.endObject();
    }

    @Override
    public Money read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        return switch (token) {
            case NUMBER -> Money.ofCents(Math.round(in.nextDouble() * 100.0d));
            case STRING -> Money.parse(in.nextString());
            case NULL -> {
                in.nextNull();
                yield Money.ZERO;
            }
            case BEGIN_OBJECT -> readObject(in);
            default -> {
                in.skipValue();
                yield Money.ZERO;
            }
        };
    }

    private Money readObject(JsonReader in) throws IOException {
        JsonObject object = JsonParser.parseReader(in).getAsJsonObject();
        if (object.has("cents") && !object.get("cents").isJsonNull()) {
            return Money.ofCents(object.get("cents").getAsLong());
        }
        if (object.has("amount") && !object.get("amount").isJsonNull()) {
            return Money.parse(object.get("amount").getAsString());
        }
        return Money.ZERO;
    }
}