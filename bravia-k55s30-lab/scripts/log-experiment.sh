#!/bin/sh
# Adds one row to tests/tests.csv after a test. Asks for each column.
#
#   sh scripts/log-experiment.sh
set -e
cd "$(dirname "$0")/.."
CSV=tests/tests.csv
n=$(($(grep -c '^T' "$CSV" || true) + 1))
id=$(printf 'T%03d' "$n")
ask() { printf '%s: ' "$1" >&2; read -r v; printf '%s' "$v" | tr -d ',' ; } # commas would break the CSV
echo "New test $id (scores 1 = bad, 5 = great; leave blank if not judged)"
source=$(ask "source (clip or app)"); res=$(ask "resolution"); fmt=$(ask "HDR format (SDR/HDR10/HLG/DV)")
room=$(ask "room lighting (day/evening/dark)"); orig=$(ask "original setting"); new=$(ask "changed setting")
how=$(ask "instrument or subjective"); dark=$(ask "dark detail 1-5"); bright=$(ask "bright highlights 1-5")
col=$(ask "colour 1-5"); art=$(ask "artifacts 1-5 (5 = none)"); result=$(ask "result (keep/revert/undecided)")
back=$(ask "how to roll back")
echo "$id,$source,$res,$fmt,$room,$orig,$new,$how,$dark,$bright,$col,$art,$result,$back" >> "$CSV"
echo "Saved $id to $CSV"
