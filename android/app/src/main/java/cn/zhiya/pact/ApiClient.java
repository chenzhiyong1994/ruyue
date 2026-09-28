package cn.zhiya.pact;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small asynchronous HTTP client; credentials are never placed in URLs or logs. */
public final class ApiClient {
    public interface Callback<T> { void success(T result); void failure(ApiError error); }
    public interface Work<T> { T run() throws Exception; }
    public static final class ApiError extends Exception {
        public final int status;
        public ApiError(int status, String message) { super(message); this.status = status; }
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile String baseUrl;
    private volatile String token;
    private volatile int generation;
    private volatile boolean closed;
    private final ThreadLocal<RequestContext> context = new ThreadLocal<>();
    private static final class RequestContext {
        final String baseUrl, token;
        final int generation;
        RequestContext(String baseUrl, String token, int generation) {
            this.baseUrl = baseUrl; this.token = token; this.generation = generation;
        }
    }
    public ApiClient(String baseUrl, String token) { this.baseUrl = baseUrl; this.token = token; }
    public synchronized void configure(String baseUrl, String token) {
        generation++; this.baseUrl = baseUrl; this.token = token;
    }
    /** Close the activity-owned worker and prevent old callbacks from painting a new session. */
    public synchronized void close() {
        closed = true; generation++; worker.shutdownNow();
    }

    public synchronized <T> void async(Work<T> work, Callback<T> callback) {
        final int expectedGeneration = generation;
        final RequestContext requestContext = new RequestContext(baseUrl, token, expectedGeneration);
        if (closed) return;
        worker.execute(() -> {
            if (closed || generation != expectedGeneration) return;
            context.set(requestContext);
            try {
                T result = work.run();
                main.post(() -> { if (!closed && generation == expectedGeneration) callback.success(result); });
            }
            catch (Exception e) {
                ApiError error = e instanceof ApiError ? (ApiError)e :
                        new ApiError(0, e instanceof IOException ? "连接未完成。请检查网络后重试，输入已保留。" : "操作未完成，请检查输入后重试。");
                main.post(() -> { if (!closed && generation == expectedGeneration) callback.failure(error); });
            } finally { context.remove(); }
        });
    }

    public JSONObject call(String path, JSONObject body) throws Exception {
        byte[] response = request(path, body);
        return new JSONObject(new String(response, StandardCharsets.UTF_8));
    }
    public byte[] media(String id) throws Exception { return request("/v1/media/" + id, null); }

    private byte[] request(String path, JSONObject body) throws Exception {
        RequestContext requestContext = context.get();
        if (closed || (requestContext != null && requestContext.generation != generation))
            throw new ApiError(0, "登录状态已经改变，请重试");
        String origin = requestContext == null ? baseUrl : requestContext.baseUrl;
        String credential = requestContext == null ? token : requestContext.token;
        if (origin == null || origin.isEmpty()) throw new ApiError(0, "尚未配置服务地址");
        HttpURLConnection connection = (HttpURLConnection) new URL(origin + path).openConnection();
        try {
            connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            if (!credential.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + credential);
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(data.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(data); }
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            byte[] response = read(input, 8 * 1024 * 1024);
            if (status < 200 || status >= 300) {
                String message = "请求失败（" + status + "），请稍后重试";
                try { message = new JSONObject(new String(response, StandardCharsets.UTF_8)).getJSONObject("error").getString("message"); }
                catch (org.json.JSONException ignored) { /* A non-JSON gateway error still reports its status. */ }
                throw new ApiError(status, message);
            }
            return response;
        } finally { connection.disconnect(); }
    }

    private static byte[] read(InputStream input, int limit) throws IOException {
        if (input == null) return new byte[0];
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > limit) throw new IOException("Response too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
