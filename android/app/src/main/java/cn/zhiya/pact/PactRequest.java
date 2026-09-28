package cn.zhiya.pact;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** A failed write retries its exact request; changed input receives a new identity. */
final class PactRequest {
    private String fingerprint = "", requestId = "";
    JSONObject prepare(JSONObject body) throws JSONException {
        String next = body.toString();
        if (!next.equals(fingerprint)) { fingerprint = next; requestId = UUID.randomUUID().toString(); }
        JSONObject copy = new JSONObject(next); copy.put("requestId",requestId); return copy;
    }
}
