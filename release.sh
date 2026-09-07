#!/usr/bin/env bash

set -Eeuo pipefail

retry_tag=""
if [[ "${1:-}" == "--retry-jitpack" ]]; then
    retry_tag="${2:-}"
    if [[ ! "${retry_tag}" =~ ^[0-9]+\.[0-9]+$ ]] || (($# > 3)); then
        echo "Usage: $0 --retry-jitpack <version> [remote]" >&2
        exit 1
    fi
    REMOTE="${3:-origin}"
else
    REMOTE="${1:-origin}"
fi
JITPACK_GROUP="${JITPACK_GROUP:-com.github.mzgs}"
JITPACK_ARTIFACT="${JITPACK_ARTIFACT:-Mzgs-Android-Helper}"
JITPACK_BASE_URL="${JITPACK_BASE_URL:-https://jitpack.io}"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "Error: release.sh must be run inside a Git repository." >&2
    exit 1
fi

if ! git remote get-url "${REMOTE}" >/dev/null 2>&1; then
    echo "Error: Git remote '${REMOTE}' does not exist." >&2
    exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
    echo "Error: curl is required to trigger the JitPack build." >&2
    exit 1
fi

if [[ -z "${retry_tag}" ]]; then
    latest_tag=""
    latest_major=-1
    latest_minor=-1

    # Capture separately so a failed remote lookup cannot be hidden by process substitution.
    remote_tags=$(git ls-remote --tags --refs "${REMOTE}")
    while read -r hash ref; do
        tag="${ref#refs/tags/}"

        # Release versions use one fractional digit: 4.9 -> 5.0 -> 5.1.
        # Ignore accidental tags such as 4.10 when choosing the next version.
        if [[ "${tag}" =~ ^([0-9]+)\.([0-9])$ ]]; then
            major=$((10#${BASH_REMATCH[1]}))
            minor=$((10#${BASH_REMATCH[2]}))

            if ((major > latest_major || (major == latest_major && minor > latest_minor))); then
                latest_tag="${tag}"
                latest_major=${major}
                latest_minor=${minor}
            fi
        fi
    done <<< "${remote_tags}"

    if [[ -z "${latest_tag}" ]]; then
        echo "Error: no numeric major.minor tags were found on '${REMOTE}'." >&2
        exit 1
    fi

    next_tag="$((latest_major + (latest_minor + 1) / 10)).$(((latest_minor + 1) % 10))"

    if git rev-parse --verify --quiet "refs/tags/${next_tag}" >/dev/null; then
        echo "Error: local tag '${next_tag}' already exists." >&2
        exit 1
    fi

    echo "Creating release tag ${next_tag} from ${latest_tag} at $(git rev-parse --short HEAD)"
    git tag --annotate "${next_tag}" --message "Release ${next_tag}"
    git push "${REMOTE}" "refs/tags/${next_tag}"
else
    next_tag="${retry_tag}"
    git ls-remote --exit-code --tags --refs "${REMOTE}" "refs/tags/${next_tag}" >/dev/null
fi

group_path="${JITPACK_GROUP//./\/}"
artifact_url="${JITPACK_BASE_URL}/${group_path}/${JITPACK_ARTIFACT}/${next_tag}/${JITPACK_ARTIFACT}-${next_tag}.pom"
build_log_url="${JITPACK_BASE_URL}/${group_path}/${JITPACK_ARTIFACT}/${next_tag}/build.log"
build_status_url="${JITPACK_BASE_URL}/api/builds/${JITPACK_GROUP}/${JITPACK_ARTIFACT}/${next_tag}"

echo "Triggering JitPack build for ${JITPACK_GROUP}:${JITPACK_ARTIFACT}:${next_tag}"
echo "Waiting up to 30 minutes for the artifact. Build status: ${build_status_url}"
deadline=$((SECONDS + 1800))
build_ready=false
status_pattern='"status"[[:space:]]*:[[:space:]]*"([^"]*)"'
while ((SECONDS < deadline)); do
    remaining=$((deadline - SECONDS))
    curl_exit=0
    # Inspect HTTP status ourselves: --fail prints alarming errors for pending 404s.
    http_status=$(curl \
        --location \
        --silent \
        --show-error \
        --connect-timeout 30 \
        --max-time "${remaining}" \
        --write-out '%{http_code}' \
        --output /dev/null \
        "${artifact_url}") || curl_exit=$?

    if ((curl_exit == 0)) && [[ "${http_status}" == "200" ]]; then
        build_ready=true
        break
    fi
    if [[ "${http_status}" == "401" || "${http_status}" == "403" ]]; then
        echo "Error: JitPack denied access (HTTP ${http_status})." >&2
        break
    fi

    remaining=$((deadline - SECONDS))
    ((remaining > 0)) || break
    status_timeout=$((remaining < 20 ? remaining : 20))
    build_status=$(curl --fail --location --silent --connect-timeout 10 \
        --max-time "${status_timeout}" "${build_status_url}") || build_status=""
    status="unknown"
    if [[ "${build_status}" =~ ${status_pattern} ]]; then
        status="${BASH_REMATCH[1]}"
    fi
    case "${status}" in
        Error|error|Failed|failed)
            echo "Error: JitPack reports a failed build: ${build_status}" >&2
            break
            ;;
        tagNotFound)
            echo "Waiting: JitPack has not found tag ${next_tag} yet (HTTP ${http_status})."
            ;;
        *)
            echo "Waiting: artifact unavailable (HTTP ${http_status}, curl ${curl_exit}, JitPack status: ${status})."
            ;;
    esac
    remaining=$((deadline - SECONDS))
    ((remaining > 0)) || break
    sleep "$((remaining < 30 ? remaining : 30))"
done

if [[ "${build_ready}" != true ]]; then
    echo "Error: tag ${next_tag} is on ${REMOTE}, but JitPack has not served its artifact." >&2
    echo "Build status: ${build_status_url}" >&2
    curl --fail --location --silent --show-error --connect-timeout 10 --max-time 30 "${build_status_url}" >&2 || true
    echo >&2
    echo "Build log: ${build_log_url}" >&2
    printf 'Retry without creating another tag: %q --retry-jitpack %q %q\n' "$0" "${next_tag}" "${REMOTE}" >&2
    exit 1
fi

echo "Released ${next_tag} to ${REMOTE}; JitPack build succeeded."
echo "Artifact: ${artifact_url}"
