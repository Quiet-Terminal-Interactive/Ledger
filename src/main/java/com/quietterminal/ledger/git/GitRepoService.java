package com.quietterminal.ledger.git;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import com.quietterminal.ledger.entity.Repo;
import com.quietterminal.ledger.error.GitPathInvalidException;
import com.quietterminal.ledger.error.GitResourceNotFoundException;
import com.quietterminal.ledger.error.GitUpstreamException;
import com.quietterminal.ledger.integration.Integration;
import com.quietterminal.ledger.integration.IntegrationKind;

import tools.jackson.databind.JsonNode;

@Service
public class GitRepoService implements Integration {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiToken;
    private final Set<String> listedUsers;

    public GitRepoService(RestTemplate restTemplate,
            @Value("${ledger.git.url}") String baseUrl,
            @Value("${ledger.git.api-token:}") String apiToken,
            @Value("${ledger.git.listed-users:}") String listedUsersRaw) {
        this.restTemplate = restTemplate;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiToken = apiToken;
        this.listedUsers = parseListedUsers(listedUsersRaw);
    }

    @Override
    public String id() {
        return "git";
    }

    @Override
    public String displayName() {
        return "Git (Gitea/Forgejo)";
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OAUTH_APP;
    }

    public List<Repo> listReposForAllListedUsers() {
        return listedUsers.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .flatMap(owner -> listPublicReposForUser(owner).stream())
                .toList();
    }

    public List<Repo> listPublicReposForOwner(String owner) {
        requireListedUser(owner);
        return listPublicReposForUser(owner);
    }

    public List<TreeEntry> listTree(String owner, String repo, String path) {
        requireListedUser(owner);
        requirePublicRepo(owner, repo);
        validatePath(path);

        JsonNode node = fetchContents(owner, repo, path);
        if (node == null) {
            throw new GitResourceNotFoundException("No path found at '" + path + "'.");
        }
        if (!node.isArray()) {
            throw new GitPathInvalidException(
                    "Path '" + path + "' is a file, not a directory. Use the file endpoint instead.");
        }

        List<TreeEntry> entries = new ArrayList<>();
        for (JsonNode entry : node) {
            entries.add(new TreeEntry(entry.path("name").asString(), entry.path("path").asString(),
                    entry.path("type").asString(), entry.path("size").asLong(0)));
        }
        return entries;
    }

    public FileContent getFile(String owner, String repo, String path) {
        if (path == null || path.isBlank()) {
            throw new GitPathInvalidException("A file path is required.");
        }
        requireListedUser(owner);
        requirePublicRepo(owner, repo);
        validatePath(path);

        JsonNode node = fetchContents(owner, repo, path);
        if (node == null) {
            throw new GitResourceNotFoundException("No file found at '" + path + "'.");
        }
        if (node.isArray()) {
            throw new GitPathInvalidException(
                    "Path '" + path + "' is a directory, not a file. Use the tree endpoint instead.");
        }

        return new FileContent(node.path("name").asString(), node.path("path").asString(), node.path("size").asLong(0),
                node.path("encoding").asString(null), node.path("content").asString(null));
    }

    private List<Repo> listPublicReposForUser(String owner) {
        JsonNode node = fetchUsersRepos(owner);
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<Repo> repos = new ArrayList<>();
        for (JsonNode repoNode : node) {
            if (repoNode.path("private").asBoolean(true)) {
                continue;
            }
            String repoOwner = repoNode.path("owner").path("login").asString(owner);
            repos.add(new Repo(repoOwner, repoNode.path("name").asString(),
                    repoNode.path("description").asString(null), repoNode.path("default_branch").asString(null)));
        }
        return repos;
    }

    private void requireListedUser(String owner) {
        boolean listed = listedUsers.stream().anyMatch(u -> u.equalsIgnoreCase(owner));
        if (!listed) {
            throw new GitResourceNotFoundException("No repository owner found for '" + owner + "'.");
        }
    }

    private void requirePublicRepo(String owner, String repo) {
        JsonNode detail = fetchRepoDetail(owner, repo);
        if (detail == null || detail.path("private").asBoolean(true)) {
            throw new GitResourceNotFoundException("No repository found at '" + owner + "/" + repo + "'.");
        }
    }

    private JsonNode fetchUsersRepos(String owner) {
        URI uri = URI.create(baseUrl + "/api/v1/users/" + encodeSegment(owner) + "/repos?limit=50");
        try {
            return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(authHeaders()), JsonNode.class)
                    .getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw new GitUpstreamException("Failed to reach git host: " + e.getMessage());
        }
    }

    private JsonNode fetchRepoDetail(String owner, String repo) {
        URI uri = URI.create(baseUrl + "/api/v1/repos/" + encodeSegment(owner) + "/" + encodeSegment(repo));
        try {
            return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(authHeaders()), JsonNode.class)
                    .getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new GitResourceNotFoundException("No repository found at '" + owner + "/" + repo + "'.");
        } catch (RestClientException e) {
            throw new GitUpstreamException("Failed to reach git host: " + e.getMessage());
        }
    }

    private JsonNode fetchContents(String owner, String repo, String path) {
        URI uri = URI.create(contentsUrl(owner, repo, path));
        try {
            return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(authHeaders()), JsonNode.class)
                    .getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw new GitUpstreamException("Failed to reach git host: " + e.getMessage());
        }
    }

    private String contentsUrl(String owner, String repo, String path) {
        StringBuilder sb = new StringBuilder(baseUrl).append("/api/v1/repos/")
                .append(encodeSegment(owner)).append('/').append(encodeSegment(repo)).append("/contents");
        if (path != null && !path.isBlank()) {
            for (String segment : path.split("/")) {
                if (!segment.isBlank()) {
                    sb.append('/').append(encodeSegment(segment));
                }
            }
        }
        return sb.toString();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "token " + apiToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private static void validatePath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new GitPathInvalidException("Path cannot contain '.' or '..' segments.");
            }
        }
    }

    private static String encodeSegment(String segment) {
        return UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static Set<String> parseListedUsers(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public record TreeEntry(String name, String path, String type, long size) {
    }

    public record FileContent(String name, String path, long size, String encoding, String content) {
    }
}
