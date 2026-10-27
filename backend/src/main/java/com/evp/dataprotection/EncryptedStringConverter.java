package com.evp.dataprotection;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for personal fields: {@code @Convert(converter = EncryptedStringConverter.class) private String address;}
 * Binds the value to its column only (a converter does not see the row id). For row binding use FieldCipher directly
 * with {@code FieldCipher.context(table, column, id)}. Never put an encrypted column in a WHERE clause: look up via BlindIndex.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    private static volatile FieldCipher cipher;

    static void init(FieldCipher c) {
        cipher = c;
    }

    private static FieldCipher cipher() {
        FieldCipher c = cipher;
        if (c == null) {
            throw new DataProtectionException("field encryption is not initialised");
        }
        return c;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher().encrypt(attribute, "jpa");
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher().decrypt(dbData, "jpa");
    }
}
