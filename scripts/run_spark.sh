#!/usr/bin/env bash
# Run the Spark pipeline (Spark SQL inside spark-shell, no PySpark).
#   SOURCE=mongo (default)  read raw data from MongoDB, write results back to MongoDB
#   SOURCE=csv              fallback: read the prepared CSV directly, write only CSV outputs
set -euo pipefail
export SOURCE="${SOURCE:-mongo}"
export MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"
# Connector for Spark 3.x / Scala 2.12. Check Maven Central for newer 10.x versions if needed.
CONNECTOR="${CONNECTOR:-org.mongodb.spark:mongo-spark-connector_2.12:10.4.1}"

rm -rf output/_spark && mkdir -p output

if [ "$SOURCE" = "mongo" ]; then
  spark-shell --packages "$CONNECTOR" -i spark/rfm_pipeline.scala
else
  spark-shell -i spark/rfm_pipeline.scala
fi

bash scripts/collect_csv.sh
