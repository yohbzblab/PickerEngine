import csv
import json
from pathlib import Path
import random
import time
from datetime import datetime

import requests

BASE_URL = "https://prod-api.featuring.co/discover/explore/yt/influencers/"
ORDER = "desc"
ORDER_BY = "follower"
OUTPUT_DIR = Path(__file__).resolve().parent
PAGES_PER_FILE = 10

HEADERS = {
    "accept": "application/json, text/plain, */*",
    "accept-language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
    "authorization": (
        "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
        "eyJ0b2tlbl90eXBlIjoiYWNjZXNzIiwiZXhwIjoxNzcwMjEyOTczLCJpYXQiOjE3"
        "Njc2MjA5NzMsImp0aSI6ImIzMWU5ZGMzMjU4NDQ1ZjY4NmY4MzBjNzA2NzM1ND"
        "Y0IiwidXNlcl9pZCI6MjEwNjd9."
        "ZN62FJlkL9IXUGbStRRfrPcv8xFDlN6f8ZYw8Va2y-c"
    ),
    "dnt": "1",
    "ft-workspace": "CFPVEJIRVB",
    "origin": "https://app.featuring.co",
    "priority": "u=1, i",
    "referer": "https://app.featuring.co/",
    "sec-ch-ua": "\"Google Chrome\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand\";v=\"24\"",
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": "\"macOS\"",
    "sec-fetch-dest": "empty",
    "sec-fetch-mode": "cors",
    "sec-fetch-site": "same-site",
    "user-agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    ),
}

COOKIE = (
    "_fwb=22C3BlxPmeHXANuG9u0btk.1767012188076; "
    "ch-veil-id=d1e3df40-f4a2-4e4b-9038-f5b942cb22d6; "
    "_gcl_au=1.1.2138038996.1767012193; "
    "landing_device_id=70499944-4331-46aa-a4a6-e49ac9ef6552; "
    "hubspotutk=6689376feade886009739cbce82b86c3; "
    "__hssrc=1; "
    "_gac_UA-132987868-3=1.1767014919.Cj0KCQiA6sjKBhCSARIsAJvYcpNibNxOJf"
    "b2-hSNau8miEmQ0DoEu-8a2tc0cwmjIGezlzzUqUu3FeQaAvbTEALw_wcB; "
    "_ga=GA1.1.1489660250.1767012192; "
    "AMP_MKTG_b844b96527=JTdCJTIydXRtX2NhbXBhaWduJTIyJTNBJTIyTGVhZHM"
    "tU2VhcmNoLTElMjIlMkMlMjJ1dG1fbWVkaXVtJTIyJTNBJTIyY3BjJTIyJTJDJ"
    "TIydXRtX3NvdXJjZSUyMiUzQSUyMmFkd29yZHMlMjIlMkMlMjJ1dG1fdGVybSU"
    "yMiUzQSUyMiVFQyU5RCVCOCVFRCU5NCU4QyVFQiVBMyVBOCVFQyU5NiVCOCVFQ"
    "yU4NCU5QyVFRCU5NCU4QyVFQiU5RSVBQiVFRCU4RiVCQyUyMiUyQyUyMnJlZmV"
    "ycmVyJTIyJTNBJTIyaHR0cHMlM0ElMkYlMkZ3d3cuZ29vZ2xlLmNvLmtyJTJDJ"
    "TIycmVmZXJyaW5nX2RvbWFpbiUyMiUzQSUyMnd3dy5nb29nbGUuY28ua3IlMj"
    "IlMkMlMjJnYnJhaWQlMjIlM0ElMjIwQUFBQUFfVDNjNUNSS1E1WDc0UzNuQ0U5N"
    "VhMcFlJOUtFJTIyJTJDJTIyZ2NsaWQlMjIlM0ElMjJDajBLQ1FpQTZzaktCaENT"
    "QVJJc0FKdlljcE5pYk54T0pmYjItaFNOYXU4bWlFbVEwRG9FdS04YTJ0YzBjd2"
    "1qSUdlemx6elVxVXUzRmVRYUF2YlRFQUx3X3djQiUyMiU3RA==; "
    "AMP_b844b96527=JTdCJTIyZGV2aWNlSWQlMjIlM0ElMjI3MDQ5OTk0NC00MzMxL"
    "TQ2YWEtYTRhNi1lNDlhYzllZjY1NTIlMjIlMkMlMjJzZXNzaW9uSWQlMjIlM0E"
    "xNzY3MDE0OTIxMjgyJTJDJTIyb3B0T3V0JTIyJTNBZmFsc2UlMkMlMjJsYXN0R"
    "XZlbnRUaW1lJTIyJTNBMTc2NzAxNDkyMTM2MCUyQyUyMmxhc3RFdmVudElkJTI"
    "yJTNBOCUyQyUyMnBhZ2VDb3VudGVyJTIyJTNBMSU3RA==; "
    "__hstc=2043822.6689376feade886009739cbce82b86c3.1767012195561.176"
    "7012195561.1767014923984.2; "
    "_gcl_aw=GCL.1767620954.Cj0KCQiA6sjKBhCSARIsAJvYcpMCXV-_cb4VLyuoJ2"
    "vv0UrQl_2ZNKNfD9NqOe3BHWLXD5JK2oGwO_QaAm5pEALw_wcB; "
    "_gcl_gs=2.1.k1$i1767620946$u50351806; "
    "_fbp=fb.1.1767620956574.196874981285809402; "
    "ft_vs_no=20555445; "
    "ft_user_session=c163b8f88f3ef85e42cfab029aad58cf389d481a; "
    "app_device_id=70499944-4331-46aa-a4a6-e49ac9ef6552; "
    "AMP_MKTG_abe8092bea=JTdCJTdE; "
    "ft_user_no=21067; "
    "ch-session-19932=eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzZXMiLCJleHAiOjE3"
    "NzAyNTc3MDYsImlhdCI6MTc2NzY2NTcwNiwia2V5IjoiMTk5MzItNjhjN2IyYT"
    "A0NmE4YjVmNDkyNDQifQ.DcYyuxj35-JKBdNulFpP9spTHEwsJG-q-62eMHVsay4; "
    "AMP_abe8092bea=JTdCJTIyZGV2aWNlSWQlMjIlM0ElMjI3MDQ5OTk0NC00MzMxLT"
    "Q2YWEtYTRhNi1lNDlhYzllZjY1NTIlMjIlMkMlMjJ1c2VySWQlMjIlM0ElMjJ5b"
    "2glNDBidXp6YmVlbGFiLmNvbSUyMiUyQyUyMnNlc3Npb25JZCUyMiUzQTE3Njc"
    "3MDQ5NjM2MjMlMkMlMjJvcHRPdXQlMjIlM0FmYWxzZSUyQyUyMmxhc3RFdmVud"
    "FRpbWUlMjIlM0ExNzY3NzA0OTk1OTQ1JTJDJTIybGFzdEV2ZW50SWQlMjIlM0Ey"
    "NzMlMkMlMjJwYWdlQ291bnRlciUyMiUzQTAlN0Q=; "
    "_ga_3DCCGMWD39=GS2.1.s1767704996$o9$g0$t1767704996$j60$l0$h0"
)


def fetch_page(session, page):
    params = {
        "page": page,
        "order": ORDER,
        "order_by": ORDER_BY,
    }
    response = session.get(BASE_URL, params=params, timeout=20)
    if not response.ok:
        return [], None
    data = response.json()
    result = (
        data.get("result")
        or data.get("data", {}).get("result")
        or data.get("pageProps", {}).get("result")
        or data.get("pageProps", {}).get("data", {}).get("result")
        or {}
    )
    items = result.get("results", [])
    total_pages = result.get("total_page")
    if isinstance(total_pages, int) and total_pages > 0:
        return items, total_pages
    return items, None


def fetch_all(start_page=1, max_pages=0, pages_per_file=PAGES_PER_FILE):
    session = requests.Session()
    session.headers.update(HEADERS)
    session.headers["cookie"] = COOKIE

    page = max(1, start_page)
    resolved_max = max_pages if max_pages and max_pages > 0 else None
    total_pages = None
    total_items = 0
    sample_items = []
    chunk_items = []
    chunk_start_page = page
    chunk_pages = 0
    last_page_with_items = None
    interrupted = False

    try:
        while True:
            items, reported_total = fetch_page(session, page)
            if total_pages is None and reported_total:
                total_pages = reported_total
            if total_pages is not None:
                print(f"fetch page {page} / total {total_pages}")
            else:
                print(f"fetch page {page}")
            if not items:
                break
            if not sample_items:
                sample_items = items[:3]
            chunk_items.extend(items)
            total_items += len(items)
            chunk_pages += 1
            last_page_with_items = page

            page += 1
            if chunk_pages >= pages_per_file:
                output = paged_output_path(chunk_start_page, last_page_with_items)
                save_csv(chunk_items, output)
                print("saved:", output)
                chunk_items = []
                chunk_pages = 0
                chunk_start_page = page
            if total_pages is not None and page > total_pages:
                break
            if resolved_max is not None and (page - start_page) >= resolved_max:
                break
            time.sleep(random.uniform(0, 1))
    except KeyboardInterrupt:
        interrupted = True
    if chunk_items and last_page_with_items is not None:
        output = paged_output_path(chunk_start_page, last_page_with_items)
        save_csv(chunk_items, output)
        print("saved:", output)
    return total_items, sample_items, interrupted


def normalize_value(value):
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    if value is None:
        return ""
    return value


def collect_fieldnames(items):
    fieldnames = []
    seen = set()
    has_non_dict = False
    for item in items:
        if isinstance(item, dict):
            for key in item.keys():
                if key not in seen:
                    seen.add(key)
                    fieldnames.append(key)
        else:
            has_non_dict = True
    if has_non_dict and "value" not in seen:
        fieldnames.insert(0, "value")
    return fieldnames or ["value"]


def to_row(item, fieldnames):
    if isinstance(item, dict):
        return {key: normalize_value(item.get(key)) for key in fieldnames}
    row = {key: "" for key in fieldnames}
    if "value" in row:
        row["value"] = normalize_value(item)
    else:
        row[fieldnames[0]] = normalize_value(item)
    return row


def save_csv(items, path):
    fieldnames = collect_fieldnames(items)
    with open(path, "w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        for item in items:
            writer.writerow(to_row(item, fieldnames))


def paged_output_path(start_page, end_page):
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    return OUTPUT_DIR / f"influencers_p{start_page}_p{end_page}_{stamp}.csv"


if __name__ == "__main__":
    total_items, sample_items, interrupted = fetch_all(start_page=4350, max_pages=0)
    print("items:", total_items)
    print(json.dumps(sample_items, ensure_ascii=False, indent=2))
    if interrupted:
        print("interrupted: saved partial batches")
    else:
        print("saved: completed batches")
