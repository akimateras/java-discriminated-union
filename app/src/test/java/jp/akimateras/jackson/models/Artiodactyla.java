package jp.akimateras.jackson.models;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "species")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Artiodactyla.Llama.class, name = "llama"),
        @JsonSubTypes.Type(value = Artiodactyla.Vicugna.class, name = "vicugna"),
        @JsonSubTypes.Type(value = Artiodactyla.Alpaca.class, name = "alpaca"),
})
public sealed interface Artiodactyla permits Artiodactyla.Alpaca, Artiodactyla.Llama, Artiodactyla.Vicugna {
    record Vicugna(String color, @Nullable List<Move> moves) implements Artiodactyla {
    }

    record Llama(String color, float weightCapacityKg, @Nullable List<Move> moves) implements Artiodactyla {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Alpaca.Huacaya.class, name = "huacaya"),
            @JsonSubTypes.Type(value = Alpaca.Suri.class, name = "suri"),
    })
    sealed interface Alpaca extends Artiodactyla permits Alpaca.Huacaya, Alpaca.Suri {
        record Huacaya(String color, int hairLength, int fluffiness, @Nullable List<Move> moves) implements Alpaca {
        }

        record Suri(String color, int hairLength, @Nullable List<Move> moves) implements Alpaca {
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "move")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Move.Run.class, name = "run"),
            @JsonSubTypes.Type(value = Move.Bite.class, name = "bite"),
            @JsonSubTypes.Type(value = Move.Spits.class, name = "spits")
    })
    sealed interface Move permits Move.Run, Move.Bite, Move.Spits {
        record Run(float speed) implements Move {
        }

        record Bite() implements Move {
        }

        record Spits() implements Move {
        }
    }
}
