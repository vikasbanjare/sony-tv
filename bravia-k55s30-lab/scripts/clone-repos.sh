#!/bin/sh
# Shallow-clones every project in config/repos.tsv into repos/, at the commit that was
# reviewed, so you read the same code the reports describe. Runs none of their code.
#
#   sh scripts/clone-repos.sh
set -e
cd "$(dirname "$0")/.."
grep -v '^#' config/repos.tsv | while IFS='	' read -r name url commit _licence _role; do
  [ -n "$name" ] || continue
  if [ -d "repos/$name/.git" ]; then echo "  have  $name"; continue; fi
  git init -q "repos/$name"
  git -C "repos/$name" remote add origin "$url"
  if git -C "repos/$name" fetch -q --depth 1 origin "$commit" && git -C "repos/$name" checkout -q FETCH_HEAD; then
    echo "  got   $name @ $(echo "$commit" | cut -c1-12)"
  else
    echo "  FAIL  $name (moved or deleted? check $url)"; rm -rf "repos/${name:?}"
  fi
done
echo "Read each project's README and scripts before running anything from it."
