package forge.game.log.model;

import java.util.List;

/**
 * Card definition in the card index.
 */
public class CardDefinition {
    private String name;
    private String cost;
    private String type;
    private String oracleId;
    private String oracleText;
    private String power;
    private String toughness;
    private List<String> subtypes;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getOracleId() { return oracleId; }
    public void setOracleId(String oracleId) { this.oracleId = oracleId; }

    public String getOracleText() { return oracleText; }
    public void setOracleText(String oracleText) { this.oracleText = oracleText; }

    public String getPower() { return power; }
    public void setPower(String power) { this.power = power; }

    public String getToughness() { return toughness; }
    public void setToughness(String toughness) { this.toughness = toughness; }

    public List<String> getSubtypes() { return subtypes; }
    public void setSubtypes(List<String> subtypes) { this.subtypes = subtypes; }
}
