# P00-GIT evidence

## Scope

- Agent: `/root/p00_git_publish`
- Base revision: no repository / no commit
- Owned paths: Git metadata, remote repository, `.gitignore`, and P00 coordination ledgers
- Product source and specifications were not modified.

## Verified actions

| Action | Exit | Result |
|---|---:|---|
| `sudo su -c 'git status --short --branch'` before initialization | 128 | Confirmed the workspace was not a Git repository |
| `sudo su -c 'gh auth status'` | 0 | Root is authenticated as `LangYa466`; token value was not persisted |
| `sudo su -c 'gh repo view LangYa466/Javelle ...'` before creation | 1 | Confirmed the requested repository did not exist |
| `sudo su -c 'git init -b main'` | 0 | Initialized local repository with unborn `main` HEAD |
| `sudo su -c 'gh repo create Javelle --private --source=. --remote=origin'` | 0 | Created private repository and configured `origin` |
| `sudo su -c 'gh repo view LangYa466/Javelle --json name,visibility,url,defaultBranchRef'` | 0 | URL `https://github.com/LangYa466/Javelle`; visibility `PRIVATE`; no default branch because no commit exists |
| JSON parse, Git root/HEAD/refs/remote, remote metadata, ignore rules, and status probe | 0 overall | Both ledgers parse; root and symbolic `main` resolve; concrete `main`/`dev` refs are absent as expected; remote and ignore rules match |

## Branch and publication status

- Local `main`: unborn; no commits.
- Local/remote `dev`: not yet materializable because Git branch references require a commit object.
- Remote: `origin` points to `https://github.com/LangYa466/Javelle.git` for fetch and push.
- Commit/push: blocked because both Git `user.name` and `user.email` are unset. Project policy forbids inventing or silently configuring identity.
- No empty/fake commit and no push were performed.

## Required continuation

The user explicitly authorized public publication and supplied `langya466@gmail.com`. GitHub API returned login `LangYa466` and name `狼牙`; these values were configured with repository-local Git config only.

| Action | Exit | Result |
|---|---:|---|
| `gh repo edit LangYa466/Javelle --visibility public --accept-visibility-change-consequences` | 0 | Repository visibility changed to `PUBLIC` |
| repository-local `git config user.name/user.email` | 0 | `狼牙` / `langya466@gmail.com`; global config was not modified |

Commit SHA and branch verification are appended after the initial commit is created.
