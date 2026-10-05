# Customer Segmentation for Online Retail (RFM) using Apache Spark, MongoDB and Power BI

A small demo project. Raw transactions go into **MongoDB**, **Apache Spark (Spark SQL, no PySpark)** cleans them
and computes **RFM** (Recency, Frequency, Monetary) segments, results are written back to MongoDB and exported
as CSV, and **Power BI** shows the dashboard.

```
CSV  ->  prepare_data.py  ->  MongoDB (retail.transactions)  ->  Spark SQL  ->  MongoDB (rfm_segments, ...)
                                                                         \->  output/*.csv  ->  Power BI
```

## Folder structure
```
data/raw/            put the downloaded dataset here (online_retail.csv)
data/processed/      cleaned, typed CSV created by prepare_data.py
scripts/             helper scripts (prepare, load into MongoDB, run Spark, collect CSVs)
spark/rfm_pipeline.scala   the Spark pipeline (Spark SQL inside spark-shell)
output/              final CSVs for Power BI
docs/DASHBOARD_SPEC.md     Power BI pages, measures
```

## 1. Install the tools (macOS, Homebrew)
```bash
brew install openjdk@17 apache-spark python
brew tap mongodb/brew
brew install mongodb-community mongodb-database-tools mongosh
```
Then make Java visible and start MongoDB:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
brew services start mongodb-community
```
Check: `java -version`, `spark-shell --version`, `mongosh --eval "db.runCommand({ping:1})"`.

**Windows/Linux:** install Java 17, Spark 3.5.x, MongoDB Community and the MongoDB Database Tools from their
official sites. The commands below stay the same (use Git Bash or WSL on Windows).

> Use **Spark 3.5.x** (Scala 2.12). Run `spark-shell --version` to see your Scala version. If it says 2.13
> (Spark 4.x), change the connector in `scripts/run_spark.sh` to `..._2.13:...`.

## 2. Get the dataset
**Option A - real data (recommended).** Download the *Online Retail* dataset (UCI / Kaggle, search "Online Retail
UCI") as CSV and save it as `data/raw/online_retail.csv`.
Needed columns: `InvoiceNo, StockCode, Description, Quantity, InvoiceDate, UnitPrice, CustomerID, Country`.
(If you only have the `.xlsx`, open it in Excel and "Save as CSV".)

**Option B - quick dry run with fake data (about 6k rows):**
```bash
python3 scripts/generate_sample_data.py
```

## 3. Run the pipeline
From the project folder:
```bash
# 1) clean the file and add MongoDB types
python3 scripts/prepare_data.py

# 2) load into MongoDB (database: retail, collection: transactions)
bash scripts/load_mongo.sh

# 3) run Spark: reads MongoDB, cleans, computes RFM, writes back to MongoDB + CSVs
bash scripts/run_spark.sh
```
The first Spark run downloads the MongoDB Spark connector, so it needs internet and takes a few minutes.

**Expected output**
- Console: raw rows, clean rows, and a table of customers per segment
- `output/rfm_segments.csv`, `segment_summary.csv`, `monthly_revenue.csv`, `revenue_by_country.csv`
- MongoDB collections in database `retail`: `rfm_segments`, `segment_summary`, `monthly_revenue`, `revenue_by_country`

Check MongoDB:
```bash
mongosh retail --quiet --eval 'db.rfm_segments.find().limit(3)'
mongosh retail --quiet --eval 'db.segment_summary.find()'
```

### Fallback: no MongoDB or connector problems
Spark can read the CSV directly. You still get all CSV outputs for Power BI:
```bash
SOURCE=csv bash scripts/run_spark.sh
```
(Then MongoDB is not used, so mention that in your report if you rely on this.)

## 4. Build the dashboard
Open `docs/DASHBOARD_SPEC.md`. Import the four CSVs from `output/` into Power BI.
Power BI Desktop runs only on Windows. On a Mac use Power BI on the web (app.powerbi.com) -> Create -> upload the CSV files, or use a Windows lab PC.

## How the segments are decided
| Score | How it is calculated |
|---|---|
| R (Recency) | NTILE(5) on days since last purchase. 5 = most recent |
| F (Frequency) | Fixed bins by number of orders: 1, 2, 3, 4-6, 7+ -> scores 1 to 5 |
| M (Monetary) | NTILE(5) on total spend. 5 = highest |

| Segment | Rule |
|---|---|
| Champions | R >= 4 and F >= 4 and M >= 4 |
| Loyal Customers | R >= 3 and F >= 3 (not Champions) |
| Potential Loyalists | R >= 3 and F < 3 |
| At Risk | R < 3 and F >= 3 |
| Lost | R < 3 and F < 3 |

Cleaning rules: drop rows with no CustomerID, cancelled invoices (InvoiceNo starting with `C`), and rows with
Quantity <= 0 or UnitPrice <= 0. TotalPrice = Quantity x UnitPrice.
Recency reference date = last date in the data + 1 day.

## Troubleshooting
| Problem | Fix |
|---|---|
| `mongoimport: command not found` | `brew install mongodb-database-tools` |
| `Connection refused` | MongoDB isn't running: `brew services start mongodb-community` |
| `Failed to find data source: mongodb` or `ClassNotFoundException` | Connector not loaded. Run through `scripts/run_spark.sh` (it passes `--packages`). Check the Scala version matches your Spark |
| Package download fails | Check internet/VPN, retry. Or use `SOURCE=csv` |
| `Unsupported class file major version` | Wrong Java. Use Java 17: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` |
| Atlas instead of local MongoDB | `export MONGO_URI="mongodb+srv://USER:PASS@CLUSTER.mongodb.net"` before steps 2 and 3, and allow your IP in Atlas Network Access |
| Empty `Customers` / 0 clean rows | Run `mongosh retail --eval 'db.transactions.findOne()'` and check the fields look right |

## Notes
- Spark is used through **Spark SQL in spark-shell** (Scala shell), not PySpark. The logic is all SQL.
- At about 5 lakh rows this does not strictly need Spark; it is used to show a pipeline that scales.
