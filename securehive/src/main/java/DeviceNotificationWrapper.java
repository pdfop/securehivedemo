import com.google.gson.*; 
import org.joda.time.DateTime;

public class DeviceNotificationWrapper
{
    private Long id;

    private String notification;

    private String deviceId;

    private Long networkId;

    private DateTime timestamp;

    private JsonObject parameters = new JsonObject();

    public DeviceNotificationWrapper(Long id, String notification, String deviceId, Long networkId, DateTime timestamp, JsonObject parameters) {
        this.id = id;
        this.notification = notification;
        this.deviceId = deviceId;
        this.networkId = networkId;
        this.timestamp = timestamp;
        this.parameters = parameters;
    }

    public Long getId() {
        return id;
    }

    public String getNotification() {
        return notification;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public DateTime getTimestamp() {
        return timestamp;
    }

    public JsonObject getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "{\n\"DeviceNotification\":{\n"
                + "\"id\":\"" + id + "\""
                + ",\n \"notification\":\"" + notification + "\""
                + ",\n \"deviceId\":\"" + deviceId + "\""
                + ",\n \"networkId\":\"" + networkId + "\""
                + ",\n \"timestamp\":" + timestamp
                + ",\n \"parameters\":" + parameters
                + "}\n}";
    }
}
