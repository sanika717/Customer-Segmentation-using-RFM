#!/usr/bin/env bash
# Load the prepared CSV into MongoDB: database "retail", collection "transactions".
set -euo pipefail
MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"
FILE="data/processed/online_retail_typed.csv"
[ -f "$FILE" ] || { echo "Missing $FILE - run: python3 scripts/prepare_data.py"; exit 1; }

mongoimport --uri "$MONGO_URI" --db retail --collection transactions \
  --type csv --headerline --columnsHaveTypes --ignoreBlanks --drop --file "$FILE"

echo "Documents in retail.transactions:"
mongosh "$MONGO_URI/retail" --quiet --eval 'db.transactions.countDocuments()'
