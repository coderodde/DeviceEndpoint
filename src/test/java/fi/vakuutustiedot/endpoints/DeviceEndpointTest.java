package fi.vakuutustiedot.endpoints;

import javax.swing.UIManager;
import org.junit.Test;

/**
 *
 * @author Rodion "rodde" Efremov
 */
public class DeviceEndpointTest {
    
    Device device = new Device();
    
    public DeviceEndpointTest() {
        device.setId(10);
        device.setName("Your device name");
        device.setDescription("Your device description");
        device.setStatus(true);
    }
    
    @Test
    public void testShowJSON() {
        DeviceEndpoint de = new DeviceEndpoint();
        System.out.println(de.getRemoveDeviceFailureMessageJson(11));
        System.out.println(de.getCreateDeviceMessageJson(device));
    }
}
