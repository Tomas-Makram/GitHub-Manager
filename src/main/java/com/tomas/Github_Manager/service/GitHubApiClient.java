package com.tomas.Github_Manager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// The primary client for interacting with the GitHub REST API
@Component
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private static final String GITHUB_API_URL = "https://api.github.com";
    private static final int MAX_RETRIES = 4;
    private static final int MAX_PAGES = 10;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    // Initializes the RestClient
    public GitHubApiClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMinutes(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMinutes(5));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    //  Returns the RestClient
    public RestClient restClient() {
        return restClient;
    }

    // =====================================================================
    // Static helpers
    // =====================================================================

    // URL-encodes the branch name; returns "main" if it is empty
    public static String encodeBranch(String branch) {
        if (branch == null || branch.isEmpty()) return "main";
        return URLEncoder.encode(branch, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // URL-encodes each part of the file path while preserving the "/" separators between the parts
    public static String encodePath(String path) {
        return Arrays.stream(path.split("/"))
                .map(seg -> URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    // Constructs a full GitHub API URI from a pre-encoded path
    public static URI uri(String path) {
        return UriComponentsBuilder.fromUriString(GITHUB_API_URL + path).build(true).toUri();
    }

    //The repository path is returned in the API in the format /repos/{owner}/{repo}
    public static String repoPath(String owner, String repo) {
        return "/repos/" + owner + "/" + repo;
    }

    //Adds the Bearer token and GitHub-specific Accept header to the request headers
    private static void authHeaders(HttpHeaders headers, String token) {
        headers.setBearerAuth(token);
        headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
    }

    //Reads a nested string value within maps using a sequence of keys
    // OR returns null if it does not exist
    public static String nestedString(Map<?, ?> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> m)) return null;
            current = m.get(key);
        }
        return current instanceof String s ? s : null;
    }

    // =====================================================================
    // Calls
    // =====================================================================

   //Executes a single request to GitHub and returns the response as a Map
    public Map<String, Object> call(HttpMethod method, String token, String path, Object body) {
        Supplier<Map<String, Object>> action = () -> {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(uri(path))
                    .headers(h -> authHeaders(h, token));
            if (body != null) spec = spec.body(body);
            return spec.retrieve().body(MAP_TYPE);
        };

        if (method == HttpMethod.POST) {
            return action.get();
        }
        return withRetry(action);
    }

    //Fetches a single page from a list (such as repositories), with retries in case of transient errors
    private List<Map<String, Object>> callList(String token, String path) {
        return withRetry(() -> restClient.get()
                .uri(uri(path))
                .headers(h -> authHeaders(h, token))
                .retrieve()
                .body(LIST_TYPE));
    }

    // Fetches all pages (100 per page), up to a maximum of MAX_PAGES
    public List<Map<String, Object>> callAllPages(String token, String path) {
        List<Map<String, Object>> all = new ArrayList<>();
        String sep = path.contains("?") ? "&" : "?";
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<Map<String, Object>> batch = callList(token, path + sep + "per_page=100&page=" + page);
            if (batch == null || batch.isEmpty()) break;
            all.addAll(batch);
            if (batch.size() < 100) break;
        }
        return all;
    }

    // ---- retry / backoff ------------------------------------------------

    //Executes the operation
    // if it fails due to a transient error (such as a rate limit or network issue), it retries with an increasing delay
    public <T> T withRetry(Supplier<T> action) {
        for (int attempt = 0; ; attempt++) {
            try {
                return action.get();
            } catch (RestClientResponseException e) {
                if (attempt >= MAX_RETRIES || !isRetryable(e)) throw e;
                long wait = backoffMillis(e, attempt);
                log.warn("GitHub API error HTTP {} - retry {}/{} in {} ms", e.getStatusCode().value(), attempt + 1, MAX_RETRIES, wait);
                sleepQuietly(wait);
            } catch (ResourceAccessException e) {
                // Network outage or transient timeout
                if (attempt >= MAX_RETRIES) throw e;
                long wait = Math.min(1000L << attempt, 10_000L) + ThreadLocalRandom.current().nextLong(250);
                log.warn("Network glitch to GitHub - retry {}/{} in {} ms: {}", attempt + 1, MAX_RETRIES, wait, e.getMessage());
                sleepQuietly(wait);
            }
        }
    }

    // Determines whether this error warrants a retry (429, 502/503/504, or 403 due to a rate limit)
    private static boolean isRetryable(RestClientResponseException e) {
        int s = e.getStatusCode().value();
        if (s == 429 || s == 502 || s == 503 || s == 504) return true;
        return s == 403 && e.getResponseBodyAsString().toLowerCase().contains("rate limit");
    }

    // Calculates the wait time before the next attempt: based on the 'Retry-After' header (if present), or using an incremental approach with a touch of randomness
    private static long backoffMillis(RestClientResponseException e, int attempt) {
        HttpHeaders headers = e.getResponseHeaders();
        String retryAfter = headers == null ? null : headers.getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return Math.min(Long.parseLong(retryAfter.trim()), 60L) * 1000L;
            } catch (NumberFormatException ignored) { /* fall through */ }
        }
        return Math.min(1000L << attempt, 30_000L) + ThreadLocalRandom.current().nextLong(250);
    }

    //It sleeps for the specified duration
    // if the thread is interrupted, it resets the interrupt flag and throws an `IllegalStateException`
    public static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}