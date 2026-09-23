package healthtech;

import java.util.LinkedHashMap;
import java.util.Map;

final class WorkspaceJoin {
    interface Directory {
        Map<String, Object> call(String method, String path, Map<String, Object> fields);
    }

    private final Directory directory;

    WorkspaceJoin(Directory directory) { this.directory = directory; }

    Map<String, Object> join(Map<String, Object> request) {
        String domain = required(request, "domain").toLowerCase(java.util.Locale.ROOT);
        String email = required(request, "email").toLowerCase(java.util.Locale.ROOT);
        String name = required(request, "txtName");
        String value = required(request, "txtValue");
        String appointment = required(request, "appointmentRef");
        if (!email.endsWith("@" + domain) || domain.indexOf('.') < 1 || appointment.length() > 80)
            throw new IllegalArgumentException("Email domain or appointment reference is invalid");

        Map<String, Object> zone = directory.call("GET", "/v1/dns/domain/get", Map.of("domain", domain));
        Object zoneId = zone.get("zone_id");
        if (zoneId == null || String.valueOf(zoneId).isBlank())
            throw new IllegalStateException("Domain response has no zone_id");
        directory.call("PUT", "/v1/dns/record/upsert", Map.of(
            "zone_id", zoneId, "record_type", "TXT", "name", name, "content", value));
        directory.call("POST", "/v1/dns/domain/verify", Map.of("domain", domain));

        // The same verified domain is the directory's workspace identifier.
        directory.call("POST", "/v1/auth/user/create", Map.of(
            "email", email, "metadata", Map.of("workspace", domain),
            "idempotency_key", "workspace-join:" + email));
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("appointmentRef", appointment);
        notification.put("kind", "APPOINTMENT_OPERATIONS");
        notification.put("message", "Appointment workflow ready for staff review");
        return Map.of("workspace", domain, "email", email, "notification", notification);
    }

    private static String required(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (!(value instanceof String) || ((String) value).isBlank())
            throw new IllegalArgumentException("Missing " + field);
        return (String) value;
    }
}
