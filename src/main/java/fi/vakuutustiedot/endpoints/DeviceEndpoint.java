package fi.vakuutustiedot.endpoints;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

/**
 * This endpoint maintains a list of devices.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Apr 9, 2018)
 */
@ServerEndpoint("/devices")
public final class DeviceEndpoint {
    
    private static final class JsonDefinitions {
        private static final String ACTION             = "action";
        private static final String SUCCEEDED          = "succeeded";
        private static final String DEVICE_ID          = "deviceId";
        private static final String DEVICE_NAME        = "deviceName";
        private static final String DEVICE_DESCRIPTION = "deviceDescription";
        private static final String DEFICE_STATUS      = "deviceStatus";
        private static final String MESSAGE            = "message";
        
        private static final class Actions {
            private static final String CREATE = "create";
            private static final String UPDATE = "update";
            private static final String REMOVE = "remove";
            private static final String UNLOAD = "unload";
        }
    }
    
    /**
     * This string specifies the format of the JSON message that is returned
     * by the endpoint upon addition of a new device.
     */
    private static final String CREATE_DEVICE_MESSAGE_FORMAT = 
            "{\"%s\":%b,"    + // success
            "\"%s\":\"%s\"," + // message
            "\"%s\":\"%s\"," + // action
            "\"%s\":%d,"     + // device ID
            "\"%s\":\"%s\"," + // device name
            "\"%s\":\"%s\"," + // device description
            "\"%s\":%b}";      // device status
    
    /**
     * This string specifies the format of the JSON message that is returned by
     * the endpoint upon successful updating of a device's information.
     */
    private static final String 
            UPDATE_DEVICE_SUCCESS_INFORMATION_MESSAGE_FORMAT = 
            "{\"%s\":true,"  + // success
            "\"%s\":\"%s\"," + // message
            "\"%s\":\"%s\"," + // action
            "\"%s\":%d,"     + // device ID
            "\"%s\":\"%s\"," + // device name
            "\"%s\":\"%s\"," + // device description
            "\"%s\":%b}";      // device status
    
    /**
     * This string specifies the format of the JSON message that is returned by
     * the endpoint upon unsuccessful updating of a device's information.
     */
    private static final String
            UPDATE_DEVICE_FAILURE_INFORMATION_MESSAGE_FORMAT = 
            "{\"%s\":false," + // succcess
            "\"%s\":\"%s\"," + // message
            "\"%s\":\"%s\"," + // action
            "\"%s\":%d}";      // device ID
    
    /**
     * This string specifies the format of the JSON message that is returned by
     * the endpoint upon removal action.
     */
    private static final String REMOVE_DEVICE_MESSAGE_FORMAT = 
            "{\"%s\":%b,"    + // success
            "\"%s\":\"%s\"," + // message
            "\"%s\":\"%s\"," + // action
            "\"%s\":%d}";      // device ID
    
    // Used for creating IDs for the devices.
    private static int deviceCounter = 0;
    private static final Set<Session> SESSIONS = new HashSet<>();
    private static final Map<Integer, Device> MAP_DEVICE_ID_TO_DEVICE = 
            new HashMap<>();
    
    @OnOpen
    public void open(Session session) {
        session.setMaxIdleTimeout(-1L);
        SESSIONS.add(session);
        
        // Send the new user all the current devices:
        for (Device device : MAP_DEVICE_ID_TO_DEVICE.values()) {
            try {
                session.getBasicRemote()
                       .sendText(getCreateDeviceMessageJson(device));
            } catch (IOException ex) {
                
            }
        }
    }
    
    @OnMessage
    public void handleMessage(String message, Session session) {
        try (JsonReader reader = 
                Json.createReader(new StringReader(message))) {
            JsonObject jsonMessage = reader.readObject();
            String actionName = jsonMessage.getString(JsonDefinitions.ACTION);
            
            switch (actionName) {
                case JsonDefinitions.Actions.CREATE:
                    handleCreateDeviceMessage(jsonMessage);
                    break;
                    
                case JsonDefinitions.Actions.UPDATE:
                    handleUpdateDeviceMessage(jsonMessage);
                    break;
                    
                case JsonDefinitions.Actions.REMOVE:
                    handleRemoveDeviceMessage(jsonMessage);
                    break;
                    
                case JsonDefinitions.Actions.UNLOAD:
                    handleUnload(session);
                    break;
                    
                default:
                    throw new IllegalStateException(
                            "Unknown action: \"" + actionName + "\".");
            }
        }
    }
    
    /**
     * Creates and stores a new device and notifies all the clients of the newly
     * created device.
     * 
     * @param jsonObject the JSON object describing the new device.
     */
    private void handleCreateDeviceMessage(JsonObject jsonObject) {
        int deviceId = deviceCounter++;
        String deviceName = jsonObject.getString(JsonDefinitions.DEVICE_NAME);
        String deviceDescription = 
                jsonObject.getString(JsonDefinitions.DEVICE_DESCRIPTION);
        boolean deviceStatus = 
                jsonObject.getBoolean(JsonDefinitions.DEFICE_STATUS);
        
        Device device = new Device();
        
        device.setId(deviceId);
        device.setName(deviceName);
        device.setDescription(deviceDescription);
        device.setStatus(deviceStatus);
        
        MAP_DEVICE_ID_TO_DEVICE.put(device.getId(), device);
        String jsonMessage = getCreateDeviceMessageJson(device);
        broadcastMessageToAllConnectedSessions(jsonMessage);
    }
    
    /**
     * Handles the message for updating a device's information.
     * 
     * @param jsonObject the JSON object describing the device update action.
     */
    private void handleUpdateDeviceMessage(JsonObject jsonObject) {
        int deviceId = jsonObject.getInt(JsonDefinitions.DEVICE_ID);
        Device device = MAP_DEVICE_ID_TO_DEVICE.get(deviceId);
        
        if (device == null) {
            String errorMessageJson = 
                    getUpdateDeviceInformationFailureMessageJson(deviceId);
            broadcastMessageToAllConnectedSessions(errorMessageJson);
        } else {
            String deviceName = 
                    jsonObject.getString(JsonDefinitions.DEVICE_NAME);
            
            String deviceDescription = 
                    jsonObject.getString(JsonDefinitions.DEVICE_DESCRIPTION);
            
            boolean deviceStatus = 
                    jsonObject.getBoolean(JsonDefinitions.DEFICE_STATUS);
            
            // Update the device data:
            device.setName(deviceName);
            device.setDescription(deviceDescription);
            device.setStatus(deviceStatus);
           
            String jsonMessage = 
                    getUpdateDeviceInformationSuccessMessageJson(device);
            broadcastMessageToAllConnectedSessions(jsonMessage);
        }
    }
    
    /**
     * Deletes a device.
     * 
     * @param jsonObject the JSON object describing the device delete action.
     */
    private void handleRemoveDeviceMessage(JsonObject jsonObject) {
        int deviceId = jsonObject.getInt(JsonDefinitions.DEVICE_ID);
        Device device = MAP_DEVICE_ID_TO_DEVICE.get(deviceId);
        
        if (device == null) {
            String errorMessageJson = 
                    getRemoveDeviceFailureMessageJson(deviceId);
            broadcastMessageToAllConnectedSessions(errorMessageJson);
        } else {
            MAP_DEVICE_ID_TO_DEVICE.remove(deviceId);
            String jsonMessage = getRemoveDeviceSuccessMessageJson(device);
            broadcastMessageToAllConnectedSessions(jsonMessage);
        }
    }
    
    /**
     * This method is called when the user leaves the device list page, and 
     * therefore we need to remove the associated session from the session list.
     * 
     * @param session the session to remove.
     */
    private void handleUnload(Session session) {
        SESSIONS.remove(session);
    }
    
    /**
     * Sends the input text message to all connected sessions.
     * 
     * @param message the message to send.
     */
    private void broadcastMessageToAllConnectedSessions(String message) {
        for (Session session : SESSIONS)  {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException ex) {
                
            }
        }
    }
    
    /**
     * Composes the JSON message representing the event of adding a new device.
     * 
     * @param device the new device.
     * @return the JSON message representing the action.
     */
    String getCreateDeviceMessageJson(Device device) {
        return String.format(CREATE_DEVICE_MESSAGE_FORMAT,
                             JsonDefinitions.SUCCEEDED,
                             true,
                             JsonDefinitions.MESSAGE,
                             "A device \\\"" + 
                                     device.getName() +
                                     "\\\" is successfully added.",
                             JsonDefinitions.ACTION,
                             JsonDefinitions.Actions.CREATE,
                             JsonDefinitions.DEVICE_ID,
                             device.getId(),
                             JsonDefinitions.DEVICE_NAME,
                             device.getName(),
                             JsonDefinitions.DEVICE_DESCRIPTION,
                             device.getDescription(),
                             JsonDefinitions.DEFICE_STATUS,
                             device.getStatus());
    }
    
    /**
     * Composes the JSON message representing the event of successfully removing
     * a device from the list.
     * 
     * @param device the successfully removed device.
     * @return the JSON message.
     */
    String getRemoveDeviceSuccessMessageJson(Device device) {
        String message = 
                "Device \\\"" + 
                device.getName() + 
                "\\\" is successfully removed.";
        
        return String.format(REMOVE_DEVICE_MESSAGE_FORMAT,
                             JsonDefinitions.SUCCEEDED,
                             true,
                             JsonDefinitions.MESSAGE,
                             message,
                             JsonDefinitions.ACTION,
                             JsonDefinitions.Actions.REMOVE,
                             JsonDefinitions.DEVICE_ID, device.getId());
    }
    
    /**
     * Composes the JSON message representing the event of unsuccessfully 
     * removing a device from the list.
     * 
     * @param deviceId the unsuccessfully removed device.
     * @return the JSON message.
     */
    String getRemoveDeviceFailureMessageJson(int deviceId) {
        return String.format(REMOVE_DEVICE_MESSAGE_FORMAT,
                             JsonDefinitions.SUCCEEDED,
                             false,
                             JsonDefinitions.MESSAGE,
                             "No device with ID " + deviceId + ".",
                             JsonDefinitions.ACTION,
                             JsonDefinitions.Actions.REMOVE,
                             JsonDefinitions.DEVICE_ID, deviceId);
    }
    
    /**
     * Returns the JSON string describing a successful update of a device's 
     * information.
     * 
     * @param device the target device.
     * @return the JSON text.
     */
    String getUpdateDeviceInformationSuccessMessageJson(Device device) {
        String message =
                "Information of the device \\\"" +
                device.getName() +
                "\\\" is successfully updated.";
        
        return String.format(UPDATE_DEVICE_SUCCESS_INFORMATION_MESSAGE_FORMAT,
                             JsonDefinitions.SUCCEEDED,
                             JsonDefinitions.MESSAGE,
                             message,
                             JsonDefinitions.ACTION,
                             JsonDefinitions.Actions.UPDATE,
                             JsonDefinitions.DEVICE_ID,
                             device.getId(),
                             JsonDefinitions.DEVICE_NAME,
                             device.getName(),
                             JsonDefinitions.DEVICE_DESCRIPTION,
                             device.getDescription(),
                             JsonDefinitions.DEFICE_STATUS,
                             device.getStatus());
    }
    
    /**
     * Returns the JSON string describing an unsuccessful update of a device's
     * information.
     * 
     * @param device the target device.
     * @return the JSON text.
     */
    String getUpdateDeviceInformationFailureMessageJson(int deviceId) {
        String message = "There is no device with ID " + deviceId + ".";
        
        return String.format(UPDATE_DEVICE_FAILURE_INFORMATION_MESSAGE_FORMAT,
                             JsonDefinitions.SUCCEEDED,
                             JsonDefinitions.MESSAGE,
                             message,
                             JsonDefinitions.ACTION,
                             JsonDefinitions.Actions.UPDATE,
                             JsonDefinitions.DEVICE_ID,
                             deviceId);
    }
}
