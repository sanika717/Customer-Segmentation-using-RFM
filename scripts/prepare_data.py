"""Prepare the raw Online Retail CSV for MongoDB + Spark.

What it does
  1. Fixes the text encoding (the Kaggle file is ISO-8859-1, not UTF-8)
  2. Normalises CustomerID (12583.0 -> 12583) and the date format (-> yyyy-MM-dd HH:mm:ss)
  3. Writes a CSV whose header carries MongoDB types, so mongoimport stores numbers as numbers

Usage: python3 scripts/prepare_data.py [input.csv] [output.csv]
Default: data/raw/online_retail.csv -> data/processed/online_retail_typed.csv
"""
import csv, sys
from datetime import datetime

src = sys.argv[1] if len(sys.argv) > 1 else "data/raw/online_retail.csv"
dst = sys.argv[2] if len(sys.argv) > 2 else "data/processed/online_retail_typed.csv"

HEADER = ["InvoiceNo.string()", "StockCode.string()", "Description.string()", "Quantity.int32()",
          "InvoiceDate.string()", "UnitPrice.double()", "CustomerID.string()", "Country.string()"]
DATE_FORMATS = ["%m/%d/%Y %H:%M", "%m/%d/%Y %H:%M:%S", "%Y-%m-%d %H:%M:%S", "%d-%m-%Y %H:%M", "%Y-%m-%d %H:%M"]

def parse_date(s):
    for fmt in DATE_FORMATS:
        try:
            return datetime.strptime(s.strip(), fmt).strftime("%Y-%m-%d %H:%M:%S")
        except ValueError:
            pass
    return ""

def detect_encoding(path):
    for enc in ("utf-8-sig", "iso-8859-1"):
        try:
            with open(path, encoding=enc, newline="") as f:
                f.read()
            return enc
        except UnicodeDecodeError:
            continue
    return "iso-8859-1"

enc = detect_encoding(src)
kept = skipped = 0
with open(src, encoding=enc, newline="") as fin, open(dst, "w", encoding="utf-8", newline="") as fout:
    reader = csv.DictReader(fin)
    reader.fieldnames = [c.strip() for c in reader.fieldnames]
    need = ["InvoiceNo", "StockCode", "Description", "Quantity", "InvoiceDate", "UnitPrice", "CustomerID", "Country"]
    missing = [c for c in need if c not in reader.fieldnames]
    if missing:
        sys.exit(f"Missing columns in input: {missing}. Found: {reader.fieldnames}")
    w = csv.writer(fout, lineterminator="\n")
    w.writerow(HEADER)
    for r in reader:
        try:
            qty = int(float(r["Quantity"]))
            price = float(r["UnitPrice"])
        except ValueError:
            skipped += 1
            continue
        cust = r["CustomerID"].strip()
        if cust.endswith(".0"):
            cust = cust[:-2]
        w.writerow([r["InvoiceNo"].strip(), r["StockCode"].strip(), r["Description"].strip(), qty,
                    parse_date(r["InvoiceDate"]), price, cust, r["Country"].strip()])
        kept += 1
print(f"Read encoding: {enc}")
print(f"Rows written: {kept}  |  Rows skipped (bad numbers): {skipped}")
print(f"Output: {dst}")
