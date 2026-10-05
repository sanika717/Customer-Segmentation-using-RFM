"""Generate a small synthetic Online Retail style CSV (for a dry run without downloading the real dataset).
Usage: python3 scripts/generate_sample_data.py
Output: data/raw/online_retail.csv  (same columns/date format as the real dataset)
"""
import csv, random
from datetime import datetime, timedelta

random.seed(42)
countries = ["United Kingdom"] * 8 + ["Germany", "France", "EIRE", "Spain", "Netherlands", "Australia"]
products = [(f"P{1000+i}", f"PRODUCT {i}", round(random.uniform(0.5, 25), 2)) for i in range(60)]
start = datetime(2010, 12, 1, 8, 0)

def fmt(d):  # like the real file: 12/1/2010 8:26
    return f"{d.month}/{d.day}/{d.year} {d.hour}:{d.minute:02d}"

rows, inv = [], 536000
for cust in range(12000, 12400):
    n_orders = random.choice([1, 1, 1, 2, 2, 3, 4, 6, 9, 14])
    active_until = random.randint(30, 365)
    country = random.choice(countries)
    for _ in range(n_orders):
        inv += 1
        when = start + timedelta(days=random.randint(0, active_until), minutes=random.randint(0, 600))
        for _ in range(random.randint(1, 6)):
            code, desc, price = random.choice(products)
            rows.append([inv, code, desc, random.randint(1, 24), fmt(when), price, cust, country])
# dirty rows, like the real data
for i in range(40):
    rows.append([inv + 1 + i, "P1000", "PRODUCT 0", 3, "12/5/2010 10:00", 2.5, "", "United Kingdom"])            # no customer
for i in range(30):
    rows.append([f"C{inv + 100 + i}", "P1001", "PRODUCT 1", -2, "12/6/2010 11:00", 4.0, 12005, "United Kingdom"])  # cancelled
for i in range(10):
    rows.append([inv + 200 + i, "P1002", "PRODUCT 2", 1, "12/7/2010 12:00", 0, 12010, "United Kingdom"])        # zero price

random.shuffle(rows)
with open("data/raw/online_retail.csv", "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow(["InvoiceNo", "StockCode", "Description", "Quantity", "InvoiceDate", "UnitPrice", "CustomerID", "Country"])
    w.writerows(rows)
print(f"Wrote {len(rows)} rows to data/raw/online_retail.csv")
