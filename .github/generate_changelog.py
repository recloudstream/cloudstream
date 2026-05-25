#!/usr/bin/env python3

import argparse
import json
import os
import re
import secrets
import subprocess
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field


MERGE_RE = re.compile(r'^Merge pull request #\d+ from |^Merge (remote-tracking )?branch ')
CC_HEADER_RE = re.compile(r'^([a-z]+)(\([^)]*\))?(!)?:(.*)$')
BREAKING_BODY_RE = re.compile(r'^BREAKING\s+CHANGES?:\s+', re.MULTILINE)

CC_TYPES: dict[str, str] = {
    'feat':     'Features',
    'fix':      'Bug Fixes',
    'docs':     'Documentation',
    'style':    'Styles',
    'refactor': 'Code Refactoring',
    'perf':     'Performance Improvements',
    'test':     'Tests',
    'build':    'Builds',
    'ci':       'Continuous Integration',
    'chore':    'Chores',
    'revert':   'Reverts',
}


@dataclass
class Commit:
    sha: str
    subject: str
    author: str
    url: str
    body: str
    cc_type: str
    scope: str
    parsed_subject: str
    breaking: bool
    prs: list[dict] = field(default_factory=list)

    @property
    def known_type(self) -> str:
        return self.cc_type if self.cc_type in CC_TYPES else ''

    @property
    def short_sha(self) -> str:
        return self.sha[:7]

    @property
    def entry(self) -> str:
        pr_string = ''
        if self.prs:
            parts = [f"[#{pr['number']}]({pr['html_url']})" for pr in self.prs]
            pr_string = ' ' + ','.join(parts)

        if self.known_type:
            scope_str = f'**{self.scope}**: ' if self.scope else ''
            return f'- {scope_str}{self.parsed_subject}{pr_string} ([{self.author}]({self.url}))'
        else:
            return f'- {self.short_sha}: {self.subject} ({self.author}){pr_string}'


class ChangelogGenerator:
    def __init__(self, token: str, repository: str, sha: str, ref: str, output_path: str):
        self.token = token
        self.owner, self.repo = repository.split('/', 1)
        self.sha = sha
        self.ref = ref
        self.output_path = output_path

    def log(self, msg: str) -> None:
        print(f'[generate_changelog] {msg}', file=sys.stderr)

    def git(self, *args: str) -> str:
        return subprocess.check_output(['git', *args], text=True).strip()

    def gh_api(self, endpoint: str) -> list | dict:
        req = urllib.request.Request(
            f'https://api.github.com{endpoint}',
            headers={
                'Authorization': f'token {self.token}',
                'Accept': 'application/vnd.github.v3+json',
            },
        )
        try:
            with urllib.request.urlopen(req) as resp:
                return json.loads(resp.read())
        except urllib.error.HTTPError as e:
            self.log(f'API request failed for {endpoint}: {e}')
            return []

    def current_tag(self) -> str:
        m = re.match(r'^refs/tags/(.+)$', self.ref)
        if m:
            tag = m.group(1)
            self.log(f'Detected tag from GITHUB_REF: {tag}')
            return tag
        try:
            return self.git('describe', '--exact-match', self.sha)
        except subprocess.CalledProcessError:
            return ''

    def find_previous_tag(self, current_tag: str) -> str:
        self.log(f'Searching for previous semver tag before {current_tag}')

        def parse_semver(tag: str) -> tuple[int, ...]:
            parts = re.split(r'[.\-]', re.sub(r'^v', '', tag))
            result = []
            for p in parts[:3]:
                try:
                    result.append(int(p))
                except ValueError:
                    result.append(0)
            return tuple(result)

        semver_re = re.compile(r'^v?\d+\.\d+\.\d+')
        tags = [t for t in self.git('tag', '--list').splitlines() if semver_re.match(t)]
        if not tags:
            self.log('No semver tags found')
            return ''

        current_ver = parse_semver(current_tag)
        candidates = sorted(
            [t for t in tags if t != current_tag and parse_semver(t) < current_ver],
            key=parse_semver,
            reverse=True,
        )
        return candidates[0] if candidates else ''

    def ref_branch(self) -> str:
        m = re.match(r'^refs/heads/(.+)$', self.ref)
        return m.group(1) if m else ''

    def tag_exists(self, tag: str) -> bool:
        try:
            self.git('fetch', '--depth=1', '--no-tags', 'origin', f'refs/tags/{tag}:refs/tags/{tag}')
            self.git('fetch', '--no-tags', f'--shallow-exclude={tag}', 'origin', f'refs/heads/{self.ref_branch()}')
            return True
        except subprocess.CalledProcessError:
            self.log(f'Tag {tag} not found, falling back to full history')
            return False

    def get_raw_commits(self, base: str) -> list[tuple[str, str]]:
        if base:
            self.log(f'Getting commits between {base} and {self.sha}')
            raw = self.git('log', '--format=%H %s', '--max-count=1000', f'{base}..{self.sha}')
        else:
            self.log(f'No previous tag - using full history to {self.sha}')
            raw = self.git('log', '--format=%H %s', '--max-count=1000', self.sha)
        return [(line.split(' ', 1)[0], line.split(' ', 1)[1]) for line in raw.splitlines() if line.strip()]

    def parse_commit(self, sha: str, subject: str) -> Commit | None:
        if MERGE_RE.match(subject):
            self.log(f'Skipping merge commit: {sha}')
            return None

        self.log(f'Processing commit {sha}: {subject}')

        author = self.git('log', '-1', '--format=%an', sha) or 'unknown'
        url = f'https://github.com/{self.owner}/{self.repo}/commit/{sha}'
        body = self.git('log', '-1', '--format=%b', sha)

        cc_type, scope, parsed_subject = '', '', ''
        m = CC_HEADER_RE.match(subject)
        if m:
            cc_type = m.group(1)
            scope = m.group(2)[1:-1] if m.group(2) else ''
            parsed_subject = m.group(4).strip()

        breaking = bool(
            re.match(r'^[a-z]+(\([^)]*\))?!:', subject)
            or BREAKING_BODY_RE.search(body or '')
        )

        self.log(f'Fetching PRs for {sha}')
        prs = self.gh_api(f'/repos/{self.owner}/{self.repo}/commits/{sha}/pulls')

        return Commit(
            sha=sha,
            subject=subject,
            author=author,
            url=url,
            body=body,
            cc_type=cc_type,
            scope=scope,
            parsed_subject=parsed_subject,
            breaking=breaking,
            prs=prs if isinstance(prs, list) else [],
        )

    def build_changelog(self, commits: list) -> str:
        breaking = [c.entry for c in commits if c.breaking]
        by_type = {k: [c.entry for c in commits if c.known_type == k] for k in CC_TYPES}
        other = [c.entry for c in commits if not c.known_type]

        sections: list[str] = []

        if breaking:
            sections.append('## Breaking Changes\n' + '\n'.join(breaking))

        for key, label in CC_TYPES.items():
            if by_type[key]:
                sections.append(f'## {label}\n' + '\n'.join(by_type[key]))

        if other:
            sections.append('## Commits\n' + '\n'.join(other))

        return '\n\n'.join(sections).strip()

    def write_output(self, changelog: str) -> None:
        delimiter = secrets.token_hex(16)
        with open(self.output_path, 'a') as f:
            f.write(f'changelog<<{delimiter}\n{changelog}\n{delimiter}\n')
        self.log("Changelog written to GITHUB_OUTPUT as 'changelog'")

    def run(self, previous_tag: str = '') -> None:
        tag = self.current_tag()
        if previous_tag and not self.tag_exists(previous_tag):
            previous_tag = ''
        if not previous_tag and tag:
            previous_tag = self.find_previous_tag(tag)

        self.log(f"Previous tag: {previous_tag or '<none>'}")

        raw_commits = self.get_raw_commits(previous_tag)
        commits = [c for sha, subject in raw_commits if (c := self.parse_commit(sha, subject))]

        changelog = self.build_changelog(commits) if commits else ''
        self.write_output(changelog)


def require_env(name: str) -> str:
    val = os.environ.get(name, '')
    if not val:
        print(f'[generate_changelog] ERROR: {name} is not set', file=sys.stderr)
        sys.exit(1)
    return val


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Generate a changelog and write it to GITHUB_OUTPUT.')
    parser.add_argument('--previous-tag', default='', help='Tag to compare from (auto-detected if omitted)')
    args = parser.parse_args()

    ChangelogGenerator(
        token=require_env('GITHUB_TOKEN'),
        repository=require_env('GITHUB_REPOSITORY'),
        sha=os.environ.get('GITHUB_SHA') or subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
        ref=os.environ.get('GITHUB_REF', ''),
        output_path=require_env('GITHUB_OUTPUT'),
    ).run(previous_tag=args.previous_tag)
