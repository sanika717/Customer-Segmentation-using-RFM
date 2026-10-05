spark.conf.set("spark.sql.ansi.enabled", "false")
// =====================================================================
// RFM Customer Segmentation - Spark pipeline (Spark SQL, run in spark-shell)
//   SOURCE=mongo : read retail.transactions from MongoDB, write results back to MongoDB
//   SOURCE=csv   : read data/processed/online_retail_typed.csv (fallback, no MongoDB needed)
// CSV outputs always go to output/_spark/<name>/ (collected by scripts/collect_csv.sh)
// =====================================================================
import org.apache.spark.sql.SaveMode

val source   = sys.env.getOrElse("SOURCE", "mongo")
val mongoUri = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
val outDir   = "output/_spark"

spark.sparkContext.setLogLevel("WARN")
println(s"\n>>> Source: $source")

// ---------- STEP 1: read raw transactions ----------
val raw =
  if (source == "mongo")
    spark.read.format("mongodb")
      .option("connection.uri", mongoUri)
      .option("database", "retail")
      .option("collection", "transactions")
      .load()
  else
    spark.read.option("header", "true")
      .csv("data/processed/online_retail_typed.csv")
      // header in the typed file looks like "Quantity.int32()", so strip the type suffix
      .toDF("InvoiceNo", "StockCode", "Description", "Quantity", "InvoiceDate", "UnitPrice", "CustomerID", "Country")

raw.createOrReplaceTempView("raw_tx")
println(s">>> Raw rows: ${raw.count()}")

// ---------- STEP 2: clean ----------
spark.sql("""
  CREATE OR REPLACE TEMPORARY VIEW clean_tx AS
  SELECT
    CAST(InvoiceNo AS STRING)                        AS InvoiceNo,
    CAST(StockCode AS STRING)                        AS StockCode,
    Description,
    CAST(Quantity AS INT)                            AS Quantity,
    to_timestamp(CAST(InvoiceDate AS STRING))        AS InvoiceTs,
    CAST(UnitPrice AS DOUBLE)                        AS UnitPrice,
    CAST(CAST(CustomerID AS DOUBLE) AS BIGINT)       AS CustomerID,
    Country,
    CAST(Quantity AS INT) * CAST(UnitPrice AS DOUBLE) AS TotalPrice
  FROM raw_tx
  WHERE CustomerID IS NOT NULL
    AND CAST(CustomerID AS STRING) <> ''
    AND CAST(InvoiceNo AS STRING) NOT LIKE 'C%'
    AND CAST(Quantity AS INT) > 0
    AND CAST(UnitPrice AS DOUBLE) > 0
""")
println(s">>> Clean rows: ${spark.table("clean_tx").count()}")

// ---------- STEP 3: RFM values per customer ----------
// Recency = days between (last date in data + 1 day) and the customer's last purchase
spark.sql("""
  CREATE OR REPLACE TEMPORARY VIEW rfm_base AS
  SELECT
    CustomerID,
    first(Country)                                                   AS Country,
    datediff(date_add(to_date((SELECT max(InvoiceTs) FROM clean_tx)), 1),
             to_date(max(InvoiceTs)))                                AS Recency,
    count(DISTINCT InvoiceNo)                                        AS Frequency,
    round(sum(TotalPrice), 2)                                        AS Monetary
  FROM clean_tx
  GROUP BY CustomerID
""")

// ---------- STEP 4: score 1-5 and assign segments ----------
// R and M: NTILE(5) quintiles (5 = best).  F: fixed bins, because most customers share the same few
// frequency values and NTILE would split identical customers into different groups.
spark.sql("""
  CREATE OR REPLACE TEMPORARY VIEW rfm_scored AS
  SELECT *,
    NTILE(5) OVER (ORDER BY Recency DESC, CustomerID)  AS R_Score,
    CASE WHEN Frequency = 1 THEN 1 WHEN Frequency = 2 THEN 2 WHEN Frequency = 3 THEN 3
         WHEN Frequency <= 6 THEN 4 ELSE 5 END         AS F_Score,
    NTILE(5) OVER (ORDER BY Monetary ASC, CustomerID)  AS M_Score
  FROM rfm_base
""")

spark.sql("""
  CREATE OR REPLACE TEMPORARY VIEW rfm_segments AS
  SELECT CustomerID, Country, Recency, Frequency, Monetary, R_Score, F_Score, M_Score,
    CASE
      WHEN R_Score >= 4 AND F_Score >= 4 AND M_Score >= 4 THEN 'Champions'
      WHEN R_Score >= 3 AND F_Score >= 3                  THEN 'Loyal Customers'
      WHEN R_Score >= 3 AND F_Score <  3                  THEN 'Potential Loyalists'
      WHEN R_Score <  3 AND F_Score >= 3                  THEN 'At Risk'
      ELSE                                                     'Lost'
    END AS Segment
  FROM rfm_scored
""")

println(">>> Customers per segment:")
spark.sql("SELECT Segment, count(*) AS Customers FROM rfm_segments GROUP BY Segment ORDER BY Customers DESC").show(false)

// ---------- STEP 5: summary tables for the dashboard ----------
spark.sql("""
  CREATE OR REPLACE TEMPORARY VIEW segment_summary AS
  SELECT Segment,
         count(*)                         AS Customers,
         round(sum(Monetary), 2)          AS TotalRevenue,
         round(avg(Monetary), 2)          AS AvgSpend,
         round(avg(Frequency), 2)         AS AvgOrders,
         round(avg(Recency), 1)           AS AvgRecencyDays
  FROM rfm_segments GROUP BY Segment
""")

spark.sql("""
  CREATE OR REPLACE TEMPORARY VIEW monthly_revenue AS
  SELECT date_format(InvoiceTs, 'yyyy-MM')   AS Month,
         round(sum(TotalPrice), 2)           AS Revenue,
         count(DISTINCT InvoiceNo)           AS Orders,
         count(DISTINCT CustomerID)          AS Customers
  FROM clean_tx GROUP BY date_format(InvoiceTs, 'yyyy-MM') ORDER BY Month
""")

spark.sql("""
  CREATE OR REPLACE TEMPORARY VIEW revenue_by_country AS
  SELECT Country,
         round(sum(TotalPrice), 2)           AS Revenue,
         count(DISTINCT CustomerID)          AS Customers,
         count(DISTINCT InvoiceNo)           AS Orders
  FROM clean_tx GROUP BY Country ORDER BY Revenue DESC
""")

// ---------- STEP 6: write outputs ----------
// CSV for Power BI
Seq("rfm_segments", "segment_summary", "monthly_revenue", "revenue_by_country").foreach { name =>
  spark.table(name).coalesce(1).write.mode(SaveMode.Overwrite).option("header", "true").csv(s"$outDir/$name")
  println(s">>> CSV written: $outDir/$name")
}

// Back to MongoDB (collections: rfm_segments, segment_summary, monthly_revenue, revenue_by_country)
if (source == "mongo") {
  Seq("rfm_segments", "segment_summary", "monthly_revenue", "revenue_by_country").foreach { name =>
    spark.table(name).write.format("mongodb")
      .mode(SaveMode.Overwrite)
      .option("connection.uri", mongoUri)
      .option("database", "retail")
      .option("collection", name)
      .save()
    println(s">>> MongoDB collection written: retail.$name")
  }
}

println("\n>>> DONE. Check the output/ folder.")
sys.exit(0)
