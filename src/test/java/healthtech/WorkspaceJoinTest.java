package healthtech;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WorkspaceJoinTest {
    public static void main(String[] args) {
        Map<String, Object> input = Map.of("domain", "clinic.example", "email", "staff@clinic.example",
            "txtName", "_ownership.clinic.example", "txtValue", "proof", "appointmentRef", "appt-1042");
        List<String> calls = new ArrayList<>();
        WorkspaceJoin verified = new WorkspaceJoin((method, path, fields) -> {
            calls.add(method + " " + path);
            if (path.equals("/v1/dns/domain/get")) return Map.of("zone_id", "zone-7");
            if (path.equals("/v1/dns/record/upsert") && !fields.get("zone_id").equals("zone-7"))
                throw new AssertionError("Record must use the returned zone ID");
            return Map.of();
        });
        Map<String, Object> result = verified.join(input);
        if (!result.get("workspace").equals("clinic.example") ||
            !JsonCodec.object(result.get("notification")).get("appointmentRef").equals("appt-1042") ||
            calls.size() != 4 || !calls.get(3).equals("POST /v1/auth/user/create"))
            throw new AssertionError("Verified employee should join with appointment event");

        List<String> rejectedCalls = new ArrayList<>();
        WorkspaceJoin rejected = new WorkspaceJoin((method, path, fields) -> {
            rejectedCalls.add(path);
            if (path.equals("/v1/dns/domain/get")) return Map.of("zone_id", "zone-7");
            if (path.equals("/v1/dns/domain/verify"))
                throw new InfraiDirectory.ApiError(422, "PROOF_REJECTED", "Domain proof rejected");
            return Map.of();
        });
        try {
            rejected.join(input);
            throw new AssertionError("Unverified domain must not join");
        } catch (InfraiDirectory.ApiError expected) {
            if (rejectedCalls.contains("/v1/auth/user/create"))
                throw new AssertionError("Unverified employee was added");
        }
        System.out.println("Workspace join decision: PASS");
    }
}
