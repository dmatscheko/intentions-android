package at.matscheko.intentions.model

/**
 * The operations the content-provider screen can perform. [mutating] ones change
 * data and are gated behind an explicit confirmation in the UI.
 */
enum class ProviderOp(val label: String, val mutating: Boolean = false) {
    QUERY("Query"),
    GET_TYPE("Get type"),
    READ("Read"),
    CALL("Call"),
    INSERT("Insert", mutating = true),
    UPDATE("Update", mutating = true),
    DELETE("Delete", mutating = true),
}
