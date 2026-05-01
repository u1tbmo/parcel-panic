package parcelpanic.shared;

/// Represents the inputs and intended actions of a single player for a given simulation tick.
/// Sent from the Client (Input Translator) to the Server (Simulator).
public record PlayerIntent(
    int playerId,
    boolean up,
    boolean down,
    boolean left,
    boolean right,
    boolean dash,
    boolean interact,
    boolean throwParcel
) {}
