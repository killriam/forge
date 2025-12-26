package forge.game.log.model;

/**
 * Card definition in the card index.
 */
public class CardDefinition {
    private String name;
    private String cost;
    private String type;
    private String oracleId;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getOracleId() { return oracleId; }
    public void setOracleId(String oracleId) { this.oracleId = oracleId; }
}

