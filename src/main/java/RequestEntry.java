import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

public class RequestEntry {
    private static int idCounter = 0;
    private final int id;
    private final HttpRequest request;
    private HttpRequest editedRequest;
    private HttpResponse response;
    private HttpResponse originalResponse;
    private int similarity = 0; // Nuevo campo

    public RequestEntry(HttpRequest request) {
        this.request = request;
        this.id = idCounter++;
    }

    public int getId() { return id; }
    public HttpRequest getRequest() { return request; }

    public HttpRequest getEditedRequest() { return editedRequest; }
    public void setEditedRequest(HttpRequest editedRequest) { this.editedRequest = editedRequest; }

    public HttpResponse getResponse() { return response; }
    public void setResponse(HttpResponse response) { this.response = response; }

    public void setOriginalResponse(HttpResponse response) { this.originalResponse = response; }
    public HttpResponse getOriginalResponse() { return originalResponse; }

    public int getSimilarity() { return similarity; }
    public void setSimilarity(int similarity) { this.similarity = similarity; }

    public String getHost() {
        try { return new java.net.URL(request.url()).getHost(); } catch (Exception e) { return ""; }
    }
    public String getMethod() { return request.method(); }
    public int getStatusCode() { return response != null ? response.statusCode() : 0; }
    public int getResponseLength() { return response != null ? response.body().length() : 0; }
}