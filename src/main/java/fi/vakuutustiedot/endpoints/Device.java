package fi.vakuutustiedot.endpoints;

/**
 * This class encapsulates all the relevant data regarding a device.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Apr 9, 2018)
 */
public class Device {
    
    private int id;
    private String name;
    private String description;
    private boolean status;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }
}
