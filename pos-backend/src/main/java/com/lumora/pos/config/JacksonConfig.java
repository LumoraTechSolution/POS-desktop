package com.lumora.pos.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Global Jackson customization for timestamp serialization.
 *
 * Entities persist instant-in-time values as {@link LocalDateTime} captured on a
 * UTC JVM (see {@code hibernate.jdbc.time_zone: UTC} and {@code LocalDateTime.now()}
 * in auditing). With the default jsr310 serializer these go out as a naive ISO
 * string with no zone marker (e.g. {@code 2026-05-28T09:00:00}). Browsers parse a
 * zoneless string as *local* time, so every timestamp rendered via {@code new Date()}
 * on the frontend was shifted by the viewer's UTC offset.
 *
 * Emitting the UTC offset ({@code 2026-05-28T09:00:00Z}) lets the client convert the
 * instant to its own zone correctly.
 *
 * Note: a small number of request-side {@code LocalDateTime} fields are user-entered
 * wall-clock dates (PurchaseOrderRequest.expectedDate, TenantConfigurationRequest
 * .subscriptionEnd). They are typically midnight date-picker values, so the UTC tag
 * keeps them on the same calendar day for positive (e.g. UTC+5:30) offsets.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeUtcCustomizer() {
        return builder -> builder.serializerByType(LocalDateTime.class, new UtcLocalDateTimeSerializer());
    }

    static class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.atOffset(ZoneOffset.UTC).format(FORMATTER));
        }
    }
}
