package forge.game.log.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Level 1 Event - Full detail event log entry.
 */
public class L1Event {
    private int i; // event index
    private String t; // time marker (T1.MP1:2)
    private String a; // actor (P1, P2, SYS)
    private String type; // event type (CAST, MOVE, DAMAGE, etc.)
    private Map<String, Object> data; // event-specific payload

    public L1Event() {
        this.data = new HashMap<>();
    }

    public L1Event(int index, String timeMarker, String actor, String eventType) {
        this.i = index;
        this.t = timeMarker;
        this.a = actor;
        this.type = eventType;
        this.data = new HashMap<>();
    }

    // Getters and Setters
    public int getI() { return i; }
    public void setI(int i) { this.i = i; }

    public String getT() { return t; }
    public void setT(String t) { this.t = t; }

    public String getA() { return a; }
    public void setA(String a) { this.a = a; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public void addData(String key, Object value) {
        this.data.put(key, value);
    }
}

