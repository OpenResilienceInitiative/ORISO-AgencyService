#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

fail() {
  printf '%s\n' "$1" >&2
  exit 1
}

require_line() {
  grep -Fq -- "$2" "$1" || fail "$1 must contain $2"
}

line_number() {
  grep -nF -- "$2" "$1" | cut -d: -f1
}

action=.github/actions/docker-build-push/action.yml
main=.github/workflows/ci-main.yml
pr=.github/workflows/ci-pull-request.yml
release=.github/workflows/release-image.yml
[[ "$(grep -Fc 'docker/build-push-action' "$action")" == 1 ]] ||
  fail 'The shared archive action must build once'
for workflow in "$main" "$pr" "$release"; do
  [[ "$(grep -Fc 'uses: ./.github/actions/docker-build-push' "$workflow")" == 1 ]] ||
    fail "$workflow must use the shared archive gate once"
  if grep -Eq 'docker/build-push-action|docker/login-action|scan_before_push:' "$workflow"; then
    fail "$workflow must use the shared archive gate for image operations"
  fi
done
require_line "$action" 'default: linux/amd64,linux/arm64'
require_line "$action" 'platforms: ${{ inputs.platforms }}'
require_line "$action" 'push: false'
require_line "$action" 'outputs: type=oci,'
require_line "$action" 'EXPECTED_ARCHIVE_SHA256: ${{ steps.scan.outputs.archive_sha256 }}'
require_line "$main" 'push_to_ghcr: true'
require_line "$main" 'subject-digest: ${{ steps.image.outputs.digest }}'
require_line "$release" 'push_to_ghcr: true'
require_line "$release" 'platforms: linux/amd64'
require_line "$release" 'image_tags: ${{ env.IMAGE }}'
require_line "$release" 'already exists. Choose a new version.'
if grep -Fq 'platforms:' "$main" "$pr"; then
  fail 'The branch and PR jobs must use the dual-platform default'
fi
validation=$(awk '
  /^  validate:$/ { printing = 1; next }
  printing && /^  [[:alnum:]_-]+:/ { exit }
  printing { print }
' "$pr")
for line in 'contents: read' 'persist-credentials: false' 'push_to_ghcr: false'; do
  grep -Fq -- "$line" <<< "$validation" || fail "PR validation must include $line"
done
if grep -Eq 'contents: write|packages: write|github_token:|push_to_ghcr: true' <<< "$validation"; then
  fail 'PR validation must not have publishing credentials or operations'
fi
for step in 'Login to GitHub Container Registry' 'Publish the scanned archive without rebuilding'; do
  grep -A 3 -F -- "- name: $step" "$action" |
    grep -Fq "inputs.push_to_ghcr == 'true'" || fail "$step must be skipped in PRs"
done
scan_line=$(line_number "$action" 'scan-image-archive.sh')
login_line=$(line_number "$action" 'docker/login-action')
publish_line=$(line_number "$action" 'publish-image-archive.sh')
((scan_line < login_line && login_line < publish_line)) || fail 'Scan must succeed before any registry operation'
guard_line=$(line_number "$release" 'git ls-remote')
image_line=$(line_number "$release" 'uses: ./.github/actions/docker-build-push')
git_tag_line=$(line_number "$release" 'git tag -a')
github_release_line=$(line_number "$release" 'Create GitHub Release')
((guard_line < image_line && image_line < git_tag_line && git_tag_line < github_release_line)) ||
  fail 'The Git tag guard, image publication, Git tag and GitHub Release must run in order'
printf '%s\n' 'AgencyService branch, release and no-push PR workflow contracts passed.'

scratch=$(mktemp -d "${TMPDIR:-/tmp}/agencyservice-archive-test.XXXXXX")
trap 'rm -rf "$scratch"' EXIT
export TEST_INDEX="$scratch/index.json" TEST_SCANS="$scratch/scans" TEST_UPLOAD="$scratch/upload"
export GITHUB_OUTPUT="$scratch/receipt" IMAGE_TAGS=example.invalid/agencyservice:fixture
export TEST_FAIL_PLATFORM='' TEST_WRONG_ARCH='' TEST_WRONG_UPLOAD_DIGEST=''
archive="$scratch/image.tar"
platforms=linux/amd64,linux/arm64
printf '%s' 'synthetic archive' > "$archive"
cat > "$TEST_INDEX" <<'JSON'
{"manifests": [
  {
    "platform": {"os": "linux", "architecture": "amd64"},
    "digest": "sha256:0000000000000000000000000000000000000000000000000000000000000001"
  },
  {
    "platform": {"os": "linux", "architecture": "arm64"},
    "digest": "sha256:0000000000000000000000000000000000000000000000000000000000000002"
  },
  {
    "platform": {"os": "unknown", "architecture": "unknown"},
    "digest": "sha256:0000000000000000000000000000000000000000000000000000000000000003",
    "annotations": {"vnd.docker.reference.type": "attestation-manifest"}
  }
]}
JSON
checksum=$(sha256sum "$TEST_INDEX")
export TEST_INDEX_DIGEST="sha256:${checksum%% *}"

cat > "$scratch/skopeo" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in
  inspect)
    [[ "$2" == --raw && "$3" == oci-archive:* ]]
    cat "$TEST_INDEX"
    ;;
  manifest-digest)
    checksum=$(sha256sum "$2")
    printf 'sha256:%s\n' "${checksum%% *}"
    ;;
  --override-os)
    [[ "$2" == linux && "$3" == --override-arch && "$5" == copy &&
       "$6" == --preserve-digests && "$7" == --digestfile ]]
    case "$4" in amd64) number=1 ;; arm64) number=2 ;; *) exit 2 ;; esac
    if [[ "$4" == "$TEST_WRONG_ARCH" ]]; then number=1; fi
    printf 'sha256:%064d\n' "$number" > "$8"
    ;;
  copy)
    [[ "$2" == --all && "$3" == --preserve-digests && "$4" == --authfile &&
       "$6" == --digestfile && "$8" == oci-archive:* && "$9" == docker://example.invalid/* ]]
    printf '%s\n' "$9" >> "$TEST_UPLOAD"
    printf '%s\n' "${TEST_WRONG_UPLOAD_DIGEST:-$TEST_INDEX_DIGEST}" > "$7"
    ;;
  *) exit 2 ;;
esac
SH
cat > "$scratch/trivy" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
[[ "$1" == image && "$2" == --input && "$4" == --platform &&
   "${*:6}" == '--format table --exit-code 1 --ignore-unfixed --vuln-type os,library --severity CRITICAL,HIGH' ]]
printf '%s\n' "$5" >> "$TEST_SCANS"
[[ "$5" != "$TEST_FAIL_PLATFORM" ]]
SH
chmod 700 "$scratch/skopeo" "$scratch/trivy"
export PATH="$scratch:$PATH"

scan() {
  bash scripts/ci/scan-image-archive.sh "$archive" "${1:-$platforms}" "$TEST_INDEX_DIGEST"
}

publish() {
  bash scripts/ci/publish-image-archive.sh "$archive" "$TEST_INDEX_DIGEST" "$1"
}

scan
[[ "$(cat "$TEST_SCANS")" == $'linux/amd64\nlinux/arm64' ]] || fail 'Both platforms must be scanned'
[[ ! -e "$TEST_UPLOAD" ]] || fail 'The scan must not upload to a registry'
receipt=$(sed -n 's/^archive_sha256=//p' "$GITHUB_OUTPUT")
publish "$receipt"
require_line "$GITHUB_OUTPUT" "digest=$TEST_INDEX_DIGEST"
require_line "$TEST_UPLOAD" "docker://$IMAGE_TAGS"

rm "$GITHUB_OUTPUT" "$TEST_UPLOAD"
if TEST_FAIL_PLATFORM=linux/arm64 scan > "$scratch/scan.log"; then fail 'A failed platform must stop the scan'; fi
[[ ! -e "$GITHUB_OUTPUT" && ! -e "$TEST_UPLOAD" ]] || fail 'A failed scan must produce no publish receipt or upload'

rm "$TEST_SCANS"
if TEST_WRONG_ARCH=arm64 scan > "$scratch/scan.log" 2>&1; then fail 'A mismatched platform must be rejected'; fi
require_line "$scratch/scan.log" 'The selected linux/arm64 manifest differs from the OCI index.'
[[ "$(cat "$TEST_SCANS")" == linux/amd64 && ! -e "$GITHUB_OUTPUT" ]] ||
  fail 'A mismatched manifest must not reach Trivy or produce a publish receipt'

if TEST_WRONG_UPLOAD_DIGEST=sha256:wrong publish "$receipt" 2> "$scratch/publish.log"; then
  fail 'A different uploaded digest must be rejected'
fi
require_line "$scratch/publish.log" 'Published image digest does not match the scanned build.'
[[ ! -e "$GITHUB_OUTPUT" ]] || fail 'A wrong uploaded digest must produce no successful publication receipt'
rm "$TEST_UPLOAD"
printf '%s' 'changed archive' > "$archive"
if publish "$receipt" 2> "$scratch/publish.log"; then fail 'A modified archive must not be published'; fi
require_line "$scratch/publish.log" 'The archive to publish differs from the scanned archive.'
[[ ! -e "$GITHUB_OUTPUT" && ! -e "$TEST_UPLOAD" ]] || fail 'A changed archive must be rejected before upload'

jq '.manifests |= map(select(.platform.architecture != "arm64"))' "$TEST_INDEX" > "$scratch/native-index.json"
mv "$scratch/native-index.json" "$TEST_INDEX"
checksum=$(sha256sum "$TEST_INDEX")
TEST_INDEX_DIGEST="sha256:${checksum%% *}"
printf '%s' 'synthetic native release archive' > "$archive"
rm "$TEST_SCANS"
scan linux/amd64
[[ "$(cat "$TEST_SCANS")" == linux/amd64 ]] || fail 'The native release must scan its amd64 manifest'
receipt=$(sed -n 's/^archive_sha256=//p' "$GITHUB_OUTPUT")
publish "$receipt"
require_line "$GITHUB_OUTPUT" "digest=$TEST_INDEX_DIGEST"
printf '%s\n' 'OCI archive scan and publication failure contracts passed.'
