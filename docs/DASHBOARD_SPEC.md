# Power BI Dashboard Spec

## Import (Get Data -> Text/CSV)
| File | Columns |
|---|---|
| rfm_segments.csv | CustomerID, Country, Recency, Frequency, Monetary, R_Score, F_Score, M_Score, Segment |
| segment_summary.csv | Segment, Customers, TotalRevenue, AvgSpend, AvgOrders, AvgRecencyDays |
| monthly_revenue.csv | Month, Revenue, Orders, Customers |
| revenue_by_country.csv | Country, Revenue, Customers, Orders |

Set data types: Month = Text (sort ascending), CustomerID = Text, Recency/Frequency/Monetary/Scores = numbers.

## Page 1 - Overview
- KPI cards: Total Revenue, Total Customers, Total Orders, Avg Order Value
- Line chart: `monthly_revenue[Month]` x `Revenue`
- Column chart: Orders by Month

## Page 2 - Customer Segments
- Donut chart: customers per Segment (`segment_summary[Segment]`, `Customers`)
- Bar chart: TotalRevenue by Segment
- Table: Segment, Customers, AvgSpend, AvgOrders, AvgRecencyDays
- Slicer: Country (from `rfm_segments`)
- Scatter: Frequency (x) vs Monetary (y), colour = Segment

## Page 3 - Geography
- Map or bar chart: Revenue by Country (`revenue_by_country`)
- Table: top 10 countries by Revenue and Customers
- Tip: the UK dominates this dataset; add a "Without UK" bar chart to see other markets

## DAX measures
```DAX
Total Revenue = SUM(monthly_revenue[Revenue])

Total Orders = SUM(monthly_revenue[Orders])

Total Customers = DISTINCTCOUNT(rfm_segments[CustomerID])

Avg Order Value = DIVIDE([Total Revenue], [Total Orders])

Champions Revenue Share =
DIVIDE(
    CALCULATE(SUM(segment_summary[TotalRevenue]), segment_summary[Segment] = "Champions"),
    SUM(segment_summary[TotalRevenue])
)
```

## Suggested insights to write down
- Share of revenue from Champions vs share of customers
- Size of the At Risk group (best win-back target)
- Revenue peak months (seasonality)
- Share of revenue from outside the UK

## Recommended actions per segment
| Segment | Action |
|---|---|
| Champions | Reward, early access, loyalty perks |
| Loyal Customers | Upsell, ask for reviews |
| Potential Loyalists | Welcome offers, nudge a second/third order |
| At Risk | Win-back discount, reminder emails |
| Lost | Low-cost reactivation campaign or ignore |
