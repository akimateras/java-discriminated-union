package com.example;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "species")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Llama.class, name = "llama"),
        @JsonSubTypes.Type(value = Vicugna.class, name = "vicugna"),
        @JsonSubTypes.Type(value = Alpaca.class, name = "alpaca"),
})
public sealed interface Artiodactyla permits Alpaca, Llama, Vicugna {
}

record Vicugna(String color, @Nullable List<Move> moves) implements Artiodactyla {
}

record Llama(String color, float weightCapacityKg, @Nullable List<Move> moves) implements Artiodactyla {
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Huacaya.class, name = "huacaya"),
        @JsonSubTypes.Type(value = Suri.class, name = "suri"),
})
sealed interface Alpaca extends Artiodactyla permits Huacaya, Suri {
}

record Huacaya(String color, int hairLength, int fluffiness, @Nullable List<Move> moves) implements Alpaca {
}

record Suri(String color, int hairLength, @Nullable List<Move> moves) implements Alpaca {
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "move")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Run.class, name = "run"),
        @JsonSubTypes.Type(value = Bite.class, name = "bite"),
        @JsonSubTypes.Type(value = Spits.class, name = "spits")
})
sealed interface Move permits Run, Bite, Spits {
}

record Run(float speed) implements Move {
}

record Bite() implements Move {
}

record Spits() implements Move {
}
