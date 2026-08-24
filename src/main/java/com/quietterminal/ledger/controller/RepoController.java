package com.quietterminal.ledger.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.entity.Repo;
import com.quietterminal.ledger.error.GitPathInvalidException;
import com.quietterminal.ledger.error.GitResourceNotFoundException;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.git.GitRepoService;
import com.quietterminal.ledger.git.GitRepoService.FileContent;
import com.quietterminal.ledger.git.GitRepoService.TreeEntry;

@RestController
@RequestMapping("/repos")
public class RepoController {

    private final GitRepoService gitRepoService;

    public RepoController(GitRepoService gitRepoService) {
        this.gitRepoService = gitRepoService;
    }

    @GetMapping
    public List<Repo> listRepos() {
        return gitRepoService.listReposForAllListedUsers();
    }

    @GetMapping("/{owner}")
    public List<Repo> listReposForOwner(@PathVariable("owner") String owner) {
        return gitRepoService.listPublicReposForOwner(owner);
    }

    @GetMapping("/{owner}/{repo}/tree")
    public List<TreeEntry> tree(@PathVariable("owner") String owner, @PathVariable("repo") String repo,
            @RequestParam(name = "path", defaultValue = "") String path) {
        return gitRepoService.listTree(owner, repo, path);
    }

    @GetMapping("/{owner}/{repo}/file")
    public FileContent file(@PathVariable("owner") String owner, @PathVariable("repo") String repo,
            @RequestParam("path") String path) {
        return gitRepoService.getFile(owner, repo, path);
    }

    @ExceptionHandler(LedgerError.class)
    public ResponseEntity<String> handleLedgerError(LedgerError e) {
        HttpStatus status = e instanceof GitResourceNotFoundException ? HttpStatus.NOT_FOUND
                : e instanceof GitPathInvalidException ? HttpStatus.BAD_REQUEST
                        : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(e.getMessage());
    }
}
