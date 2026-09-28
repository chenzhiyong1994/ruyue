"""Validate the standalone public project's documentation and source boundaries."""

from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = (
    "README.md", "README.zh-CN.md", "AGENTS.md",
    "docs/prd.md", "docs/api.md", "docs/architecture.md", "docs/acceptance.md",
    "docs/user-guide.md", "docs/development.md", "docs/testing.md", "site/index.html",
    "server/service.py", "server/http.py", "tests/test_service.py", "tests/test_http.py",
    "tests/test_contract.py", "android/app/build.gradle", "android/build.gradle",
    "android/settings.gradle", "android/gradlew", "android/gradlew.bat",
    "android/gradle/wrapper/gradle-wrapper.jar",
    "android/gradle/wrapper/gradle-wrapper.properties", "scripts/android.ps1",
)
RETIRED = (
    "content", "dist", "android/app/src/main/java/cn/zhiya/learning",
    "android/app/src/test/java/cn/zhiya/learning",
    "android/app/src/androidTest/java/cn/zhiya/learning",
    "android/app/src/main/assets/lessons",
    "scripts/content_review.py", "scripts/generate_android_test_content.py",
    "scripts/generate_answer_sounds.py", "scripts/package_android.py",
    "scripts/smoke_android.py", "scripts/sync_report.py",
    "scripts/validate_android_content.py", "tests/learning.test.mjs",
    "tests/test_android_content.py", "tests/test_content_review.py",
    "tests/test_android_package.py", "tests/validate_static.py",
)
OLD_BUILD_TOKENS = (
    "cn.zhiya.learning", "cn/zhiya/learning", "prepareLearningAssets", "verifyPreviewContent",
    "verifyReleaseContent", "generateContentTest", "generate_android_test_content",
    "validate_android_content", "course-assets", "coursePackage",
    "GrowthRegression", "DesignRegression", "growthRegression", "designRegression",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read(path):
    return path.read_text(encoding="utf-8-sig")


def check_link(root, path, target, boundary=None):
    target = target.strip()
    if not target:
        return 0
    parts = urlsplit(target)
    require(parts.scheme.lower() != "file" and not re.match(r"^[A-Za-z]:", target),
            f"Nonportable local link in {path.relative_to(root)}: {target}")
    if parts.scheme or parts.netloc or not parts.path:
        return 0
    decoded = unquote(parts.path)
    require(not decoded.startswith(("/", "\\")) and "\\" not in decoded,
            f"Use a portable relative link in {path.relative_to(root)}: {target}")
    destination = (path.parent / decoded).resolve()
    limit = boundary or root
    require(destination.is_relative_to(limit), f"Link escapes its published tree in {path.relative_to(root)}: {target}")
    require(destination.exists(), f"Missing local link in {path.relative_to(root)}: {target}")
    if boundary and destination.is_dir():
        require((destination / "index.html").is_file(), f"Site directory has no index.html: {target}")
    return 1


def markdown_links(path):
    source = read(path)
    fence = None
    visible = []
    for line in source.splitlines():
        match = re.match(r"^\s{0,3}(`{3,}|~{3,})", line)
        if match:
            marker = match.group(1)
            if fence is None:
                fence = marker
            elif marker[0] == fence[0] and len(marker) >= len(fence):
                fence = None
        elif fence is None:
            visible.append(line)
    require(fence is None, f"Unclosed code fence: {path.name}")
    source = "\n".join(visible)
    for match in re.finditer(r"!?\[[^\]]*\]\(\s*(?:<([^>]+)>|([^\s)]+))(?:\s+['\"][^\n]*?['\"])?\s*\)", source):
        yield match.group(1) or match.group(2)
    for match in re.finditer(r"^\s{0,3}\[[^\]]+\]:\s*(?:<([^>]+)>|(\S+))", source, re.M):
        yield match.group(1) or match.group(2)


def css_links(source):
    for match in re.finditer(r"url\(\s*(['\"]?)(.*?)\1\s*\)", source, re.I):
        yield match.group(2)
    for match in re.finditer(r"@import\s+['\"]([^'\"]+)['\"]", source, re.I):
        yield match.group(1)


class AssetLinks(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.links = []
        self.in_style = False

    def handle_starttag(self, tag, attrs):
        require(tag != "base", "Use relative site links instead of an HTML base URL")
        self.in_style = self.in_style or tag == "style"
        for key, value in attrs:
            if not value:
                continue
            if key in {"href", "src", "poster", "data"}:
                self.links.append(value)
            elif key == "srcset":
                require("data:" not in value, "Use an asset file instead of an inline srcset data URL")
                self.links.extend(item.strip().split()[0] for item in value.split(",") if item.strip())
            elif key == "style":
                self.links.extend(css_links(value))

    def handle_endtag(self, tag):
        if tag == "style":
            self.in_style = False

    def handle_data(self, value):
        if self.in_style:
            self.links.extend(css_links(value))


def validate_sources(root):
    for relative in RETIRED:
        path = root / relative
        populated = path.is_file() or (path.is_dir() and any(item.is_file() for item in path.rglob("*")))
        require(not populated, f"Retired business entry still active: {relative}")
    build_paths = [root / name for name in ("android/app/build.gradle", "android/build.gradle",
                                          "android/settings.gradle", "scripts/android.ps1")]
    sources = build_paths + [path for path in (root / "android/app/src").rglob("*")
                             if path.is_file() and path.suffix in {".java", ".xml", ".json", ".pro"}]
    for path in sources:
        source = read(path)
        for token in OLD_BUILD_TOKENS:
            require(token not in source, f"Legacy source reference {token}: {path.relative_to(root)}")
    for path in build_paths:
        require(not re.search(r"(?:\.\./|\.\.\\|['\"])archive[/\\]", read(path)),
                f"Build must use the current public tree: {path.relative_to(root)}")
    for path in (root / "android/app/src").rglob("*"):
        require(path.name not in {"curriculum.json", "progress-v2.bin"},
                f"Legacy content resource still active: {path.relative_to(root)}")
    app_build = read(root / "android/app/build.gradle")
    require("applicationId 'cn.zhiya.pact'" in app_build and "applicationIdSuffix '.preview'" in app_build,
            "Current Android application identity is missing")
    require("cn.zhiya.pact.PactInstrumentation" in app_build, "Current instrumentation runner is missing")
    wrapper = read(root / "android/gradle/wrapper/gradle-wrapper.properties")
    require(re.search(r"^distributionSha256Sum=[0-9a-f]{64}$", wrapper, re.M),
            "Gradle distribution checksum is missing")


def validate(root=ROOT):
    root = Path(root).resolve()
    for relative in REQUIRED:
        require((root / relative).is_file(), f"Missing public project file: {relative}")
    docs = sorted(root.glob("*.md")) + sorted((root / "docs").rglob("*.md"))
    link_count = 0
    for path in docs:
        link_count += sum(check_link(root, path, target) for target in markdown_links(path))
        html = AssetLinks()
        html.feed(read(path))
        link_count += sum(check_link(root, path, target) for target in html.links)
    acceptance_ids = set(re.findall(r"\bPA-\d{3}\b", read(root / "docs/acceptance.md")))
    require({f"PA-{index:03}" for index in range(1, 9)} <= acceptance_ids,
            "Core PA-001 through PA-008 acceptance contracts must remain documented")
    api_text = read(root / "docs/api.md")
    for field in ("taskState", "rewardState", "confirmationMode", "requestId", "expectedVersion"):
        require(field in api_text, f"Missing shared API contract field: {field}")
    site = root / "site"
    site_files = sorted(path for path in site.rglob("*") if path.suffix in {".html", ".css"})
    site_links = 0
    for path in site_files:
        source = read(path)
        if path.suffix == ".html":
            html = AssetLinks()
            html.feed(source)
            targets = html.links
        else:
            targets = css_links(source)
        site_links += sum(check_link(root, path, target, site) for target in targets)
    validate_sources(root)
    print(f"PASS: {len(docs)} Markdown files, {link_count} local documentation links, "
          f"{len(site_files)} site files, {site_links} site asset links, "
          f"{len(acceptance_ids)} acceptance IDs; current source boundaries intact")


if __name__ == "__main__":
    try:
        validate()
    except (ValueError, KeyError, OSError) as error:
        sys.exit(f"FAIL: {error}")
