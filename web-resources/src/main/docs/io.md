---
layout: page
title: Input/Output
permalink: /io
---

Depending on the requested data type the API offers some Input/Output processing.
In case of `quantity` datasets a client might request a rendered chart instead 
of raw data.

The following sections list currently supported I/O operations.

- TOC
{:toc}

## Chart Rendering

{:.n52-callout .n52-callout-info}
Chart rendering is only supported for `quantity` types.

The following diagram types are available and can be set via style options:

* line charts
* bar charts

Styles can be used whereever a chart is rendered. It does not matter if you request 
an image or a report (which embeds the chart).

### Line Charts

The following style properties can be set.

{:.table}
Property   | Description
-----------|------------
`lineType` | The line style. Possible values are `solid`, `dotted` or `dashed`
`width`    | The thickness of a line as integer, dash gap, point size (dependend on the chart type to be rendered)
`color`    | A 6-digit hex color value, e.g. `#5f5f5f`

**Example**
<div class="clearfix">
<div class="col-lg-5">
  <img alt="lineChart" src="{{site.baseurl}}/assets/images/io_chart_linestyle_example.png" title="Example for a line chart style.">
</div>
<div class="col-lg-5 pull-right">
<pre>{
  "chartType": "line",
  "properties": {
    "type": "dashed",
    "width": 4,
    "color": "#0000ff"
  }
}</pre>
</div>
 </div>

### Bar Charts

The following style properties can be set.

{:.table}
Property   | Description
-----------|------------
`interval` | The time period a bar shall represent. Possible values are `byDay`, `byWeek`, `byMonth`.
`width`    | Value between `0` and `1`, which defines the bar width in percent (`width=1` means maximum width, i.e. bar next to bar).
`color`    | A 6-digit hex color value, e.g. `#5f5f5f`

**Example**
<div class="clearfix">
<div class="col-lg-5">
  <img alt="lineChart" src="{{site.baseurl}}/assets/images/io_chart_barstyle_example.png" title="Example for a bar chart style.">
</div>
<div class="col-lg-5 pull-right">
<pre>{
  "chartType": "bar",
  "properties": {
    "interval": "byDay",
    "width": 0.8,
    "color": "#0000ff"
  }
}</pre>
</div>
</div>

### Overlaying Datasets

<div class="clearfix">
<div class="col-lg-6">
    <img alt="combinedCharts" src="{{site.baseurl}}/assets/images/io_chart_overlay_example.png" title="Example of combined charts.">
</div>
<div class="col-lg-5 pull-right">
<pre>POST /api/v1/datasets/data HTTP/1.1
Host: example.org
Content-Type: application/json
Accept: image/png

{
  "legend": true,
  "timespan": "P0Y0M3D/2013-01-31TZ",
  "width": 400,
  "height": 300,
  "language": "de",
  "grid": true,
  "styleOptions": {
    "ts_eafa3af4e61db76b367746980d149b7e": {
      "chartType": "line",
      "properties": {
        "color": "#0000FF",
        "lineType": "solid",
        "width": 1
      }
    },
    "ts_92385cd6fb670e886cab8e542185e847": {
      "chartType": "bar",
      "properties": {
        "color": "#2f2f2f",
        "interval": "byHour",
        "width": 0.7
      }
    }
  }
}</pre>
</div>
</div>

{::options parse_block_html="true" /}
{: .n52-callout .n52-callout-info }
<div>
If you are interested in the PNG output you can either parse it from your
favorite programming language. For a quick review you can use Curl from command line
(adapt parameters as needed, e.g. if you want a PDF report instead):

```
curl -X POST -d 'PASTE IN HERE YOUR POST REQUEST' \
-H "content-type:application/json" -H "accept:image/png" \
http://localhost:8080/api/v1/timeseries/getData &gt; img.png
```
</div>

## Generalizing Raw Data

{: .n52-callout .n52-callout-info}
Chart rendering is only supported for `quantity` types.

Depending on sampling resolution and timespan timeseries data can be huge. Generalizing data can make 
sense in more than just a low bandwidth use case (e.g. smoothing the curve).

Generalization can be enabled by `generalize=true` query parameter. By default generalization behaviour 
is set to `false`. The API currently supports two generalization algorithms.

### Largest-Triangle-Three Bucket Downsampling (default)

Downsamples to a fix amount of output values 
([Details](http://skemman.is/stream/get/1946/15343/37285/3/SS_MSthesis.pdf)). This is the default
algorithm chosen, when `generalizing_algorithm` parameter is missing.

Parameters
* `generalize=true`
* `generalizing_algorithm=lttb`
* `threshold={int-value}` (default is `200`)

### Douglas-Peucker Downsampling

Downsamples timeseries values by using a threshold value 
([Details](http://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm)).

Parameters
* `generalize=true`
* `generalizing_algorithm=dp`
* `tolerance_value={double-value}` (default is 0.1)

## Formatting Raw Data

{:.n52-callout .n52-callout-info}
Chart rendering is only supported for `quantity` types.

Raw data can be requested in a specific format. This can be useful if you work with a specific chart API 
and want to avoid to convert data outout from one format to another. Possible formats are:

* `tvp` (the default)
* `highchart`

To retrieve extra reference values (if available for that timeseries) valid for the requested timespan just
add `expand=true`.

{:.n52-callout .n52-callout-info}
Serving arbitrary formats is limited. Most probably you have to combine timeseries metadata and the actual 
data differently within the used API. Please refer to the actual data output so that it can be used as 
intended by the 3rd party API.

### TVP Format (default)

The format returns timeseries data as time-value tuples.

**Example (single dataset)**
```
{
  "values": [
    {
      "timestamp": 1376524800000,
      "value": 433.0
    },
    {
      "timestamp": 1376524860000,
      "value": 432.4
    },
    {
      "timestamp": 1376524920000,
      "value": 432.0
    },
    {
      "timestamp": 1376524980000,
      "value": 431.1
    }
  ]
}
```

**Example (multiple datasets)**
```
{
  "ts_ad3edeff973ab62e39f76c14f95d1e82": {
    "values": []
  },
  "ts_c8ed5ddbb89c19b2e385de23eecbde98": {
    "values": [
      {
        "timestamp": 1376589600000,
        "value": 546
      },
      {
        "timestamp": 1376589660000,
        "value": 546.6
      },
      {
        "timestamp": 1376589720000,
        "value": 547
      }
    ]
  }
}
```

{:.n52-callout .n52-callout-info}
Please note that when adding <code>expanded=true</code>, reference values will be added as
a dataset on their own. You can track reference values with the corresponding identifiers 
available from the dataset metadata.

### Highchart Format

The format returns timeseries data in [Highcharts series format](http://api.highcharts.com/highcharts#series). 
To add metadata or a readable dataset name you will have to replace the `datasetId` with a 
readable label taken from the Dataset metadata.

**Example (single dataset)**
```{
  "name": "ts_c8ed5ddbb89c19b2e385de23eecbde98",
  "data": [
    [
      1376524800000,
      433.3
    ],
    [
      1376524860000,
      432.4
    ],
    [
      1376524920000,
      432.1
    ]
  ]
}
```

**Example (multiple datasets)**
```
[
  {
    "name": "ts_ad3edeff973ab62e39f76c14f95d1e82",
    "data": []
  },
  {
    "name": "ts_c8ed5ddbb89c19b2e385de23eecbde98",
    "data": [
      [
        1376589600000,
        546
      ],
      [
        1376589660000,
        546.6
      ],
      [
        1376589720000,
        547
      ],
      [
        1376589780000,
        548
      ]
    ]
  }
]
```