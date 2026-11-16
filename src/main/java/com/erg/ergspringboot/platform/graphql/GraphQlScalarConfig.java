package com.erg.ergspringboot.platform.graphql;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLScalarType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/** Registers the {@code Date} and {@code DateTime} scalars; both travel as ISO-8601 strings. */
@AutoConfiguration
public class GraphQlScalarConfig {

    @Bean
    public RuntimeWiringConfigurer dateScalars() {
        return wiring -> wiring
                .scalar(scalar("Date", "ISO local date yyyy-MM-dd", value -> LocalDate.parse(value).toString()))
                .scalar(scalar("DateTime", "ISO-8601 offset date-time", value -> OffsetDateTime.parse(value).toString()));
    }

    // shortcut: values stay Strings in the Java request records (like the older calendar inputs); upgrade to LocalDate when a record needs date math.
    private static GraphQLScalarType scalar(String name, String description, java.util.function.UnaryOperator<String> validate) {
        return GraphQLScalarType.newScalar().name(name).description(description).coercing(new Coercing<String, String>() {
            @Override
            public String serialize(Object dataFetcherResult) {
                return dataFetcherResult == null ? null : dataFetcherResult.toString();
            }

            @Override
            public String parseValue(Object input) {
                if (input instanceof String text) {
                    try {
                        return validate.apply(text);
                    } catch (DateTimeParseException error) {
                        throw new CoercingParseValueException(name + " is not a valid ISO value: " + text);
                    }
                }
                throw new CoercingParseValueException(name + " must be a string");
            }

            @Override
            public String parseLiteral(Object input) {
                if (input instanceof StringValue text) {
                    try {
                        return validate.apply(text.getValue());
                    } catch (DateTimeParseException error) {
                        throw new CoercingParseLiteralException(name + " is not a valid ISO value");
                    }
                }
                throw new CoercingParseLiteralException(name + " must be a string literal");
            }
        }).build();
    }
}
