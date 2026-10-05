#!/usr/bin/env bash
# Spark writes each CSV as a folder with a part-*.csv file. Copy them to simple, single files.
set -euo pipefail
for name in rfm_segments segment_summary monthly_revenue revenue_by_country; do
  part=$(ls output/_spark/$name/part-*.csv 2>/dev/null | head -n 1 || true)
  if [ -n "$part" ]; then cp "$part" "output/$name.csv"; echo "Created output/$name.csv"; else echo "WARNING: no output for $name"; fi
done
